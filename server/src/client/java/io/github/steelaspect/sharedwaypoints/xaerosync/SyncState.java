package io.github.steelaspect.sharedwaypoints.xaerosync;

import io.github.steelaspect.sharedwaypoints.protocol.SyncedWaypoint;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the server has told this client, and which dimensions' "Shared" sets still have to be rewritten. Plain logic
 * (no Minecraft or Xaero), so it can be unit tested. Only used on the client thread.
 */
final class SyncState {
	/** Order inside a "Shared" set: by name, like Xaero's own lists. */
	static final Comparator<SyncedWaypoint> ORDER =
			Comparator.comparing(SyncedWaypoint::name, String.CASE_INSENSITIVE_ORDER).thenComparing(SyncedWaypoint::id);

	private final Map<UUID, SyncedWaypoint> waypoints = new LinkedHashMap<>();
	private final Set<String> dirty = new HashSet<>();
	/** True once a full list arrived since joining; before that, nothing is written. */
	private boolean synced;

	/**
	 * Replaces everything with the server's full list. Every dimension that had or has shared waypoints, plus
	 * {@code otherDimensions} (all dimensions of the server), is marked for rewriting, so "Shared" waypoints that no
	 * longer exist are removed on join (reconcile).
	 */
	void acceptFull(List<SyncedWaypoint> list, Set<String> otherDimensions) {
		waypoints.values().forEach(waypoint -> dirty.add(waypoint.dimension()));
		waypoints.clear();
		list.forEach(waypoint -> waypoints.put(waypoint.id(), waypoint));
		list.forEach(waypoint -> dirty.add(waypoint.dimension()));
		dirty.addAll(otherDimensions);
		synced = true;
	}

	/** A waypoint was added or edited; a move to another dimension rewrites both. */
	void upsert(SyncedWaypoint waypoint) {
		SyncedWaypoint previous = waypoints.put(waypoint.id(), waypoint);
		if (previous != null) {
			dirty.add(previous.dimension());
		}
		dirty.add(waypoint.dimension());
	}

	void delete(UUID id) {
		SyncedWaypoint removed = waypoints.remove(id);
		if (removed != null) {
			dirty.add(removed.dimension());
		}
	}

	/** Left the server: forget everything (nothing is written until the next full list). */
	void clear() {
		waypoints.clear();
		dirty.clear();
		synced = false;
	}

	boolean hasWork() {
		return synced && !dirty.isEmpty();
	}

	/** The dimensions to rewrite; the caller calls {@link #done(String)} for each one it managed to write. */
	Set<String> dirtyDimensions() {
		return Set.copyOf(dirty);
	}

	void done(String dimension) {
		dirty.remove(dimension);
	}

	/** The shared waypoints in one dimension, in set order. */
	List<SyncedWaypoint> inDimension(String dimension) {
		return waypoints.values().stream().filter(waypoint -> waypoint.dimension().equals(dimension)).sorted(ORDER).toList();
	}

	int size() {
		return waypoints.size();
	}
}
