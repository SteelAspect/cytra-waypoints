package io.github.steelaspect.sharedwaypoints.waypoint;

import io.github.steelaspect.sharedwaypoints.SharedWaypoints;
import io.github.steelaspect.sharedwaypoints.util.Gsons;
import io.github.steelaspect.sharedwaypoints.util.JsonFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory list of routes backed by {@code config/sharedwaypoints/routes.json}, saved after every change.
 * Like {@link WaypointStore}, all access happens on the server thread.
 */
public final class RouteStore {
	private static final int FORMAT_VERSION = 1;

	private final Path file;
	/** Keyed by lower-case name so lookups and uniqueness are case-insensitive. */
	private final Map<String, Route> byName = new HashMap<>();
	private final Map<UUID, Route> byId = new HashMap<>();
	private final List<Runnable> changeListeners = new ArrayList<>();
	private boolean saveFailed;

	public RouteStore(Path file) {
		this.file = file;
	}

	/** Called after every add, remove, edit and load. */
	public void onChanged(Runnable listener) {
		changeListeners.add(listener);
	}

	// ---------------------------------------------------------------- queries

	public Optional<Route> get(String name) {
		return Optional.ofNullable(byName.get(key(name)));
	}

	public Optional<Route> get(UUID id) {
		return Optional.ofNullable(byId.get(id));
	}

	public boolean contains(String name) {
		return byName.containsKey(key(name));
	}

	/** All routes, sorted by name. */
	public List<Route> all() {
		return byName.values().stream().sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name())).toList();
	}

	public int size() {
		return byName.size();
	}

	public boolean lastSaveFailed() {
		return saveFailed;
	}

	// -------------------------------------------------------------- mutations

	/** Adds a route and saves. Returns false (and changes nothing) if the name is already taken. */
	public boolean add(Route route) {
		if (contains(route.name())) {
			return false;
		}
		put(route);
		save();
		changed();
		return true;
	}

	/** Removes a route by id and saves. Returns the removed route, if there was one. */
	public Optional<Route> remove(UUID id) {
		Route removed = byId.remove(id);
		if (removed != null) {
			byName.remove(key(removed.name()));
			save();
			changed();
		}
		return Optional.ofNullable(removed);
	}

	/** Replaces a stored route (same id) with an edited copy and saves. The caller checks a new name is free. */
	public Route update(Route updated) {
		Route previous = byId.get(updated.id());
		if (previous != null) {
			byName.remove(key(previous.name()));
		}
		put(updated);
		save();
		changed();
		return updated;
	}

	/** Drops a deleted waypoint from every route that stops there. */
	public void forgetWaypoint(UUID waypoint) {
		List<Route> affected = byId.values().stream().filter(route -> route.stops().contains(waypoint)).toList();
		if (affected.isEmpty()) {
			return;
		}
		affected.forEach(route -> put(route.withoutWaypoint(waypoint)));
		save();
		changed();
	}

	// ------------------------------------------------------------ persistence

	/** Replaces the in-memory list with the contents of the JSON file (if it exists). */
	public void load() {
		byName.clear();
		byId.clear();
		saveFailed = false;
		JsonFiles.read(file, RouteFile.class, Gsons.GSON).ifPresent(data -> {
			for (Route raw : data.routes == null ? List.<Route>of() : data.routes) {
				Route route = sanitize(raw);
				if (route == null) {
					SharedWaypoints.LOGGER.warn("Skipping invalid route entry in {}: {}", file, raw);
				} else if (contains(route.name()) || byId.containsKey(route.id())) {
					SharedWaypoints.LOGGER.warn("Skipping duplicate route in {}: {}", file, route.name());
				} else {
					put(route);
				}
			}
		});
		changed();
	}

	/** Writes the current list to disk. Failures are logged and remembered in {@link #lastSaveFailed()}. */
	public void save() {
		RouteFile data = new RouteFile();
		data.routes = new ArrayList<>(all());
		try {
			JsonFiles.writeAtomically(file, data, Gsons.GSON);
			saveFailed = false;
		} catch (IOException e) {
			saveFailed = true;
			SharedWaypoints.LOGGER.error("Could not save routes to {}", file, e);
		}
	}

	// ---------------------------------------------------------------- helpers

	private void changed() {
		changeListeners.forEach(Runnable::run);
	}

	private void put(Route route) {
		byName.put(key(route.name()), route);
		byId.put(route.id(), route);
	}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	/** Fills in defaults for a hand-edited entry; returns null if it can't be used at all. */
	private static Route sanitize(Route raw) {
		if (raw == null || raw.name() == null) {
			return null;
		}
		String name = raw.name().trim();
		if (Waypoint.validateName(name).isPresent()) {
			return null;
		}
		List<UUID> stops = raw.stops().stream().limit(Route.MAX_STOPS).toList();
		return new Route(
				raw.id() != null ? raw.id() : UUID.randomUUID(),
				name,
				stops,
				raw.description() == null || raw.description().isBlank() ? null : raw.description().trim(),
				raw.creatorUuid() != null ? raw.creatorUuid() : Waypoint.SERVER_UUID,
				raw.creatorName() != null ? raw.creatorName() : "Unknown",
				raw.created() != null ? raw.created() : Instant.EPOCH);
	}

	/** Root object of the JSON file. */
	private static final class RouteFile {
		int version = FORMAT_VERSION;
		List<Route> routes = new ArrayList<>();
	}
}
