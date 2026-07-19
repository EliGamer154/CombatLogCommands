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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
	private double teleportWarmupSeconds = 3.0;
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
		instance.makeThreadSafe();
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
					loaded.makeThreadSafe();
					loaded.save();
					return loaded;
				}
			} catch (Exception e) {
				CombatLogCommands.LOGGER.error("Failed to read combatlogcommands.json, using defaults", e);
			}
		}
		ModConfig config = new ModConfig();
		config.makeThreadSafe();
		config.save();
		return config;
	}

	// The mixin reads these collections on every command, potentially off the main thread, while the
	// in-game /combatlog commands mutate them - swap Gson's plain collections for concurrent ones.
	private void makeThreadSafe() {
		blockedCommands = new CopyOnWriteArrayList<>(blockedCommands == null ? List.of() : blockedCommands);
		blockedWhenTargetInCombat = new CopyOnWriteArrayList<>(blockedWhenTargetInCombat == null ? List.of() : blockedWhenTargetInCombat);
		playerOverrides = new ConcurrentHashMap<>(playerOverrides == null ? Map.of() : playerOverrides);
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

	public int teleportWarmupTicks() {
		return (int) Math.round(teleportWarmupSeconds * 20);
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

	// --- in-game mutation (each change saves to disk immediately) ---

	public synchronized void setCombatDurationSeconds(double seconds) {
		combatDurationSeconds = seconds;
		save();
	}

	public synchronized void setBackCooldownSeconds(double seconds) {
		backCooldownSeconds = seconds;
		save();
	}

	public synchronized void setFireworkCooldownSeconds(double seconds) {
		fireworkCooldownSeconds = seconds;
		save();
	}

	public synchronized void setFireworkEveryThirdCooldownSeconds(double seconds) {
		fireworkEveryThirdCooldownSeconds = seconds;
		save();
	}

	public synchronized void setTeleportWarmupSeconds(double seconds) {
		teleportWarmupSeconds = seconds;
		save();
	}

	/** Returns false if the command was already in the list. */
	public synchronized boolean addBlockedCommand(String command) {
		if (containsIgnoreCase(blockedCommands, command)) {
			return false;
		}
		blockedCommands.add(command.toLowerCase(Locale.ROOT));
		save();
		return true;
	}

	/** Returns false if the command was not in the list. */
	public synchronized boolean removeBlockedCommand(String command) {
		boolean removed = blockedCommands.removeIf(entry -> entry.equalsIgnoreCase(command));
		if (removed) {
			save();
		}
		return removed;
	}

	/** Returns false if the command was already in the list. */
	public synchronized boolean addTargetBlockedCommand(String command) {
		if (containsIgnoreCase(blockedWhenTargetInCombat, command)) {
			return false;
		}
		blockedWhenTargetInCombat.add(command.toLowerCase(Locale.ROOT));
		save();
		return true;
	}

	/** Returns false if the command was not in the list. */
	public synchronized boolean removeTargetBlockedCommand(String command) {
		boolean removed = blockedWhenTargetInCombat.removeIf(entry -> entry.equalsIgnoreCase(command));
		if (removed) {
			save();
		}
		return removed;
	}

	public synchronized void setPlayerCombatDuration(String playerName, double seconds) {
		overrideOrCreate(playerName).combatDurationSeconds = seconds;
		save();
	}

	public synchronized void setPlayerFireworkCooldown(String playerName, double seconds) {
		overrideOrCreate(playerName).fireworkCooldownSeconds = seconds;
		save();
	}

	public synchronized void setPlayerFireworkEveryThirdCooldown(String playerName, double seconds) {
		overrideOrCreate(playerName).fireworkEveryThirdCooldownSeconds = seconds;
		save();
	}

	/** Removes all overrides for a player. Returns false if they had none. */
	public synchronized boolean clearPlayerOverride(String playerName) {
		boolean removed = playerOverrides.keySet().removeIf(key -> key.equalsIgnoreCase(playerName));
		if (removed) {
			save();
		}
		return removed;
	}

	private PlayerOverride overrideOrCreate(String playerName) {
		PlayerOverride existing = overrideFor(playerName);
		if (existing != null) {
			return existing;
		}
		PlayerOverride created = new PlayerOverride();
		playerOverrides.put(playerName, created);
		return created;
	}

	/** Multiline plain-text summary of the current settings, for /combatlog show. */
	public synchronized String describe() {
		StringBuilder text = new StringBuilder();
		text.append("Combat duration: ").append(combatDurationSeconds).append("s");
		text.append(" | /back cooldown: ").append(backCooldownSeconds).append("s");
		text.append("\nFirework cooldown: ").append(fireworkCooldownSeconds).append("s");
		text.append(" (every 3rd: ").append(fireworkEveryThirdCooldownSeconds).append("s)");
		text.append("\nTeleport warmup: ").append(teleportWarmupSeconds).append("s");
		text.append("\nBlocked in combat: ").append(String.join(", ", blockedCommands));
		text.append("\nBlocked when target in combat: ").append(String.join(", ", blockedWhenTargetInCombat));
		if (playerOverrides.isEmpty()) {
			text.append("\nPlayer overrides: none");
		} else {
			text.append("\nPlayer overrides:");
			for (Map.Entry<String, PlayerOverride> entry : playerOverrides.entrySet()) {
				PlayerOverride override = entry.getValue();
				text.append("\n  ").append(entry.getKey()).append(":");
				if (override.combatDurationSeconds != null) {
					text.append(" combat ").append(override.combatDurationSeconds).append("s");
				}
				if (override.fireworkCooldownSeconds != null) {
					text.append(" firework ").append(override.fireworkCooldownSeconds).append("s");
				}
				if (override.fireworkEveryThirdCooldownSeconds != null) {
					text.append(" every-3rd ").append(override.fireworkEveryThirdCooldownSeconds).append("s");
				}
			}
		}
		return text.toString();
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
