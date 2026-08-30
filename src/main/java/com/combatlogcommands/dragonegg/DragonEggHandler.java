package com.combatlogcommands.dragonegg;

import com.combatlogcommands.CombatLogCommands;
import com.combatlogcommands.gamerule.ModGameRules;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;

/**
 * While the dragoneggpowers gamerule is on, any player holding a dragon egg anywhere in their
 * inventory gets +5 hearts (a MAX_HEALTH attribute modifier of +10) and Strength II. Reconciled a
 * few times a second: the health modifier is added/removed as the egg comes and goes, and the
 * strength effect is topped up while the egg is held and simply fades once it's gone.
 */
public final class DragonEggHandler {
	private static final Identifier HEALTH_MODIFIER_ID =
			Identifier.fromNamespaceAndPath(CombatLogCommands.MOD_ID, "dragon_egg_health");
	private static final double EXTRA_HEALTH = 10.0; // 5 hearts
	private static final int STRENGTH_AMPLIFIER = 1; // Strength II
	private static final int STRENGTH_DURATION_TICKS = 60;
	private static final int CHECK_INTERVAL_TICKS = 20;

	private DragonEggHandler() {
	}

	public static void onServerTick(MinecraftServer server) {
		try {
			if (server.getTickCount() % CHECK_INTERVAL_TICKS != 0) {
				return;
			}
			boolean ruleOn = ModGameRules.isDragonEggPowers();
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				boolean hasEgg = ruleOn && player.getInventory().contains(stack -> stack.getItem() == Items.DRAGON_EGG);
				reconcileHealth(player, hasEgg);
				if (hasEgg) {
					player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, STRENGTH_DURATION_TICKS, STRENGTH_AMPLIFIER, false, false));
				}
			}
		} catch (Throwable t) {
			CombatLogCommands.LOGGER.error("combatlogcommands dragon egg tick threw", t);
		}
	}

	private static void reconcileHealth(ServerPlayer player, boolean shouldHave) {
		AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null) {
			return;
		}
		boolean has = maxHealth.hasModifier(HEALTH_MODIFIER_ID);
		if (shouldHave && !has) {
			maxHealth.addTransientModifier(new AttributeModifier(HEALTH_MODIFIER_ID, EXTRA_HEALTH, AttributeModifier.Operation.ADD_VALUE));
		} else if (!shouldHave && has) {
			maxHealth.removeModifier(HEALTH_MODIFIER_ID);
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
		}
	}
}
