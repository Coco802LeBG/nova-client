package com.nova;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class NovaHud {
	// Theme colors (changed by applyTheme)
	static int BG = 0xE8130B26, BG_DARK = 0xFF0C0618, PILL = 0xFF2A1A4D, BORDER = 0xFF9B5CFF, ACCENT2 = 0xFFC77DFF;
	static final int WHITE = 0xFFFFFFFF, MUTED = 0xFFA79BC9, GREEN = 0xFF4ADE80, RED = 0xFFFB7185;

	static final String[] THEMES = { "Purple", "Blue", "Pink", "Green", "Cyan", "Orange", "Red" };
	// accent, accent2, bg, bgDark, pill
	private static final int[][] TH = {
			{ 0xFF9B5CFF, 0xFFC77DFF, 0xE8130B26, 0xFF0C0618, 0xFF2A1A4D },
			{ 0xFF2F6BFF, 0xFF7FA8FF, 0xE80B1230, 0xFF060A1E, 0xFF1A2A5E },
			{ 0xFFFF5CA8, 0xFFFF9CCB, 0xE8260B1A, 0xFF180610, 0xFF4D1A33 },
			{ 0xFF33D17A, 0xFF7BF0AE, 0xE80B2616, 0xFF06180E, 0xFF1A4D2E },
			{ 0xFF22D3EE, 0xFF7BE8F7, 0xE80B2330, 0xFF06141C, 0xFF1A3F4D },
			{ 0xFFFF9A3C, 0xFFFFC27D, 0xE8261A0B, 0xFF180F06, 0xFF4D331A },
			{ 0xFFFF4D5E, 0xFFFF8A96, 0xE8260B10, 0xFF180608, 0xFF4D1A20 } };

	private static final ItemStack COIN = new ItemStack(Items.GOLD_INGOT);
	private static final Random R = new Random();

	// animation state
	static float glow = 0.5f;
	private static long lastNs = System.nanoTime();
	private static float glowT = 0, pulseT = 0, toastP = 0, flash = 0;
	private static Session.Auction lastA;
	private static Session.Record lastBanner;
	private static double shown = 0;
	private static long lastTarget = 0;

	private static final class Pop { String txt; int col; float life = 1.4f; }
	private static final class Conf { float x, y, vx, vy, life; int col; }
	private static final List<Pop> pops = new ArrayList<>();
	private static final List<Conf> conf = new ArrayList<>();

	private NovaHud() {}

	// ---------- theme / color helpers ----------

	static void applyTheme() {
		String n = Session.I.cfg.theme;
		int i = 0;
		for (int k = 0; k < THEMES.length; k++) if (THEMES[k].equalsIgnoreCase(n)) i = k;
		int[] t = TH[i];
		BORDER = t[0]; ACCENT2 = t[1]; BG = t[2]; BG_DARK = t[3]; PILL = t[4];
	}

	static String nextTheme(String cur) {
		for (int k = 0; k < THEMES.length; k++) if (THEMES[k].equalsIgnoreCase(cur)) return THEMES[(k + 1) % THEMES.length];
		return THEMES[0];
	}

	static int lerp(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int r = 0;
		for (int sh = 0; sh <= 24; sh += 8) {
			int ca = (a >> sh) & 255, cb = (b >> sh) & 255;
			r |= (((int) (ca + (cb - ca) * t)) & 255) << sh;
		}
		return r;
	}

	static int alpha(int c, int a) {
		return (Math.max(8, Math.min(255, a)) << 24) | (c & 0xFFFFFF);
	}

	static float easeOutBack(float t) {
		t = Math.max(0, Math.min(1, t));
		float c1 = 1.70158f, c3 = c1 + 1;
		double u = t - 1;
		return (float) (1 + c3 * u * u * u + c1 * u * u);
	}

	// ---------- drawing helpers ----------

	static void rr(GuiGraphics g, int x, int y, int w, int h, int r, int c) {
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;
			int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
			g.fill(x + inset, y + i, x + w - inset, y + i + 1, c);
			g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, c);
		}
		g.fill(x, y + r, x + w, y + h - r, c);
	}

	/** Dark rounded panel with a breathing colored glow. */
	static void panel(GuiGraphics g, int x, int y, int w, int h, int border) {
		int ga = (int) (0x14 + 0x2C * glow);
		rr(g, x - 3, y - 3, w + 6, h + 6, 11, alpha(border, ga / 2));
		rr(g, x - 1, y - 1, w + 2, h + 2, 9, alpha(border, ga));
		rr(g, x, y, w, h, 8, border);
		rr(g, x + 1, y + 1, w - 2, h - 2, 7, BG);
		g.fillGradient(x + 3, y + 1, x + w - 3, y + 16, alpha(border, 0x30), 0x00000000);
	}

	static int tw(Font f, String s, float scale) {
		return (int) Math.ceil(f.width(s) * scale);
	}

	static void text(GuiGraphics g, Font f, String s, int x, int y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.drawString(f, s, 0, 0, color);
		g.pose().popMatrix();
	}

	static int pill(GuiGraphics g, Font f, String s, int x, int y, int bg, int fg, float scale) {
		int w = tw(f, s, scale) + 14, h = (int) (8 * scale) + 7;
		rr(g, x, y, w, h, h / 2, bg);
		text(g, f, s, x + 7, y + 4, scale, fg);
		return w;
	}

	static void item(GuiGraphics g, ItemStack st, int x, int y, float scale) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.renderItem(st, 0, 0);
		g.pose().popMatrix();
	}

	static void iconBox(GuiGraphics g, ItemStack st, int x, int y, int size) {
		rr(g, x, y, size, size, 8, BG_DARK);
		rr(g, x + 1, y + 1, size - 2, size - 2, 7, lerp(BG_DARK, PILL, 0.35f));
		float sc = size >= 36 ? 1.5f : 1.25f;
		int off = (int) ((size - 16 * sc) / 2);
		item(g, st, x + off, y + off, sc);
	}

	static void bar(GuiGraphics g, int x, int y, int w, int h, double frac, int color) {
		rr(g, x, y, w, h, h / 2, 0x33FFFFFF);
		int fw = Math.max(h, (int) (w * Math.max(0, Math.min(1, frac))));
		rr(g, x, y, fw, h, h / 2, color);
		g.fillGradient(x + h / 2, y, x + Math.max(h / 2 + 1, fw - h / 2), y + Math.max(1, h / 2), 0x44FFFFFF, 0x00FFFFFF);
	}

	private static void burst(int cx, int cy) {
		int[] pal = { BORDER, ACCENT2, GREEN, 0xFFFFD166, WHITE };
		for (int i = 0; i < 80; i++) {
			Conf c = new Conf();
			double ang = R.nextDouble() * Math.PI * 2, sp = 60 + R.nextDouble() * 260;
			c.x = cx;
			c.y = cy;
			c.vx = (float) (Math.cos(ang) * sp);
			c.vy = (float) (Math.sin(ang) * sp - 120);
			c.life = 1.2f + R.nextFloat() * 1.0f;
			c.col = pal[R.nextInt(pal.length)];
			conf.add(c);
		}
	}

	// ---------- main render ----------

	public static void render(GuiGraphics g, DeltaTracker dt0) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.player == null) return;
		Font f = mc.font;
		Session s = Session.I;
		NovaConfig c = s.cfg;
		applyTheme();

		long nn = System.nanoTime();
		float dt = Math.min(0.1f, (nn - lastNs) / 1e9f);
		lastNs = nn;
		boolean an = c.animations;
		float spd = (float) Math.max(0.1, c.animSpeed);
		if (an) { glowT += dt * 2f * spd; pulseT += dt * 9f * spd; }
		glow = an ? 0.5f + 0.5f * (float) Math.sin(glowT) : 0.5f;
		float pulse = an ? 0.5f + 0.5f * (float) Math.sin(pulseT) : 1f;

		float sc = (float) Math.max(0.5, c.hudScale);
		int sw = (int) (g.guiWidth() / sc), gh = (int) (g.guiHeight() / sc);
		long now = System.currentTimeMillis();

		g.pose().pushMatrix();
		g.pose().scale(sc, sc);

		// ===== Auction toast =====
		Session.Auction a = s.cur;
		boolean ban = s.banner != null && now < s.bannerUntil;
		if (a != null) lastA = a;
		if (s.banner != null && s.banner != lastBanner) {
			lastBanner = s.banner;
			if (c.confetti) burst(sw / 2, c.toastBottom ? gh - 90 : 30);
		}
		boolean active = c.showToast && (a != null || ban);
		float tgt = active ? 1f : 0f;
		toastP = an ? toastP + (tgt - toastP) * Math.min(1f, dt * 9f * spd) : tgt;
		if (toastP > 0.01f && (a != null || ban || lastA != null)) {
			boolean isBanner = a == null && ban;
			Session.Auction ad = a != null ? a : lastA;
			int w = 250, h = isBanner ? 52 : 74, x = (sw - w) / 2;
			int baseY = c.toastBottom ? gh - h - 62 : 8;
			int slide = c.toastBottom ? (int) ((h + 70) * (1 - toastP)) : -(int) ((h + 14) * (1 - toastP));
			int y = baseY + slide;
			if (isBanner) {
				Session.Record r = s.banner;
				panel(g, x, y, w, h, GREEN);
				iconBox(g, r.stack, x + 8, y + 8, 36);
				text(g, f, r.by, x + 52, y + 11, 1.2f, WHITE);
				text(g, f, "won for $" + Money.fmt(r.top), x + 52, y + 27, 1.0f, GREEN);
				String nc = "Name copied";
				int pw = tw(f, nc, 0.8f) + 14;
				pill(g, f, nc, x + w - pw - 8, y + 12, PILL, ACCENT2, 0.8f);
			} else if (ad != null) {
				double left = Math.max(0, (ad.endMs - now) / 1000.0);
				boolean urgent = left < 5 && a != null;
				int col = urgent ? lerp(RED, 0xFFFFB3BA, pulse) : BORDER;
				panel(g, x, y, w, h, col);
				iconBox(g, ad.stack, x + 8, y + 8, 38);
				String title = ad.top > 0 ? ad.by + " \u2022 $" + Money.fmt(ad.top) : "No bids";
				text(g, f, title, x + 54, y + 11, 1.2f, WHITE);
				pill(g, f, "Min " + Money.fmt(ad.min), x + 54, y + 26, PILL, WHITE, 0.85f);
				String t = String.format("%d:%02d", (int) left / 60, (int) left % 60);
				int tpw = Math.max(56, tw(f, t, 1.4f) + 22);
				rr(g, x + w - tpw - 8, y + 12, tpw, 26, 13, col);
				rr(g, x + w - tpw - 7, y + 13, tpw - 2, 24, 12, BG_DARK);
				text(g, f, t, x + w - tpw - 8 + (tpw - tw(f, t, 1.4f)) / 2, y + 20, 1.4f, urgent ? lerp(RED, WHITE, 1 - pulse) : WHITE);
				int dotA = (int) (140 + 115 * (an ? pulse : 1f));
				g.fill(x + 10, y + 49, x + 13, y + 52, alpha(col, dotA));
				text(g, f, ad.totalSec + " Seconds To Make Order", x + 17, y + 47, 0.85f, WHITE);
				if (ad.worth > 0) {
					String wt = "Worth " + Money.fmt(ad.worth);
					int pw = tw(f, wt, 0.85f) + 14;
					pill(g, f, wt, x + w - pw - 8, y + 43, col, WHITE, 0.85f);
				}
				bar(g, x + 8, y + h - 17, w - 16, 5, left / ad.totalSec, col);
				String cl = "Nova Client";
				text(g, f, cl, x + (w - tw(f, cl, 0.65f)) / 2, y + h - 10, 0.65f, MUTED);
			}
		}

		// ===== Session profit =====
		long target = s.profit();
		if (target != lastTarget) {
			Pop p = new Pop();
			long d = target - lastTarget;
			p.txt = Money.signed(d);
			p.col = d >= 0 ? GREEN : RED;
			pops.add(p);
			flash = 1f;
			lastTarget = target;
		}
		shown = an ? shown + (target - shown) * Math.min(1.0, dt * 6.0 * spd) : target;
		if (Math.abs(target - shown) < 1) shown = target;
		flash = Math.max(0, flash - dt * 2.5f);

		if (c.showProfit) {
			long pv = Math.round(shown);
			String val = (pv < 0 ? "-$" : "$") + Money.fmt(Math.abs(pv));
			boolean gl = s.gambleOn && c.showGambleLine;
			int ph = gl ? 66 : 52;
			int pw = Math.max(150, 56 + tw(f, val, 1.7f) + 12);
			boolean top = c.profitCorner.startsWith("T"), left = c.profitCorner.endsWith("L");
			int px = left ? 10 : sw - pw - 10, py = top ? 8 : gh - ph - 10;
			int pc = lerp(target >= 0 ? BORDER : RED, WHITE, flash);
			panel(g, px, py, pw, ph, pc);
			iconBox(g, COIN, px + 8, py + 9, 34);
			text(g, f, "SESSION PROFIT", px + 50, py + 8, 0.65f, MUTED);
			text(g, f, val, px + 50, py + 17, 1.7f, pv >= 0 ? GREEN : RED);
			text(g, f, Money.signed(s.lastDelta), px + 50, py + 36, 0.9f, s.lastDelta >= 0 ? GREEN : RED);
			if (gl) {
				g.fill(px + 8, py + 48, px + pw - 8, py + 49, 0x33FFFFFF);
				int cx = px + 10;
				text(g, f, "W " + s.wins, cx, py + 53, 0.9f, GREEN);
				cx += tw(f, "W " + s.wins, 0.9f) + 10;
				text(g, f, "L " + s.losses, cx, py + 53, 0.9f, RED);
				cx += tw(f, "L " + s.losses, 0.9f) + 10;
				text(g, f, s.winRate() + "%", cx, py + 53, 0.9f, ACCENT2);
			}
			// floating +$ popups
			for (int i = pops.size() - 1; i >= 0; i--) {
				Pop p = pops.get(i);
				p.life -= dt;
				if (p.life <= 0) { pops.remove(i); continue; }
				float k = 1 - p.life / 1.4f;
				int dir = top ? 1 : -1;
				int ty = top ? py + ph + 4 + (int) (k * 16) : py - 12 - (int) (k * 16);
				int a2 = (int) (255 * Math.min(1f, p.life / 0.6f));
				text(g, f, p.txt, px + (pw - tw(f, p.txt, 1.1f)) / 2, ty, 1.1f, alpha(p.col, a2));
			}
		} else {
			pops.clear();
		}

		// ===== Confetti =====
		for (int i = conf.size() - 1; i >= 0; i--) {
			Conf p = conf.get(i);
			p.life -= dt;
			p.vy += 420f * dt;
			p.vx *= 1f - 0.8f * dt;
			p.x += p.vx * dt;
			p.y += p.vy * dt;
			if (p.life <= 0 || p.y > gh + 10) { conf.remove(i); continue; }
			int px = (int) p.x, py = (int) p.y;
			g.fill(px, py, px + 3, py + 3, alpha(p.col, (int) (255 * Math.min(1f, p.life / 0.5f))));
		}

		g.pose().popMatrix();
	}
}
