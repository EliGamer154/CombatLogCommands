package com.combatlogcommands.command;

import com.combatlogcommands.combat.TpaAuto;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /tpauto           - toggle auto-accepting ALL incoming teleport requests for yourself.
 * /tpauto &lt;player&gt;  - toggle auto-accepting requests from just that one player.
 *
 * When auto-accept applies to an incoming /tpa //tpahere, it's accepted without the target having to
 * open the /tpaccept menu (the teleport still runs the normal 3-2-1 countdown).
 */
public class TpaAutoCommand {
	private TpaAutoCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("tpauto")
				.executes(context -> toggleGlobal(context.getSource()))
				.then(Commands.argument("player", StringArgumentType.word())
						.suggests((context, builder) -> SharedSuggestionProvider.suggest(context.getSource().getOnlinePlayerNames(), builder))
						.executes(context -> togglePlayer(context.getSource(), StringArgumentType.getString(context, "player")))));
	}

	private static int toggleGlobal(CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("Only players can use /tpauto."));
			return 0;
		}
		boolean on = TpaAuto.toggleGlobal(player.getUUID());
		source.sendSuccess(() -> Component.literal("Auto-accept teleport requests: " + (on ? "ON" : "OFF"))
				.withStyle(on ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
		return 1;
	}

	private static int togglePlayer(CommandSourceStack source, String targetName) {
		ServerPlayer player = source.getPlayer();
		if (player == null) {
			source.sendFailure(Component.literal("Only players can use /tpauto."));
			return 0;
		}
		ServerPlayer other = source.getServer().getPlayerList().getPlayerByName(targetName);
		if (other == null) {
			source.sendFailure(Component.literal("Player not found (must be online): " + targetName).withStyle(ChatFormatting.RED));
			return 0;
		}
		if (other == player) {
			source.sendFailure(Component.literal("You can't set auto-accept for yourself.").withStyle(ChatFormatting.RED));
			return 0;
		}
		boolean on = TpaAuto.togglePlayer(player.getUUID(), other.getUUID());
		source.sendSuccess(() -> Component.literal("Auto-accept teleport requests from " + other.getScoreboardName() + ": "
				+ (on ? "ON" : "OFF")).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
		return 1;
	}
}
