package io.github.steelaspect.sharedwaypoints.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import io.github.steelaspect.sharedwaypoints.SharedWaypoints;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory list of waypoints backed by {@code config/sharedwaypoints/waypoints.json}.
 *
 * <p>The file is read when the server starts and rewritten after every change. Writes go to a temporary file
 * first and are then moved over the real file, so a crash mid-write can't leave half a JSON file behind.
 * All access happens on the server thread (commands and lifecycle events), so no locking is needed.
 */
public final class WaypointStore {
	private static final int FORMAT_VERSION = 1;

	/** Listing order: by category (enum order), then by name. */
	private static final Comparator<Waypoint> DISPLAY_ORDER = Comparator
			.comparing(Waypoint::category)
			.thenComparing(Waypoint::name, String.CASE_INSENSITIVE_ORDER);

	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.registerTypeAdapter(Instant.class, new InstantAdapter().nullSafe())
			.create();

	private final Path file;
	/** Keyed by lower-case name so lookups and uniqueness are case-insensitive. */
	private final Map<String, Waypoint> waypoints = new HashMap<>();
	/** True when the last save failed, so commands can warn that changes are only in memory. */
	private boolean saveFailed;

	public WaypointStore(Path file) {
		this.file = file;
	}

	// ---------------------------------------------------------------- queries

	public Optional<Waypoint> get(String name) {
		return Optional.ofNullable(waypoints.get(key(name)));
	}

	public boolean contains(String name) {
		return waypoints.containsKey(key(name));
	}

	/** All waypoints, sorted by category and then name. */
	public List<Waypoint> all() {
		return waypoints.values().stream().sorted(DISPLAY_ORDER).toList();
	}

	public List<Waypoint> inCategory(Category category) {
		return waypoints.values().stream()
				.filter(waypoint -> waypoint.category() == category)
				.sorted(DISPLAY_ORDER)
				.toList();
	}

	public Map<Category, Integer> countsByCategory() {
		Map<Category, Integer> counts = new EnumMap<>(Category.class);
		for (Category category : Category.values()) {
			counts.put(category, 0);
		}
		for (Waypoint waypoint : waypoints.values()) {
			counts.merge(waypoint.category(), 1, Integer::sum);
		}
		return counts;
	}

	public int size() {
		return waypoints.size();
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
		waypoints.put(key(waypoint.name()), waypoint);
		save();
		return true;
	}

	/** Removes a waypoint by name and saves. Returns the removed waypoint, if there was one. */
	public Optional<Waypoint> remove(String name) {
		Waypoint removed = waypoints.remove(key(name));
		if (removed != null) {
			save();
		}
		return Optional.ofNullable(removed);
	}

	/**
	 * Renames a waypoint and saves. The caller must have checked that {@code newName} is valid and free
	 * (a case-only change of the same waypoint is allowed).
	 */
	public Waypoint rename(Waypoint waypoint, String newName) {
		Waypoint renamed = waypoint.withName(newName);
		waypoints.remove(key(waypoint.name()));
		waypoints.put(key(newName), renamed);
		save();
		return renamed;
	}

	// ------------------------------------------------------------ persistence

	/** Replaces the in-memory list with the contents of the JSON file (if it exists). */
	public void load() {
		waypoints.clear();
		saveFailed = false;
		if (!Files.exists(file)) {
			SharedWaypoints.LOGGER.info("No waypoint file at {} yet; it will be created on the first change", file);
			return;
		}

		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			StoreFile data = GSON.fromJson(reader, StoreFile.class);
			if (data != null && data.waypoints != null) {
				for (Waypoint raw : data.waypoints) {
					Waypoint waypoint = sanitize(raw);
					if (waypoint == null) {
						SharedWaypoints.LOGGER.warn("Skipping invalid waypoint entry in {}: {}", file, raw);
					} else if (waypoints.putIfAbsent(key(waypoint.name()), waypoint) != null) {
						SharedWaypoints.LOGGER.warn("Skipping duplicate waypoint name in {}: {}", file, waypoint.name());
					}
				}
			}
			SharedWaypoints.LOGGER.info("Loaded {} shared waypoint(s) from {}", waypoints.size(), file);
		} catch (IOException | JsonParseException e) {
			// Keep a copy of the unreadable file; otherwise the next save would silently overwrite it.
			Path backup = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
			try {
				Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException copyError) {
				e.addSuppressed(copyError);
			}
			SharedWaypoints.LOGGER.error("Could not read {}; starting with an empty list (original kept as {})",
					file, backup, e);
			waypoints.clear();
		}
	}

	/** Writes the current list to disk. Failures are logged and remembered in {@link #lastSaveFailed()}. */
	public void save() {
		StoreFile data = new StoreFile();
		data.waypoints = new ArrayList<>(all());

		Path temp = file.resolveSibling(file.getFileName() + ".tmp");
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				GSON.toJson(data, writer);
			}
			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
			saveFailed = false;
		} catch (IOException e) {
			saveFailed = true;
			SharedWaypoints.LOGGER.error("Could not save waypoints to {}", file, e);
		}
	}

	// ---------------------------------------------------------------- helpers

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	/** Fills in defaults for a hand-edited or older entry; returns null if it can't be used at all. */
	private static Waypoint sanitize(Waypoint raw) {
		if (raw == null || raw.name() == null || raw.dimension() == null) {
			return null;
		}
		String name = raw.name().trim();
		if (Waypoint.validateName(name).isPresent()) {
			return null;
		}
		return new Waypoint(
				name,
				raw.category() != null ? raw.category() : Category.OTHER, // unknown category -> "other"
				raw.x(),
				raw.y(),
				raw.z(),
				raw.dimension(),
				raw.creatorUuid() != null ? raw.creatorUuid() : Waypoint.SERVER_UUID,
				raw.creatorName() != null ? raw.creatorName() : "Unknown",
				raw.created() != null ? raw.created() : Instant.EPOCH);
	}

	/** Root object of the JSON file. */
	private static final class StoreFile {
		int version = FORMAT_VERSION;
		List<Waypoint> waypoints = new ArrayList<>();
	}

	/** Stores {@link Instant} as an ISO-8601 string; also accepts epoch milliseconds when reading. */
	private static final class InstantAdapter extends TypeAdapter<Instant> {
		@Override
		public void write(JsonWriter out, Instant value) throws IOException {
			out.value(value.toString());
		}

		@Override
		public Instant read(JsonReader in) throws IOException {
			if (in.peek() == JsonToken.NUMBER) {
				return Instant.ofEpochMilli(in.nextLong());
			}
			String text = in.nextString();
			try {
				return Instant.parse(text);
			} catch (DateTimeParseException e) {
				throw new JsonParseException("Invalid timestamp: " + text, e);
			}
		}
	}
}
