package com.combatlogcommands.mixin;

import com.combatlogcommands.mace.MaceState;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When the onemace gamerule is on and a mace has already been crafted, clear the crafting result so
 * a second mace can't be crafted. slotChangedCraftingGrid is the single method both the crafting
 * table and the player's 2x2 inventory grid use to compute their result, so blocking here covers
 * both grids and every take path (normal click and shift-click both read the result slot). The
 * result slot, remote-slot mirror, and client packet are all corrected to empty, matching how
 * vanilla itself pushes the result to the client.
 */
@Mixin(CraftingMenu.class)
public class CraftingMenuMixin {
	@Inject(method = "slotChangedCraftingGrid", at = @At("TAIL"))
	private static void combatlogcommands$blockSecondMace(AbstractContainerMenu menu, ServerLevel level, Player player,
			CraftingContainer grid, ResultContainer result, RecipeHolder<CraftingRecipe> recipe, CallbackInfo ci) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		if (result.getItem(0).getItem() != Items.MACE) {
			return;
		}
		MinecraftServer server = level.getServer();
		if (server == null || !MaceState.isBlocked(server)) {
			return;
		}
		result.setItem(0, ItemStack.EMPTY);
		menu.setRemoteSlot(0, ItemStack.EMPTY);
		serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), 0, ItemStack.EMPTY));
	}
}
