package com.combatlogcommands.nethupgrade;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player choice of which enchantments to leave OFF the maxed diamond gear, toggled through the
 * confirm menu that opens after a donethupgradesarmor upgrade. In memory only - resets on restart.
 */
public final class NethEnchantPrefs {
	private static final Map<UUID, Set<ResourceKey<Enchantment>>> disabledByPlayer = new ConcurrentHashMap<>();

	private NethEnchantPrefs() {
	}

	public static boolean isDisabled(UUID player, ResourceKey<Enchantment> key) {
		if (player == null) {
			return false;
		}
		Set<ResourceKey<Enchantment>> set = disabledByPlayer.get(player);
		return set != null && set.contains(key);
	}

	public static boolean isEnabled(UUID player, ResourceKey<Enchantment> key) {
		return !isDisabled(player, key);
	}

	/** Flips the enchant on/off for the player; returns true if it is now DISABLED. */
	public static boolean toggle(UUID player, ResourceKey<Enchantment> key) {
		Set<ResourceKey<Enchantment>> set = disabledByPlayer.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet());
		if (set.remove(key)) {
			return false;
		}
		set.add(key);
		return true;
	}
}
