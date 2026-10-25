package com.nova;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Optional;

/** Client-side nametag: recolors your name and adds symbols around it in messages you receive. */
public final class Nick {
	public static final String[] COLORS = { "White", "Red", "Orange", "Yellow", "Green", "Aqua", "Blue", "Purple", "Pink", "Gray", "Rainbow", "Theme" };
	private static final int[] RGB = { 0xFFFFFF, 0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5555FF, 0xAA55FF, 0xFF55FF, 0xAAAAAA, 0, 0 };
	public static final String[] SYMBOLS = { "", "\u2605", "\u2726", "\u2764", "\u2620", "\u2694", "\u265B", "\u26A1", "\u2744", "\u2600", "\u273F", "\u266A", "\u262F", "\u2714" };

	public static final String[] STAR_SYMBOLS = { "\u2605", "\u2726", "\u2727", "\u272A", "\u272F", "\u2736", "\u2742", "\u2739" };
	public static final String[] STAR_POS = { "Before", "After", "Both" };

	private Nick() {}

	public static String nextStar(String cur) {
		for (int i = 0; i < STAR_SYMBOLS.length; i++) if (STAR_SYMBOLS[i].equals(cur)) return STAR_SYMBOLS[(i + 1) % STAR_SYMBOLS.length];
		return STAR_SYMBOLS[0];
	}

	public static String nextPos(String cur) {
		for (int i = 0; i < STAR_POS.length; i++) if (STAR_POS[i].equalsIgnoreCase(cur)) return STAR_POS[(i + 1) % STAR_POS.length];
		return STAR_POS[0];
	}

	private static MutableComponent stars(Style st, NovaConfig c) {
		MutableComponent out = Component.empty();
		int n = Math.max(0, Math.min(5, c.nickStars));
		for (int i = 0; i < n; i++)
			out.append(Component.literal(c.nickStarSymbol).withStyle(st.withColor(TextColor.fromRgb(colorAt(c.nickStarColor, i, n)))));
		return out;
	}

	public static String next(String cur) {
		for (int i = 0; i < COLORS.length; i++) if (COLORS[i].equalsIgnoreCase(cur)) return COLORS[(i + 1) % COLORS.length];
		return COLORS[0];
	}

	public static String nextSymbol(String cur) {
		for (int i = 0; i < SYMBOLS.length; i++) if (SYMBOLS[i].equals(cur)) return SYMBOLS[(i + 1) % SYMBOLS.length];
		return SYMBOLS[1];
	}

	private static int hue(float h) {
		float h6 = (h % 1f) * 6f, f = h6 - (int) h6, r, g, b;
		switch ((int) h6) {
			case 0 -> { r = 1; g = f; b = 0; }
			case 1 -> { r = 1 - f; g = 1; b = 0; }
			case 2 -> { r = 0; g = 1; b = f; }
			case 3 -> { r = 0; g = 1 - f; b = 1; }
			case 4 -> { r = f; g = 0; b = 1; }
			default -> { r = 1; g = 0; b = 1 - f; }
		}
		return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
	}

	private static int colorAt(String mode, int i, int n) {
		float t = n <= 1 ? 0 : i / (float) (n - 1);
		if (mode.equalsIgnoreCase("Rainbow")) return hue(t * 0.85f);
		if (mode.equalsIgnoreCase("Theme")) return NovaHud.lerp(NovaHud.BORDER, NovaHud.ACCENT2, t) & 0xFFFFFF;
		for (int k = 0; k < COLORS.length; k++) if (COLORS[k].equalsIgnoreCase(mode)) return RGB[k];
		return 0xFFFFFF;
	}

	public static MutableComponent styled(String name, Style base) {
		NovaConfig c = Session.I.cfg;
		Style st = c.nickBold ? base.withBold(true) : base;
		MutableComponent out = Component.empty();
		if (!c.nickPrefix.isBlank()) out.append(Component.literal(c.nickPrefix + " ").withStyle(st));
		boolean hasStars = c.nickStars > 0;
		boolean before = hasStars && !c.nickStarPos.equalsIgnoreCase("After");
		boolean after = hasStars && !c.nickStarPos.equalsIgnoreCase("Before");
		if (before) out.append(stars(st, c)).append(Component.literal(" ").withStyle(st));
		for (int i = 0; i < name.length(); i++)
			out.append(Component.literal(String.valueOf(name.charAt(i))).withStyle(st.withColor(TextColor.fromRgb(colorAt(c.nickColor, i, name.length())))));
		if (after) out.append(Component.literal(" ").withStyle(st)).append(stars(st, c));
		if (!c.nickSuffix.isBlank()) out.append(Component.literal(" " + c.nickSuffix).withStyle(st));
		return out;
	}

	/** Your name as it should look in the menu. */
	public static Component display(String name) {
		return Session.I.cfg.nickEnabled ? styled(name, Style.EMPTY) : Component.literal(name);
	}

	/** Replaces every occurrence of your name in a received message. */
	public static Component apply(Component msg) {
		NovaConfig c = Session.I.cfg;
		if (!c.nickEnabled) return msg;
		String me = Minecraft.getInstance().getUser().getName();
		if (me == null || me.isEmpty() || !msg.getString().contains(me)) return msg;
		MutableComponent out = Component.empty();
		msg.<Boolean>visit((style, str) -> {
			int idx = 0;
			while (true) {
				int k = str.indexOf(me, idx);
				if (k < 0) {
					if (idx < str.length()) out.append(Component.literal(str.substring(idx)).withStyle(style));
					break;
				}
				if (k > idx) out.append(Component.literal(str.substring(idx, k)).withStyle(style));
				out.append(styled(me, style));
				idx = k + me.length();
			}
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}
}
