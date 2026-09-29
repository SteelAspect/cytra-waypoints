package io.github.steelaspect.sharedwaypoints.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.steelaspect.sharedwaypoints.util.Page;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FormatsTest {
	private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");

	private static String ago(Duration duration) {
		return Formats.relativeAge(NOW.minus(duration), NOW);
	}

	@Test
	void relativeAges() {
		assertEquals("just now", ago(Duration.ofSeconds(30)));
		assertEquals("just now", Formats.relativeAge(NOW.plusSeconds(5), NOW), "clock skew never goes negative");
		assertEquals("5 min ago", ago(Duration.ofMinutes(5)));
		assertEquals("3 h ago", ago(Duration.ofHours(3)));
		assertEquals("1 day ago", ago(Duration.ofDays(1)));
		assertEquals("45 days ago", ago(Duration.ofDays(45)));
		assertEquals("4 months ago", ago(Duration.ofDays(125)));
		assertEquals("2 years ago", ago(Duration.ofDays(800)));
	}

	@Test
	void pagingClampsAndSlices() {
		List<Integer> items = List.of(1, 2, 3, 4, 5, 6, 7);
		Page<Integer> second = Page.of(items, 2, 3);
		assertEquals(List.of(4, 5, 6), second.items());
		assertEquals(3, second.count());
		assertEquals(List.of(7), Page.of(items, 99, 3).items(), "too-high page shows the last page");
		assertEquals(1, Page.of(List.of(), 5, 3).number(), "empty list still has page 1");
		assertEquals(false, Page.of(items, 1, 3).hasPrevious());
		assertEquals(true, Page.of(items, 1, 3).hasNext());
	}
}
