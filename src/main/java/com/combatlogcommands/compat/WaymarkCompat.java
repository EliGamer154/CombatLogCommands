package com.combatlogcommands.compat;

import com.combatlogcommands.CombatLogCommands;
import com.combatlogcommands.combat.CombatState;
import com.combatlogcommands.combat.TeleportWarmup;
import com.combatlogcommands.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.UUID;

/**
 * Optional runtime bridge to the third-party Waymark home mod. Waymark's menu teleports the player
 * inside its own code (not through a command), so our /home command countdown never sees it; the
 * {@code WaymarkHomeActionsMenuMixin} soft-hook routes that teleport here so it gets the same 3-2-1
 * warmup. Everything is reflection-based and guarded, so nothing here is coupled to Waymark being
 * present or to a specific Waymark version - if anything looks off, we bail and let Waymark run
 * normally.
 */
public final class WaymarkCompat {
	private WaymarkCompat() {
	}

	/**
	 * @param menu the Waymark HomeActionsMenu instance (reflected for its player field)
	 * @param home the Waymark Home record (reflected for its coordinates)
	 * @return true if we've taken over the teleport (the caller should cancel Waymark's), false to
	 *         let Waymark teleport normally
	 */
	public static boolean interceptTeleport(Object menu, Object home) {
		try {
			// Respect the config: if an admin took "home" out of the countdown list, don't interfere.
			if (!ModConfig.get().isWarmupCommand("home") || menu == null || home == null) {
				return false;
			}

			ServerPlayer player = extractPlayer(menu);
			if (player == null) {
				return false;
			}

			String dimension = (String) home.getClass().getMethod("dimension").invoke(home);
			double x = (Double) home.getClass().getMethod("x").invoke(home);
			double y = (Double) home.getClass().getMethod("y").invoke(home);
			double z = (Double) home.getClass().getMethod("z").invoke(home);
			float yaw = (Float) home.getClass().getMethod("yaw").invoke(home);
			float pitch = (Float) home.getClass().getMethod("pitch").invoke(home);

			MinecraftServer server = player.level().getServer();
			if (server == null) {
				return false;
			}

			// Close the menu so the action-bar countdown is actually visible.
			player.closeContainer();

			if (CombatState.get(server).isInCombat(player.getUUID())) {
				TeleportWarmup.actionBar(player,
						Component.literal("You can't teleport home during combat.").withStyle(ChatFormatting.RED));
				return true;
			}

			UUID id = player.getUUID();
			TeleportWarmup.begin(player, null, ModConfig.get().teleportWarmupTicks(),
					() -> completeHomeTeleport(server, id, dimension, x, y, z, yaw, pitch));
			return true;
		} catch (Throwable t) {
			CombatLogCommands.LOGGER.error("combatlogcommands Waymark hook failed, letting the teleport run normally", t);
			return false;
		}
	}

	private static ServerPlayer extractPlayer(Object menu) throws ReflectiveOperationException {
		Field field = menu.getClass().getDeclaredField("player");
		field.setAccessible(true);
		Object value = field.get(menu);
		return value instanceof ServerPlayer serverPlayer ? serverPlayer : null;
	}

	// Replicates what Waymark's own teleport does, run after the countdown completes.
	private static void completeHomeTeleport(MinecraftServer server, UUID id, String dimension,
			double x, double y, double z, float yaw, float pitch) {
		ServerPlayer player = server.getPlayerList().getPlayer(id);
		if (player == null) {
			return;
		}
		ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension));
		ServerLevel level = server.getLevel(levelKey);
		if (level == null) {
			player.sendSystemMessage(Component.literal("That home's dimension no longer exists.").withStyle(ChatFormatting.RED));
			return;
		}
		player.teleportTo(level, x, y, z, Set.of(), yaw, pitch, false);
		TeleportWarmup.playArrivalSound(player);
		TeleportWarmup.actionBar(player, Component.literal("Teleported home!").withStyle(ChatFormatting.GREEN));
	}
}
