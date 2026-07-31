package com.combatlogcommands.gamerule;

import com.combatlogcommands.CombatLogCommands;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

/**
 * Custom boolean gamerules to fully disable specific teleport commands, independent of combat.
 * All default to false (command enabled). When a rule is true, that command is refused for everyone.
 *
 * Registered under this mod's namespace, so in game they are:
 *   /gamerule combatlogcommands:disable_back true
 *   /gamerule combatlogcommands:disable_rtp true
 *   /gamerule combatlogcommands:disable_home true
 */
public final class ModGameRules {
	public static GameRule<Boolean> disableBack;
	public static GameRule<Boolean> disableRtp;
	public static GameRule<Boolean> disableHome;

	private ModGameRules() {
	}

	public static void register() {
		disableBack = boolRule("disable_back");
		disableRtp = boolRule("disable_rtp");
		disableHome = boolRule("disable_home");
	}

	private static GameRule<Boolean> boolRule(String path) {
		return GameRuleBuilder.forBoolean(false)
				.category(GameRuleCategory.PLAYER)
				.buildAndRegister(Identifier.fromNamespaceAndPath(CombatLogCommands.MOD_ID, path));
	}

	/** True if the given command label ("back"/"rtp"/"home") is disabled by its gamerule. */
	public static boolean isCommandDisabled(MinecraftServer server, String label) {
		GameRule<Boolean> rule = switch (label.toLowerCase()) {
			case "back" -> disableBack;
			case "rtp" -> disableRtp;
			case "home" -> disableHome;
			default -> null;
		};
		return rule != null && Boolean.TRUE.equals(server.getGameRules().get(rule));
	}

	public static boolean isHomeDisabled(MinecraftServer server) {
		return disableHome != null && Boolean.TRUE.equals(server.getGameRules().get(disableHome));
	}
}
