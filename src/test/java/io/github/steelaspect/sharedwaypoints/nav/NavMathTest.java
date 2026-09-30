package io.github.steelaspect.sharedwaypoints.nav;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.steelaspect.sharedwaypoints.waypoint.TestCategories;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NavMathTest {
	private static Waypoint at(String dimension, int x, int y, int z) {
		return new Waypoint(UUID.randomUUID(), "W", TestCategories.OTHER, x, y, z, dimension, null, Waypoint.SERVER_UUID,
				"Server", Instant.EPOCH);
	}

	@Test
	void sameDimensionIsDirect() {
		assertEquals(Optional.of(new NavMath.Target(100, 64, -20, false)),
				NavMath.project(at("minecraft:overworld", 100, 64, -20), "minecraft:overworld"));
	}

	@Test
	void overworldWaypointSeenFromTheNetherIsDividedByEight() {
		// floorDiv, so -20 / 8 -> -3 (the block that actually contains the spot), like vanilla portal maths.
		assertEquals(Optional.of(new NavMath.Target(12, 64, -3, true)),
				NavMath.project(at("minecraft:overworld", 100, 64, -20), "minecraft:the_nether"));
	}

	@Test
	void netherWaypointSeenFromTheOverworldIsMultipliedByEight() {
		assertEquals(Optional.of(new NavMath.Target(80, 70, -160, true)),
				NavMath.project(at("minecraft:the_nether", 10, 70, -20), "minecraft:overworld"));
	}

	@Test
	void theEndAndModdedDimensionsAreNotProjected() {
		assertTrue(NavMath.project(at("minecraft:the_end", 0, 60, 0), "minecraft:overworld").isEmpty());
		assertTrue(NavMath.project(at("minecraft:overworld", 0, 60, 0), "mymod:mining").isEmpty());
		assertTrue(NavMath.portalEquivalent(at("minecraft:the_end", 0, 60, 0)).isEmpty());
	}

	@Test
	void portalEquivalentGoesBothWays() {
		assertEquals(new NavMath.Target(12, 64, -3, true),
				NavMath.portalEquivalent(at("minecraft:overworld", 100, 64, -20)).orElseThrow());
		assertEquals(new NavMath.Target(80, 70, -160, true),
				NavMath.portalEquivalent(at("minecraft:the_nether", 10, 70, -20)).orElseThrow());
	}

	@ParameterizedTest(name = "yaw {0}, target ({1}, {2}) -> {3}")
	@CsvSource({
			// Facing south (yaw 0): +Z ahead, -X (west) on the right.
			"0, 0, 10, ↑",
			"0, 0, -10, ↓",
			"0, -10, 0, →",
			"0, 10, 0, ←",
			"0, -10, 10, ↗",
			// Facing north (yaw 180): +X (east) on the right.
			"180, 10, 0, →",
			"180, 0, -10, ↑",
			// Facing east (yaw -90).
			"-90, 10, 0, ↑",
			"-90, 0, 10, →",
	})
	void arrowIsRelativeToWhereYouLook(float yaw, double toX, double toZ, String expected) {
		assertEquals(expected, NavMath.arrow(yaw, 0, 0, toX, toZ));
	}

	@ParameterizedTest
	@CsvSource({"0, 10, S", "0, -10, N", "10, 0, E", "-10, 0, W", "10, -10, NE", "-10, 10, SW"})
	void compassDirections(double toX, double toZ, String expected) {
		assertEquals(expected, NavMath.compass(0, 0, toX, toZ));
	}

	@Test
	void distancesReadNicely() {
		assertEquals("0m", NavMath.formatDistance(0.2));
		assertEquals("999m", NavMath.formatDistance(999.4));
		assertEquals("1.0km", NavMath.formatDistance(1000));
		assertEquals("12.3km", NavMath.formatDistance(12_345));
	}

	@Test
	void horizontalDistanceUsesBlockCentre() {
		assertEquals(5.0, new NavMath.Target(3, 0, 4, false).horizontalDistance(0.5, 0.5), 1e-9);
	}

	@Test
	void routeLengthAddsUpTheLegs() {
		assertEquals(0.0, NavMath.routeLength(List.of()));
		assertEquals(0.0, NavMath.routeLength(List.of(at("minecraft:overworld", 0, 64, 0))));
		// 3-4-5 then 6-8-10: 15 blocks.
		assertEquals(15.0, NavMath.routeLength(List.of(at("minecraft:overworld", 0, 64, 0),
				at("minecraft:overworld", 3, 70, 4), at("minecraft:overworld", 9, 64, 12))), 1e-9);
	}

	@Test
	void routeLegIntoTheNetherIsMeasuredToThePortalSpot() {
		// From the Nether at 0,0 to an Overworld stop at 80,0: you travel in the Nether to its portal spot at 10,0.
		double length = NavMath.routeLength(List.of(at("minecraft:the_nether", 0, 64, 0), at("minecraft:overworld", 80, 64, 0)));
		assertEquals(10.0, length, 1e-9);
		// Into the End can't be measured and counts as 0.
		assertEquals(0.0, NavMath.routeLength(List.of(at("minecraft:overworld", 0, 64, 0), at("minecraft:the_end", 100, 64, 0))));
	}
}
