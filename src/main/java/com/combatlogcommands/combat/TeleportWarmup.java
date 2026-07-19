package com.combatlogcommands.combat;

import com.combatlogcommands.CombatLogCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
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
 * The 3... 2... 1... teleport countdown. A held command (/tpaccept or /back) is cancelled at
 * dispatch, and while the player who is about to teleport stands still, a subtitle counts down each
 * second. When it reaches zero the original command is re-dispatched (with a bypass so our mixin
 * lets it through) and an ender pearl sound plays; if the watched player moves or gets
 * combat-tagged first, the teleport is cancelled and nothing is dispatched.
 */
public class TeleportWarmup {
	/** Squared blocks of drift allowed before it counts as "moved" (0.15 blocks). */
	private static final double MOVE_TOLERANCE_SQ = 0.0225;

	private static class Pending {
		final UUID watchedId;
		final UUID dispatcherId;
		final CommandSourceStack dispatchSource;
		final String command;
		final Vec3 startPos;
		final Runnable onSuccess;
		int ticksRemaining;

		Pending(ServerPlayer watched, UUID dispatcherId, CommandSourceStack dispatchSource, String command,
				int ticksRemaining, Runnable onSuccess) {
			this.watchedId = watched.getUUID();
			this.dispatcherId = dispatcherId;
			this.dispatchSource = dispatchSource;
			this.command = command;
			this.startPos = watched.position();
			this.ticksRemaining = ticksRemaining;
			this.onSuccess = onSuccess;
		}
	}

	private static final Map<UUID, Pending> pendingByWatched = new ConcurrentHashMap<>();
	private static final Set<UUID> bypassNextCommand = ConcurrentHashMap.newKeySet();

	private TeleportWarmup() {
	}

	/**
	 * Starts (or restarts) a countdown. {@code watched} is the player who will teleport and must
	 * stand still; {@code dispatchSource}/{@code command} is the original command to re-dispatch on
	 * success, on behalf of whoever typed it (not necessarily the watched player - for /tpa it's the
	 * accepter). {@code onSuccess} runs just before the re-dispatch.
	 */
	public static void begin(ServerPlayer watched, ServerPlayer dispatcher, CommandSourceStack dispatchSource,
			String command, int warmupTicks, Runnable onSuccess) {
		pendingByWatched.put(watched.getUUID(),
				new Pending(watched, dispatcher.getUUID(), dispatchSource, command, warmupTicks, onSuccess));
		showCount(watched, (warmupTicks + 19) / 20);
	}

	/** True exactly once per re-dispatched command: tells the mixin to let it straight through. */
	public static boolean consumeBypass(UUID dispatcherId) {
		return bypassNextCommand.remove(dispatcherId);
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
				continue;
			}

			if (watched.position().distanceToSqr(pending.startPos) > MOVE_TOLERANCE_SQ) {
				cancel(server, pending, watched, "you moved", watched.getScoreboardName() + " moved");
				continue;
			}

			if (CombatState.get(server).isInCombat(pending.watchedId)) {
				cancel(server, pending, watched, "you entered combat", watched.getScoreboardName() + " entered combat");
				continue;
			}

			pending.ticksRemaining--;
			if (pending.ticksRemaining <= 0) {
				pendingByWatched.remove(pending.watchedId);
				pending.onSuccess.run();
				bypassNextCommand.add(pending.dispatcherId);
				server.getCommands().performPrefixedCommand(pending.dispatchSource, pending.command);
				bypassNextCommand.remove(pending.dispatcherId);
				watched.level().playSound(null, watched.getX(), watched.getY(), watched.getZ(),
						SoundEvents.ENDER_PEARL_THROW, SoundSource.PLAYERS, 1.0f, 1.0f);
			} else if (pending.ticksRemaining % 20 == 0) {
				showCount(watched, pending.ticksRemaining / 20);
			}
		}
	}

	private static void cancel(MinecraftServer server, Pending pending, ServerPlayer watched,
			String reasonForWatched, String reasonForDispatcher) {
		pendingByWatched.remove(pending.watchedId);
		watched.connection.send(new ClientboundSetSubtitleTextPacket(
				Component.literal("Teleport cancelled!").withStyle(ChatFormatting.RED)));
		watched.connection.send(new ClientboundSetTitleTextPacket(Component.literal("")));
		watched.sendSystemMessage(Component.literal("Teleport cancelled - " + reasonForWatched + ".").withStyle(ChatFormatting.RED));
		if (!pending.dispatcherId.equals(pending.watchedId)) {
			ServerPlayer dispatcher = server.getPlayerList().getPlayer(pending.dispatcherId);
			if (dispatcher != null) {
				dispatcher.sendSystemMessage(
						Component.literal("Teleport cancelled - " + reasonForDispatcher + ".").withStyle(ChatFormatting.RED));
			}
		}
	}

	private static void showCount(ServerPlayer player, int secondsLeft) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 25, 5));
		// Subtitles only render while a title is up, so show an empty title alongside.
		player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("")));
		player.connection.send(new ClientboundSetSubtitleTextPacket(
				Component.literal(secondsLeft + "...").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)));
	}
}
