package io.github.steelaspect.sharedwaypoints.waypoint;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A single shared waypoint. This record is also the JSON shape stored in {@code waypoints.json}.
 *
 * @param name        display name, unique (case-insensitive)
 * @param category    category, which decides the colour
 * @param x           block X
 * @param y           block Y
 * @param z           block Z
 * @param dimension   dimension id, e.g. {@code minecraft:overworld}
 * @param creatorUuid UUID of the player who added it (all zeros for the console / command blocks)
 * @param creatorName name of the creator at the time it was added
 * @param created     when it was added (stored as ISO-8601 UTC)
 */
public record Waypoint(
		String name,
		Category category,
		int x,
		int y,
		int z,
		String dimension,
		UUID creatorUuid,
		String creatorName,
		Instant created) {

	/** Xaero's Minimap rejects shared waypoint names longer than 32 characters. */
	public static final int MAX_NAME_LENGTH = 32;

	/** Creator UUID used for waypoints added by the console or a command block. */
	public static final UUID SERVER_UUID = new UUID(0L, 0L);

	public Waypoint withName(String newName) {
		return new Waypoint(newName, category, x, y, z, dimension, creatorUuid, creatorName, created);
	}

	/** Coordinates as {@code "x y z"}, the format used by vanilla commands like /tp. */
	public String coordinates() {
		return x + " " + y + " " + z;
	}

	/**
	 * Checks a (trimmed) name for problems.
	 *
	 * @return an error message, or empty if the name is fine
	 */
	public static Optional<String> validateName(String name) {
		if (name.isEmpty()) {
			return Optional.of("Name cannot be empty");
		}
		if (name.length() > MAX_NAME_LENGTH) {
			return Optional.of("Name is too long (max " + MAX_NAME_LENGTH + " characters, Xaero's limit)");
		}
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			// '§' is stripped by Xaero, and '<' / '>' confuse Xaero's "who shared this" detection.
			if (Character.isISOControl(c) || c == '§' || c == '<' || c == '>') {
				return Optional.of("Name contains an unsupported character: '" + c + "'");
			}
		}
		return Optional.empty();
	}
}
