package com.combatlogcommands.mixin;

import com.combatlogcommands.gamerule.ModGameRules;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
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
 * While the dragoneggpowers gamerule is on, disallow placing a dragon egg into an ender chest.
 * Every placement path (click, shift-click, hotbar swap) checks {@link Slot#mayPlace}, and ender
 * chest slots are the ones backed by a {@link PlayerEnderChestContainer}, so vetoing here blocks
 * exactly those without touching any other container.
 */
@Mixin(Slot.class)
public class SlotMixin {
	@Shadow
	@Final
	public Container container;

	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void combatlogcommands$blockDragonEggInEnderChest(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (container instanceof PlayerEnderChestContainer
				&& stack.getItem() == Items.DRAGON_EGG
				&& ModGameRules.isDragonEggPowers()) {
			cir.setReturnValue(false);
		}
	}
}
