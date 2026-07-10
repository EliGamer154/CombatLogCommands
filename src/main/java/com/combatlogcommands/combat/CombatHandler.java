package com.combatlogcommands.combat;

import com.combatlogcommands.CombatLogCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

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

	public static void onServerTick(MinecraftServer server) {
		try {
			handleServerTick(server);
		} catch (Throwable t) {
			CombatLogCommands.LOGGER.error("combatlogcommands tick handling threw", t);
		}
	}

	public static InteractionResult onUseItem(Level world, Player player, InteractionHand hand) {
		try {
			return handleUseItem(world, player, hand);
		} catch (Throwable t) {
			CombatLogCommands.LOGGER.error("combatlogcommands item-use handling threw", t);
			return InteractionResult.PASS;
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

	// ServerPlayerEvents.LEAVE fires at the very start of player removal, while the player is still
	// fully present in the world - so killing them here (item drop, death message, the works) happens
	// before disconnect processing, not after. That's what stops combat-logging from being a free out:
	// there's no window where staying disconnected avoids the punishment.
	private static void handleLeave(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();

		CombatState state = CombatState.get(server);
		if (state.isInCombat(player.getUUID())) {
			CombatLogCommands.LOGGER.info("Slaying {} for disconnecting during combat", player.getScoreboardName());
			strikeVisualLightning(player);
			playThunderSound(player);
			player.kill(player.level());
			// A literal (non-translatable) component, not a data-driven death message: this mod ships no
			// client-side resource pack, and translation keys are resolved on the client, so a custom
			// DamageType's message would just show as its raw, untranslated key to players.
			Component message = Component.literal(player.getScoreboardName() + " has logged out during combat!").withStyle(COMBAT_COLOR);
			server.getPlayerList().broadcastSystemMessage(message, false);
		}
		state.clear(player.getUUID());
	}

	// Visual-only: no damage, no block ignition, no copper effects - purely a "struck down" effect for
	// the combat-log kill.
	private static void strikeVisualLightning(ServerPlayer player) {
		ServerLevel level = player.level();
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt == null) {
			return;
		}
		bolt.setVisualOnly(true);
		bolt.setPos(player.getX(), player.getY(), player.getZ());
		level.addFreshEntity(bolt);
	}

	// Same volume/pitch vanilla itself uses for real lightning strikes - thunder is meant to carry, not be
	// subtle. null "except" so it's audible to everyone nearby, including the disconnecting player: LEAVE
	// fires before their connection actually closes, so the sound packet still reaches them.
	private static void playThunderSound(ServerPlayer player) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 10000.0f, 0.8f);
	}

	// ItemEvents.USE wraps ItemStack's own dispatch to Item.use() - returning non-PASS here skips the
	// vanilla firework use entirely (including the elytra-boost path, which also goes through use()).
	private static InteractionResult handleUseItem(Level world, Player player, InteractionHand hand) {
		if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.PASS;
		}

		ItemStack stack = player.getItemInHand(hand);
		if (stack.getItem() != Items.FIREWORK_ROCKET) {
			return InteractionResult.PASS;
		}

		MinecraftServer server = serverPlayer.level().getServer();
		if (server == null || !CombatState.get(server).isInCombat(serverPlayer.getUUID())) {
			return InteractionResult.PASS;
		}

		UUID id = serverPlayer.getUUID();
		long remainingMs = FireworkCooldown.remainingMillis(id);
		if (remainingMs > 0) {
			long remainingSeconds = (remainingMs + 999) / 1000;
			serverPlayer.sendSystemMessage(
					Component.literal("You must wait " + remainingSeconds + "s before using another firework rocket.").withStyle(COMBAT_COLOR));
			return InteractionResult.FAIL;
		}

		FireworkCooldown.use(id);
		return InteractionResult.PASS;
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
