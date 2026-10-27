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

	// ---- Sounds ----
	public boolean sounds = true;
	public double soundVolume = 0.8;
	public boolean soundTick = true;
	public boolean soundMenu = true;
	public boolean soundClick = true;

	public int configVersion = 0;
	private static final int CURRENT = 3;

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
		NovaConfig defaults = new NovaConfig();
		try {
			Path p = path();
			if (Files.exists(p)) {
				NovaConfig c = GSON.fromJson(Files.readString(p), NovaConfig.class);
				if (c != null) {
					// Older configs may not contain newly added fields. Restore safe defaults
					// without overwriting any values the user already customized.
					if (c.paidYouPattern == null || c.paidYouPattern.isBlank()) c.paidYouPattern = defaults.paidYouPattern;
					if (c.youPaidPattern == null || c.youPaidPattern.isBlank()) c.youPaidPattern = defaults.youPaidPattern;
					if (c.winPatterns == null || c.winPatterns.isEmpty()) c.winPatterns = defaults.winPatterns;
					if (c.lossPatterns == null || c.lossPatterns.isEmpty()) c.lossPatterns = defaults.lossPatterns;
					if (c.nickColor == null) c.nickColor = defaults.nickColor;
					if (c.nickStarColor == null) c.nickStarColor = defaults.nickStarColor;
					if (c.nickStarSymbol == null) c.nickStarSymbol = defaults.nickStarSymbol;
					if (c.nickStarPos == null) c.nickStarPos = defaults.nickStarPos;
					if (c.nickPrefix == null) c.nickPrefix = defaults.nickPrefix;
					if (c.nickSuffix == null) c.nickSuffix = defaults.nickSuffix;
					if (c.theme == null) c.theme = defaults.theme;
					if (c.profitCorner == null) c.profitCorner = defaults.profitCorner;
					c.soundVolume = Math.max(0.0, Math.min(1.0, c.soundVolume));
					if (c.configVersion < CURRENT) {
						// Click feedback was added in config version 3.
						if (c.configVersion < 3) c.soundClick = true;
						c.configVersion = CURRENT;
					}
					c.save();
					return c;
				}
			}
			defaults.configVersion = CURRENT;
			defaults.save();
			return defaults;
		} catch (Exception e) {
			return defaults;
		}
	}

}
