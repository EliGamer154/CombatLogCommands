package com.combatlogcommands.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory 3.5-second cooldown between firework rocket uses while in combat. */
public class FireworkCooldown {
	private static final long COOLDOWN_MILLIS = 3_500L;
	private static final Map<UUID, Long> lastUsedAt = new ConcurrentHashMap<>();

	private FireworkCooldown() {
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
