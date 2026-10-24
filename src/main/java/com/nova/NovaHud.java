package com.nova;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class NovaHud {
	static final int BG = 0xD00E1A42, BORDER = 0xFF2F6BFF, WHITE = 0xFFFFFFFF, MUTED = 0xFF8D9BC4,
			GREEN = 0xFF33D17A, RED = 0xFFFF5A6A;

	private NovaHud() {}

	static void panel(GuiGraphics g, int x, int y, int w, int h, int border) {
		g.fill(x, y, x + w, y + h, BG);
		g.fill(x, y, x + w, y + 1, border);
		g.fill(x, y + h - 1, x + w, y + h, border);
		g.fill(x, y, x + 1, y + h, border);
		g.fill(x + w - 1, y, x + w, y + h, border);
	}

	static void text(GuiGraphics g, Font f, String s, int x, int y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.drawString(f, s, 0, 0, color);
		g.pose().popMatrix();
	}

	public static void render(GuiGraphics g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.player == null) return;
		Font f = mc.font;
		Session s = Session.I;
		int sw = g.guiWidth();

		// ----- Auction toast (top center) -----
		int w = 220, h = 46, x = (sw - w) / 2, y = 6;
		if (s.cur != null) {
			Session.Auction a = s.cur;
			double left = Math.max(0, (a.endMs - System.currentTimeMillis()) / 1000.0);
			boolean urgent = left < 5;
			panel(g, x, y, w, h, urgent ? RED : BORDER);
			g.renderItem(a.stack, x + 8, y + 8);
			text(g, f, a.stack.getHoverName().getString() + " x" + a.qty, x + 32, y + 7, 1f, WHITE);
			text(g, f, a.top > 0 ? "Bid " + Money.fmt(a.top) + " (" + a.by + ")" : "No bids", x + 32, y + 19, 0.9f, MUTED);
			text(g, f, "Min " + Money.fmt(a.min) + (a.worth > 0 ? "  Worth " + Money.fmt(a.worth) : ""), x + 32, y + 29, 0.75f, MUTED);
			String t = String.format("%d:%02d", (int) left / 60, (int) left % 60);
			text(g, f, t, x + w - 8 - f.width(t) * 1.4f > 0 ? (int) (x + w - 10 - f.width(t) * 1.4f) : x, y + 10, 1.4f, urgent ? RED : WHITE);
			int bw = (int) ((w - 8) * Math.min(1.0, left / a.totalSec));
			g.fill(x + 4, y + h - 6, x + w - 4, y + h - 3, 0x30FFFFFF);
			g.fill(x + 4, y + h - 6, x + 4 + bw, y + h - 3, urgent ? RED : BORDER);
		} else if (s.banner != null && System.currentTimeMillis() < s.bannerUntil) {
			Session.Record r = s.banner;
			panel(g, x, y, w, h - 8, GREEN);
			g.renderItem(r.stack, x + 8, y + 8);
			text(g, f, r.by, x + 32, y + 7, 1.1f, WHITE);
			text(g, f, "won for " + Money.fmt(r.top), x + 32, y + 21, 0.95f, GREEN);
			text(g, f, "Name copied", x + w - 8 - f.width("Name copied") * 0.8f, y + 8, 0.8f, MUTED);
		}

		// ----- Session profit (top right) -----
		int pw = 130, ph = s.gambleOn ? 62 : 50, px = sw - pw - 6, py = 6;
		panel(g, px, py, pw, ph, s.profit() >= 0 ? GREEN : RED);
		text(g, f, "SESSION PROFIT", px + 8, py + 6, 0.7f, MUTED);
		long p = s.profit();
		text(g, f, (p < 0 ? "-$" : "$") + Money.fmt(Math.abs(p)), px + 8, py + 16, 1.6f, p >= 0 ? GREEN : RED);
		text(g, f, Money.signed(s.lastDelta), px + 8, py + 34, 0.9f, s.lastDelta >= 0 ? GREEN : RED);
		if (s.gambleOn)
			text(g, f, "W " + s.wins + "  L " + s.losses + "  " + s.winRate() + "%", px + 8, py + 47, 0.9f, WHITE);
	}
}
