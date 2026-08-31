package com.combatlogcommands.mixin;

import com.combatlogcommands.gamerule.ModGameRules;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With the donethupgradesarmor gamerule on, upgrading a DIAMOND armor piece or tool/weapon in the
 * smithing table with a netherite upgrade template + netherite ingot produces the same diamond item
 * maxed out with its best enchantments (instead of netherite gear). Meant to pair with a "no
 * netherite" setup: the netherite result is unwanted, so the normal netherite upgrade is repurposed
 * to max the diamond item.
 *
 * The result is computed from the input slots at the tail of createResult, so it works whether or
 * not the vanilla netherite smithing recipe still exists. The result slot always allows pickup and
 * onTake shrinks all three inputs (template + diamond + ingot) by one, so vanilla handles consumption.
 */
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin {
	@Shadow
	@Final
	private Level level;

	@Inject(method = "createResult", at = @At("TAIL"))
	private void combatlogcommands$nethUpgradesArmor(CallbackInfo ci) {
		MinecraftServer server = level.getServer();
		if (server == null || !ModGameRules.isDoNethUpgradesArmor(server)) {
			return;
		}
		Container inputSlots = ((ItemCombinerMenuAccessor) (Object) this).combatlogcommands$inputSlots();
		ItemStack template = inputSlots.getItem(0);
		ItemStack base = inputSlots.getItem(1);
		ItemStack addition = inputSlots.getItem(2);
		if (template.getItem() != Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE
				|| addition.getItem() != Items.NETHERITE_INGOT
				|| !combatlogcommands$canMax(base.getItem())) {
			return;
		}
		ResultContainer resultSlots = ((ItemCombinerMenuAccessor) (Object) this).combatlogcommands$resultSlots();
		resultSlots.setItem(0, combatlogcommands$buildMaxed(base));
	}

	private static boolean combatlogcommands$canMax(Item item) {
		return item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE
				|| item == Items.DIAMOND_LEGGINGS || item == Items.DIAMOND_BOOTS
				|| item == Items.DIAMOND_SWORD || item == Items.DIAMOND_SPEAR
				|| item == Items.DIAMOND_AXE || item == Items.DIAMOND_PICKAXE
				|| item == Items.DIAMOND_SHOVEL || item == Items.DIAMOND_HOE;
	}

	private ItemStack combatlogcommands$buildMaxed(ItemStack base) {
		ItemStack result = base.copy();
		result.setCount(1);
		Registry<Enchantment> reg = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		Item item = base.getItem();
		EnchantmentHelper.updateEnchantments(result, m -> combatlogcommands$applyMax(m, reg, item));
		return result;
	}

	private static void combatlogcommands$applyMax(ItemEnchantments.Mutable m, Registry<Enchantment> reg, Item item) {
		// Every maxed item gets Mending + Unbreaking III.
		m.set(reg.getOrThrow(Enchantments.MENDING), 1);
		m.set(reg.getOrThrow(Enchantments.UNBREAKING), 3);

		if (item == Items.DIAMOND_HELMET) {
			m.set(reg.getOrThrow(Enchantments.PROTECTION), 4);
			m.set(reg.getOrThrow(Enchantments.AQUA_AFFINITY), 1);
			m.set(reg.getOrThrow(Enchantments.RESPIRATION), 3);
		} else if (item == Items.DIAMOND_CHESTPLATE) {
			m.set(reg.getOrThrow(Enchantments.PROTECTION), 4);
		} else if (item == Items.DIAMOND_LEGGINGS) {
			m.set(reg.getOrThrow(Enchantments.PROTECTION), 4);
			m.set(reg.getOrThrow(Enchantments.SWIFT_SNEAK), 3);
		} else if (item == Items.DIAMOND_BOOTS) {
			m.set(reg.getOrThrow(Enchantments.PROTECTION), 4);
			m.set(reg.getOrThrow(Enchantments.DEPTH_STRIDER), 3);
			m.set(reg.getOrThrow(Enchantments.SOUL_SPEED), 3);
		} else if (item == Items.DIAMOND_SWORD) {
			m.set(reg.getOrThrow(Enchantments.SHARPNESS), 5);
			m.set(reg.getOrThrow(Enchantments.LOOTING), 3);
			m.set(reg.getOrThrow(Enchantments.SWEEPING_EDGE), 3);
			m.set(reg.getOrThrow(Enchantments.FIRE_ASPECT), 2);
			m.set(reg.getOrThrow(Enchantments.KNOCKBACK), 2);
		} else if (item == Items.DIAMOND_SPEAR) {
			// A spear is a melee/sharp weapon (but not a sword), so no Sweeping Edge.
			m.set(reg.getOrThrow(Enchantments.SHARPNESS), 5);
			m.set(reg.getOrThrow(Enchantments.LOOTING), 3);
			m.set(reg.getOrThrow(Enchantments.FIRE_ASPECT), 2);
			m.set(reg.getOrThrow(Enchantments.KNOCKBACK), 2);
		} else if (item == Items.DIAMOND_AXE) {
			m.set(reg.getOrThrow(Enchantments.SHARPNESS), 5);
			m.set(reg.getOrThrow(Enchantments.EFFICIENCY), 5);
		} else if (item == Items.DIAMOND_PICKAXE || item == Items.DIAMOND_SHOVEL || item == Items.DIAMOND_HOE) {
			m.set(reg.getOrThrow(Enchantments.EFFICIENCY), 5);
			m.set(reg.getOrThrow(Enchantments.FORTUNE), 3);
		}
	}
}
