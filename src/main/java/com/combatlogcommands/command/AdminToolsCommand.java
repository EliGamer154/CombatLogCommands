package com.combatlogcommands.command;

import com.combatlogcommands.admin.AdminToolsState;
import com.combatlogcommands.admin.AdminToolsHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * Op-only /admintools: a clickable in-chat menu of legitimate admin conveniences (fly, god mode,
 * speed, night vision, no fall, heal, feed, repair, spectator, and freeze-a-player for holding a
 * rule-breaker still while you deal with them). Gated on the gamemaster permission (op).
 */
public class AdminToolsCommand {
	private AdminToolsCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("admintools")
				.requires(Commands.hasPermission(new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER)))
				.executes(context -> showMenu(context.getSource()))
				.then(Commands.literal("fly").executes(context -> toggle(context.getSource(), AdminToolsState.Toggle.FLY)))
				.then(Commands.literal("god").executes(context -> toggle(context.getSource(), AdminToolsState.Toggle.GOD)))
				.then(Commands.literal("speed").executes(context -> toggle(context.getSource(), AdminToolsState.Toggle.SPEED)))
				.then(Commands.literal("nightvision").executes(context -> toggle(context.getSource(), AdminToolsState.Toggle.NIGHT_VISION)))
				.then(Commands.literal("nofall").executes(context -> toggle(context.getSource(), AdminToolsState.Toggle.NO_FALL)))
				.then(Commands.literal("heal").executes(context -> heal(context.getSource())))
				.then(Commands.literal("feed").executes(context -> feed(context.getSource())))
				.then(Commands.literal("repair").executes(context -> repair(context.getSource())))
				.then(Commands.literal("spectate").executes(context -> spectate(context.getSource())))
				.then(Commands.literal("freeze")
						.then(Commands.argument("player", StringArgumentType.word())
								.suggests((context, builder) -> SharedSuggestionProvider.suggest(context.getSource().getOnlinePlayerNames(), builder))
								.executes(context -> freeze(context.getSource(), StringArgumentType.getString(context, "player"))))));
	}

	private static ServerPlayer requirePlayer(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("Only players can use /admintools."));
		}
		return player;
	}

	private static int toggle(CommandSourceStack source, AdminToolsState.Toggle type) {
		ServerPlayer player = requirePlayer(source);
		if (player == null) {
			return 0;
		}
		boolean on = AdminToolsState.toggle(type, player.getUUID());
		switch (type) {
			case FLY -> AdminToolsHandler.applyFly(player, on);
			case SPEED -> AdminToolsHandler.reconcileSpeed(player, on);
			case NIGHT_VISION -> AdminToolsHandler.applyNightVision(player, on);
			default -> {
			}
		}
		showMenu(source);
		return 1;
	}

	private static int heal(CommandSourceStack source) {
		ServerPlayer player = requirePlayer(source);
		if (player == null) {
			return 0;
		}
		player.setHealth(player.getMaxHealth());
		source.sendSystemMessage(Component.literal("Healed.").withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int feed(CommandSourceStack source) {
		ServerPlayer player = requirePlayer(source);
		if (player == null) {
			return 0;
		}
		player.getFoodData().setFoodLevel(20);
		player.getFoodData().setSaturation(20.0f);
		source.sendSystemMessage(Component.literal("Fed.").withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int repair(CommandSourceStack source) {
		ServerPlayer player = requirePlayer(source);
		if (player == null) {
			return 0;
		}
		ItemStack held = player.getMainHandItem();
		if (held.isEmpty() || !held.isDamageableItem()) {
			source.sendFailure(Component.literal("Hold a damageable item to repair."));
			return 0;
		}
		held.setDamageValue(0);
		source.sendSystemMessage(Component.literal("Repaired the held item.").withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int spectate(CommandSourceStack source) {
		ServerPlayer player = requirePlayer(source);
		if (player == null) {
			return 0;
		}
		if (player.gameMode() == GameType.SPECTATOR) {
			player.setGameMode(AdminToolsState.takeRememberedGameMode(player.getUUID()));
			source.sendSystemMessage(Component.literal("Spectator mode off.").withStyle(ChatFormatting.YELLOW));
		} else {
			AdminToolsState.rememberGameMode(player.getUUID(), player.gameMode());
			player.setGameMode(GameType.SPECTATOR);
			source.sendSystemMessage(Component.literal("Spectator mode on - fly through walls, invisible to others.").withStyle(ChatFormatting.GREEN));
		}
		return 1;
	}

	private static int freeze(CommandSourceStack source, String targetName) {
		ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(targetName);
		if (target == null) {
			source.sendFailure(Component.literal("Player not found: " + targetName).withStyle(ChatFormatting.RED));
			return 0;
		}
		if (AdminToolsState.isFrozen(target.getUUID())) {
			AdminToolsState.unfreeze(target.getUUID());
			source.sendSuccess(() -> Component.literal("Unfroze " + target.getScoreboardName() + "."), true);
			target.sendSystemMessage(Component.literal("You have been unfrozen.").withStyle(ChatFormatting.GREEN));
		} else {
			AdminToolsState.setFrozen(target.getUUID(), new AdminToolsState.FreezeAnchor(
					target.level().dimension(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot()));
			source.sendSuccess(() -> Component.literal("Froze " + target.getScoreboardName() + "."), true);
			target.sendSystemMessage(Component.literal("You have been frozen by an admin.").withStyle(ChatFormatting.RED));
		}
		return 1;
	}

	private static int showMenu(CommandSourceStack source) {
		ServerPlayer player = requirePlayer(source);
		if (player == null) {
			return 0;
		}
		source.sendSystemMessage(Component.literal("=== Admin Tools ===").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		source.sendSystemMessage(Component.empty()
				.append(toggleButton(player, "Fly", AdminToolsState.Toggle.FLY, "fly"))
				.append(Component.literal("   "))
				.append(toggleButton(player, "God", AdminToolsState.Toggle.GOD, "god"))
				.append(Component.literal("   "))
				.append(toggleButton(player, "Speed", AdminToolsState.Toggle.SPEED, "speed")));
		source.sendSystemMessage(Component.empty()
				.append(toggleButton(player, "Night Vision", AdminToolsState.Toggle.NIGHT_VISION, "nightvision"))
				.append(Component.literal("   "))
				.append(toggleButton(player, "No Fall", AdminToolsState.Toggle.NO_FALL, "nofall")));
		source.sendSystemMessage(Component.literal("Actions: ").withStyle(ChatFormatting.GRAY)
				.append(actionButton("[Heal]", "heal", ChatFormatting.GREEN)).append(Component.literal(" "))
				.append(actionButton("[Feed]", "feed", ChatFormatting.GREEN)).append(Component.literal(" "))
				.append(actionButton("[Repair]", "repair", ChatFormatting.GREEN)).append(Component.literal(" "))
				.append(actionButton("[Spectate]", "spectate", ChatFormatting.AQUA)));
		source.sendSystemMessage(Component.literal("Freeze a player: ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal("[Freeze...]").withStyle(style -> style
						.withColor(ChatFormatting.AQUA)
						.withClickEvent(new ClickEvent.SuggestCommand("/admintools freeze ")))));
		return 1;
	}

	private static MutableComponent toggleButton(ServerPlayer player, String label, AdminToolsState.Toggle type, String sub) {
		boolean on = AdminToolsState.isOn(type, player.getUUID());
		MutableComponent state = Component.literal(on ? "[ON]" : "[OFF]")
				.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED)
				.withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/admintools " + sub)));
		return Component.literal(label + ": ").withStyle(ChatFormatting.GRAY).append(state);
	}

	private static MutableComponent actionButton(String text, String sub, ChatFormatting color) {
		return Component.literal(text).withStyle(color)
				.withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/admintools " + sub)));
	}
}
