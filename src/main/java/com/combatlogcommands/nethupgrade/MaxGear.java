package com.combatlogcommands.nethupgrade;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

/**
 * Defines which diamond items the donethupgradesarmor upgrade can max, the enchant set for each, and
 * how to build the maxed item (skipping any enchant the player has toggled off in the confirm menu).
 * All enchants in a set are mutually compatible, so leaving any of them off never yields an illegal
 * combination.
 */
public final class MaxGear {
	/** One enchant of a maxed set: the enchant, the level to apply, and a menu display name. */
	public record Entry(ResourceKey<Enchantment> key, int level, String display) {
	}

	private static final Entry PROT = new Entry(Enchantments.PROTECTION, 4, "Protection IV");
	private static final Entry MEND = new Entry(Enchantments.MENDING, 1, "Mending");
	private static final Entry UNBR = new Entry(Enchantments.UNBREAKING, 3, "Unbreaking III");

	private MaxGear() {
	}

	public static boolean canMax(Item item) {
		return !enchantsFor(item).isEmpty();
	}

	/** The full enchant set for a maxable diamond item, or an empty list if the item isn't maxable. */
	public static List<Entry> enchantsFor(Item item) {
		if (item == Items.DIAMOND_HELMET) {
			return List.of(PROT, MEND, UNBR,
					new Entry(Enchantments.AQUA_AFFINITY, 1, "Aqua Affinity"),
					new Entry(Enchantments.RESPIRATION, 3, "Respiration III"));
		} else if (item == Items.DIAMOND_CHESTPLATE) {
			return List.of(PROT, MEND, UNBR);
		} else if (item == Items.DIAMOND_LEGGINGS) {
			return List.of(PROT, MEND, UNBR,
					new Entry(Enchantments.SWIFT_SNEAK, 3, "Swift Sneak III"));
		} else if (item == Items.DIAMOND_BOOTS) {
			return List.of(PROT, MEND, UNBR,
					new Entry(Enchantments.DEPTH_STRIDER, 3, "Depth Strider III"),
					new Entry(Enchantments.SOUL_SPEED, 3, "Soul Speed III"));
		} else if (item == Items.DIAMOND_SWORD) {
			return List.of(
					new Entry(Enchantments.SHARPNESS, 5, "Sharpness V"),
					new Entry(Enchantments.LOOTING, 3, "Looting III"),
					new Entry(Enchantments.SWEEPING_EDGE, 3, "Sweeping Edge III"),
					new Entry(Enchantments.FIRE_ASPECT, 2, "Fire Aspect II"),
					new Entry(Enchantments.KNOCKBACK, 2, "Knockback II"), MEND, UNBR);
		} else if (item == Items.DIAMOND_SPEAR) {
			return List.of(
					new Entry(Enchantments.SHARPNESS, 5, "Sharpness V"),
					new Entry(Enchantments.LOOTING, 3, "Looting III"),
					new Entry(Enchantments.FIRE_ASPECT, 2, "Fire Aspect II"),
					new Entry(Enchantments.KNOCKBACK, 2, "Knockback II"),
					new Entry(Enchantments.LUNGE, 3, "Lunge III"), MEND, UNBR);
		} else if (item == Items.DIAMOND_AXE) {
			return List.of(
					new Entry(Enchantments.SHARPNESS, 5, "Sharpness V"),
					new Entry(Enchantments.EFFICIENCY, 5, "Efficiency V"), MEND, UNBR);
		} else if (item == Items.DIAMOND_PICKAXE || item == Items.DIAMOND_SHOVEL || item == Items.DIAMOND_HOE) {
			return List.of(
					new Entry(Enchantments.EFFICIENCY, 5, "Efficiency V"),
					new Entry(Enchantments.FORTUNE, 3, "Fortune III"), MEND, UNBR);
		}
		return List.of();
	}

	/** Builds the maxed item from a base diamond item, applying only the enchants the player has enabled. */
	public static ItemStack build(Level level, ItemStack base, UUID player) {
		ItemStack result = base.copy();
		result.setCount(1);
		Registry<Enchantment> reg = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		List<Entry> entries = enchantsFor(base.getItem());
		EnchantmentHelper.updateEnchantments(result, mutable -> {
			for (Entry entry : entries) {
				if (NethEnchantPrefs.isEnabled(player, entry.key())) {
					mutable.set(reg.getOrThrow(entry.key()), entry.level());
				}
			}
		});
		return result;
	}
}
