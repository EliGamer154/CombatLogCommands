package com.combatlogcommands;

import com.combatlogcommands.combat.CombatHandler;
import com.combatlogcommands.config.ModConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CombatLogCommands implements ModInitializer {
	public static final String MOD_ID = "combatlogcommands";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final long COMBAT_DURATION_MILLIS = 15_000L;

	@Override
	public void onInitialize() {
		ModConfig.get();

		ServerLivingEntityEvents.AFTER_DAMAGE.register(CombatHandler::onDamage);
		ServerPlayerEvents.LEAVE.register(CombatHandler::onLeave);
		ServerPlayerEvents.JOIN.register(CombatHandler::onJoin);
		ServerTickEvents.END_SERVER_TICK.register(CombatHandler::onServerTick);

		LOGGER.info("CombatLogCommands initialized");
	}
}
