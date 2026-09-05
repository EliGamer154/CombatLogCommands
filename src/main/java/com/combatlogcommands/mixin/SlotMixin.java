package com.combatlogcommands.mixin;

import com.combatlogcommands.gamerule.ModGameRules;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While the dragoneggpowers gamerule is on, disallow placing a dragon egg into ANY container - ender
 * chests, chests, opened shulker boxes, barrels, etc. Every placement path (click, shift-click,
 * hotbar swap) checks {@link Slot#mayPlace}. Slots backed by the player's own {@link Inventory} are
 * allowed (so the egg can still be carried and moved around your own inventory); everything else is
 * a storage container and is blocked. Bundles and shulker-box items are handled separately via
 * {@code ItemMixin} (canFitInsideContainerItems).
 */
@Mixin(Slot.class)
public class SlotMixin {
	@Shadow
	@Final
	public Container container;

	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void combatlogcommands$blockDragonEggInContainers(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (stack.getItem() == Items.DRAGON_EGG
				&& !(container instanceof Inventory)
				&& ModGameRules.isDragonEggPowers()) {
			cir.setReturnValue(false);
		}
	}
}
