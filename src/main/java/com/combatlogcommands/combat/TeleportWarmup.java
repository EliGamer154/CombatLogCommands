package com.combatlogcommands.combat;

import com.combatlogcommands.CombatLogCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The 3... 2... 1... teleport countdown, shown as small action-bar text (same spot as the combat
 * timer) with a pearl sound per count. The watched player is the one who will teleport and must
 * stand still; the optional observer (the other party of a tpa/tpahere) sees the same countdown.
 * Moving, entering combat, or either party logging out cancels; when the count reaches zero the
 * {@code onComplete} action runs (a direct teleport for tpa flows, a held-command re-dispatch for
 * /back).
 */
public class TeleportWarmup {
	/** Squared blocks of drift allowed before it counts as "moved" (0.15 blocks). */
	private static final double MOVE_TOLERANCE_SQ = 0.0225;

	private static class Pending {
		final UUID watchedId;
		final UUID observerId;
		final Vec3 startPos;
		final Runnable onComplete;
		int ticksRemaining;

		Pending(ServerPlayer watched, UUID observerId, int ticksRemaining, Runnable onComplete) {
			this.watchedId = watched.getUUID();
			this.observerId = observerId;
			this.startPos = watched.position();
			this.ticksRemaining = ticksRemaining;
			this.onComplete = onComplete;
		}
	}

	private static final Map<UUID, Pending> pendingByWatched = new ConcurrentHashMap<>();
	private static final Set<UUID> bypassNextCommand = ConcurrentHashMap.newKeySet();

	private TeleportWarmup() {
	}

	public static void begin(ServerPlayer watched, ServerPlayer observerOrNull, int warmupTicks, Runnable onComplete) {
		UUID observerId = observerOrNull == null ? null : observerOrNull.getUUID();
		pendingByWatched.put(watched.getUUID(), new Pending(watched, observerId, warmupTicks, onComplete));
		int seconds = (warmupTicks + 19) / 20;
		showCount(watched, seconds);
		if (observerOrNull != null) {
			showCount(observerOrNull, seconds);
		}
	}

	/** True while the player is part of any active countdown, as the teleporter or the other party. */
	public static boolean isCountingDown(UUID playerId) {
		if (pendingByWatched.containsKey(playerId)) {
			return true;
		}
		for (Pending pending : pendingByWatched.values()) {
			if (playerId.equals(pending.observerId)) {
				return true;
			}
		}
		return false;
	}

	/** True exactly once per re-dispatched command: tells the mixin to let it straight through. */
	public static boolean consumeBypass(UUID dispatcherId) {
		return bypassNextCommand.remove(dispatcherId);
	}

	/** Re-runs a held command as its original sender, bypassing our own interception once. */
	public static void redispatchWithBypass(MinecraftServer server, CommandSourceStack source, UUID dispatcherId, String command) {
		bypassNextCommand.add(dispatcherId);
		try {
			server.getCommands().performPrefixedCommand(source, command);
		} finally {
			bypassNextCommand.remove(dispatcherId);
		}
	}

	public static void playArrivalSound(ServerPlayer player) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENDER_PEARL_THROW, SoundSource.PLAYERS, 1.0f, 1.0f);
	}

	public static void actionBar(ServerPlayer player, Component text) {
		player.connection.send(new ClientboundSetActionBarTextPacket(text));
	}

	public static void onServerTick(MinecraftServer server) {
		try {
			tick(server);
		} catch (Throwable t) {
			CombatLogCommands.LOGGER.error("combatlogcommands teleport warmup tick threw", t);
		}
	}

	private static void tick(MinecraftServer server) {
		for (Pending pending : Map.copyOf(pendingByWatched).values()) {
			ServerPlayer watched = server.getPlayerList().getPlayer(pending.watchedId);
			if (watched == null) {
				pendingByWatched.remove(pending.watchedId);
				notifyCancel(server, pending.observerId, "the other player left");
				continue;
			}

			ServerPlayer observer = pending.observerId == null ? null : server.getPlayerList().getPlayer(pending.observerId);
			if (pending.observerId != null && observer == null) {
				cancel(pending, watched, null, "the other player left", "");
				continue;
			}

			if (watched.position().distanceToSqr(pending.startPos) > MOVE_TOLERANCE_SQ) {
				cancel(pending, watched, observer, "you moved", watched.getScoreboardName() + " moved");
				continue;
			}

			CombatState combat = CombatState.get(server);
			if (combat.isInCombat(pending.watchedId)) {
				cancel(pending, watched, observer, "you entered combat", watched.getScoreboardName() + " entered combat");
				continue;
			}
			if (observer != null && combat.isInCombat(pending.observerId)) {
				cancel(pending, watched, observer, observer.getScoreboardName() + " entered combat", "you entered combat");
				continue;
			}

			pending.ticksRemaining--;
			if (pending.ticksRemaining <= 0) {
				pendingByWatched.remove(pending.watchedId);
				pending.onComplete.run();
			} else if (pending.ticksRemaining % 20 == 0) {
				int seconds = pending.ticksRemaining / 20;
				showCount(watched, seconds);
				if (observer != null) {
					showCount(observer, seconds);
				}
			}
		}
	}

	private static void cancel(Pending pending, ServerPlayer watched, ServerPlayer observer,
			String reasonForWatched, String reasonForObserver) {
		pendingByWatched.remove(pending.watchedId);
		actionBar(watched, Component.literal("Teleport cancelled - " + reasonForWatched + "!").withStyle(ChatFormatting.RED));
		if (observer != null) {
			actionBar(observer, Component.literal("Teleport cancelled - " + reasonForObserver + "!").withStyle(ChatFormatting.RED));
		}
	}

	private static void notifyCancel(MinecraftServer server, UUID playerId, String reason) {
		if (playerId == null) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(playerId);
		if (player != null) {
			actionBar(player, Component.literal("Teleport cancelled - " + reason + "!").withStyle(ChatFormatting.RED));
		}
	}

	private static void showCount(ServerPlayer player, int secondsLeft) {
		actionBar(player, Component.literal("Teleporting in " + secondsLeft + "...")
				.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
		// A pearl sound per count, pitched up as it approaches zero: 3 -> 0.8, 2 -> 1.0, 1 -> 1.2.
		float pitch = 1.4f - 0.2f * secondsLeft;
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENDER_PEARL_THROW, SoundSource.PLAYERS, 1.0f, pitch);
	}
}
