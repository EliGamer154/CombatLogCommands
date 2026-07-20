package com.combatlogcommands.gui;

import com.combatlogcommands.combat.TpaManager;
import com.combatlogcommands.combat.TpaRequests;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * The /tpaccept screen: each pending teleport request shows as the requester's head - click one to
 * accept it and start the countdown. Works with a vanilla client (GENERIC_9x3 chest UI, every click
 * intercepted; no real item movement can happen).
 */
public class TpaRequestsMenu extends ChestMenu {
	private static final int SIZE = 27;
	private static final int MAX_REQUESTS = 7;

	private final ServerPlayer player;
	private final SimpleContainer buttons;
	private final Runnable[] actions = new Runnable[SIZE];

	private TpaRequestsMenu(int containerId, ServerPlayer player, SimpleContainer container) {
		super(MenuType.GENERIC_9x3, containerId, player.getInventory(), container, 3);
		this.player = player;
		this.buttons = container;
		render();
	}

	private void render() {
		for (int i = 0; i < SIZE; i++) {
			setButton(i, icon(new ItemStack(Items.GRAY_STAINED_GLASS_PANE), " "), null);
		}

		List<TpaRequests.Request> pending = TpaRequests.pending(player.getUUID());
		int shown = Math.min(pending.size(), MAX_REQUESTS);
		for (int i = 0; i < shown; i++) {
			// Newest first, laid out across the middle row.
			TpaRequests.Request request = pending.get(pending.size() - 1 - i);
			ItemStack head = new ItemStack(Items.PLAYER_HEAD);
			head.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(request.requesterId()));
			String wants = request.type() == TpaRequests.Type.TPA ? "Wants to teleport to you" : "Wants you to teleport to them";
			setButton(10 + i, icon(head, request.requesterName(), wants, "Click to accept"),
					() -> accept(request));
		}

		if (shown == 0) {
			setButton(13, icon(new ItemStack(Items.BARRIER), "No pending requests"), null);
		} else {
			setButton(22, icon(new ItemStack(Items.BARRIER), "Deny all", "Turn down every pending request"),
					this::denyAll);
		}

		broadcastChanges();
	}

	private void accept(TpaRequests.Request request) {
		// Container switches must not happen while the server is still processing this click's
		// packet, so defer by a tick (same pattern as the other server-side menus on this server).
		player.level().getServer().execute(() -> {
			player.closeContainer();
			TpaManager.accept(player.level().getServer(), player, request);
		});
	}

	private void denyAll() {
		player.level().getServer().execute(() -> {
			player.closeContainer();
			TpaManager.deny(player);
		});
	}

	private void setButton(int slot, ItemStack stack, Runnable action) {
		buttons.setItem(slot, stack);
		actions[slot] = action;
	}

	private static ItemStack icon(ItemStack base, String name, String... lore) {
		ItemStack stack = base.copy();
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(style -> style.withItalic(false)));
		if (lore.length > 0) {
			List<Component> lines = new ArrayList<>();
			for (String line : lore) {
				lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
			}
			stack.set(DataComponents.LORE, new ItemLore(lines));
		}
		return stack;
	}

	@Override
	public void clicked(int slotId, int clickData, ContainerInput containerInput, Player clicker) {
		if (slotId >= 0 && slotId < SIZE && actions[slotId] != null) {
			actions[slotId].run();
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	public static void open(ServerPlayer player) {
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new TpaRequestsMenu(id, player, new SimpleContainer(SIZE)),
				Component.literal("Teleport Requests")));
	}
}
