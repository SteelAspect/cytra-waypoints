package io.github.steelaspect.sharedwaypoints.waypoint;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A single shared waypoint. This record is also the JSON shape stored in {@code waypoints.json}.
 *
 * @param id          stable id; favourites and navigation refer to this, so they survive renames
 * @param name        display name, unique (case-insensitive)
 * @param category    category, which decides the colour
 * @param x           block X
 * @param y           block Y
 * @param z           block Z
 * @param dimension   dimension id, e.g. {@code minecraft:overworld}
 * @param description optional short note shown in hover cards and /cway info (null if none)
 * @param creatorUuid UUID of the player who added it (all zeros for the console / command blocks)
 * @param creatorName name of the creator at the time it was added
 * @param created     when it was added (stored as ISO-8601 UTC)
 * @param status      where the build stands (planned, WIP, done, broken), or null for none
 */
public record Waypoint(
		UUID id,
		String name,
		Category category,
		int x,
		int y,
		int z,
		String dimension,
		String description,
		UUID creatorUuid,
		String creatorName,
		Instant created,
		ProjectStatus status) {

	/** Xaero's Minimap rejects shared waypoint names longer than 32 characters. */
	public static final int MAX_NAME_LENGTH = 32;
	public static final int MAX_DESCRIPTION_LENGTH = 120;

	/** Creator UUID used for waypoints added by the console or a command block. */
	public static final UUID SERVER_UUID = new UUID(0L, 0L);

	/** A waypoint without a status. */
	public Waypoint(UUID id, String name, Category category, int x, int y, int z, String dimension, String description,
			UUID creatorUuid, String creatorName, Instant created) {
		this(id, name, category, x, y, z, dimension, description, creatorUuid, creatorName, created, null);
	}

	public Waypoint withName(String newName) {
		return new Waypoint(id, newName, category, x, y, z, dimension, description, creatorUuid, creatorName, created, status);
	}

	/** Copy with a new status; {@code null} clears it. */
	public Waypoint withStatus(ProjectStatus newStatus) {
		return new Waypoint(id, name, category, x, y, z, dimension, description, creatorUuid, creatorName, created, newStatus);
	}

	public Optional<ProjectStatus> statusInfo() {
		return Optional.ofNullable(status);
	}

	/** Copy with a new description; {@code null} or blank clears it. */
	public Waypoint withDescription(String newDescription) {
		String cleaned = newDescription == null || newDescription.isBlank() ? null : newDescription.trim();
		return new Waypoint(id, name, category, x, y, z, dimension, cleaned, creatorUuid, creatorName, created, status);
	}

	public Optional<String> descriptionText() {
		return Optional.ofNullable(description);
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

	/** Checks a (trimmed) description; returns an error message or empty. */
	public static Optional<String> validateDescription(String description) {
		if (description.length() > MAX_DESCRIPTION_LENGTH) {
			return Optional.of("Description is too long (max " + MAX_DESCRIPTION_LENGTH + " characters)");
		}
		for (int i = 0; i < description.length(); i++) {
			char c = description.charAt(i);
			if (Character.isISOControl(c) || c == '§') {
				return Optional.of("Description contains an unsupported character");
			}
		}
		return Optional.empty();
	}
}
