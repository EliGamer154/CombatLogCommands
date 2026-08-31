package com.combatlogcommands.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ResultContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes ItemCombinerMenu's protected input/result containers so SmithingMenuMixin can read/write them. */
@Mixin(ItemCombinerMenu.class)
public interface ItemCombinerMenuAccessor {
	@Accessor("inputSlots")
	Container combatlogcommands$inputSlots();

	@Accessor("resultSlots")
	ResultContainer combatlogcommands$resultSlots();
}
