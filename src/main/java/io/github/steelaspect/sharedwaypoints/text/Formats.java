package io.github.steelaspect.sharedwaypoints.text;

import java.time.Duration;
import java.time.Instant;

/** Small text helpers that don't touch game classes (unit tested). */
public final class Formats {
	private Formats() {
	}

	/** "just now", "5 min ago", "3 h ago", "1 day ago", "4 months ago", "2 years ago". */
	public static String relativeAge(Instant then, Instant now) {
		long seconds = Math.max(0, Duration.between(then, now).getSeconds());
		if (seconds < 60) {
			return "just now";
		}
		long minutes = seconds / 60;
		if (minutes < 60) {
			return minutes + " min ago";
		}
		long hours = minutes / 60;
		if (hours < 24) {
			return hours + " h ago";
		}
		long days = hours / 24;
		if (days < 60) {
			return plural(days, "day") + " ago";
		}
		if (days < 730) {
			return plural(days / 30, "month") + " ago";
		}
		return plural(days / 365, "year") + " ago";
	}

	static String plural(long count, String unit) {
		return count + " " + unit + (count == 1 ? "" : "s");
	}
}
