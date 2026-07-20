package com.combatlogcommands.command;

import com.combatlogcommands.config.ModConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;

import java.util.function.DoubleConsumer;

/**
 * Admin-only config management, gated on the same gamemaster permission vanilla uses for /gamemode
 * (op level 2+). Every change is saved to the config file immediately.
 *
 * /combatlog show|reload|reset
 * /combatlog set combatduration|backcooldown|fireworkcooldown|fireworkthirdcooldown <seconds>
 * /combatlog blocked add|remove <command>
 * /combatlog targetblocked add|remove <command>
 * /combatlog player <name> combatduration|fireworkcooldown|fireworkthirdcooldown <seconds>
 * /combatlog player <name> clear
 */
public class CombatLogAdminCommand {
	private CombatLogAdminCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("combatlog")
				.requires(Commands.hasPermission(new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER)))
				.then(Commands.literal("show").executes(context -> {
					context.getSource().sendSuccess(() -> Component.literal(ModConfig.get().describe()), false);
					return 1;
				}))
				.then(Commands.literal("reload").executes(context -> {
					ModConfig.reload();
					context.getSource().sendSuccess(() -> Component.literal("CombatLogCommands config reloaded from disk."), true);
					return 1;
				}))
				.then(Commands.literal("reset").executes(context -> {
					ModConfig.reset();
					context.getSource().sendSuccess(() -> Component.literal("CombatLogCommands config reset to default settings."), true);
					return 1;
				}))
				.then(Commands.literal("set")
						.then(globalSetting("combatduration", "combat duration",
								seconds -> ModConfig.get().setCombatDurationSeconds(seconds)))
						.then(globalSetting("backcooldown", "/back cooldown",
								seconds -> ModConfig.get().setBackCooldownSeconds(seconds)))
						.then(globalSetting("fireworkcooldown", "firework cooldown",
								seconds -> ModConfig.get().setFireworkCooldownSeconds(seconds)))
						.then(globalSetting("fireworkthirdcooldown", "every-3rd firework cooldown",
								seconds -> ModConfig.get().setFireworkEveryThirdCooldownSeconds(seconds)))
						.then(globalSetting("warmup", "teleport warmup",
								seconds -> ModConfig.get().setTeleportWarmupSeconds(seconds))))
				.then(listEditor("blocked", ListKind.BLOCKED))
				.then(listEditor("targetblocked", ListKind.TARGET_BLOCKED))
				.then(listEditor("warmupcmd", ListKind.WARMUP))
				.then(Commands.literal("player")
						.then(Commands.argument("name", StringArgumentType.word())
								.then(playerSetting("combatduration", "combat duration",
										(name, seconds) -> ModConfig.get().setPlayerCombatDuration(name, seconds)))
								.then(playerSetting("fireworkcooldown", "firework cooldown",
										(name, seconds) -> ModConfig.get().setPlayerFireworkCooldown(name, seconds)))
								.then(playerSetting("fireworkthirdcooldown", "every-3rd firework cooldown",
										(name, seconds) -> ModConfig.get().setPlayerFireworkEveryThirdCooldown(name, seconds)))
								.then(Commands.literal("clear").executes(context -> {
									String name = StringArgumentType.getString(context, "name");
									if (ModConfig.get().clearPlayerOverride(name)) {
										context.getSource().sendSuccess(
												() -> Component.literal("Cleared all overrides for " + name + "."), true);
										return 1;
									}
									context.getSource().sendFailure(Component.literal(name + " has no overrides."));
									return 0;
								})))));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> globalSetting(
			String literal, String description, DoubleConsumer setter) {
		return Commands.literal(literal)
				.then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0))
						.executes(context -> {
							double seconds = DoubleArgumentType.getDouble(context, "seconds");
							setter.accept(seconds);
							context.getSource().sendSuccess(
									() -> Component.literal("Set " + description + " to " + seconds + "s."), true);
							return 1;
						}));
	}

	private interface PlayerSetting {
		void apply(String playerName, double seconds);
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> playerSetting(
			String literal, String description, PlayerSetting setter) {
		return Commands.literal(literal)
				.then(Commands.argument("seconds", DoubleArgumentType.doubleArg(0))
						.executes(context -> {
							String name = StringArgumentType.getString(context, "name");
							double seconds = DoubleArgumentType.getDouble(context, "seconds");
							setter.apply(name, seconds);
							context.getSource().sendSuccess(
									() -> Component.literal("Set " + description + " for " + name + " to " + seconds + "s."), true);
							return 1;
						}));
	}

	private enum ListKind {
		BLOCKED("combat-blocked commands"),
		TARGET_BLOCKED("target-blocked commands"),
		WARMUP("countdown commands");

		final String label;

		ListKind(String label) {
			this.label = label;
		}

		boolean add(String command) {
			return switch (this) {
				case BLOCKED -> ModConfig.get().addBlockedCommand(command);
				case TARGET_BLOCKED -> ModConfig.get().addTargetBlockedCommand(command);
				case WARMUP -> ModConfig.get().addWarmupCommand(command);
			};
		}

		boolean remove(String command) {
			return switch (this) {
				case BLOCKED -> ModConfig.get().removeBlockedCommand(command);
				case TARGET_BLOCKED -> ModConfig.get().removeTargetBlockedCommand(command);
				case WARMUP -> ModConfig.get().removeWarmupCommand(command);
			};
		}
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> listEditor(
			String literal, ListKind kind) {
		return Commands.literal(literal)
				.then(Commands.literal("add").then(Commands.argument("command", StringArgumentType.word())
						.executes(context -> addToList(context, kind))))
				.then(Commands.literal("remove").then(Commands.argument("command", StringArgumentType.word())
						.executes(context -> removeFromList(context, kind))));
	}

	private static int addToList(CommandContext<CommandSourceStack> context, ListKind kind) {
		String command = StringArgumentType.getString(context, "command");
		if (kind.add(command)) {
			context.getSource().sendSuccess(() -> Component.literal("Added /" + command + " to " + kind.label + "."), true);
			return 1;
		}
		context.getSource().sendFailure(Component.literal("/" + command + " is already in " + kind.label + "."));
		return 0;
	}

	private static int removeFromList(CommandContext<CommandSourceStack> context, ListKind kind) {
		String command = StringArgumentType.getString(context, "command");
		if (kind.remove(command)) {
			context.getSource().sendSuccess(() -> Component.literal("Removed /" + command + " from " + kind.label + "."), true);
			return 1;
		}
		context.getSource().sendFailure(Component.literal("/" + command + " is not in " + kind.label + "."));
		return 0;
	}
}
