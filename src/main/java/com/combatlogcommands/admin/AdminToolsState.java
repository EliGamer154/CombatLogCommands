package com.combatlogcommands.admin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory per-player state for the op-only /admintools toggles. Resets on restart. These are
 * legitimate admin conveniences (fly, god, speed, etc.) - nothing here hides from or fights an
 * anti-cheat.
 */
public final class AdminToolsState {
	public enum Toggle {
		FLY, GOD, SPEED, NIGHT_VISION, NO_FALL
	}

	public record FreezeAnchor(ResourceKey<Level> dimension, double x, double y, double z, float yaw, float pitch) {
	}

	private static final Map<Toggle, Set<UUID>> toggles = new EnumMap<>(Toggle.class);
	private static final Map<UUID, FreezeAnchor> frozen = new ConcurrentHashMap<>();
	private static final Map<UUID, GameType> gameModeBeforeSpectate = new ConcurrentHashMap<>();

	static {
		for (Toggle toggle : Toggle.values()) {
			toggles.put(toggle, ConcurrentHashMap.newKeySet());
		}
	}

	private AdminToolsState() {
	}

	public static boolean isOn(Toggle toggle, UUID id) {
		return toggles.get(toggle).contains(id);
	}

	/** Flips a toggle; returns true if it is now ON. */
	public static boolean toggle(Toggle toggle, UUID id) {
		Set<UUID> set = toggles.get(toggle);
		if (set.remove(id)) {
			return false;
		}
		set.add(id);
		return true;
	}

	public static Set<UUID> ids(Toggle toggle) {
		return toggles.get(toggle);
	}

	public static boolean isFrozen(UUID id) {
		return frozen.containsKey(id);
	}

	public static FreezeAnchor freezeAnchor(UUID id) {
		return frozen.get(id);
	}

	public static void setFrozen(UUID id, FreezeAnchor anchor) {
		frozen.put(id, anchor);
	}

	public static void unfreeze(UUID id) {
		frozen.remove(id);
	}

	public static Set<UUID> frozenIds() {
		return frozen.keySet();
	}

	public static void rememberGameMode(UUID id, GameType mode) {
		gameModeBeforeSpectate.put(id, mode);
	}

	public static GameType takeRememberedGameMode(UUID id) {
		GameType mode = gameModeBeforeSpectate.remove(id);
		return mode == null ? GameType.SURVIVAL : mode;
	}
}
