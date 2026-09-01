package com.combatlogcommands.admin;

import com.combatlogcommands.CombatLogCommands;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.GameType;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Applies the /admintools toggles: cancels damage for god/no-fall, keeps buffs topped up, and holds frozen players in place. */
public final class AdminToolsHandler {
	private static final Identifier SPEED_MODIFIER_ID =
			Identifier.fromNamespaceAndPath(CombatLogCommands.MOD_ID, "admin_speed");
	private static final double SPEED_BONUS = 1.0; // +100% movement speed

	private AdminToolsHandler() {
	}

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		try {
			if (entity instanceof ServerPlayer player) {
				UUID id = player.getUUID();
				if (AdminToolsState.isOn(AdminToolsState.Toggle.GOD, id)) {
					return false;
				}
				if (AdminToolsState.isOn(AdminToolsState.Toggle.NO_FALL, id) && source.is(DamageTypes.FALL)) {
					return false;
				}
			}
		} catch (Throwable t) {
			CombatLogCommands.LOGGER.error("combatlogcommands admin damage check threw", t);
		}
		return true;
	}

	public static void onServerTick(MinecraftServer server) {
		try {
			holdFrozen(server);
			if (server.getTickCount() % 10 == 0) {
				reconcileBuffs(server);
			}
		} catch (Throwable t) {
			CombatLogCommands.LOGGER.error("combatlogcommands admin tick threw", t);
		}
	}

	private static void holdFrozen(MinecraftServer server) {
		for (UUID id : List.copyOf(AdminToolsState.frozenIds())) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			AdminToolsState.FreezeAnchor anchor = AdminToolsState.freezeAnchor(id);
			if (player == null || anchor == null) {
				continue;
			}
			if (player.getX() != anchor.x() || player.getY() != anchor.y() || player.getZ() != anchor.z()) {
				ServerLevel level = server.getLevel(anchor.dimension());
				if (level != null) {
					player.teleportTo(level, anchor.x(), anchor.y(), anchor.z(), Set.of(), anchor.yaw(), anchor.pitch(), false);
				}
			}
		}
	}

	private static void reconcileBuffs(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			applyFly(player, AdminToolsState.isOn(AdminToolsState.Toggle.FLY, id));
			reconcileSpeed(player, AdminToolsState.isOn(AdminToolsState.Toggle.SPEED, id));
			if (AdminToolsState.isOn(AdminToolsState.Toggle.NIGHT_VISION, id)) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false));
			}
		}
	}

	/** Grants or revokes survival flight. Creative/spectator already fly, so it leaves those alone. */
	public static void applyFly(ServerPlayer player, boolean on) {
		GameType mode = player.gameMode();
		if (mode == GameType.CREATIVE || mode == GameType.SPECTATOR) {
			return;
		}
		Abilities abilities = player.getAbilities();
		if (on && !abilities.mayfly) {
			abilities.mayfly = true;
			player.onUpdateAbilities();
		} else if (!on && abilities.mayfly) {
			abilities.mayfly = false;
			abilities.flying = false;
			player.onUpdateAbilities();
		}
	}

	public static void reconcileSpeed(ServerPlayer player, boolean on) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		boolean has = speed.hasModifier(SPEED_MODIFIER_ID);
		if (on && !has) {
			speed.addTransientModifier(new AttributeModifier(SPEED_MODIFIER_ID, SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		} else if (!on && has) {
			speed.removeModifier(SPEED_MODIFIER_ID);
		}
	}

	public static void applyNightVision(ServerPlayer player, boolean on) {
		if (on) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false));
		} else {
			player.removeEffect(MobEffects.NIGHT_VISION);
		}
	}
}
