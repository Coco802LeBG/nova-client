package com.nova;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class NovaScreen extends Screen {
	private static final int W = 500, H = 260, ROWS = 5;
	private static final long[] MINS = { 1, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000 };
	private static final int[] SECS = { 10, 15, 30, 60 };
	private static final long[] WORTHS = { 0, 1_000_000, 10_000_000, 100_000_000, 500_000_000, 1_000_000_000 };
	private static final String[] TABS = { "Auction", "Recent", "Bets", "Gamble", "Balance", "Nametag", "Settings" };
	private static final double[] SPEEDS = { 0.5, 1.0, 1.5, 2.0 };
	private static final double[] VOLS = { 0.25, 0.5, 0.8, 1.0 };
	private static final double[] SCALES = { 0.75, 1.0, 1.25, 1.5 };
	private static final String[] CORNERS = { "TL", "TR", "BL", "BR" };

	private static int tab = 0, minI = 3, secI = 1, worthI = 0;
	private int x0, y0, cx;
	private final long openMs = System.currentTimeMillis();
	private float barY = -1;
	private long tabMs = System.currentTimeMillis();
	private static int histScroll = 0;

	public NovaScreen() {
		super(Component.literal("Nova"));
		Sfx.menu();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static Component c(String s) {
		return Component.literal(s);
	}

	private void add(String label, int x, int y, int w, Runnable r) {
		addRenderableWidget(Button.builder(c(label), b -> { Sfx.click(); r.run(); }).bounds(x, y, w, 20).build());
	}

	private static String onoff(boolean b) {
		return b ? "ON" : "OFF";
	}

	private static double nxt(double v, double[] a) {
		for (int i = 0; i < a.length; i++) if (Math.abs(a[i] - v) < 1e-6) return a[(i + 1) % a.length];
		return a[0];
	}

	private static String nextCorner(String v) {
		for (int i = 0; i < CORNERS.length; i++) if (CORNERS[i].equals(v)) return CORNERS[(i + 1) % CORNERS.length];
		return CORNERS[0];
	}

	private List<Object> rowsData() {
		List<Object> l = new ArrayList<>();
		if (Session.I.cur != null) l.add(Session.I.cur);
		l.addAll(Session.I.history);
		return l;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (tab == 1) {
			int max = Math.max(0, rowsData().size() - ROWS);
			int n = (int) Math.max(0, Math.min(max, histScroll - Math.signum(sy)));
			if (n != histScroll) {
				histScroll = n;
				rebuildWidgets();
			}
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	private void upd() {
		Session.I.cfg.save();
		rebuildWidgets();
	}

	@Override
	protected void init() {
		x0 = (width - W) / 2;
		y0 = (height - H) / 2;
		cx = x0 + 104;
		Session s = Session.I;
		NovaConfig cfg = s.cfg;

		for (int i = 0; i < TABS.length; i++) {
			final int t = i;
			add(TABS[i], x0 + 10, y0 + 34 + i * 24, 82, () -> { tab = t; tabMs = System.currentTimeMillis(); histScroll = 0; rebuildWidgets(); });
		}

		if (tab == 0) {
			add("Min bid: " + Money.fmt(MINS[minI]), cx, y0 + 76, 170, () -> { minI = (minI + 1) % MINS.length; rebuildWidgets(); });
			add("Timer: " + SECS[secI] + "s", cx, y0 + 100, 170, () -> { secI = (secI + 1) % SECS.length; rebuildWidgets(); });
			add("Worth each: " + (WORTHS[worthI] == 0 ? "none" : Money.fmt(WORTHS[worthI])), cx, y0 + 124, 170, () -> { worthI = (worthI + 1) % WORTHS.length; rebuildWidgets(); });
			if (s.cur != null) {
				add("Cancel auction", cx, y0 + 156, 170, () -> { s.cancel(); onClose(); });
			} else {
				add("Start auction (held item)", cx, y0 + 156, 170, () -> {
					Minecraft mc = Minecraft.getInstance();
					ItemStack held = mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
					if (held.isEmpty()) return;
					s.start(held, MINS[minI], WORTHS[worthI], SECS[secI]);
					onClose();
				});
			}
		} else if (tab == 1) {
			List<Object> d = rowsData();
			histScroll = Math.min(histScroll, Math.max(0, d.size() - ROWS));
			add("Clear History", x0 + W - 116, y0 + 12, 104, () -> { s.history.clear(); histScroll = 0; rebuildWidgets(); });
			for (int i = 0; i < ROWS && histScroll + i < d.size(); i++) {
				Object o = d.get(histScroll + i);
				if (o instanceof Session.Record rec) {
					int ry = y0 + 58 + i * 34;
					Button rb = Button.builder(c("Redo"), b -> {
						Sfx.click();
						if (s.cur == null) { s.redo(rec, SECS[secI]); s.history.remove(rec); onClose(); }
					}).bounds(cx + 282, ry + 6, 40, 20).build();
					rb.active = s.cur == null;
					addRenderableWidget(rb);
					add("X", cx + 366, ry + 6, 18, () -> { s.history.remove(rec); rebuildWidgets(); });
				}
			}
		} else if (tab == 2) {
			add("Clear bets", cx, y0 + H - 32, 110, () -> { s.bets.clear(); rebuildWidgets(); });
		} else if (tab == 3) {
			add("Tracker: " + onoff(s.gambleOn), cx, y0 + 40, 170, () -> { s.gambleOn = !s.gambleOn; rebuildWidgets(); });
			add("+ Win", cx, y0 + 132, 54, () -> s.manual(true));
			add("+ Loss", cx + 58, y0 + 132, 54, () -> s.manual(false));
			add("Undo", cx + 116, y0 + 132, 54, s::undo);
			add("Reset W/L", cx, y0 + 156, 84, s::resetGamble);
			add("Reset profit", cx + 86, y0 + 156, 84, s::resetProfit);
		} else if (tab == 4) {
			add("Reset balance", cx, y0 + 168, 170, s::resetBalance);
		} else if (tab == 5) {
			int lx = cx, rx = cx + 156, y = y0 + 34, bw = 150;
			add("Nametag: " + onoff(cfg.nickEnabled), lx, y, bw, () -> { cfg.nickEnabled = !cfg.nickEnabled; upd(); });
			add("Bold: " + onoff(cfg.nickBold), rx, y, bw, () -> { cfg.nickBold = !cfg.nickBold; upd(); });
			add("Name color: " + cfg.nickColor, lx, y + 22, bw, () -> { cfg.nickColor = Nick.next(cfg.nickColor); upd(); });
			add("Star color: " + cfg.nickStarColor, rx, y + 22, bw, () -> { cfg.nickStarColor = Nick.next(cfg.nickStarColor); upd(); });
			add("Stars: " + cfg.nickStars, lx, y + 44, bw, () -> { cfg.nickStars = (cfg.nickStars + 1) % 6; upd(); });
			add("Star style: " + cfg.nickStarSymbol, rx, y + 44, bw, () -> { cfg.nickStarSymbol = Nick.nextStar(cfg.nickStarSymbol); upd(); });
			add("Stars position: " + cfg.nickStarPos, lx, y + 66, bw, () -> { cfg.nickStarPos = Nick.nextPos(cfg.nickStarPos); upd(); });
			EditBox pre = new EditBox(font, lx, y0 + 140, 72, 18, c("Prefix"));
			pre.setMaxLength(12);
			pre.setValue(cfg.nickPrefix);
			pre.setResponder(v -> { cfg.nickPrefix = v; cfg.save(); });
			addRenderableWidget(pre);
			EditBox suf = new EditBox(font, lx + 78, y0 + 140, 72, 18, c("Suffix"));
			suf.setMaxLength(12);
			suf.setValue(cfg.nickSuffix);
			suf.setResponder(v -> { cfg.nickSuffix = v; cfg.save(); });
			addRenderableWidget(suf);
		} else {
			int lx = cx, rx = cx + 156, y = y0 + 34, d = 22, bw = 150;
			add("Theme: " + cfg.theme, lx, y, bw, () -> { cfg.theme = NovaHud.nextTheme(cfg.theme); upd(); });
			add("Animations: " + onoff(cfg.animations), rx, y, bw, () -> { cfg.animations = !cfg.animations; upd(); });
			add("Anim speed: " + cfg.animSpeed + "x", lx, y + d, bw, () -> { cfg.animSpeed = nxt(cfg.animSpeed, SPEEDS); upd(); });
			add("HUD scale: " + cfg.hudScale + "x", rx, y + d, bw, () -> { cfg.hudScale = nxt(cfg.hudScale, SCALES); upd(); });
			add("Profit corner: " + cfg.profitCorner, lx, y + 2 * d, bw, () -> { cfg.profitCorner = nextCorner(cfg.profitCorner); upd(); });
			add("Toast: " + (cfg.toastBottom ? "Bottom" : "Top"), rx, y + 2 * d, bw, () -> { cfg.toastBottom = !cfg.toastBottom; upd(); });
			add("Confetti: " + onoff(cfg.confetti), lx, y + 3 * d, bw, () -> { cfg.confetti = !cfg.confetti; upd(); });
			add("Menu anim: " + onoff(cfg.menuAnim), rx, y + 3 * d, bw, () -> { cfg.menuAnim = !cfg.menuAnim; upd(); });
			add("Show profit: " + onoff(cfg.showProfit), lx, y + 4 * d, bw, () -> { cfg.showProfit = !cfg.showProfit; upd(); });
			add("Show toast: " + onoff(cfg.showToast), rx, y + 4 * d, bw, () -> { cfg.showToast = !cfg.showToast; upd(); });
			add("Gamble line: " + onoff(cfg.showGambleLine), lx, y + 5 * d, bw, () -> { cfg.showGambleLine = !cfg.showGambleLine; upd(); });
			add("Preview win", rx, y + 5 * d, bw, () -> {
				Minecraft mc = Minecraft.getInstance();
				s.preview(mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem());
			});
			add("Sounds: " + onoff(cfg.sounds), lx, y + 6 * d, bw, () -> { cfg.sounds = !cfg.sounds; upd(); });
			add("Volume: " + (int) Math.round(cfg.soundVolume * 100) + "%", rx, y + 6 * d, bw, () -> { cfg.soundVolume = nxt(cfg.soundVolume, VOLS); upd(); });
			add("Tick sounds: " + onoff(cfg.soundTick), lx, y + 7 * d, bw, () -> { cfg.soundTick = !cfg.soundTick; upd(); });
			add("Menu sound: " + onoff(cfg.soundMenu), rx, y + 7 * d, bw, () -> { cfg.soundMenu = !cfg.soundMenu; upd(); });
			add("Click sound: " + onoff(cfg.soundClick), lx, y + 8 * d, bw, () -> { cfg.soundClick = !cfg.soundClick; upd(); });
			add("Test sounds", rx, y + 8 * d, bw, Sfx::test);
			add("Reload config", lx, y + 9 * d, bw, () -> { s.reload(); rebuildWidgets(); });
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (NovaClient.MENU.matches(event) && !(getFocused() instanceof EditBox)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		Session s = Session.I;
		NovaHud.applyTheme();

		// open animation (scale + settle)
		boolean anim = s.cfg.animations && s.cfg.menuAnim;
		float p = 1f;
		if (anim) p = Math.min(1f, (System.currentTimeMillis() - openMs) / (260f / (float) Math.max(0.1, s.cfg.animSpeed)));
		float sc = anim ? 0.88f + 0.12f * NovaHud.easeOutBack(p) : 1f;
		g.pose().pushMatrix();
		g.pose().translate(width / 2f, height / 2f);
		g.pose().scale(sc, sc);
		g.pose().translate(-width / 2f, -height / 2f);

		NovaHud.panel(g, x0, y0, W, H, NovaHud.BORDER);
		NovaHud.rr(g, x0 + 5, y0 + 5, 94, H - 10, 7, 0x66000000);
		// sliding tab indicator
		float target = y0 + 36 + tab * 24;
		if (barY < 0 || !anim) barY = target;
		barY += (target - barY) * 0.28f;
		NovaHud.rr(g, x0 + 5, (int) barY, 3, 16, 1, NovaHud.BORDER);
		g.fillGradient(x0 + 5, y0 + 5, x0 + W - 5, y0 + 9, NovaHud.alpha(NovaHud.BORDER, 0x40), 0x00000000);

		super.render(g, mx, my, pt);

		g.drawString(font, "NOVA", x0 + 14, y0 + 14, NovaHud.ACCENT2);
		g.drawString(font, Nick.display(Minecraft.getInstance().getUser().getName()), x0 + 14, y0 + H - 15, NovaHud.MUTED);

		if (tab == 0) {
			ItemStack held = Minecraft.getInstance().player == null ? ItemStack.EMPTY : Minecraft.getInstance().player.getMainHandItem();
			g.drawString(font, "New auction", cx, y0 + 14, NovaHud.WHITE);
			if (held.isEmpty()) {
				g.drawString(font, "Hold the item you want to auction", cx, y0 + 50, NovaHud.MUTED);
			} else {
				g.renderItem(held, cx, y0 + 44);
				g.drawString(font, held.getHoverName().getString() + " x" + held.getCount(), cx + 22, y0 + 48, NovaHud.WHITE);
			}
			g.drawString(font, "Bids = chat lines \"X paid you $Y\"", cx, y0 + 184, NovaHud.MUTED);
		} else if (tab == 1) {
			g.drawString(font, "Recent Auctions", cx, y0 + 14, NovaHud.WHITE);
			NovaHud.pill(g, font, String.valueOf(s.history.size()), cx + font.width("Recent Auctions") + 8, y0 + 11, NovaHud.PILL, NovaHud.ACCENT2, 0.8f);
			NovaHud.text(g, font, "Your auction history. Press Redo to run one again.", cx, y0 + 31, 0.8f, NovaHud.MUTED);
			List<Object> d = rowsData();
			if (d.isEmpty()) g.drawString(font, "Nothing yet.", cx, y0 + 64, NovaHud.MUTED);
			long ms = System.currentTimeMillis() - tabMs;
			long nowMs = System.currentTimeMillis();
			for (int i = 0; i < ROWS && histScroll + i < d.size(); i++) {
				Object o = d.get(histScroll + i);
				int ry = y0 + 58 + i * 34;
				float pr = Math.max(0f, Math.min(1f, (ms - i * 55) / 240f));
				float e = 1f - (1f - pr) * (1f - pr) * (1f - pr);
				boolean live = o instanceof Session.Auction;
				ItemStack st;
				String name, sub, by = "";
				long min, top, worth;
				int qty;
				if (o instanceof Session.Auction a) {
					st = a.stack; qty = a.qty; min = a.min; top = a.top; worth = a.worth;
					name = st.getHoverName().getString();
					sub = "x" + qty + " \u2022 " + a.totalSec + "s timer";
				} else {
					Session.Record r = (Session.Record) o;
					st = r.stack; qty = r.qty; min = r.min; top = r.top; worth = r.worth;
					name = st.getHoverName().getString();
					sub = "x" + qty + (r.top > 0 ? " \u2022 won by " + r.by : " \u2022 no bids");
				}
				long worthEach = qty > 0 ? worth / qty : 0;
				g.pose().pushMatrix();
				g.pose().translate((int) ((1f - e) * 26), 0);
				NovaHud.rr(g, cx, ry, 388, 32, 8, live ? NovaHud.alpha(NovaHud.BORDER, (int) (0x90 * e)) : NovaHud.alpha(0x0C0618, (int) (0xCC * e)));
				NovaHud.rr(g, cx + 1, ry + 1, 386, 30, 7, live ? NovaHud.alpha(0x160D2B, (int) (0xF0 * e)) : NovaHud.alpha(0x120A22, (int) (0xCC * e)));
				if (live) NovaHud.rr(g, cx, ry + 5, 3, 22, 1, NovaHud.BORDER);
				NovaHud.iconBox(g, st, cx + 6, ry + 3, 26);
				NovaHud.text(g, font, name.length() > 16 ? name.substring(0, 16) : name, cx + 38, ry + 6, 1.0f, NovaHud.WHITE);
				NovaHud.text(g, font, sub, cx + 38, ry + 19, 0.75f, NovaHud.MUTED);
				NovaHud.text(g, font, "MIN BID", cx + 124, ry + 5, 0.55f, NovaHud.MUTED);
				NovaHud.text(g, font, "$" + Money.fmt(min), cx + 124, ry + 15, 0.95f, NovaHud.WHITE);
				NovaHud.text(g, font, "TOP BID", cx + 170, ry + 5, 0.55f, NovaHud.MUTED);
				String tv = top > 0 ? "$" + Money.fmt(top) : "-";
				NovaHud.text(g, font, tv, cx + 170, ry + 15, 0.95f, worth > 0 && top >= worth ? NovaHud.GREEN : NovaHud.WHITE);
				if (worth > 0 && top > 0)
					NovaHud.text(g, font, (top * 100 / worth) + "%", cx + 170 + NovaHud.tw(font, tv, 0.95f) + 3, ry + 17, 0.6f, NovaHud.MUTED);
				NovaHud.text(g, font, "WORTH EACH", cx + 222, ry + 5, 0.55f, NovaHud.MUTED);
				NovaHud.text(g, font, worthEach > 0 ? "$" + Money.fmt(worthEach) : "-", cx + 222, ry + 15, 0.95f, NovaHud.WHITE);
				if (o instanceof Session.Auction a) {
					NovaHud.rr(g, cx + 282, ry + 6, 40, 20, 6, 0x30FFFFFF);
					NovaHud.text(g, font, "Redo", cx + 282 + (40 - NovaHud.tw(font, "Redo", 1f)) / 2, ry + 12, 1f, NovaHud.MUTED);
					long secs = Math.max(0, (a.endMs - nowMs + 999) / 1000);
					NovaHud.pill(g, font, secs + "s", cx + 330, ry + 9, NovaHud.BORDER, NovaHud.WHITE, 0.85f);
				} else {
					Session.Record r = (Session.Record) o;
					if (r.top > 0) NovaHud.pill(g, font, "Sold", cx + 330, ry + 9, 0xFF1F8F4E, NovaHud.WHITE, 0.85f);
					else NovaHud.pill(g, font, "No bids", cx + 320, ry + 9, NovaHud.PILL, NovaHud.MUTED, 0.85f);
				}
				g.pose().popMatrix();
			}
			if (d.size() > ROWS) NovaHud.text(g, font, "Scroll for more (" + d.size() + ")", cx, y0 + H - 14, 0.75f, NovaHud.MUTED);
		} else if (tab == 2) {
			g.drawString(font, "Bets (" + s.bets.size() + ")", cx, y0 + 14, NovaHud.WHITE);
			if (s.bets.isEmpty()) g.drawString(font, "No bets yet. Turn the tracker ON in Gamble.", cx, y0 + 44, NovaHud.MUTED);
			for (int i = 0; i < Math.min(9, s.bets.size()); i++) {
				Session.Bet b = s.bets.get(i);
				int y = y0 + 34 + i * 16;
				g.drawString(font, b.who, cx, y, NovaHud.WHITE);
				g.drawString(font, (b.win ? "Win +$" : "Loss -$") + Money.fmt(b.amt), cx + 130, y, b.win ? NovaHud.GREEN : NovaHud.RED);
			}
		} else if (tab == 3) {
			g.drawString(font, "Gamble tracker", cx, y0 + 14, NovaHud.WHITE);
			g.drawString(font, "Wins: " + s.wins, cx, y0 + 70, NovaHud.GREEN);
			g.drawString(font, "Losses: " + s.losses, cx, y0 + 82, NovaHud.RED);
			g.drawString(font, "Win rate: " + s.winRate() + "%", cx, y0 + 94, NovaHud.WHITE);
			g.drawString(font, "Net: " + Money.signed(s.gambleNet), cx, y0 + 106, s.gambleNet >= 0 ? NovaHud.GREEN : NovaHud.RED);
			g.drawString(font, "Auto: paid you = win, you pay back 2x = loss", cx, y0 + 172, NovaHud.MUTED);
			g.drawString(font, "Numpad 1 = win, Numpad 2 = loss (menu closed)", cx, y0 + 184, NovaHud.MUTED);
			String lp = s.lastPay.isEmpty() ? "(none yet)" : s.lastPay;
			if (lp.length() > 46) lp = lp.substring(0, 46) + "...";
			g.drawString(font, "Messages seen: " + s.seen + "  Last pay: " + lp, cx, y0 + 198, NovaHud.ACCENT2);
		} else if (tab == 4) {
			long secs = (System.currentTimeMillis() - s.startMs) / 1000;
			double hrs = Math.max(secs, 60) / 3600.0;
			long perH = (long) (s.profit() / hrs);
			g.drawString(font, "Balance", cx, y0 + 14, NovaHud.WHITE);
			g.drawString(font, "Session time: " + (secs / 3600) + "h " + ((secs % 3600) / 60) + "m", cx, y0 + 38, NovaHud.MUTED);
			g.drawString(font, "Received: $" + Money.fmt(s.received), cx, y0 + 54, NovaHud.GREEN);
			g.drawString(font, "Sent (/pay): $" + Money.fmt(s.sent), cx, y0 + 66, NovaHud.RED);
			g.drawString(font, "Net flow: " + Money.signed(s.received - s.sent), cx, y0 + 78, NovaHud.WHITE);
			g.drawString(font, "Auction sales: " + Money.signed(s.auctionSales), cx, y0 + 98, NovaHud.WHITE);
			g.drawString(font, "Gamble net: " + Money.signed(s.gambleNet), cx, y0 + 110, NovaHud.WHITE);
			g.drawString(font, "Profit: " + Money.signed(s.profit()), cx, y0 + 122, s.profit() >= 0 ? NovaHud.GREEN : NovaHud.RED);
			g.drawString(font, "Per hour: " + Money.signed(perH), cx, y0 + 138, NovaHud.ACCENT2);
			g.drawString(font, "Sent counts /pay commands you type.", cx, y0 + 198, NovaHud.MUTED);
		} else if (tab == 5) {
			g.drawString(font, "Nametag", cx, y0 + 14, NovaHud.WHITE);
			g.drawString(font, "Extra text before / after (optional)", cx, y0 + 128, NovaHud.MUTED);
			g.drawString(font, "Preview:", cx, y0 + 172, NovaHud.MUTED);
			g.drawString(font, Nick.styled(Minecraft.getInstance().getUser().getName(), net.minecraft.network.chat.Style.EMPTY), cx + 50, y0 + 172, NovaHud.WHITE);
			g.drawString(font, "Only you see this (chat messages with your name).", cx, y0 + 198, NovaHud.MUTED);
		} else {
			g.drawString(font, "Settings", cx, y0 + 14, NovaHud.WHITE);
			g.drawString(font, "Saved automatically in config/nova.json", cx, y0 + H - 12, NovaHud.MUTED);
		}

		g.pose().popMatrix();
	}
}
