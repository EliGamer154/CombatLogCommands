package com.combatlogcommands;

import com.combatlogcommands.combat.CombatHandler;
import com.combatlogcommands.command.CombatLogAdminCommand;
import com.combatlogcommands.config.ModConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.ItemEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CombatLogCommands implements ModInitializer {
	public static final String MOD_ID = "combatlogcommands";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModConfig.get();

		ServerLivingEntityEvents.AFTER_DAMAGE.register(CombatHandler::onDamage);
		ServerPlayerEvents.LEAVE.register(CombatHandler::onLeave);
		ServerTickEvents.END_SERVER_TICK.register(CombatHandler::onServerTick);
		ItemEvents.USE.register(CombatHandler::onUseItem);
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> CombatLogAdminCommand.register(dispatcher));

		LOGGER.info("CombatLogCommands initialized");
	}
}
