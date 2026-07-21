package com.combatlogcommands.mixin;

import com.combatlogcommands.compat.WaymarkCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Soft-target hook on the third-party Waymark mod's home menu ({@code homes.waymark.ui.HomeActionsMenu}).
 * Its teleport button teleports the player in its own code rather than by dispatching a command, so
 * the /home command countdown never sees it. This intercepts that teleport and routes it through the
 * warmup instead.
 *
 * {@code @Pseudo} + {@code remap = false} means this targets a non-Minecraft class by name and is
 * silently skipped on servers that don't have Waymark installed. {@code @Coerce Object} lets us
 * capture the Waymark {@code Home} argument without compiling against Waymark - it's read reflectively.
 */
@Pseudo
@Mixin(targets = "homes.waymark.ui.HomeActionsMenu", remap = false)
public class WaymarkHomeActionsMenuMixin {
	// require = 0: if a future Waymark changes this method, degrade to "no countdown" instead of crashing.
	@Inject(method = "teleport", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void combatlogcommands$homeWarmup(@Coerce Object home, CallbackInfo ci) {
		if (WaymarkCompat.interceptTeleport(this, home)) {
			ci.cancel();
		}
	}
}
