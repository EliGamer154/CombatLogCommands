package com.combatlogcommands.mixin;

import com.combatlogcommands.combat.CombatState;
import com.combatlogcommands.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every player-typed command funnels through {@link Commands#performPrefixedCommand}, regardless of
 * which mod registered it. Intercepting here lets us block the configured escape commands for any
 * mod's command, not just ones this mod knows about.
 */
@Mixin(Commands.class)
public class CommandsMixin {
	@Inject(method = "performPrefixedCommand", at = @At("HEAD"), cancellable = true)
	private void combatlogcommands$blockDuringCombat(CommandSourceStack source, String command, CallbackInfo ci) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return;
		}

		MinecraftServer server = source.getServer();
		if (server == null || !CombatState.get(server).isInCombat(player.getUUID())) {
			return;
		}

		String label = command.startsWith("/") ? command.substring(1) : command;
		int spaceIndex = label.indexOf(' ');
		if (spaceIndex >= 0) {
			label = label.substring(0, spaceIndex);
		}

		if (ModConfig.get().isBlockedCommand(label)) {
			source.sendFailure(Component.literal("You can't use /" + label + " during combat.").withStyle(ChatFormatting.RED));
			ci.cancel();
		}
	}
}
