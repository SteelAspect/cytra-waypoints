package io.github.steelaspect.sharedwaypoints.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FavoritesStoreTest {
	private static final UUID ALICE = UUID.randomUUID();
	private static final UUID BOB = UUID.randomUUID();
	private static final UUID BASE = UUID.randomUUID();
	private static final UUID FARM = UUID.randomUUID();

	@TempDir
	Path dir;

	private FavoritesStore loaded() {
		FavoritesStore store = new FavoritesStore(dir.resolve("favorites.json"));
		store.load();
		return store;
	}

	@Test
	void togglePersistsPerPlayer() {
		FavoritesStore store = loaded();
		assertTrue(store.toggle(ALICE, BASE));
		assertTrue(store.toggle(ALICE, FARM));
		assertTrue(store.toggle(BOB, FARM));
		assertFalse(store.toggle(ALICE, FARM), "second toggle removes it");

		FavoritesStore reloaded = loaded();
		assertEquals(Set.of(BASE), reloaded.of(ALICE));
		assertEquals(Set.of(FARM), reloaded.of(BOB));
		assertTrue(reloaded.isFavorite(BOB, FARM));
		assertFalse(reloaded.isFavorite(ALICE, FARM));
	}

	@Test
	void forgettingAWaypointClearsItForEveryone() {
		FavoritesStore store = loaded();
		store.toggle(ALICE, FARM);
		store.toggle(BOB, FARM);
		store.toggle(BOB, BASE);
		store.forget(FARM);

		FavoritesStore reloaded = loaded();
		assertEquals(Set.of(), reloaded.of(ALICE));
		assertEquals(Set.of(BASE), reloaded.of(BOB));
	}
}
