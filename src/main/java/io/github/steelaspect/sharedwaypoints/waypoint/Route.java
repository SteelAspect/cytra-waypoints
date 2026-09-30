package io.github.steelaspect.sharedwaypoints.waypoint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A named, ordered list of waypoints to visit one after another. This record is also the JSON shape stored in
 * {@code routes.json}.
 *
 * @param id          stable id; navigation refers to this, so it survives renames
 * @param name        display name, unique among routes (case-insensitive); same rules as waypoint names
 * @param stops       waypoint ids in visiting order (the same waypoint may appear more than once)
 * @param description optional short note (null if none)
 * @param creatorUuid UUID of the player who created it (all zeros for the console / command blocks)
 * @param creatorName name of the creator at the time it was created
 * @param created     when it was created
 */
public record Route(
		UUID id,
		String name,
		List<UUID> stops,
		String description,
		UUID creatorUuid,
		String creatorName,
		Instant created) {

	/** Enough for a long tour; keeps chat output and the network payload bounded. */
	public static final int MAX_STOPS = 64;

	public Route {
		// Hand-edited files may contain nulls; drop them rather than failing to load.
		stops = stops == null ? List.of() : stops.stream().filter(Objects::nonNull).toList();
	}

	public Route withName(String newName) {
		return new Route(id, newName, stops, description, creatorUuid, creatorName, created);
	}

	/** Copy with a new description; {@code null} or blank clears it. */
	public Route withDescription(String newDescription) {
		String cleaned = newDescription == null || newDescription.isBlank() ? null : newDescription.trim();
		return new Route(id, name, stops, cleaned, creatorUuid, creatorName, created);
	}

	public Route withStops(List<UUID> newStops) {
		return new Route(id, name, newStops, description, creatorUuid, creatorName, created);
	}

	/** Copy with a waypoint appended as the last stop. */
	public Route withStopAdded(UUID waypoint) {
		List<UUID> list = new ArrayList<>(stops);
		list.add(waypoint);
		return withStops(list);
	}

	/** Copy without the stop at {@code index} (0-based). */
	public Route withStopRemoved(int index) {
		List<UUID> list = new ArrayList<>(stops);
		list.remove(index);
		return withStops(list);
	}

	/** Copy with the stop at {@code from} moved to position {@code to} (both 0-based). */
	public Route withStopMoved(int from, int to) {
		List<UUID> list = new ArrayList<>(stops);
		list.add(to, list.remove(from));
		return withStops(list);
	}

	/** Copy without every stop at this waypoint (it was deleted). */
	public Route withoutWaypoint(UUID waypoint) {
		return withStops(stops.stream().filter(stop -> !stop.equals(waypoint)).toList());
	}

	public Optional<String> descriptionText() {
		return Optional.ofNullable(description);
	}
}
