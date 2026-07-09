package com.combatlogcommands.mixin;

import com.combatlogcommands.CombatLogCommands;
import com.combatlogcommands.combat.BackCooldown;
import com.combatlogcommands.combat.CombatState;
import com.combatlogcommands.config.ModConfig;
import com.mojang.brigadier.ParseResults;
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
 * Every command dispatch - chat-typed, prefixed, command blocks, functions - funnels through
 * {@link Commands#performCommand}, regardless of which mod registered the command. Intercepting here
 * lets us block the configured escape commands for any mod's command, not just ones this mod knows about.
 * (performPrefixedCommand is not the right target: it just strips a leading "/" and delegates here -
 * the normal player chat-command path calls this method directly.)
 */
@Mixin(Commands.class)
public class CommandsMixin {
	@Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
	private void combatlogcommands$blockDuringCombat(ParseResults<CommandSourceStack> parseResults, String command, CallbackInfo ci) {
		// This method is the single choke point for every command on the server, from every mod. A bug
		// here must never be allowed to escape and break command execution for other players/mods, so
		// anything unexpected is swallowed and logged rather than left to propagate.
		try {
			combatlogcommands$check(parseResults, command, ci);
		} catch (Throwable t) {
			CombatLogCommands.LOGGER.error("combatlogcommands command check threw, letting the command through unblocked", t);
		}
	}

	private static void combatlogcommands$check(ParseResults<CommandSourceStack> parseResults, String command, CallbackInfo ci) {
		CommandSourceStack source = parseResults.getContext().getSource();
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			return;
		}

		MinecraftServer server = source.getServer();
		if (server == null) {
			return;
		}

		String label = command.startsWith("/") ? command.substring(1) : command;
		int spaceIndex = label.indexOf(' ');
		if (spaceIndex >= 0) {
			label = label.substring(0, spaceIndex);
		}

		if (CombatState.get(server).isInCombat(player.getUUID()) && ModConfig.get().isBlockedCommand(label)) {
			source.sendFailure(Component.literal("You can't use /" + label + " during combat.").withStyle(ChatFormatting.RED));
			ci.cancel();
			return;
		}

		if (label.equalsIgnoreCase("back")) {
			long remainingMs = BackCooldown.remainingMillis(player.getUUID());
			if (remainingMs > 0) {
				long remainingSeconds = (remainingMs + 999) / 1000;
				source.sendFailure(Component.literal("You must wait " + remainingSeconds + "s before using /back again.").withStyle(ChatFormatting.RED));
				ci.cancel();
				return;
			}
			BackCooldown.use(player.getUUID());
		}
	}
}
