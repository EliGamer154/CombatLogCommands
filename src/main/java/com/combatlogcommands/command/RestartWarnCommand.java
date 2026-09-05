package com.combatlogcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.Permissions;

/**
 * Op-only /restartwarn - broadcasts a "server restarting" heads-up to everyone, as both a chat line
 * and an on-screen title.
 */
public class RestartWarnCommand {
	private RestartWarnCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("restartwarn")
				.requires(Commands.hasPermission(new PermissionCheck.Require(Permissions.COMMANDS_GAMEMASTER)))
				.executes(context -> {
					Component title = Component.literal("SERVER RESTARTING...").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
					Component subtitle = Component.literal("Back online in 30 seconds or less").withStyle(ChatFormatting.YELLOW);
					Component chat = Component.literal(
							"SERVER RESTARTING... BACK ONLINE IN 30 SECONDS OR LESS. IF IT TAKES MORE THAN 30 SECONDS THE ADMINS WILL FIX THE ISSUE.")
							.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

					for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
						player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
						player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
						player.connection.send(new ClientboundSetTitleTextPacket(title));
						player.sendSystemMessage(chat);
					}
					context.getSource().sendSuccess(() -> Component.literal("Sent the restart warning to everyone."), true);
					return 1;
				}));
	}
}
