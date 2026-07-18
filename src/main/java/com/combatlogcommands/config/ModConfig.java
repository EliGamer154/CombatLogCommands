package com.combatlogcommands.config;

import com.combatlogcommands.CombatLogCommands;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side config. Loaded lazily, re-savable via {@code /combatlog reload} and
 * {@code /combatlog reset}. Fields missing from an older config file keep their defaults (Gson only
 * sets fields present in the JSON) and are written back on load, so old files upgrade in place.
 */
public class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ModConfig instance;

	private List<String> blockedCommands = new ArrayList<>(List.of("back", "tpa", "tpaccept", "home", "spawn", "tpahere"));
	private List<String> blockedWhenTargetInCombat = new ArrayList<>(List.of("tpa", "tpahere"));
	private double combatDurationSeconds = 15.0;
	private double backCooldownSeconds = 30.0;
	private double fireworkCooldownSeconds = 1.5;
	private double fireworkEveryThirdCooldownSeconds = 2.5;
	private Map<String, PlayerOverride> playerOverrides = new HashMap<>();

	// Commands can be dispatched off the main server thread by other mods/panels, and this can be
	// reached from the very first command check, so the lazy load must not race.
	public static synchronized ModConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	/** Re-reads the config file from disk, e.g. after an admin edited it. */
	public static synchronized void reload() {
		instance = load();
	}

	/** Overwrites the config file with default settings. */
	public static synchronized void reset() {
		instance = new ModConfig();
		instance.save();
	}

	private static Path configPath() {
		return FabricLoader.getInstance().getConfigDir().resolve("combatlogcommands.json");
	}

	private static ModConfig load() {
		Path path = configPath();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
				if (loaded != null) {
					if (loaded.blockedCommands == null) {
						loaded.blockedCommands = new ArrayList<>();
					}
					if (loaded.blockedWhenTargetInCombat == null) {
						loaded.blockedWhenTargetInCombat = new ArrayList<>();
					}
					if (loaded.playerOverrides == null) {
						loaded.playerOverrides = new HashMap<>();
					}
					loaded.save();
					return loaded;
				}
			} catch (Exception e) {
				CombatLogCommands.LOGGER.error("Failed to read combatlogcommands.json, using defaults", e);
			}
		}
		ModConfig config = new ModConfig();
		config.save();
		return config;
	}

	private void save() {
		Path path = configPath();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			CombatLogCommands.LOGGER.error("Failed to save combatlogcommands.json", e);
		}
	}

	/** Command label without the leading slash, e.g. "back". Matching is case-insensitive. */
	public boolean isBlockedCommand(String label) {
		return containsIgnoreCase(blockedCommands, label);
	}

	/** Commands that can't be used to target a player who is in combat, e.g. "tpa &lt;name&gt;". */
	public boolean isTargetBlockedCommand(String label) {
		return containsIgnoreCase(blockedWhenTargetInCombat, label);
	}

	private static boolean containsIgnoreCase(List<String> list, String value) {
		for (String entry : list) {
			if (entry.equalsIgnoreCase(value)) {
				return true;
			}
		}
		return false;
	}

	public long combatDurationMillis(String playerName) {
		PlayerOverride override = overrideFor(playerName);
		double seconds = override != null && override.combatDurationSeconds != null
				? override.combatDurationSeconds : combatDurationSeconds;
		return (long) (seconds * 1000);
	}

	public long backCooldownMillis() {
		return (long) (backCooldownSeconds * 1000);
	}

	public int fireworkCooldownTicks(String playerName) {
		PlayerOverride override = overrideFor(playerName);
		double seconds = override != null && override.fireworkCooldownSeconds != null
				? override.fireworkCooldownSeconds : fireworkCooldownSeconds;
		return (int) Math.round(seconds * 20);
	}

	public int fireworkEveryThirdCooldownTicks(String playerName) {
		PlayerOverride override = overrideFor(playerName);
		double seconds = override != null && override.fireworkEveryThirdCooldownSeconds != null
				? override.fireworkEveryThirdCooldownSeconds : fireworkEveryThirdCooldownSeconds;
		return (int) Math.round(seconds * 20);
	}

	private PlayerOverride overrideFor(String playerName) {
		if (playerName == null) {
			return null;
		}
		for (Map.Entry<String, PlayerOverride> entry : playerOverrides.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(playerName) && entry.getValue() != null) {
				return entry.getValue();
			}
		}
		return null;
	}

	/**
	 * Per-player settings, keyed by player name (case-insensitive). Any field left out of the JSON
	 * stays null and falls back to the matching global setting.
	 */
	public static class PlayerOverride {
		private Double combatDurationSeconds;
		private Double fireworkCooldownSeconds;
		private Double fireworkEveryThirdCooldownSeconds;
	}
}
