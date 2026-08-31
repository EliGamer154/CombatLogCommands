package com.combatlogcommands.mixin;

import com.combatlogcommands.gamerule.ModGameRules;
import com.combatlogcommands.mace.MaceState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records that a mace has been crafted the moment one is taken from a crafting result slot (while
 * the onemace rule is on), so every later mace craft is blocked by {@link CraftingMenuMixin}. Runs
 * at HEAD, before the ingredients are consumed and the grid recomputes, so a shift-click that would
 * craft several maces only ever produces the first.
 */
@Mixin(ResultSlot.class)
public class ResultSlotMixin {
	@Inject(method = "onTake", at = @At("HEAD"))
	private void combatlogcommands$markMaceCrafted(Player player, ItemStack stack, CallbackInfo ci) {
		if (stack.getItem() == Items.MACE && player instanceof ServerPlayer serverPlayer) {
			MinecraftServer server = serverPlayer.level().getServer();
			if (server != null && ModGameRules.isOneMace(server)) {
				MaceState.get(server).markCrafted();
			}
		}
	}
}
