package io.github.steelaspect.sharedwaypoints.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RouteStoreTest {
	private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
	private static final Instant WHEN = Instant.parse("2026-09-30T12:00:00Z");
	private static final UUID HUB = UUID.randomUUID();
	private static final UUID FARM = UUID.randomUUID();
	private static final UUID BASE = UUID.randomUUID();

	@TempDir
	Path dir;

	private RouteStore loaded() {
		RouteStore store = new RouteStore(dir.resolve("routes.json"));
		store.load();
		return store;
	}

	private static Route route(String name, UUID... stops) {
		return new Route(UUID.randomUUID(), name, List.of(stops), null, STEVE, "Steve", WHEN);
	}

	@Test
	void savesAndLoadsInOrder() {
		RouteStore store = loaded();
		Route tour = route("Nether Tour", HUB, FARM, BASE);
		assertTrue(store.add(tour));
		assertFalse(store.add(route("nether tour")), "names are unique regardless of case");

		RouteStore reloaded = loaded();
		assertEquals(1, reloaded.size());
		Route loaded = reloaded.get("NETHER TOUR").orElseThrow();
		assertEquals(tour.id(), loaded.id());
		assertEquals(List.of(HUB, FARM, BASE), loaded.stops());
		assertEquals(WHEN, loaded.created());
	}

	@Test
	void editingStops() {
		Route tour = route("Tour", HUB, FARM);
		assertEquals(List.of(HUB, FARM, BASE), tour.withStopAdded(BASE).stops());
		assertEquals(List.of(FARM), tour.withStopRemoved(0).stops());
		assertEquals(List.of(BASE, HUB, FARM), tour.withStopAdded(BASE).withStopMoved(2, 0).stops());
		assertEquals(List.of(FARM, HUB), tour.withStopMoved(0, 1).stops());
		assertEquals(List.of(HUB, FARM), tour.stops(), "records are immutable");
	}

	@Test
	void renameKeepsIdAndFreesOldName() {
		RouteStore store = loaded();
		Route tour = route("Tour", HUB);
		store.add(tour);
		store.update(tour.withName("Grand Tour"));
		assertFalse(store.contains("Tour"));
		assertEquals(tour.id(), store.get("grand tour").orElseThrow().id());
	}

	@Test
	void deletedWaypointIsDroppedFromEveryRoute() {
		RouteStore store = loaded();
		store.add(route("A", HUB, FARM, HUB, BASE));
		store.add(route("B", FARM));
		store.forgetWaypoint(HUB);

		RouteStore reloaded = loaded();
		assertEquals(List.of(FARM, BASE), reloaded.get("A").orElseThrow().stops());
		assertEquals(List.of(FARM), reloaded.get("B").orElseThrow().stops());
	}

	@Test
	void handEditedFileIsSanitised() throws Exception {
		Files.writeString(dir.resolve("routes.json"), """
				{"version": 1, "routes": [
				  {"name": "  Padded  ", "stops": ["%s", null]},
				  {"name": ""},
				  {"name": "Padded"}
				]}""".formatted(HUB));
		RouteStore store = loaded();
		assertEquals(1, store.size(), "the empty name and the duplicate are skipped");
		Route route = store.get("Padded").orElseThrow();
		assertEquals(List.of(HUB), route.stops());
		assertEquals("Unknown", route.creatorName());
		assertEquals(Waypoint.SERVER_UUID, route.creatorUuid());
	}
}
