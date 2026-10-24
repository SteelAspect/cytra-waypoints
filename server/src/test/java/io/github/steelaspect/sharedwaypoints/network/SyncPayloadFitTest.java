package io.github.steelaspect.sharedwaypoints.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 1.20.4 has a 1 MiB packet cap: the menu snapshot must always fit. */
class SyncPayloadFitTest {
	/** Longest allowed texts, in 3-byte characters: the worst case per waypoint. */
	private static SyncPayload.WaypointData waypoint(int i) {
		return new SyncPayload.WaypointData(UUID.randomUUID(), "€".repeat(32), "storage", i, 64, -i, "minecraft:overworld",
				"€".repeat(120), "player" + i, 1_700_000_000L, true, true, false,
				new SyncPayload.StatusData("wip", "€".repeat(80), "player", 1_700_000_000L), true);
	}

	private static SyncPayload snapshot(int count) {
		List<SyncPayload.WaypointData> waypoints = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			waypoints.add(waypoint(i));
		}
		List<SyncPayload.RouteData> routes = List.of(new SyncPayload.RouteData(UUID.randomUUID(), "Tour",
				List.of(waypoints.get(0).id()), "€".repeat(120), "player", true, true));
		return new SyncPayload(List.of(new SyncPayload.CategoryData("storage", "Storage", 11)), waypoints, true, false,
				null, routes, true, null);
	}

	@Test
	void aSmallSnapshotIsSentAsItIs() {
		SyncPayload small = snapshot(50);
		assertSame(small, small.fitted());
	}

	@Test
	void aHugeSnapshotFirstLosesDescriptions() {
		SyncPayload big = snapshot(2_000);
		assertTrue(big.encodedSize() > SyncPayload.MAX_SIZE, "the test data is over the cap");
		SyncPayload fitted = big.fitted();
		assertTrue(fitted.encodedSize() <= SyncPayload.BUDGET);
		assertEquals(2_000, fitted.waypoints().size(), "without descriptions and notes all 2000 still fit");
		assertNull(fitted.waypoints().get(0).description());
		assertNull(fitted.waypoints().get(0).status().note());
		assertNull(fitted.routes().get(0).description());
	}

	@Test
	void anEvenBiggerSnapshotAlsoLosesTheLastWaypoints() {
		SyncPayload fitted = snapshot(12_000).fitted();
		assertTrue(fitted.encodedSize() <= SyncPayload.BUDGET);
		assertTrue(fitted.waypoints().size() < 12_000 && fitted.waypoints().size() > 5_000, "kept " + fitted.waypoints().size());
	}
}
