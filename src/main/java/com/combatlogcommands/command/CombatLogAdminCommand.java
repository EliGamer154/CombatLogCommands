package com.combatlogcommands.command;

import com.combatlogcommands.config.ModConfig;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;

/**
 * Admin-only config management: /combatlog reload re-reads config/combatlogcommands.json after
 * hand-edits, /combatlog reset overwrites it with default settings. Gated on the same gamemaster
 * permission vanilla uses for /gamemode etc. (op level 2+).
 */
public class CombatLogAdminCommand {
	private CombatLogAdminCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("combatlog")
				.requires(Commands.hasPermission(new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER)))
				.then(Commands.literal("reload").executes(context -> {
					ModConfig.reload();
					context.getSource().sendSuccess(() -> Component.literal("CombatLogCommands config reloaded from disk."), true);
					return 1;
				}))
				.then(Commands.literal("reset").executes(context -> {
					ModConfig.reset();
					context.getSource().sendSuccess(() -> Component.literal("CombatLogCommands config reset to default settings."), true);
					return 1;
				})));
	}
}
