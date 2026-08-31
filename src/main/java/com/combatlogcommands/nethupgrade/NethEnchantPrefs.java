package com.combatlogcommands.nethupgrade;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player record of which maxed-gear enchants have been flipped from their default on/off state,
 * via the confirm menu. Storing the flip (rather than absolute on/off) lets enchants have sensible
 * defaults - e.g. Fortune defaults on and Silk Touch defaults off. In memory only - resets on restart.
 */
public final class NethEnchantPrefs {
	private static final Map<UUID, Set<ResourceKey<Enchantment>>> flippedByPlayer = new ConcurrentHashMap<>();

	private NethEnchantPrefs() {
	}

	public static boolean isFlipped(UUID player, ResourceKey<Enchantment> key) {
		if (player == null) {
			return false;
		}
		Set<ResourceKey<Enchantment>> set = flippedByPlayer.get(player);
		return set != null && set.contains(key);
	}

	public static void setFlipped(UUID player, ResourceKey<Enchantment> key, boolean flipped) {
		Set<ResourceKey<Enchantment>> set = flippedByPlayer.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet());
		if (flipped) {
			set.add(key);
		} else {
			set.remove(key);
		}
	}
}
