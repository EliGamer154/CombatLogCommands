package com.combatlogcommands.mixin;

import com.combatlogcommands.gamerule.ModGameRules;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every hopper transfer (pulling a dropped egg into a hopper, or pushing it into another container)
 * goes through {@code canPlaceItemInContainer}. Veto the dragon egg here while the dragoneggpowers
 * rule is on, so it can't be moved into any container by automation either.
 */
@Mixin(HopperBlockEntity.class)
public class HopperBlockEntityMixin {
	@Inject(method = "canPlaceItemInContainer", at = @At("HEAD"), cancellable = true)
	private static void combatlogcommands$blockDragonEgg(Container container, ItemStack stack, int slot, Direction direction,
			CallbackInfoReturnable<Boolean> cir) {
		if (stack.getItem() == Items.DRAGON_EGG && ModGameRules.isDragonEggPowers()) {
			cir.setReturnValue(false);
		}
	}
}
