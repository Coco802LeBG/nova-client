package com.nova;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Saved in .minecraft/config/nova.json. Edit the regex patterns to match your server's chat. */
public class NovaConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** A player paid you. group 1 = name, group 2 = amount. This is how bids are detected. */
	public String paidYouPattern = "(?i)([\\w.]{3,17}) paid you\\D{0,8}([\\d.,]+[kmbt]?)";
	/** You paid a player. group 1 = name, group 2 = amount. Used to detect that you paid out (= you lost the bet). */
	public String youPaidPattern = "(?i)you paid ([\\w.]{3,17})\\D{0,8}([\\d.,]+[kmbt]?)";
	/** Auto gamble: someone pays you = win. You pay them back >= payoutMultiplier x their bet = loss. */
	public boolean autoGamble = true;
	public double payoutMultiplier = 1.9;
	/** How long (seconds) a payout still counts for the bet it belongs to. */
	public int windowSec = 120;
	/** Gamble results. Optional named group "amt" = amount won/lost. */
	public List<String> winPatterns = List.of("(?i)you won(?: \\$?(?<amt>[\\d.,]+[kmbt]?))?");
	public List<String> lossPatterns = List.of("(?i)you lost(?: \\$?(?<amt>[\\d.,]+[kmbt]?))?");

	// ---- Nametag (client-side only: you see it, other players do not) ----
	public boolean nickEnabled = false;
	public String nickColor = "Purple";
	public boolean nickBold = false;
	public String nickPrefix = "";
	public String nickSuffix = "";
	public int nickStars = 3;                 // 0 to 5
	public String nickStarSymbol = "\u2605";
	public String nickStarColor = "Yellow";   // same colors as the name, incl. Rainbow / Theme
	public String nickStarPos = "Before";     // Before, After, Both

	public int configVersion = 0;
	private static final int CURRENT = 2;

	// ---- Look & feel (also editable in the menu: Settings) ----
	public String theme = "Purple";           // Purple, Blue, Pink, Green, Cyan, Orange, Red
	public boolean animations = true;
	public double animSpeed = 1.0;
	public double hudScale = 1.0;
	public String profitCorner = "TR";        // TL, TR, BL, BR
	public boolean toastBottom = false;
	public boolean confetti = true;
	public boolean menuAnim = true;
	public boolean showProfit = true;
	public boolean showToast = true;
	public boolean showGambleLine = true;

	public void save() {
		try {
			Files.writeString(path(), GSON.toJson(this));
		} catch (Exception ignored) {
		}
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("nova.json");
	}

	public static NovaConfig load() {
		try {
			Path p = path();
			if (Files.exists(p)) {
				NovaConfig c = GSON.fromJson(Files.readString(p), NovaConfig.class);
				if (c != null) {
					if (c.configVersion < CURRENT) {
						NovaConfig d = new NovaConfig();
						c.paidYouPattern = d.paidYouPattern;
						c.youPaidPattern = d.youPaidPattern;
						c.configVersion = CURRENT;
						c.save();
					}
					return c;
				}
			}
			NovaConfig c = new NovaConfig();
			c.configVersion = CURRENT;
			Files.writeString(p, GSON.toJson(c));
			return c;
		} catch (Exception e) {
			return new NovaConfig();
		}
	}
}
