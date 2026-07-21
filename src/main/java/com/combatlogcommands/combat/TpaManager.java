package com.combatlogcommands.combat;

import com.combatlogcommands.CombatLogCommands;
import com.combatlogcommands.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Our own /tpa //tpahere flow, replacing the providing mod's entirely (its dispatch is cancelled in
 * the command mixin). Requests never touch chat: the target gets an action-bar notice with a sound,
 * re-shown every few seconds while pending, and accepts through the /tpaccept menu. Accepting runs
 * the shared action-bar countdown for both players and then teleports directly.
 */
public class TpaManager {
	private static final int REMINDER_INTERVAL_TICKS = 100;

	private TpaManager() {
	}

	public static void request(MinecraftServer server, ServerPlayer requester, ServerPlayer target, TpaRequests.Type type) {
		// Anti-spam: while an earlier request to this same target is still pending, refuse to send
		// another (which would re-flash the notice and re-play the chime). They wait it out or the
		// target responds.
		long remainingMs = TpaRequests.existingRequestRemainingMillis(target.getUUID(), requester.getUUID());
		if (remainingMs > 0) {
			long remainingSeconds = (remainingMs + 999) / 1000;
			TeleportWarmup.actionBar(requester, Component.literal("You already have a request pending to "
					+ target.getScoreboardName() + " - wait " + remainingSeconds + "s.").withStyle(ChatFormatting.RED));
			return;
		}

		TpaRequests.record(target.getUUID(), requester.getUUID(), requester.getScoreboardName(), type);
		showRequestNotice(target, requester.getScoreboardName(), type);
		// A distinct "incoming request" chime, so the notice isn't missed.
		target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
				SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.6f);
		TeleportWarmup.actionBar(requester, Component.literal("Request sent to " + target.getScoreboardName() + ".")
				.withStyle(ChatFormatting.GREEN));
	}

	public static void deny(ServerPlayer target) {
		TpaRequests.clear(target.getUUID());
		TeleportWarmup.actionBar(target, Component.literal("Teleport requests denied.").withStyle(ChatFormatting.YELLOW));
	}

	/** Starts the countdown for an accepted request. Called from the /tpaccept menu. */
	public static void accept(MinecraftServer server, ServerPlayer target, TpaRequests.Request request) {
		ServerPlayer requester = server.getPlayerList().getPlayer(request.requesterId());
		if (requester == null || request.expired()) {
			TpaRequests.consume(target.getUUID(), request);
			TeleportWarmup.actionBar(target, Component.literal("That request has expired.").withStyle(ChatFormatting.RED));
			return;
		}

		// For /tpa the requester travels to the target; for /tpahere the target travels to the requester.
		ServerPlayer moving = request.type() == TpaRequests.Type.TPA ? requester : target;
		ServerPlayer anchor = request.type() == TpaRequests.Type.TPA ? target : requester;

		CombatState combat = CombatState.get(server);
		if (combat.isInCombat(moving.getUUID()) || combat.isInCombat(anchor.getUUID())) {
			TeleportWarmup.actionBar(target, Component.literal("You can't teleport during combat.").withStyle(ChatFormatting.RED));
			return;
		}

		UUID movingId = moving.getUUID();
		UUID anchorId = anchor.getUUID();
		UUID targetId = target.getUUID();
		TeleportWarmup.begin(moving, anchor, ModConfig.get().teleportWarmupTicks(),
				() -> completeTeleport(server, movingId, anchorId, targetId, request));
	}

	private static void completeTeleport(MinecraftServer server, UUID movingId, UUID anchorId, UUID targetId,
			TpaRequests.Request request) {
		ServerPlayer moving = server.getPlayerList().getPlayer(movingId);
		ServerPlayer anchor = server.getPlayerList().getPlayer(anchorId);
		if (moving == null || anchor == null) {
			return;
		}
		TpaRequests.consume(targetId, request);
		moving.teleportTo(anchor.level(), anchor.getX(), anchor.getY(), anchor.getZ(), Set.of(),
				moving.getYRot(), moving.getXRot(), false);
		TeleportWarmup.playArrivalSound(moving);
		TeleportWarmup.actionBar(moving, Component.literal("Teleported!").withStyle(ChatFormatting.GREEN));
		TeleportWarmup.actionBar(anchor, Component.literal("Teleported!").withStyle(ChatFormatting.GREEN));
	}

	public static void onServerTick(MinecraftServer server) {
		try {
			if (server.getTickCount() % REMINDER_INTERVAL_TICKS != 0) {
				return;
			}
			remindPendingTargets(server);
		} catch (Throwable t) {
			CombatLogCommands.LOGGER.error("combatlogcommands tpa reminder tick threw", t);
		}
	}

	// The action bar only lingers a couple of seconds, so quietly re-show the notice while a request
	// is pending - unless the combat timer or a countdown currently owns that screen space.
	private static void remindPendingTargets(MinecraftServer server) {
		for (UUID targetId : TpaRequests.targetsWithPending()) {
			ServerPlayer target = server.getPlayerList().getPlayer(targetId);
			if (target == null || CombatState.get(server).isInCombat(targetId) || TeleportWarmup.isCountingDown(targetId)) {
				continue;
			}
			List<TpaRequests.Request> pending = TpaRequests.pending(targetId);
			if (!pending.isEmpty()) {
				TpaRequests.Request latest = pending.get(pending.size() - 1);
				showRequestNotice(target, latest.requesterName(), latest.type());
			}
		}
	}

	private static void showRequestNotice(ServerPlayer target, String requesterName, TpaRequests.Type type) {
		String text = type == TpaRequests.Type.TPA
				? requesterName + " wants to teleport to you - use /tpaccept"
				: requesterName + " wants you to teleport to them - use /tpaccept";
		TeleportWarmup.actionBar(target, Component.literal(text).withStyle(ChatFormatting.AQUA));
	}
}
