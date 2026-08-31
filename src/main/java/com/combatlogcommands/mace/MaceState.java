package com.combatlogcommands.mace;

import com.combatlogcommands.CombatLogCommands;
import com.combatlogcommands.gamerule.ModGameRules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Persistent flag: whether a mace has already been crafted on this world. Used by the onemace
 * gamerule to allow exactly one mace ever - once one is crafted, further mace crafting is blocked
 * (while the rule stays on). Saved so a restart can't reset the count.
 */
public class MaceState extends SavedData {
	public static final Codec<MaceState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("crafted", false).forGetter(state -> state.crafted)
	).apply(instance, MaceState::new));

	public static final SavedDataType<MaceState> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(CombatLogCommands.MOD_ID, "one_mace"), MaceState::new, CODEC, null);

	private boolean crafted;

	public MaceState() {
	}

	private MaceState(boolean crafted) {
		this.crafted = crafted;
	}

	public static MaceState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean isCrafted() {
		return crafted;
	}

	public void markCrafted() {
		if (!crafted) {
			crafted = true;
			setDirty();
		}
	}

	/** True when the onemace rule is on and a mace has already been crafted - so crafting another is blocked. */
	public static boolean isBlocked(MinecraftServer server) {
		return ModGameRules.isOneMace(server) && get(server).isCrafted();
	}
}
