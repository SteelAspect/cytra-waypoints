package io.github.steelaspect.sharedwaypoints.xaerosync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.steelaspect.sharedwaypoints.protocol.SyncedWaypoint;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SyncStateTest {
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";

	private static SyncedWaypoint waypoint(String name, String dimension) {
		return new SyncedWaypoint(UUID.randomUUID(), name, name.substring(0, 1), "other", "Other", 15, 0, 64, 0, dimension, null);
	}

	private static List<String> names(List<SyncedWaypoint> waypoints) {
		return waypoints.stream().map(SyncedWaypoint::name).toList();
	}

	@Test
	void nothingIsWrittenBeforeTheFullList() {
		SyncState state = new SyncState();
		state.upsert(waypoint("Early", OVERWORLD));
		assertFalse(state.hasWork(), "an update before the full list is only remembered");
	}

	@Test
	void fullListMarksEveryServerDimensionSoStaleSetsAreCleared() {
		SyncState state = new SyncState();
		state.acceptFull(List.of(waypoint("Base", OVERWORLD)), Set.of(OVERWORLD, NETHER, "minecraft:the_end"));
		assertEquals(Set.of(OVERWORLD, NETHER, "minecraft:the_end"), state.dirtyDimensions(),
				"the Nether and End have no shared waypoints now, but their old ones must go");
		assertEquals(List.of(), state.inDimension(NETHER));
		assertEquals(List.of("Base"), names(state.inDimension(OVERWORLD)));
	}

	@Test
	void editsAndDeletesOnlyTouchTheirDimensions() {
		SyncState state = new SyncState();
		SyncedWaypoint farm = waypoint("Farm", OVERWORLD);
		SyncedWaypoint hub = waypoint("Hub", NETHER);
		state.acceptFull(List.of(farm, hub), Set.of());
		state.dirtyDimensions().forEach(state::done);
		assertFalse(state.hasWork());

		state.upsert(new SyncedWaypoint(farm.id(), "Iron Farm", "I", "farms", "Farms", 10, 1, 64, 1, OVERWORLD, "note"));
		assertEquals(Set.of(OVERWORLD), state.dirtyDimensions(), "a rename rewrites its own dimension");
		assertEquals(List.of("Iron Farm"), names(state.inDimension(OVERWORLD)), "matched by id, not duplicated");
		state.done(OVERWORLD);

		state.delete(hub.id());
		assertEquals(Set.of(NETHER), state.dirtyDimensions());
		assertEquals(List.of(), state.inDimension(NETHER));
		state.done(NETHER);
		state.delete(UUID.randomUUID());
		assertFalse(state.hasWork(), "deleting an unknown id changes nothing");
	}

	@Test
	void movingToAnotherDimensionRewritesBoth() {
		SyncState state = new SyncState();
		SyncedWaypoint spot = waypoint("Spot", OVERWORLD);
		state.acceptFull(List.of(spot), Set.of());
		state.dirtyDimensions().forEach(state::done);
		state.upsert(new SyncedWaypoint(spot.id(), "Spot", "S", "other", "Other", 15, 5, 70, 5, NETHER, null));
		assertEquals(Set.of(OVERWORLD, NETHER), state.dirtyDimensions());
	}

	@Test
	void rejoiningStartsOverAndSetsAreSortedByName() {
		SyncState state = new SyncState();
		state.acceptFull(List.of(waypoint("zeta", OVERWORLD), waypoint("Alpha", OVERWORLD), waypoint("beta", OVERWORLD)), Set.of());
		assertEquals(List.of("Alpha", "beta", "zeta"), names(state.inDimension(OVERWORLD)));
		state.clear();
		assertFalse(state.hasWork());
		assertEquals(0, state.size());
	}

	@Test
	void initialsAreAlwaysOneToThreeCharacters() {
		assertEquals("MS", XaeroBridge.initials(new SyncedWaypoint(UUID.randomUUID(), "Main Storage", "MS", "s", "S", 11, 0, 0, 0, OVERWORLD, null)));
		assertEquals("B", XaeroBridge.initials(new SyncedWaypoint(UUID.randomUUID(), "Base", " ", "s", "S", 11, 0, 0, 0, OVERWORLD, null)));
		assertEquals("ABC", XaeroBridge.initials(new SyncedWaypoint(UUID.randomUUID(), "X", "ABCD", "s", "S", 11, 0, 0, 0, OVERWORLD, null)));
		assertTrue(XaeroBridge.initials(new SyncedWaypoint(UUID.randomUUID(), "", null, "s", "S", 11, 0, 0, 0, OVERWORLD, null)).length() == 1);
	}
}
