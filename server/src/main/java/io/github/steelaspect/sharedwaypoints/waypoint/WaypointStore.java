package io.github.steelaspect.sharedwaypoints.waypoint;

import io.github.steelaspect.sharedwaypoints.SharedWaypoints;
import io.github.steelaspect.sharedwaypoints.util.Gsons;
import io.github.steelaspect.sharedwaypoints.util.JsonFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * In-memory list of waypoints backed by {@code config/sharedwaypoints/waypoints.json}.
 *
 * <p>The file is read when the server starts and rewritten after every change (atomically, see
 * {@link JsonFiles}). All access happens on the server thread (commands, ticks and lifecycle events), so no
 * locking is needed.
 */
public final class WaypointStore {
	/** 2 added {@code id} and {@code description}; version 1 files are upgraded on load. */
	private static final int FORMAT_VERSION = 2;

	/** Listing order: by category (config order, unknown ids last), then by name. */
	public static final Comparator<Waypoint> DISPLAY_ORDER = Comparator
			.comparingInt((Waypoint waypoint) -> waypoint.category().order())
			.thenComparing(waypoint -> waypoint.category().id())
			.thenComparing(Waypoint::name, String.CASE_INSENSITIVE_ORDER);

	private final Path file;
	private final Supplier<CategoryRegistry> categories;
	/** Keyed by lower-case name so lookups and uniqueness are case-insensitive. */
	private final Map<String, Waypoint> byName = new HashMap<>();
	private final Map<UUID, Waypoint> byId = new HashMap<>();
	private final List<Consumer<Waypoint>> removalListeners = new ArrayList<>();
	private final List<Runnable> changeListeners = new ArrayList<>();
	/** True when the last save failed, so commands can warn that changes are only in memory. */
	private boolean saveFailed;

	/**
	 * @param file       the JSON file
	 * @param categories the current categories; stored category ids are resolved against it on load
	 */
	public WaypointStore(Path file, Supplier<CategoryRegistry> categories) {
		this.file = file;
		this.categories = categories;
	}

	/** Called with every waypoint that gets removed (favourites and navigation clean up through this). */
	public void onRemoved(Consumer<Waypoint> listener) {
		removalListeners.add(listener);
	}

	/** Called after every add, remove, edit and load (web-map markers refresh through this). */
	public void onChanged(Runnable listener) {
		changeListeners.add(listener);
	}

	// ---------------------------------------------------------------- queries

	public Optional<Waypoint> get(String name) {
		return Optional.ofNullable(byName.get(key(name)));
	}

	public Optional<Waypoint> get(UUID id) {
		return Optional.ofNullable(byId.get(id));
	}

	public boolean contains(String name) {
		return byName.containsKey(key(name));
	}

	/** All waypoints, sorted by category and then name. */
	public List<Waypoint> all() {
		return byName.values().stream().sorted(DISPLAY_ORDER).toList();
	}

	public List<Waypoint> inCategory(Category category) {
		return byName.values().stream()
				.filter(waypoint -> waypoint.category().id().equals(category.id()))
				.sorted(DISPLAY_ORDER)
				.toList();
	}

	/** Case-insensitive match on name, description or creator. */
	public List<Waypoint> search(String query) {
		String needle = query.trim().toLowerCase(Locale.ROOT);
		return byName.values().stream()
				.filter(waypoint -> contains(waypoint.name(), needle)
						|| contains(waypoint.description(), needle)
						|| contains(waypoint.creatorName(), needle))
				.sorted(DISPLAY_ORDER)
				.toList();
	}

	/** Number of waypoints per category id: every configured category (possibly 0), plus any unknown ids. */
	public Map<String, Integer> countsByCategory() {
		Map<String, Integer> counts = new LinkedHashMap<>();
		categories.get().ids().forEach(id -> counts.put(id, 0));
		for (Waypoint waypoint : byName.values()) {
			counts.merge(waypoint.category().id(), 1, Integer::sum);
		}
		return counts;
	}

	public int size() {
		return byName.size();
	}

	public boolean lastSaveFailed() {
		return saveFailed;
	}

	// -------------------------------------------------------------- mutations

	/** Adds a waypoint and saves. Returns false (and changes nothing) if the name is already taken. */
	public boolean add(Waypoint waypoint) {
		if (contains(waypoint.name())) {
			return false;
		}
		put(waypoint);
		save();
		changed();
		return true;
	}

