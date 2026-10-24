package com.nova;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Money {
	private static final Pattern P = Pattern.compile("^([0-9]*\\.?[0-9]+)\\s*([kmbt]?)$");

	private Money() {}

	/** "10k" -> 10000, "1.5M" -> 1500000, "$2,000" -> 2000. Returns 0 if invalid. */
	public static long parse(String s) {
		if (s == null) return 0;
		Matcher m = P.matcher(s.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("$", ""));
		if (!m.matches()) return 0;
		double n = Double.parseDouble(m.group(1));
		double mul = switch (m.group(2)) {
			case "k" -> 1e3;
			case "m" -> 1e6;
			case "b" -> 1e9;
			case "t" -> 1e12;
			default -> 1;
		};
		return Math.round(n * mul);
	}

	public static String fmt(long v) {
		long a = Math.abs(v);
		String sign = v < 0 ? "-" : "";
		if (a >= 1_000_000_000_000L) return sign + trim(a / 1e12) + "T";
		if (a >= 1_000_000_000L) return sign + trim(a / 1e9) + "B";
		if (a >= 1_000_000L) return sign + trim(a / 1e6) + "M";
		if (a >= 1_000L) return sign + trim(a / 1e3) + "K";
		return sign + a;
	}

	public static String signed(long v) {
		return (v < 0 ? "-$" : "+$") + fmt(Math.abs(v));
	}

	private static String trim(double d) {
		String s = String.format(Locale.ROOT, "%.1f", d);
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}
}
