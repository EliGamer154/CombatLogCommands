package com.combatlogcommands.combat;

import com.combatlogcommands.CombatLogCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.UUID;

public class CombatHandler {
	private static final ChatFormatting COMBAT_COLOR = ChatFormatting.RED;

	private CombatHandler() {
	}

	// Each of these is registered directly against a shared Fabric event (fired for every entity/player/tick
	// on the server, for every mod listening). Fabric chains listeners together, so an uncaught exception here
	// would also stop any other mod's listener on the same event from running. Never let one escape.

	public static void onDamage(LivingEntity entity, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked) {
		try {
			handleDamage(entity, source);
		} catch (Throwable t) {
			CombatLogCommands.LOGGER.error("combatlogcommands damage handling threw", t);
		}
	}

	public static void onLeave(ServerPlayer player) {
		try {
			handleLeave(player);
		} catch (Throwable t) {
			CombatLogCommands.LOGGER.error("combatlogcommands leave handling threw", t);
		}
	}

	public static void onJoin(ServerPlayer player) {
		try {
			handleJoin(player);
		} catch (Throwable t) {
			CombatLogCommands.LOGGER.error("combatlogcommands join handling threw", t);
		}
	}

	public static void onServerTick(MinecraftServer server) {
		try {
			handleServerTick(server);
		} catch (Throwable t) {
			CombatLogCommands.LOGGER.error("combatlogcommands tick handling threw", t);
		}
	}

	private static void handleDamage(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer victim)) {
			return;
		}
		if (!(source.getEntity() instanceof ServerPlayer attacker) || attacker == victim) {
			return;
		}

		MinecraftServer server = victim.level().getServer();

		CombatState state = CombatState.get(server);
		state.tag(attacker.getUUID(), CombatLogCommands.COMBAT_DURATION_MILLIS);
		state.tag(victim.getUUID(), CombatLogCommands.COMBAT_DURATION_MILLIS);
	}

	private static void handleLeave(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();

		CombatState state = CombatState.get(server);
		if (state.isInCombat(player.getUUID())) {
			state.markPendingLogoutKill(player.getUUID());
			CombatLogCommands.LOGGER.info("{} disconnected while in combat, will be slain on next login", player.getScoreboardName());
		}
		state.clear(player.getUUID());
	}

	private static void handleJoin(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();

		CombatState state = CombatState.get(server);
		if (state.consumePendingLogoutKill(player.getUUID())) {
			CombatLogCommands.LOGGER.info("Slaying {} for disconnecting during combat", player.getScoreboardName());
			player.kill(player.level());
			player.sendSystemMessage(Component.literal("You disconnected during combat and were slain.").withStyle(COMBAT_COLOR));
		}
	}

	private static void handleServerTick(MinecraftServer server) {
		if (server.getTickCount() % 10 != 0) {
			return;
		}

		CombatState state = CombatState.get(server);
		for (UUID id : List.copyOf(state.combatants())) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null) {
				continue;
			}

			long remainingMs = state.remainingMillis(id);
			if (remainingMs <= 0) {
				state.clear(id);
				player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal("")));
				continue;
			}

			long remainingSeconds = (remainingMs + 999) / 1000;
			Component text = Component.literal("Combat Tag: " + remainingSeconds + "s").withStyle(COMBAT_COLOR);
			player.connection.send(new ClientboundSetActionBarTextPacket(text));
		}
	}
}
