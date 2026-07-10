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
import java.util.List;

/** Server-side config: which commands are blocked while a player is combat-tagged. */
public class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ModConfig instance;

	private List<String> blockedCommands = new ArrayList<>(List.of("back", "tpa", "tpaccept", "home", "spawn", "tpahere"));

	// Commands can be dispatched off the main server thread by other mods/panels, and this can be
	// reached from the very first command check, so the lazy load must not race.
	public static synchronized ModConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
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
					loaded.save();
					return loaded;
				}
			} catch (IOException e) {
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
		for (String blocked : blockedCommands) {
			if (blocked.equalsIgnoreCase(label)) {
				return true;
			}
		}
		return false;
	}
}
