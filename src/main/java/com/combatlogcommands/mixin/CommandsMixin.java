package com.combatlogcommands.mixin;

import com.combatlogcommands.CombatLogCommands;
import com.combatlogcommands.combat.BackCooldown;
import com.combatlogcommands.combat.CombatState;
import com.combatlogcommands.combat.TeleportWarmup;
import com.combatlogcommands.combat.TpaRequests;
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

		// A command we held for a teleport countdown and are now re-dispatching ourselves.
		if (TeleportWarmup.consumeBypass(player.getUUID())) {
			return;
		}

		String withoutSlash = command.startsWith("/") ? command.substring(1) : command;
		int spaceIndex = withoutSlash.indexOf(' ');
		String label = spaceIndex >= 0 ? withoutSlash.substring(0, spaceIndex) : withoutSlash;
		String firstArg = combatlogcommands$firstArg(withoutSlash, spaceIndex);

		if (CombatState.get(server).isInCombat(player.getUUID()) && ModConfig.get().isBlockedCommand(label)) {
			source.sendFailure(Component.literal("You can't use /" + label + " during combat.").withStyle(ChatFormatting.RED));
			ci.cancel();
			return;
		}

		// e.g. "/tpa <name>" or "/tpahere <name>" aimed at someone who is mid-fight: refuse to disturb
		// them regardless of whether the sender is in combat themselves.
		if (ModConfig.get().isTargetBlockedCommand(label) && firstArg != null) {
			ServerPlayer target = server.getPlayerList().getPlayerByName(firstArg);
			if (target != null && CombatState.get(server).isInCombat(target.getUUID())) {
				source.sendFailure(Component.literal("You can't send /" + label + " to " + target.getScoreboardName()
						+ " right now - they are in combat.").withStyle(ChatFormatting.RED));
				ci.cancel();
				return;
			}
		}

		// Record outgoing teleport requests so a later /tpaccept knows who would teleport.
		if ((label.equalsIgnoreCase("tpa") || label.equalsIgnoreCase("tpahere")) && firstArg != null) {
			ServerPlayer target = server.getPlayerList().getPlayerByName(firstArg);
			if (target != null) {
				TpaRequests.Type type = label.equalsIgnoreCase("tpa") ? TpaRequests.Type.TPA : TpaRequests.Type.TPAHERE;
				TpaRequests.record(target.getUUID(), player.getUUID(), player.getScoreboardName(), type);
			}
			return;
		}

		if (label.equalsIgnoreCase("tpdeny")) {
			TpaRequests.clear(player.getUUID());
			return;
		}

		if (label.equalsIgnoreCase("tpaccept")) {
			TpaRequests.Request request = TpaRequests.find(player.getUUID(), firstArg);
			if (request == null) {
				// Nothing recorded (request predates us or came from an unknown path): let the
				// providing mod handle it directly, without a countdown.
				return;
			}
			// For /tpa the REQUESTER teleports to the accepter; for /tpahere the accepter teleports.
			ServerPlayer teleporting = request.type() == TpaRequests.Type.TPA
					? server.getPlayerList().getPlayer(request.requesterId())
					: player;
			if (teleporting == null) {
				return;
			}
			ci.cancel();
			TeleportWarmup.begin(teleporting, player, source, command, ModConfig.get().teleportWarmupTicks(),
					() -> TpaRequests.consume(player.getUUID(), request));
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
			// Hold the command for the countdown; the cooldown only starts if the teleport goes through.
			ci.cancel();
			TeleportWarmup.begin(player, player, source, command, ModConfig.get().teleportWarmupTicks(),
					() -> BackCooldown.use(player.getUUID()));
		}
	}

	private static String combatlogcommands$firstArg(String withoutSlash, int spaceIndex) {
		if (spaceIndex < 0) {
			return null;
		}
		String rest = withoutSlash.substring(spaceIndex + 1).trim();
		if (rest.isEmpty()) {
			return null;
		}
		int nextSpace = rest.indexOf(' ');
		return nextSpace >= 0 ? rest.substring(0, nextSpace) : rest;
	}
}
