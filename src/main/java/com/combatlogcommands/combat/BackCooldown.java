package com.combatlogcommands.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Simple in-memory 30-second cooldown between uses of /back. Commands can be dispatched off the main thread by other mods/panels, so this needs to be concurrency-safe. */
public class BackCooldown {
	private static final long COOLDOWN_MILLIS = 30_000L;
	private static final Map<UUID, Long> lastUsedAt = new ConcurrentHashMap<>();

	private BackCooldown() {
	}

	public static long remainingMillis(UUID id) {
		Long usedAt = lastUsedAt.get(id);
		if (usedAt == null) {
			return 0;
		}
		return Math.max(0, COOLDOWN_MILLIS - (System.currentTimeMillis() - usedAt));
	}

	public static void use(UUID id) {
		lastUsedAt.put(id, System.currentTimeMillis());
	}
}
