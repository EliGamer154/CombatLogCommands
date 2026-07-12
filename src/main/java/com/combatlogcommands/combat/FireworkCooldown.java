package com.combatlogcommands.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Picks the cooldown for each in-combat firework rocket use: 1.5s normally, stretched to 2.5s on
 * every 3rd rocket. The cooldown itself is enforced and displayed by vanilla's item-cooldown system
 * (the ender-pearl-style white bar), not by this class - this only counts uses.
 */
public class FireworkCooldown {
	private static final int NORMAL_COOLDOWN_TICKS = 30;
	private static final int EVERY_THIRD_COOLDOWN_TICKS = 50;
	private static final Map<UUID, Integer> rocketsUsed = new ConcurrentHashMap<>();

	private FireworkCooldown() {
	}

	/** Records one in-combat rocket use and returns the cooldown ticks to apply for it. */
	public static int nextCooldownTicks(UUID id) {
		int count = rocketsUsed.merge(id, 1, Integer::sum);
		return count % 3 == 0 ? EVERY_THIRD_COOLDOWN_TICKS : NORMAL_COOLDOWN_TICKS;
	}
}
