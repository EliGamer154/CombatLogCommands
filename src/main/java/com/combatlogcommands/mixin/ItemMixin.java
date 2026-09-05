package com.combatlogcommands.mixin;

import com.combatlogcommands.gamerule.ModGameRules;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While the dragoneggpowers gamerule is on, the dragon egg can't go inside container items - bundles
 * and shulker boxes (as items). Both check {@link Item#canFitInsideContainerItems()} before accepting
 * an item, so forcing it false for the dragon egg closes the "stash it in a bundle/shulker, then put
 * that in an ender chest" loophole. The block-container path is handled by {@code SlotMixin}.
 */
@Mixin(Item.class)
public class ItemMixin {
	@Inject(method = "canFitInsideContainerItems", at = @At("HEAD"), cancellable = true)
	private void combatlogcommands$blockDragonEgg(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this == Items.DRAGON_EGG && ModGameRules.isDragonEggPowers()) {
			cir.setReturnValue(false);
		}
	}
}
