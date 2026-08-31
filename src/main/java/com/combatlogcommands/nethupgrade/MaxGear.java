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
 * how to build the maxed item (skipping any enchant the player toggled off in the confirm menu).
 *
 * Most enchants default ON and are independent. Enchants that share a non-null {@code group} are
 * mutually exclusive - at most one is applied - so e.g. a pickaxe offers Fortune (default) OR Silk
 * Touch, and picking one turns the other off. All non-exclusive enchants in a set are mutually
 * compatible, so no illegal combination is ever produced.
 */
public final class MaxGear {
	public record Entry(ResourceKey<Enchantment> key, int level, String display, boolean defaultOn, String group) {
		static Entry of(ResourceKey<Enchantment> key, int level, String display) {
			return new Entry(key, level, display, true, null);
		}

		static Entry grouped(ResourceKey<Enchantment> key, int level, String display, boolean defaultOn, String group) {
			return new Entry(key, level, display, defaultOn, group);
		}
	}

	private static final Entry PROT = Entry.of(Enchantments.PROTECTION, 4, "Protection IV");
	private static final Entry MEND = Entry.of(Enchantments.MENDING, 1, "Mending");
	private static final Entry UNBR = Entry.of(Enchantments.UNBREAKING, 3, "Unbreaking III");

	private MaxGear() {
	}

	public static boolean canMax(Item item) {
		return !enchantsFor(item).isEmpty();
	}

	public static List<Entry> enchantsFor(Item item) {
		if (item == Items.DIAMOND_HELMET) {
			return List.of(PROT, MEND, UNBR,
					Entry.of(Enchantments.AQUA_AFFINITY, 1, "Aqua Affinity"),
					Entry.of(Enchantments.RESPIRATION, 3, "Respiration III"));
		} else if (item == Items.DIAMOND_CHESTPLATE) {
			return List.of(PROT, MEND, UNBR);
		} else if (item == Items.DIAMOND_LEGGINGS) {
			return List.of(PROT, MEND, UNBR,
					Entry.of(Enchantments.SWIFT_SNEAK, 3, "Swift Sneak III"));
		} else if (item == Items.DIAMOND_BOOTS) {
			return List.of(PROT, MEND, UNBR,
					Entry.of(Enchantments.FEATHER_FALLING, 4, "Feather Falling IV"),
					Entry.of(Enchantments.DEPTH_STRIDER, 3, "Depth Strider III"),
					Entry.of(Enchantments.SOUL_SPEED, 3, "Soul Speed III"));
		} else if (item == Items.DIAMOND_SWORD) {
			return List.of(
					Entry.of(Enchantments.SHARPNESS, 5, "Sharpness V"),
					Entry.of(Enchantments.LOOTING, 3, "Looting III"),
					Entry.of(Enchantments.SWEEPING_EDGE, 3, "Sweeping Edge III"),
					Entry.of(Enchantments.FIRE_ASPECT, 2, "Fire Aspect II"),
					Entry.of(Enchantments.KNOCKBACK, 2, "Knockback II"), MEND, UNBR);
		} else if (item == Items.DIAMOND_SPEAR) {
			return List.of(
					Entry.of(Enchantments.SHARPNESS, 5, "Sharpness V"),
					Entry.of(Enchantments.LOOTING, 3, "Looting III"),
					Entry.of(Enchantments.FIRE_ASPECT, 2, "Fire Aspect II"),
					Entry.of(Enchantments.KNOCKBACK, 2, "Knockback II"),
					Entry.of(Enchantments.LUNGE, 3, "Lunge III"), MEND, UNBR);
		} else if (item == Items.DIAMOND_AXE) {
			return List.of(
					Entry.of(Enchantments.SHARPNESS, 5, "Sharpness V"),
					Entry.of(Enchantments.EFFICIENCY, 5, "Efficiency V"), MEND, UNBR);
		} else if (item == Items.DIAMOND_PICKAXE || item == Items.DIAMOND_SHOVEL || item == Items.DIAMOND_HOE) {
			return List.of(
					Entry.of(Enchantments.EFFICIENCY, 5, "Efficiency V"),
					Entry.grouped(Enchantments.FORTUNE, 3, "Fortune III", true, "mining"),
					Entry.grouped(Enchantments.SILK_TOUCH, 1, "Silk Touch", false, "mining"),
					MEND, UNBR);
		}
		return List.of();
	}

	/** Whether an enchant entry is currently on for this player (its default, flipped by their toggles). */
	public static boolean isEnabled(UUID player, Entry entry) {
		return entry.defaultOn() != NethEnchantPrefs.isFlipped(player, entry.key());
	}

	/** Builds the maxed item from a base diamond item, applying only the enchants the player has enabled. */
	public static ItemStack build(Level level, ItemStack base, UUID player) {
		ItemStack result = base.copy();
		result.setCount(1);
		Registry<Enchantment> reg = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		List<Entry> entries = enchantsFor(base.getItem());
		EnchantmentHelper.updateEnchantments(result, mutable -> {
			for (Entry entry : entries) {
				if (isEnabled(player, entry)) {
					mutable.set(reg.getOrThrow(entry.key()), entry.level());
				}
			}
		});
		return result;
	}
}
