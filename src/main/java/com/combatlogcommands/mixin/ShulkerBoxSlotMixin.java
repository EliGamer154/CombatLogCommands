package com.combatlogcommands.mixin;

import com.combatlogcommands.gamerule.ModGameRules;
import net.minecraft.world.inventory.ShulkerBoxSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shulker box slots use their own {@link ShulkerBoxSlot#mayPlace} override, so the general
 * {@code SlotMixin} on {@code Slot.mayPlace} never runs for them. Block the dragon egg here too while
 * the dragoneggpowers rule is on.
 */
@Mixin(ShulkerBoxSlot.class)
public class ShulkerBoxSlotMixin {
	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void combatlogcommands$blockDragonEgg(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (stack.getItem() == Items.DRAGON_EGG && ModGameRules.isDragonEggPowers()) {
			cir.setReturnValue(false);
		}
	}
}