	/** Removes a waypoint by name and saves. Returns the removed waypoint, if there was one. */
	public Optional<Waypoint> remove(String name) {
		Waypoint removed = byName.remove(key(name));
		if (removed != null) {
			byId.remove(removed.id());
			save();
			removalListeners.forEach(listener -> listener.accept(removed));
			changed();
		}
		return Optional.ofNullable(removed);
	}

	/**
	 * Replaces a stored waypoint (same id) with an edited copy and saves. The caller must have checked that a
	 * changed name is valid and free (a case-only change of the same waypoint is allowed).
	 */
	public Waypoint update(Waypoint updated) {
		Waypoint previous = byId.get(updated.id());
		if (previous != null) {
			byName.remove(key(previous.name()));
		}
		put(updated);
		save();
		changed();
		return updated;
	}

	// ------------------------------------------------------------ persistence

	/** Replaces the in-memory list with the contents of the JSON file (if it exists). */
	public void load() {
		byName.clear();
		byId.clear();
		saveFailed = false;

		Optional<StoreFile> data = JsonFiles.read(file, StoreFile.class, Gsons.withCategories(categories.get()));
		if (data.isEmpty()) {
			SharedWaypoints.LOGGER.info("No readable waypoint file at {}; starting with an empty list", file);
			changed();
			return;
		}
		boolean upgraded = data.get().version < FORMAT_VERSION;
		for (Waypoint raw : data.get().waypoints == null ? List.<Waypoint>of() : data.get().waypoints) {
			Waypoint waypoint = sanitize(raw);
			if (waypoint == null) {
				SharedWaypoints.LOGGER.warn("Skipping invalid waypoint entry in {}: {}", file, raw);
			} else if (contains(waypoint.name()) || byId.containsKey(waypoint.id())) {
				SharedWaypoints.LOGGER.warn("Skipping duplicate waypoint in {}: {}", file, waypoint.name());
			} else {
				upgraded |= raw.id() == null;
				put(waypoint);
			}
		}
		if (upgraded) {
			// Write the generated ids back so they stay stable across restarts.
			save();
		}
		SharedWaypoints.LOGGER.info("Loaded {} shared waypoint(s) from {}", byName.size(), file);
		changed();
	}

	/** Writes the current list to disk. Failures are logged and remembered in {@link #lastSaveFailed()}. */
	public void save() {
		StoreFile data = new StoreFile();
		data.waypoints = new ArrayList<>(all());
		try {
			JsonFiles.writeAtomically(file, data, Gsons.withCategories(categories.get()));
			saveFailed = false;
		} catch (IOException e) {
			saveFailed = true;
			SharedWaypoints.LOGGER.error("Could not save waypoints to {}", file, e);
		}
	}

	// ---------------------------------------------------------------- helpers

	private void changed() {
		changeListeners.forEach(Runnable::run);
	}

	private void put(Waypoint waypoint) {
		byName.put(key(waypoint.name()), waypoint);
		byId.put(waypoint.id(), waypoint);
	}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	private static boolean contains(String haystack, String lowerCaseNeedle) {
		return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(lowerCaseNeedle);
	}

	/** Fills in defaults for a hand-edited or older entry; returns null if it can't be used at all. */
	private Waypoint sanitize(Waypoint raw) {
		if (raw == null || raw.name() == null || raw.dimension() == null) {
			return null;
		}
		String name = raw.name().trim();
		if (Waypoint.validateName(name).isPresent()) {
			return null;
		}
		return new Waypoint(
				raw.id() != null ? raw.id() : UUID.randomUUID(),
				name,
				raw.category() != null ? raw.category() : categories.get().resolve(null),
				raw.x(),
				raw.y(),
				raw.z(),
				raw.dimension(),
				raw.description() == null || raw.description().isBlank() ? null : raw.description().trim(),
				raw.creatorUuid() != null ? raw.creatorUuid() : Waypoint.SERVER_UUID,
				raw.creatorName() != null ? raw.creatorName() : "Unknown",
				raw.created() != null ? raw.created() : Instant.EPOCH);
	}

	/** Root object of the JSON file. */
	private static final class StoreFile {
		int version = FORMAT_VERSION;
		List<Waypoint> waypoints = new ArrayList<>();
	}
}
