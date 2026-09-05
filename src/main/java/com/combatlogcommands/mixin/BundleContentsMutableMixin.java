package com.combatlogcommands.mixin;

import com.combatlogcommands.gamerule.ModGameRules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bundles insert items through {@link BundleContents.Mutable#tryInsert} (not the item-slot or
 * canFitInsideContainerItems paths), so block the dragon egg there while the dragoneggpowers rule is
 * on. Returning 0 means "nothing was inserted".
 */
@Mixin(BundleContents.Mutable.class)
public class BundleContentsMutableMixin {
	@Inject(method = "tryInsert", at = @At("HEAD"), cancellable = true)
	private void combatlogcommands$blockDragonEgg(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (stack.getItem() == Items.DRAGON_EGG && ModGameRules.isDragonEggPowers()) {
			cir.setReturnValue(0);
		}
	}
}
