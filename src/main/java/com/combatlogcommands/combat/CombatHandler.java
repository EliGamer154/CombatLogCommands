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

	public static void onDamage(LivingEntity entity, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked) {
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

	public static void onLeave(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();

		CombatState state = CombatState.get(server);
		if (state.isInCombat(player.getUUID())) {
			state.markPendingLogoutKill(player.getUUID());
		}
		state.clear(player.getUUID());
	}

	public static void onJoin(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();

		CombatState state = CombatState.get(server);
		if (state.consumePendingLogoutKill(player.getUUID())) {
			player.kill(player.level());
			player.sendSystemMessage(Component.literal("You disconnected during combat and were slain.").withStyle(COMBAT_COLOR));
		}
	}

	public static void onServerTick(MinecraftServer server) {
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
