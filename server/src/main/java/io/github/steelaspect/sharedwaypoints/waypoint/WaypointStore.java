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
	/** When each player was last online (for "new since you last played"), stored in the same file. */
	private final Map<UUID, Instant> lastSeen = new HashMap<>();
	private final List<Consumer<Waypoint>> removalListeners = new ArrayList<>();
	private final List<Runnable> changeListeners = new ArrayList<>();
	private final List<Consumer<Waypoint>> saveListeners = new ArrayList<>();
	private final List<Runnable> loadListeners = new ArrayList<>();
	/** True when the last save failed, so commands can warn that changes are only in memory. */
	private boolean saveFailed;
	/** Last-seen times changed but aren't on disk yet. */
	private boolean seenChanged;

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

	/** Called with every waypoint that is added or edited, as it is now (client-mod sync sends these). */
	public void onSaved(Consumer<Waypoint> listener) {
		saveListeners.add(listener);
	}

	/** Called after the whole list is (re)loaded from disk. */
	public void onLoaded(Runnable listener) {
		loadListeners.add(listener);
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

	/** Case-insensitive match on name, description, creator or status note. */
	public List<Waypoint> search(String query) {
		String needle = query.trim().toLowerCase(Locale.ROOT);
		return byName.values().stream()
				.filter(waypoint -> contains(waypoint.name(), needle)
						|| contains(waypoint.description(), needle)
						|| contains(waypoint.creatorName(), needle)
						|| waypoint.statusInfo().map(status -> contains(status.note(), needle)).orElse(false))
				.sorted(DISPLAY_ORDER)
				.toList();
	}

	/** Waypoints with this status, or with any status when {@code state} is null; most recently changed first. */
	public List<Waypoint> withStatus(ProjectStatus.State state) {
		return byName.values().stream()
				.filter(waypoint -> waypoint.status() != null && (state == null || waypoint.status().state() == state))
				.sorted(Comparator.comparing((Waypoint waypoint) -> waypoint.status().state())
						.thenComparing(waypoint -> waypoint.status().setAt(), Comparator.reverseOrder())
						.thenComparing(Waypoint::name, String.CASE_INSENSITIVE_ORDER))
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

	/** Number of waypoints with each status, in {@link ProjectStatus.State} order (0 for unused ones). */
	public Map<ProjectStatus.State, Integer> countsByStatus() {
		Map<ProjectStatus.State, Integer> counts = new java.util.EnumMap<>(ProjectStatus.State.class);
		for (ProjectStatus.State state : ProjectStatus.State.values()) {
			counts.put(state, 0);
		}
		for (Waypoint waypoint : byName.values()) {
			if (waypoint.status() != null) {
				counts.merge(waypoint.status().state(), 1, Integer::sum);
			}
		}
		return counts;
	}

	public int size() {
		return byName.size();
	}

	/** When the player was last online, if they have been before. */
	public Optional<Instant> lastSeen(UUID player) {
		return Optional.ofNullable(lastSeen.get(player));
	}

	/**
	 * Waypoints added at or after {@code since}, newest first. "At" counts, because waypoint times are whole
	 * seconds: showing one twice is better than missing one.
	 */
	public List<Waypoint> addedSince(Instant since) {
		Instant from = since.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
		return byName.values().stream()
				.filter(waypoint -> !waypoint.created().isBefore(from))
				.sorted(Comparator.comparing(Waypoint::created).reversed().thenComparing(DISPLAY_ORDER))
				.toList();
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
		saveListeners.forEach(listener -> listener.accept(waypoint));
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
		saveListeners.forEach(listener -> listener.accept(updated));
		changed();
		return updated;
	}

	/**
	 * Remembers that the player is (or was just) online. Doesn't count as a waypoint change, and isn't written right
	 * away: joins and leaves would otherwise rewrite the whole file each time. {@link #saveSeenIfChanged()} writes it.
	 */
	public void markSeen(UUID player, Instant when) {
		lastSeen.put(player, when);
		seenChanged = true;
	}

	/** Writes the file if last-seen times changed since the last save (every minute, and when the server stops). */
	public void saveSeenIfChanged() {
		if (seenChanged) {
			save();
		}
	}

	// ------------------------------------------------------------ persistence

	/** Replaces the in-memory list with the contents of the JSON file (if it exists). */
	public void load() {
		byName.clear();
		byId.clear();
		lastSeen.clear();
		saveFailed = false;

		Optional<StoreFile> data = JsonFiles.read(file, StoreFile.class, Gsons.withCategories(categories.get()));
		if (data.isEmpty()) {
			SharedWaypoints.LOGGER.info("No readable waypoint file at {}; starting with an empty list", file);
			loadListeners.forEach(Runnable::run);
			changed();
			return;
		}
		boolean upgraded = data.get().version < FORMAT_VERSION;
		if (data.get().lastSeen != null) {
			data.get().lastSeen.forEach((player, when) -> {
				if (player != null && when != null) {
					lastSeen.put(player, when);
				}
			});
		}
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
		loadListeners.forEach(Runnable::run);
		changed();
	}

	/** Writes the current list to disk. Failures are logged and remembered in {@link #lastSaveFailed()}. */
	public void save() {
		StoreFile data = new StoreFile();
		data.waypoints = new ArrayList<>(all());
		data.lastSeen = new java.util.TreeMap<>(lastSeen);
		try {
			JsonFiles.writeAtomically(file, data, Gsons.withCategories(categories.get()));
			saveFailed = false;
			seenChanged = false;
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
				raw.created() != null ? raw.created() : Instant.EPOCH,
				sanitize(raw.status()));
	}

	/** A usable status, or null: unknown states (a typo in a hand edit) and blank notes are dropped. */
	private static ProjectStatus sanitize(ProjectStatus raw) {
		if (raw == null || raw.state() == null) {
			return null;
		}
		String note = raw.note() == null || raw.note().isBlank() ? null : raw.note().trim();
		return new ProjectStatus(raw.state(), note,
				raw.setByUuid() != null ? raw.setByUuid() : Waypoint.SERVER_UUID,
				raw.setByName() != null ? raw.setByName() : "Unknown",
				raw.setAt() != null ? raw.setAt() : Instant.EPOCH);
	}

	/** Root object of the JSON file. */
	private static final class StoreFile {
		int version = FORMAT_VERSION;
		List<Waypoint> waypoints = new ArrayList<>();
		/** Player UUID → when they were last online. Older files don't have it. */
		Map<UUID, Instant> lastSeen = new LinkedHashMap<>();
	}
}
