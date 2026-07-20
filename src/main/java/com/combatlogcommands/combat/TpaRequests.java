package com.combatlogcommands.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mirror of the teleport-request state that /tpa and /tpahere (from whatever mod provides them)
 * create. We can't see that mod's internals, so we record requests as we watch the commands go
 * through the dispatcher. Needed to know WHO teleports when /tpaccept runs: the requester for /tpa,
 * the accepter for /tpahere.
 */
public class TpaRequests {
	/** Matches the typical teleport-request timeout, after which a recorded request is ignored. */
	private static final long EXPIRY_MILLIS = 60_000L;

	public enum Type {
		TPA, TPAHERE
	}

	public record Request(UUID requesterId, String requesterName, Type type, long createdAt) {
		public boolean expired() {
			return System.currentTimeMillis() - createdAt > EXPIRY_MILLIS;
		}
	}

	// Keyed by the request TARGET (the player who can /tpaccept).
	private static final Map<UUID, List<Request>> byTarget = new ConcurrentHashMap<>();

	private TpaRequests() {
	}

	public static void record(UUID targetId, UUID requesterId, String requesterName, Type type) {
		List<Request> list = byTarget.computeIfAbsent(targetId, key -> new ArrayList<>());
		synchronized (list) {
			list.removeIf(request -> request.requesterId().equals(requesterId) || request.expired());
			list.add(new Request(requesterId, requesterName, type, System.currentTimeMillis()));
		}
	}

	/**
	 * The request a /tpaccept from this target would act on: the named requester's if a name is
	 * given, otherwise the most recent. Null if nothing (unexpired) is recorded.
	 */
	public static Request find(UUID targetId, String requesterNameOrNull) {
		List<Request> list = byTarget.get(targetId);
		if (list == null) {
			return null;
		}
		synchronized (list) {
			Request match = null;
			for (Request request : list) {
				if (request.expired()) {
					continue;
				}
				if (requesterNameOrNull == null || request.requesterName().equalsIgnoreCase(requesterNameOrNull)) {
					match = request;
				}
			}
			return match;
		}
	}

	/** All unexpired requests waiting on this target, oldest first. */
	public static List<Request> pending(UUID targetId) {
		List<Request> list = byTarget.get(targetId);
		if (list == null) {
			return List.of();
		}
		synchronized (list) {
			List<Request> result = new ArrayList<>();
			for (Request request : list) {
				if (!request.expired()) {
					result.add(request);
				}
			}
			return result;
		}
	}

	/** Players who currently have at least one unexpired request waiting on them. */
	public static List<UUID> targetsWithPending() {
		List<UUID> result = new ArrayList<>();
		for (UUID targetId : byTarget.keySet()) {
			if (!pending(targetId).isEmpty()) {
				result.add(targetId);
			}
		}
		return result;
	}

	public static void consume(UUID targetId, Request request) {
		List<Request> list = byTarget.get(targetId);
		if (list != null) {
			synchronized (list) {
				list.remove(request);
			}
		}
	}

	public static void clear(UUID targetId) {
		byTarget.remove(targetId);
	}
}
