package com.combatlogcommands.gui;

import com.combatlogcommands.nethupgrade.MaxGear;
import com.combatlogcommands.nethupgrade.NethEnchantPrefs;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Opens right after a donethupgradesarmor upgrade. Shows the item being maxed plus a toggle for each
 * of its enchants; click the emerald to confirm and receive the item, or just close the menu (either
 * way you get the item with your current toggles - no losing your diamond). Vanilla-client friendly:
 * a chest UI with every click intercepted, no real item movement.
 */
public class NethUpgradeMenu extends ChestMenu {
	private static final int SIZE = 54;
	private static final int PREVIEW_SLOT = 4;
	private static final int CONFIRM_SLOT = 49;

	private final ServerPlayer player;
	private final ItemStack base;
	private final SimpleContainer buttons;
	private final Runnable[] actions = new Runnable[SIZE];
	private boolean delivered;

	private NethUpgradeMenu(int containerId, ServerPlayer player, ItemStack base, SimpleContainer container) {
		super(MenuType.GENERIC_9x6, containerId, player.getInventory(), container, 6);
		this.player = player;
		this.base = base;
		this.buttons = container;
		render();
	}

	private void render() {
		for (int i = 0; i < SIZE; i++) {
			setButton(i, icon(new ItemStack(Items.GRAY_STAINED_GLASS_PANE), " "), null);
		}

		setButton(PREVIEW_SLOT, MaxGear.build(player.level(), base, player.getUUID()), null);

		List<MaxGear.Entry> entries = MaxGear.enchantsFor(base.getItem());
		for (int i = 0; i < entries.size(); i++) {
			MaxGear.Entry entry = entries.get(i);
			int slot = 18 + (i / 7) * 9 + (i % 7) + 1;
			boolean enabled = MaxGear.isEnabled(player.getUUID(), entry);
			ItemStack book = new ItemStack(enabled ? Items.ENCHANTED_BOOK : Items.BOOK);
			String hint = entry.group() == null
					? (enabled ? "Click to remove it" : "Click to add it back")
					: (enabled ? "Click to swap it out" : "Click to use this one");
			setButton(slot, icon(book, (enabled ? "§a" : "§c") + entry.display(),
							enabled ? "Status: ENABLED" : "Status: DISABLED", hint),
					() -> toggle(entry));
		}

		setButton(CONFIRM_SLOT, icon(new ItemStack(Items.EMERALD), "§aConfirm",
				"Take your maxed item"), this::confirm);

		broadcastChanges();
	}

	private void toggle(MaxGear.Entry entry) {
		boolean nowEnabled = !MaxGear.isEnabled(player.getUUID(), entry);
		setEnabled(entry, nowEnabled);
		// Turning on a member of an exclusive group (e.g. Silk Touch) turns the others off (Fortune),
		// so incompatible enchants can never both be applied.
		if (nowEnabled && entry.group() != null) {
			for (MaxGear.Entry other : MaxGear.enchantsFor(base.getItem())) {
				if (entry.group().equals(other.group()) && !other.key().equals(entry.key())) {
					setEnabled(other, false);
				}
			}
		}
		render();
	}

	private void setEnabled(MaxGear.Entry entry, boolean enabled) {
		// isEnabled == defaultOn ^ flipped, so flipped needed for a target state = defaultOn != enabled.
		NethEnchantPrefs.setFlipped(player.getUUID(), entry.key(), entry.defaultOn() != enabled);
	}

	private void confirm() {
		if (delivered) {
			return;
		}
		delivered = true;
		deliver();
		player.level().getServer().execute(player::closeContainer);
	}

	private void deliver() {
		player.getInventory().placeItemBackInInventory(MaxGear.build(player.level(), base, player.getUUID()));
	}

	@Override
	public void removed(Player p) {
		// Closing the menu without confirming still hands over the item, so the upgrade is never lost.
		if (!delivered) {
			delivered = true;
			deliver();
		}
		super.removed(p);
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

	public static void open(ServerPlayer player, ItemStack base) {
		ItemStack baseCopy = base.copy();
		baseCopy.setCount(1);
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new NethUpgradeMenu(id, player, baseCopy, new SimpleContainer(SIZE)),
				Component.literal("Choose Enchants")));
	}
}
