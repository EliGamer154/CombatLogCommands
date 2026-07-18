package com.combatlogcommands.combat;

import com.combatlogcommands.config.ModConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Simple in-memory cooldown between uses of /back (duration set by backCooldownSeconds in the config, default 30s). Commands can be dispatched off the main thread by other mods/panels, so this needs to be concurrency-safe. */
public class BackCooldown {
	private static final Map<UUID, Long> lastUsedAt = new ConcurrentHashMap<>();

	private BackCooldown() {
	}

	public static long remainingMillis(UUID id) {
		Long usedAt = lastUsedAt.get(id);
		if (usedAt == null) {
			return 0;
		}
		return Math.max(0, ModConfig.get().backCooldownMillis() - (System.currentTimeMillis() - usedAt));
	}

	public static void use(UUID id) {
		lastUsedAt.put(id, System.currentTimeMillis());
	}
}
