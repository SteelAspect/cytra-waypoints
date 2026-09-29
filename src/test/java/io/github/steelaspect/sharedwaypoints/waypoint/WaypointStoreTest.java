package io.github.steelaspect.sharedwaypoints.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WaypointStoreTest {
	private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
	private static final Instant WHEN = Instant.parse("2026-09-29T12:00:00Z");

	@TempDir
	Path dir;

	private static Waypoint waypoint(String name, Category category) {
		return new Waypoint(UUID.randomUUID(), name, category, 1, 64, -2, "minecraft:overworld", null, STEVE, "Steve", WHEN);
	}

	private Path file() {
		return dir.resolve("sharedwaypoints").resolve("waypoints.json");
	}

	private WaypointStore loaded() {
		WaypointStore store = new WaypointStore(file());
		store.load();
		return store;
	}

	@Test
	void savesOnChangeAndLoadsBack() throws IOException {
		WaypointStore store = loaded();
		Waypoint original = waypoint("Main Storage", Category.STORAGE).withDescription("Sorted chests");
		assertTrue(store.add(original));
		assertTrue(Files.exists(file()), "file is written on the first change");

		JsonObject json = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
		assertEquals(2, json.get("version").getAsInt());
		JsonObject entry = json.getAsJsonArray("waypoints").get(0).getAsJsonObject();
		assertEquals(original.id().toString(), entry.get("id").getAsString());
		assertEquals("Main Storage", entry.get("name").getAsString());
		assertEquals("storage", entry.get("category").getAsString());
		assertEquals(-2, entry.get("z").getAsInt());
		assertEquals("minecraft:overworld", entry.get("dimension").getAsString());
		assertEquals("Sorted chests", entry.get("description").getAsString());
		assertEquals(STEVE.toString(), entry.get("creatorUuid").getAsString());
		assertEquals("Steve", entry.get("creatorName").getAsString());
		assertEquals("2026-09-29T12:00:00Z", entry.get("created").getAsString());

		assertEquals(List.of(original), loaded().all());
	}

	@Test
	void namesAreCaseInsensitive() {
		WaypointStore store = loaded();
		store.add(waypoint("Iron Farm", Category.FARMS));
		assertFalse(store.add(waypoint("iron farm", Category.FARMS)));
		assertTrue(store.get("IRON FARM").isPresent());
	}

	@Test
	void updateKeepsIdAndPersists() {
		WaypointStore store = loaded();
		Waypoint old = waypoint("Old", Category.BASES);
		store.add(old);
		store.add(waypoint("Gone", Category.OTHER));
		store.update(old.withName("New Base").withDescription("  moved here  "));
		store.remove("gone");

		WaypointStore reloaded = loaded();
		assertEquals(List.of("New Base"), reloaded.all().stream().map(Waypoint::name).toList());
		Waypoint renamed = reloaded.get(old.id()).orElseThrow();
		assertEquals("moved here", renamed.description());
		assertFalse(reloaded.contains("Old"));
	}

	@Test
	void removalListenersHearAboutRemovals() {
		WaypointStore store = loaded();
		List<String> removed = new ArrayList<>();
		store.onRemoved(waypoint -> removed.add(waypoint.name()));
		store.add(waypoint("Temp", Category.OTHER));
		store.remove("TEMP");
		store.remove("missing");
		assertEquals(List.of("Temp"), removed);
	}

	@Test
	void listIsGroupedByCategoryThenName() {
		WaypointStore store = loaded();
		store.add(waypoint("zeta", Category.OTHER));
		store.add(waypoint("Beta", Category.STORAGE));
		store.add(waypoint("alpha", Category.STORAGE));
		store.add(waypoint("Nether Hub", Category.PORTALS));
		assertEquals(List.of("alpha", "Beta", "Nether Hub", "zeta"),
				store.all().stream().map(Waypoint::name).toList());
	}

	@Test
	void searchLooksAtNameDescriptionAndCreator() {
		WaypointStore store = loaded();
		store.add(waypoint("Iron Farm", Category.FARMS));
		store.add(waypoint("Blaze", Category.FARMS).withDescription("XP and rods"));
		store.add(new Waypoint(UUID.randomUUID(), "Shop", Category.BASES, 0, 0, 0, "minecraft:overworld", null,
				UUID.randomUUID(), "Alex", WHEN));
		assertEquals(List.of("Iron Farm"), names(store.search("IRON")));
		assertEquals(List.of("Blaze"), names(store.search("rods")));
		assertEquals(List.of("Shop"), names(store.search("alex")));
		assertEquals(3, store.search(" ").size(), "a blank query matches everything");
	}

	@Test
	void version1FilesGetStableIdsAndDefaults() throws IOException {
		Files.createDirectories(file().getParent());
		Files.writeString(file(), """
				{"version": 1, "waypoints": [
				  {"name": "Shop", "category": "shops", "x": 5, "y": 70, "z": 5, "dimension": "minecraft:overworld",
				   "created": 1790000000000},
				  {"name": "", "dimension": "minecraft:overworld"}
				]}
				""", StandardCharsets.UTF_8);
		WaypointStore store = loaded();

		Waypoint shop = store.get("shop").orElseThrow();
		assertNotNull(shop.id(), "an id is generated");
		assertEquals(Category.OTHER, shop.category(), "unknown category falls back to other");
		assertEquals(Waypoint.SERVER_UUID, shop.creatorUuid());
		assertEquals(Instant.ofEpochMilli(1790000000000L), shop.created());
		assertEquals(1, store.size(), "the entry with an empty name is skipped");
		assertEquals(shop.id(), loaded().get("shop").orElseThrow().id(), "the generated id was saved");
	}

	@Test
	void brokenFileIsKeptAsBackup() throws IOException {
		Files.createDirectories(file().getParent());
		Files.writeString(file(), "{ this is not json", StandardCharsets.UTF_8);
		assertEquals(0, loaded().size());
		try (Stream<Path> files = Files.list(file().getParent())) {
			assertTrue(files.anyMatch(path -> path.getFileName().toString().startsWith("waypoints.json.broken-")));
		}
	}

	private static List<String> names(List<Waypoint> waypoints) {
		return waypoints.stream().map(Waypoint::name).toList();
	}
}
