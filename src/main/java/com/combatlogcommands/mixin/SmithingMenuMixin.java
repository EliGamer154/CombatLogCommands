package com.combatlogcommands.mixin;

import com.combatlogcommands.gamerule.ModGameRules;
import com.combatlogcommands.gui.NethUpgradeMenu;
import com.combatlogcommands.nethupgrade.MaxGear;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * The donethupgradesarmor flow. When the rule is on and a diamond item is set up for a netherite
 * upgrade (netherite template + netherite ingot), the smithing result shows a preview of the maxed
 * item; taking it consumes the inputs and opens the {@link NethUpgradeMenu} to confirm which enchants
 * to keep, which then hands over the finished item. Computing from the input slots (not the vanilla
 * recipe) means it works whether or not the netherite recipe still exists.
 */
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin {
	@Shadow
	@Final
	private Level level;

	@Inject(method = "createResult", at = @At("TAIL"))
	private void combatlogcommands$previewMaxed(CallbackInfo ci) {
		MinecraftServer server = level.getServer();
		if (server == null || !ModGameRules.isDoNethUpgradesArmor(server)) {
			return;
		}
		Container inputSlots = ((ItemCombinerMenuAccessor) (Object) this).combatlogcommands$inputSlots();
		ItemStack base = inputSlots.getItem(1);
		if (!combatlogcommands$isUpgrade(inputSlots)) {
			return;
		}
		Player player = combatlogcommands$findPlayer();
		UUID id = player == null ? null : player.getUUID();
		((ItemCombinerMenuAccessor) (Object) this).combatlogcommands$resultSlots().setItem(0, MaxGear.build(level, base, id));
	}

	@Inject(method = "onTake", at = @At("HEAD"), cancellable = true)
	private void combatlogcommands$onUpgradeTaken(Player player, ItemStack stack, CallbackInfo ci) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return;
		}
		MinecraftServer server = level.getServer();
		if (server == null || !ModGameRules.isDoNethUpgradesArmor(server)) {
			return;
		}
		Container inputSlots = ((ItemCombinerMenuAccessor) (Object) this).combatlogcommands$inputSlots();
		if (!combatlogcommands$isUpgrade(inputSlots)) {
			return;
		}

		ItemStack base = inputSlots.getItem(1).copy();
		// Rebuild exactly what was previewed/delivered so we can find and remove it (matching item +
		// components, NOT count - a shift-move changes the count on the passed stack, which is what let
		// the item be duplicated). Build it before consuming the inputs.
		ItemStack delivered = MaxGear.build(level, base, serverPlayer.getUUID());

		// Consume the inputs ourselves (template + diamond + ingot), then skip vanilla onTake.
		inputSlots.removeItem(0, 1);
		inputSlots.removeItem(1, 1);
		inputSlots.removeItem(2, 1);

		// The maxed preview was just handed to the cursor or the inventory - take exactly one back; the
		// real item is delivered through the confirm menu instead. Diamond gear is non-stackable, so
		// there's a single matching stack to remove.
		AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
		if (ItemStack.isSameItemSameComponents(menu.getCarried(), delivered)) {
			menu.setCarried(ItemStack.EMPTY);
		} else {
			Inventory inv = serverPlayer.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				if (ItemStack.isSameItemSameComponents(inv.getItem(i), delivered)) {
					inv.removeItemNoUpdate(i);
					break;
				}
			}
		}
		((ItemCombinerMenuAccessor) (Object) this).combatlogcommands$resultSlots().setItem(0, ItemStack.EMPTY);

		UUID id = serverPlayer.getUUID();
		server.execute(() -> {
			ServerPlayer target = server.getPlayerList().getPlayer(id);
			if (target != null) {
				NethUpgradeMenu.open(target, base);
			}
		});
		ci.cancel();
	}

	private boolean combatlogcommands$isUpgrade(Container inputSlots) {
		return inputSlots.getItem(0).getItem() == Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE
				&& inputSlots.getItem(2).getItem() == Items.NETHERITE_INGOT
				&& MaxGear.canMax(inputSlots.getItem(1).getItem());
	}

	private Player combatlogcommands$findPlayer() {
		for (Slot slot : ((AbstractContainerMenu) (Object) this).slots) {
			if (slot.container instanceof Inventory inv) {
				return inv.player;
			}
		}
		return null;
	}
}
