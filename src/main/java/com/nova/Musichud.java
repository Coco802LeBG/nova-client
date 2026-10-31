package com.nova;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** "Now playing" card + animated karaoke-style lyrics. */
final class MusicHud {
	private static float cardP = 0, lineT = 1f;
	private static int lastIdx = -2;

	private MusicHud() {}

	private static String fit(Font f, String s, float sc, int maxW) {
		if (f.width(s) * sc <= maxW) return s;
		String t = s;
		while (t.length() > 1 && f.width(t + "...") * sc > maxW) t = t.substring(0, t.length() - 1);
		return t + "...";
	}

	private static String time(long ms) {
		long s = Math.max(0, ms / 1000);
		return (s / 60) + ":" + String.format("%02d", s % 60);
	}

	private static void line(GuiGraphics g, Font f, String txt, int cx, int y, float sc, float prog, float a, int sw) {
		if (txt.isEmpty()) txt = "\u266A";
		float w = f.width(txt) * sc;
		float maxW = sw * 0.85f;
		if (w > maxW) { sc *= maxW / w; w = maxW; }
		int x = (int) (cx - w / 2);
		int k = Math.max(0, Math.min(txt.length(), (int) (txt.length() * prog)));
		String sung = txt.substring(0, k), rest = txt.substring(k);
		int al = (int) (255 * Math.max(0f, Math.min(1f, a)));
		NovaHud.text(g, f, sung, x, y, sc, NovaHud.alpha(NovaHud.WHITE, al));
		NovaHud.text(g, f, rest, x + (int) (f.width(sung) * sc), y, sc, NovaHud.alpha(0x9A93B5, al));
	}

	static void render(GuiGraphics g, Font f, int sw, int gh, float dt, long now, boolean an, float spd) {
		NovaConfig c = Session.I.cfg;
		Media m = Media.I;
		Media.Track t = m.track;
		boolean active = c.musicEnabled && t != null && t.playing;
		cardP = an ? cardP + ((active ? 1f : 0f) - cardP) * Math.min(1f, dt * 8f * spd) : (active ? 1f : 0f);
		if (cardP < 0.01f || t == null) { lastIdx = -2; return; }

		long pos = m.estPosMs();

		// ===== Card =====
		if (c.musicCard) {
			int w = 196, h = 48;
			boolean top = c.musicCardPos.startsWith("T"), left = c.musicCardPos.endsWith("L");
			int x = left ? 10 : sw - w - 10, y = top ? 8 : gh - h - 10;
			if (c.showProfit && c.musicCardPos.equals(c.profitCorner)) {
				int off = (Session.I.gambleOn && c.showGambleLine ? 66 : 52) + 8;
				y += top ? off : -off;
			}
			x += (int) ((left ? -(w + 14) : (w + 14)) * (1 - cardP));
			NovaHud.panel(g, x, y, w, h, NovaHud.BORDER);
			// equalizer icon
			NovaHud.rr(g, x + 8, y + 8, 32, 32, 8, NovaHud.BG_DARK);
			NovaHud.rr(g, x + 9, y + 9, 30, 30, 7, NovaHud.lerp(NovaHud.BG_DARK, NovaHud.PILL, 0.35f));
			for (int i = 0; i < 4; i++) {
				double ph = now / 1000.0 * (3.0 + i * 1.3) + i * 1.7;
				int bh = 4 + (int) (17 * (an ? 0.5 + 0.5 * Math.sin(ph) : 0.6));
				int bx = x + 8 + 3 + i * 7;
				NovaHud.rr(g, bx, y + 40 - 6 - bh, 4, bh, 2, NovaHud.lerp(NovaHud.BORDER, NovaHud.ACCENT2, i / 3f));
			}
			NovaHud.text(g, f, fit(f, t.title, 1.0f, w - 56), x + 48, y + 8, 1.0f, NovaHud.WHITE);
			NovaHud.text(g, f, fit(f, t.artist.isEmpty() ? t.app : t.artist, 0.8f, w - 56), x + 48, y + 20, 0.8f, NovaHud.MUTED);
			if (t.end > 0) {
				double frac = Math.min(1.0, pos / (t.end * 1000.0));
				NovaHud.bar(g, x + 48, y + 32, w - 56, 3, frac, NovaHud.BORDER);
				NovaHud.text(g, f, time(pos), x + 48, y + 38, 0.6f, NovaHud.MUTED);
				String tot = time((long) (t.end * 1000));
				NovaHud.text(g, f, tot, x + w - 8 - NovaHud.tw(f, tot, 0.6f), y + 38, 0.6f, NovaHud.MUTED);
			}
		}

		// ===== Lyrics =====
		List<Media.Line> L = m.lines;
		if (!c.musicLyrics || m.lstate != Media.LState.FOUND || L.isEmpty()) { lastIdx = -2; return; }
		long p = pos + c.lyricsOffsetMs;
		int idx = -1;
		while (idx + 1 < L.size() && L.get(idx + 1).ms() <= p) idx++;
		if (idx != lastIdx) { lastIdx = idx; lineT = 0f; }
		lineT += dt;
		float e = an ? Math.min(1f, lineT / (0.35f / spd)) : 1f;
		e = 1f - (1f - e) * (1f - e) * (1f - e);

		float prog = 0f;
		if (idx >= 0) {
			long a0 = L.get(idx).ms();
			long a1 = idx + 1 < L.size() ? L.get(idx + 1).ms() : a0 + 4000;
			prog = (float) Math.min(1.0, (p - a0) / Math.max(1.0, (a1 - a0) * 0.9));
		}

		int rows = Math.max(1, c.lyricsLines);
		int r = (rows - 1) / 2;
		float base = (float) c.lyricsScale;
		float rowH = 22f * base;
		int cx = sw / 2;
		float baseY = c.lyricsPos.equals("Top") ? 64 : c.lyricsPos.equals("Middle") ? gh / 2f + 30 : gh - 112;
		if (an) baseY += (float) Math.sin(now / 900.0) * 2.5f;

		for (int i = -r - 1; i <= r + 1; i++) {
			int li = idx + i;
			if (li < 0 || li >= L.size()) continue;
			float slot = i + (1f - e);
			float al = Math.max(0f, 1f - 0.38f * Math.abs(slot));
			if (rows == 1 && Math.abs(slot) > 0.01f) al = Math.max(0f, 1f - Math.abs(slot)) * (i == 0 ? 1f : 0.6f);
			if (al <= 0.02f) continue;
			float sc = 1.0f * base * (1f + 0.5f * Math.max(0f, 1f - Math.abs(slot)));
			float pr = i < 0 ? 1f : (i == 0 ? prog : 0f);
			int y = (int) (baseY + slot * rowH * 1.15f - 4 * sc);
			line(g, f, L.get(li).text(), cx, y, sc, pr, al * cardP, sw);
		}
	}
}
