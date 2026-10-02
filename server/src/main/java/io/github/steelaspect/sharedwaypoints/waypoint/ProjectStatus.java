package io.github.steelaspect.sharedwaypoints.waypoint;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;

/**
 * Where a build stands: planned, being built, done or broken, with an optional short note ("out of bonemeal") and
 * who set it when. Stored inside the waypoint in {@code waypoints.json}; waypoints without one have no status.
 *
 * @param state     the status
 * @param note      optional short note, or null
 * @param setByUuid who set it (all zeros for the console)
 * @param setByName their name at the time
 * @param setAt     when it was set (whole seconds)
 */
public record ProjectStatus(State state, String note, UUID setByUuid, String setByName, Instant setAt) {
	public static final int MAX_NOTE_LENGTH = 80;

	/** The statuses, in the order they're listed. The JSON and command names are the lower-case ids. */
	public enum State {
		@SerializedName("broken") BROKEN("Broken", "⚠", ChatFormatting.RED),
		@SerializedName("wip") WIP("WIP", "⚒", ChatFormatting.GOLD),
		@SerializedName("planned") PLANNED("Planned", "✎", ChatFormatting.AQUA),
		@SerializedName("done") DONE("Done", "✔", ChatFormatting.GREEN);

		private final String displayName;
		private final String symbol;
		private final ChatFormatting color;

		State(String displayName, String symbol, ChatFormatting color) {
			this.displayName = displayName;
			this.symbol = symbol;
			this.color = color;
		}

		/** "broken", "wip", "planned", "done": what commands and the JSON file use. */
		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public String displayName() {
			return displayName;
		}

		/** A one-character symbol every client's font has. */
		public String symbol() {
			return symbol;
		}

		public ChatFormatting color() {
			return color;
		}

		/** RGB of {@link #color()}, for the web maps. */
		public int rgb() {
			Integer rgb = color.getColor();
			return rgb != null ? rgb : 0xFFFFFF;
		}

		public static Optional<State> byId(String id) {
			for (State state : values()) {
				if (state.id().equalsIgnoreCase(id)) {
					return Optional.of(state);
				}
			}
			return Optional.empty();
		}
	}

	public Optional<String> noteText() {
		return Optional.ofNullable(note);
	}

	/** "⚠ Broken" or "⚠ Broken: out of bonemeal". */
	public String summary() {
		return state.symbol() + " " + state.displayName() + (note == null ? "" : ": " + note);
	}

	/** Checks a (trimmed) note; returns an error message or empty. */
	public static Optional<String> validateNote(String note) {
		if (note.length() > MAX_NOTE_LENGTH) {
			return Optional.of("Note is too long (max " + MAX_NOTE_LENGTH + " characters)");
		}
		for (int i = 0; i < note.length(); i++) {
			char c = note.charAt(i);
			if (Character.isISOControl(c) || c == '§') {
				return Optional.of("Note contains an unsupported character");
			}
		}
		return Optional.empty();
	}
}
