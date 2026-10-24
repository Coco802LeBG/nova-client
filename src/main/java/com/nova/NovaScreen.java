package com.nova;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class NovaScreen extends Screen {
	private static final int W = 420, H = 240;
	private static final long[] MINS = { 1, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000 };
	private static final int[] SECS = { 10, 15, 30, 60 };
	private static final long[] WORTHS = { 0, 1_000_000, 10_000_000, 100_000_000, 500_000_000, 1_000_000_000 };
	private static final String[] TABS = { "Auction", "Recent", "Gamble", "Settings" };
	private static final double[] SPEEDS = { 0.5, 1.0, 1.5, 2.0 };
	private static final double[] SCALES = { 0.75, 1.0, 1.25, 1.5 };
	private static final String[] CORNERS = { "TL", "TR", "BL", "BR" };

	private static int tab = 0, minI = 3, secI = 1, worthI = 0;
	private int x0, y0, cx;
	private final long openMs = System.currentTimeMillis();
	private float barY = -1;

	public NovaScreen() {
		super(Component.literal("Nova"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static Component c(String s) {
		return Component.literal(s);
	}

	private void add(String label, int x, int y, int w, Runnable r) {
		addRenderableWidget(Button.builder(c(label), b -> r.run()).bounds(x, y, w, 20).build());
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
			add(TABS[i], x0 + 10, y0 + 34 + i * 24, 82, () -> { tab = t; rebuildWidgets(); });
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
			for (int i = 0; i < Math.min(5, s.history.size()); i++) {
				final int k = i;
				add("Redo", cx + 200, y0 + 40 + i * 28, 44, () -> { s.redo(s.history.get(k), SECS[secI]); s.history.remove(k); onClose(); });
				add("X", cx + 248, y0 + 40 + i * 28, 20, () -> { s.history.remove(k); rebuildWidgets(); });
			}
			add("Clear history", cx, y0 + H - 32, 110, () -> { s.history.clear(); rebuildWidgets(); });
		} else if (tab == 2) {
			add("Tracker: " + onoff(s.gambleOn), cx, y0 + 40, 170, () -> { s.gambleOn = !s.gambleOn; rebuildWidgets(); });
			add("+ Win", cx, y0 + 132, 54, () -> s.manual(true));
			add("+ Loss", cx + 58, y0 + 132, 54, () -> s.manual(false));
			add("Undo", cx + 116, y0 + 132, 54, s::undo);
			add("Reset", cx, y0 + 156, 170, s::resetGamble);
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
			add("Reload config", lx, y + 6 * d, bw, () -> { s.reload(); rebuildWidgets(); });
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (NovaClient.MENU.matches(event)) {
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
		g.drawString(font, Minecraft.getInstance().getUser().getName(), x0 + 14, y0 + H - 15, NovaHud.MUTED);

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
			g.drawString(font, "Recent auctions (" + s.history.size() + ")", cx, y0 + 14, NovaHud.WHITE);
			if (s.history.isEmpty()) g.drawString(font, "Nothing yet.", cx, y0 + 44, NovaHud.MUTED);
			for (int i = 0; i < Math.min(5, s.history.size()); i++) {
				Session.Record r = s.history.get(i);
				int y = y0 + 40 + i * 28;
				g.renderItem(r.stack, cx, y + 1);
				g.drawString(font, r.stack.getHoverName().getString() + " x" + r.qty, cx + 22, y, NovaHud.WHITE);
				g.drawString(font, r.top > 0 ? r.by + " won for $" + Money.fmt(r.top) : "No bids", cx + 22, y + 11, r.top > 0 ? NovaHud.GREEN : NovaHud.MUTED);
			}
		} else if (tab == 2) {
			g.drawString(font, "Gamble tracker", cx, y0 + 14, NovaHud.WHITE);
			g.drawString(font, "Wins: " + s.wins, cx, y0 + 70, NovaHud.GREEN);
			g.drawString(font, "Losses: " + s.losses, cx, y0 + 82, NovaHud.RED);
			g.drawString(font, "Win rate: " + s.winRate() + "%", cx, y0 + 94, NovaHud.WHITE);
			g.drawString(font, "Net: " + Money.signed(s.gambleNet), cx, y0 + 106, s.gambleNet >= 0 ? NovaHud.GREEN : NovaHud.RED);
			g.drawString(font, "Auto: paid you = win, you pay back 2x = loss", cx, y0 + 172, NovaHud.MUTED);
			g.drawString(font, "Numpad 1 = win, Numpad 2 = loss (menu closed)", cx, y0 + 184, NovaHud.MUTED);
		} else {
			g.drawString(font, "Settings", cx, y0 + 14, NovaHud.WHITE);
			g.drawString(font, "Saved automatically in config/nova.json", cx, y0 + 216, NovaHud.MUTED);
		}

		g.pose().popMatrix();
	}
}
