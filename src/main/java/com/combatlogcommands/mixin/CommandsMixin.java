package com.combatlogcommands.mixin;

import com.combatlogcommands.CombatLogCommands;
import com.combatlogcommands.combat.BackCooldown;
import com.combatlogcommands.combat.CombatState;
import com.combatlogcommands.combat.TeleportWarmup;
import com.combatlogcommands.combat.TpaManager;
import com.combatlogcommands.combat.TpaRequests;
import com.combatlogcommands.config.ModConfig;
import com.combatlogcommands.gui.TpaRequestsMenu;
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

import java.util.UUID;

/**
 * Every command dispatch - chat-typed, prefixed, command blocks, functions - funnels through
 * {@link Commands#performCommand}, regardless of which mod registered the command. Intercepting here
 * lets us block the configured escape commands for any mod's command, and fully take over the
 * /tpa //tpahere //tpaccept //tpdeny flow (cancelled here and handled by {@code TpaManager} instead
 * of whatever mod registered them, so requests can be silent + action-bar based with a menu accept).
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

		// From here on the teleport-request flow is entirely ours; the providing mod never sees it.
		if (label.equalsIgnoreCase("tpa") || label.equalsIgnoreCase("tpahere")) {
			ci.cancel();
			if (firstArg == null) {
				source.sendFailure(Component.literal("Usage: /" + label.toLowerCase() + " <player>").withStyle(ChatFormatting.RED));
				return;
			}
			ServerPlayer target = server.getPlayerList().getPlayerByName(firstArg);
			if (target == null) {
				source.sendFailure(Component.literal("Player not found: " + firstArg).withStyle(ChatFormatting.RED));
				return;
			}
			if (target == player) {
				source.sendFailure(Component.literal("You can't send a teleport request to yourself.").withStyle(ChatFormatting.RED));
				return;
			}
			TpaRequests.Type type = label.equalsIgnoreCase("tpa") ? TpaRequests.Type.TPA : TpaRequests.Type.TPAHERE;
			TpaManager.request(server, player, target, type);
			return;
		}

		if (label.equalsIgnoreCase("tpdeny")) {
			ci.cancel();
			TpaManager.deny(player);
			return;
		}

		if (label.equalsIgnoreCase("tpaccept")) {
			ci.cancel();
			if (TpaRequests.pending(player.getUUID()).isEmpty()) {
				TeleportWarmup.actionBar(player, Component.literal("You have no pending teleport requests.").withStyle(ChatFormatting.RED));
				return;
			}
			TpaRequestsMenu.open(player);
			return;
		}

		// Self-teleport commands (/back, /rtp, ...): hold for the countdown, then re-dispatch to the
		// providing mod. /back additionally has its own cooldown, checked and consumed here.
		if (ModConfig.get().isWarmupCommand(label)) {
			boolean isBack = label.equalsIgnoreCase("back");
			if (isBack) {
				long remainingMs = BackCooldown.remainingMillis(player.getUUID());
				if (remainingMs > 0) {
					long remainingSeconds = (remainingMs + 999) / 1000;
					source.sendFailure(Component.literal("You must wait " + remainingSeconds + "s before using /back again.").withStyle(ChatFormatting.RED));
					ci.cancel();
					return;
				}
			}
			// The cooldown only starts if the teleport actually goes through.
			ci.cancel();
			UUID playerId = player.getUUID();
			TeleportWarmup.begin(player, null, ModConfig.get().teleportWarmupTicks(), () -> {
				if (isBack) {
					BackCooldown.use(playerId);
				}
				TeleportWarmup.redispatchWithBypass(server, source, playerId, command);
				ServerPlayer arrived = server.getPlayerList().getPlayer(playerId);
				if (arrived != null) {
					TeleportWarmup.playArrivalSound(arrived);
				}
			});
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
