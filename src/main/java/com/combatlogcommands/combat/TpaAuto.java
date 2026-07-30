package com.combatlogcommands.combat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player auto-accept preferences for incoming teleport requests, set with /tpauto. A player can
 * auto-accept EVERYONE (global toggle) and/or auto-accept SPECIFIC players (per-requester toggle).
 * In memory only - resets on server restart.
 */
public class TpaAuto {
	private static final Set<UUID> autoAcceptAll = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, Set<UUID>> autoAcceptFrom = new ConcurrentHashMap<>();

	private TpaAuto() {
	}

	/** Flips the "auto-accept everyone" toggle for a player; returns the new state. */
	public static boolean toggleGlobal(UUID player) {
		if (autoAcceptAll.remove(player)) {
			return false;
		}
		autoAcceptAll.add(player);
		return true;
	}

	/** Flips whether {@code player} auto-accepts requests from {@code requester}; returns the new state. */
	public static boolean togglePlayer(UUID player, UUID requester) {
		Set<UUID> allowed = autoAcceptFrom.computeIfAbsent(player, key -> ConcurrentHashMap.newKeySet());
		if (allowed.remove(requester)) {
			return false;
		}
		allowed.add(requester);
		return true;
	}

	public static boolean isGlobalOn(UUID player) {
		return autoAcceptAll.contains(player);
	}

	/** Whether a request from {@code requester} to {@code target} should be accepted automatically. */
	public static boolean shouldAutoAccept(UUID target, UUID requester) {
		if (autoAcceptAll.contains(target)) {
			return true;
		}
		Set<UUID> allowed = autoAcceptFrom.get(target);
		return allowed != null && allowed.contains(requester);
	}
}
