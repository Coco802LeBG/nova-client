package com.nova;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Session {
	public static final Session I = new Session();

	public static class Auction {
		public ItemStack stack;
		public int qty, totalSec;
		public long min, worth, top, endMs;
		public String by = "";
	}

	public static class Record {
		public ItemStack stack;
		public int qty;
		public long min, worth, top;
		public String by;
	}

	public NovaConfig cfg;
	private Pattern paidYou, youPaid;
	private static class Pending {
		long amt, time;
		long[] entry;
		Pending(long a, long t, long[] e) { amt = a; time = t; entry = e; }
	}
	private final Map<String, Pending> pending = new HashMap<>();
	private final List<Pattern> winP = new ArrayList<>(), lossP = new ArrayList<>();

	public Auction cur;
	public Record banner;
	public long bannerUntil;
	public final List<Record> history = new ArrayList<>();

	public boolean gambleOn;
	public int wins, losses;
	public long gambleNet, auctionSales, lastDelta;
	private final List<long[]> gambleLog = new ArrayList<>();

	private Session() {
		reload();
	}

	public void reload() {
		cfg = NovaConfig.load();
		paidYou = Pattern.compile(cfg.paidYouPattern);
		youPaid = Pattern.compile(cfg.youPaidPattern);
		winP.clear();
		lossP.clear();
		for (String s : cfg.winPatterns) winP.add(Pattern.compile(s));
		for (String s : cfg.lossPatterns) lossP.add(Pattern.compile(s));
	}

	public long profit() {
		return auctionSales + gambleNet;
	}

	private static long now() {
		return System.currentTimeMillis();
	}

	// ---------- Auction ----------

	public void start(ItemStack held, long min, long worth, int sec) {
		Auction a = new Auction();
		a.stack = held.copy();
		a.qty = held.getCount();
		a.min = min;
		a.worth = worth * a.qty;
		a.totalSec = sec;
		a.endMs = now() + sec * 1000L;
		cur = a;
	}

	public void cancel() {
		cur = null;
	}

	public void redo(Record r, int sec) {
		Auction a = new Auction();
		a.stack = r.stack.copy();
		a.qty = r.qty;
		a.min = r.min;
		a.worth = r.worth;
		a.totalSec = sec;
		a.endMs = now() + sec * 1000L;
		cur = a;
	}

	public void tick(Minecraft mc) {
		if (cur != null && now() >= cur.endMs) finish(mc);
	}

	private void finish(Minecraft mc) {
		Record r = new Record();
		r.stack = cur.stack;
		r.qty = cur.qty;
		r.min = cur.min;
		r.worth = cur.worth;
		r.top = cur.top;
		r.by = cur.by;
		if (r.top > 0) {
			auctionSales += r.top;
			lastDelta = r.top;
			banner = r;
			bannerUntil = now() + 6000;
			mc.keyboardHandler.setClipboard(r.by);
		}
		history.add(0, r);
		if (history.size() > 30) history.remove(history.size() - 1);
		cur = null;
	}

	// ---------- Chat ----------

	public void onChat(String s) {
		if (cur != null) {
			Matcher m = paidYou.matcher(s);
			if (m.find()) {
				long amt = Money.parse(m.group(2));
				if (amt >= cur.min && amt > cur.top) {
					cur.top = amt;
					cur.by = m.group(1);
					if (cur.endMs - now() < 5000) cur.endMs = now() + 5000;
				}
			}
		}
		if (gambleOn && cur == null && cfg.autoGamble) trackPay(s);
		if (gambleOn) {
			for (Pattern p : winP) {
				Matcher m = p.matcher(s);
				if (m.find()) { gamble(true, amt(m)); return; }
			}
			for (Pattern p : lossP) {
				Matcher m = p.matcher(s);
				if (m.find()) { gamble(false, amt(m)); return; }
			}
		}
	}

	/** Someone pays you = win. You pay them back ~2x = that win becomes a loss. */
	private void trackPay(String s) {
		long t = now();
		pending.values().removeIf(p -> t - p.time > cfg.windowSec * 1000L);
		Matcher in = paidYou.matcher(s);
		if (in.find()) {
			long a = Money.parse(in.group(2));
			if (a > 0) {
				gamble(true, a);
				pending.put(in.group(1).toLowerCase(), new Pending(a, t, gambleLog.get(gambleLog.size() - 1)));
			}
			return;
		}
		Matcher out = youPaid.matcher(s);
		if (!out.find()) return;
		String who = out.group(1).toLowerCase();
		Pending p = pending.get(who);
		if (p == null) return;
		long b = Money.parse(out.group(2));
		if (b >= p.amt * cfg.payoutMultiplier) {
			wins--;
			gambleNet -= p.amt;
			losses++;
			long loss = b - p.amt;
			gambleNet -= loss;
			p.entry[0] = 0;
			p.entry[1] = loss;
			lastDelta = -loss;
			pending.remove(who);
		} else if (b >= p.amt * 0.99) {
			// refund of the same amount = push, not a win
			wins--;
			gambleNet -= p.amt;
			gambleLog.remove(p.entry);
			lastDelta = 0;
			pending.remove(who);
		}
	}

	private static long amt(Matcher m) {
		try {
			String a = m.group("amt");
			return a == null ? 0 : Money.parse(a);
		} catch (IllegalArgumentException e) {
			return 0;
		}
	}

	// ---------- Gamble ----------

	public void gamble(boolean win, long amount) {
		if (win) { wins++; gambleNet += amount; lastDelta = amount; }
		else { losses++; gambleNet -= amount; lastDelta = -amount; }
		gambleLog.add(new long[] { win ? 1 : 0, amount });
	}

	public void manual(boolean win) {
		if (!gambleOn) return;
		gamble(win, 0);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null)
			mc.player.displayClientMessage(Component.literal("[Nova] " + (win ? "Win" : "Loss") + " counted (" + wins + "W / " + losses + "L)"), true);
	}

	public void undo() {
		if (gambleLog.isEmpty()) return;
		long[] l = gambleLog.remove(gambleLog.size() - 1);
		pending.values().removeIf(p -> p.entry == l);
		if (l[0] == 1) { wins--; gambleNet -= l[1]; } else { losses--; gambleNet += l[1]; }
	}

	public void resetGamble() {
		wins = losses = 0;
		gambleNet = 0;
		gambleLog.clear();
		pending.clear();
	}

	public int winRate() {
		int t = wins + losses;
		return t == 0 ? 0 : Math.round(wins * 100f / t);
	}
}
