package com.combatlogcommands.combat;

import com.combatlogcommands.CombatLogCommands;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks per-player combat tag expiry and who needs to be slain for logging out mid-combat.
 * Persisted so a combat-logger is still punished even after a server restart.
 */
public class CombatState extends SavedData {
	public static final Codec<CombatState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).fieldOf("combat_ends_at").forGetter(s -> s.combatEndsAt),
			UUIDUtil.CODEC.listOf().fieldOf("pending_logout_kill").forGetter(s -> new ArrayList<>(s.pendingLogoutKill))
	).apply(instance, CombatState::new));

	public static final SavedDataType<CombatState> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(CombatLogCommands.MOD_ID, "combat_data"), CombatState::new, CODEC, null);

	private final Map<UUID, Long> combatEndsAt;
	private final Set<UUID> pendingLogoutKill;

	public CombatState() {
		this(new HashMap<>(), new ArrayList<>());
	}

	private CombatState(Map<UUID, Long> combatEndsAt, List<UUID> pendingLogoutKill) {
		this.combatEndsAt = combatEndsAt;
		this.pendingLogoutKill = new HashSet<>(pendingLogoutKill);
	}

	public static CombatState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean isInCombat(UUID id) {
		Long endsAt = combatEndsAt.get(id);
		return endsAt != null && endsAt > System.currentTimeMillis();
	}

	public long remainingMillis(UUID id) {
		Long endsAt = combatEndsAt.get(id);
		if (endsAt == null) {
			return 0;
		}
		return Math.max(0, endsAt - System.currentTimeMillis());
	}

	public void tag(UUID id, long durationMillis) {
		combatEndsAt.put(id, System.currentTimeMillis() + durationMillis);
		setDirty();
	}

	public void clear(UUID id) {
		if (combatEndsAt.remove(id) != null) {
			setDirty();
		}
	}

	public void markPendingLogoutKill(UUID id) {
		if (pendingLogoutKill.add(id)) {
			setDirty();
		}
	}

	public boolean consumePendingLogoutKill(UUID id) {
		boolean was = pendingLogoutKill.remove(id);
		if (was) {
			setDirty();
		}
		return was;
	}

	public Set<UUID> combatants() {
		return combatEndsAt.keySet();
	}
}
