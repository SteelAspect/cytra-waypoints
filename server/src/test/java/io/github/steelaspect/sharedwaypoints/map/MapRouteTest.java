package io.github.steelaspect.sharedwaypoints.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import io.github.steelaspect.sharedwaypoints.waypoint.Route;
import io.github.steelaspect.sharedwaypoints.waypoint.TestCategories;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MapRouteTest {
	private static Waypoint at(String name, int x, int z, String dimension) {
		return new Waypoint(UUID.randomUUID(), name, TestCategories.PORTALS, x, 64, z, dimension, null,
				Waypoint.SERVER_UUID, "Server", Instant.EPOCH);
	}

	private static Route route(String name, String description, List<Waypoint> stops) {
		return new Route(UUID.randomUUID(), name, stops.stream().map(Waypoint::id).toList(), description,
				Waypoint.SERVER_UUID, "<b>Admin</b>", Instant.EPOCH);
	}

	@Test
	void splitsIntoOneLinePerDimensionStretch() {
		List<Waypoint> stops = List.of(
				at("A", 0, 0, Dimensions.OVERWORLD), at("B", 100, 0, Dimensions.OVERWORLD),
				at("Portal", 10, 10, Dimensions.NETHER),
				at("C", 200, 0, Dimensions.OVERWORLD), at("D", 300, 0, Dimensions.OVERWORLD), at("E", 400, 0, Dimensions.OVERWORLD));
		Route route = route("Tour", null, stops);
		List<MapRoute> lines = MapRoute.of(route, stops);

		assertEquals(2, lines.size(), "the single Nether stop draws no line");
		assertEquals(2, lines.get(0).points().size());
		assertEquals(3, lines.get(1).points().size());
		assertEquals(Dimensions.OVERWORLD, lines.get(1).dimension());
		assertEquals(200.5, lines.get(1).points().get(0)[0]);
		assertTrue(lines.get(0).key().matches("[a-z0-9-]+"));
		assertFalse(lines.get(0).key().equals(lines.get(1).key()));
	}

	@Test
	void escapesPlayerText() {
		List<Waypoint> stops = List.of(at("A", 0, 0, Dimensions.OVERWORLD), at("B", 5, 5, Dimensions.OVERWORLD));
		MapRoute line = MapRoute.of(route("<script>x</script>", "a & b", stops), stops).get(0);
		assertEquals("&lt;script&gt;x&lt;/script&gt;", line.labelHtml());
		assertTrue(line.detailHtml().contains("a &amp; b"));
		assertTrue(line.detailHtml().contains("&lt;b&gt;Admin&lt;/b&gt;"));
		assertFalse(line.detailHtml().contains("<script>"));
	}
}
