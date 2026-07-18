package com.combatlogcommands.combat;

import com.combatlogcommands.config.ModConfig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Picks the cooldown for each in-combat firework rocket use: fireworkCooldownSeconds normally
 * (default 1.5s), stretched to fireworkEveryThirdCooldownSeconds (default 2.5s) on every 3rd rocket.
 * The cooldown itself is enforced and displayed by vanilla's item-cooldown system (the
 * ender-pearl-style white bar), not by this class - this only counts uses.
 */
public class FireworkCooldown {
	private static final Map<UUID, Integer> rocketsUsed = new ConcurrentHashMap<>();

	private FireworkCooldown() {
	}

	/** Records one in-combat rocket use and returns the cooldown ticks to apply for it. */
	public static int nextCooldownTicks(UUID id) {
		int count = rocketsUsed.merge(id, 1, Integer::sum);
		ModConfig config = ModConfig.get();
		return count % 3 == 0 ? config.fireworkEveryThirdCooldownTicks() : config.fireworkCooldownTicks();
	}
}
