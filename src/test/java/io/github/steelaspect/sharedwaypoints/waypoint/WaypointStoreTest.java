package io.github.steelaspect.sharedwaypoints.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
		return new Waypoint(name, category, 1, 64, -2, "minecraft:overworld", STEVE, "Steve", WHEN);
	}

	private Path file() {
		return dir.resolve("sharedwaypoints").resolve("waypoints.json");
	}

	@Test
	void savesOnChangeAndLoadsBack() throws IOException {
		WaypointStore store = new WaypointStore(file());
		store.load();
		assertTrue(store.add(waypoint("Main Storage", Category.STORAGE)));
		assertTrue(Files.exists(file()), "file is written on the first change");

		JsonObject json = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
		assertEquals(1, json.get("version").getAsInt());
		JsonObject entry = json.getAsJsonArray("waypoints").get(0).getAsJsonObject();
		assertEquals("Main Storage", entry.get("name").getAsString());
		assertEquals("storage", entry.get("category").getAsString());
		assertEquals(-2, entry.get("z").getAsInt());
		assertEquals("minecraft:overworld", entry.get("dimension").getAsString());
		assertEquals(STEVE.toString(), entry.get("creatorUuid").getAsString());
		assertEquals("Steve", entry.get("creatorName").getAsString());
		assertEquals("2026-09-29T12:00:00Z", entry.get("created").getAsString());

		WaypointStore reloaded = new WaypointStore(file());
		reloaded.load();
		assertEquals(List.of(waypoint("Main Storage", Category.STORAGE)), reloaded.all());
	}

	@Test
	void namesAreCaseInsensitive() {
		WaypointStore store = new WaypointStore(file());
		store.add(waypoint("Iron Farm", Category.FARMS));
		assertFalse(store.add(waypoint("iron farm", Category.FARMS)));
		assertTrue(store.get("IRON FARM").isPresent());
	}

	@Test
	void renameAndRemovePersist() {
		WaypointStore store = new WaypointStore(file());
		store.add(waypoint("Old", Category.BASES));
		store.add(waypoint("Gone", Category.OTHER));
		store.rename(store.get("old").orElseThrow(), "New Base");
		store.remove("gone");

		WaypointStore reloaded = new WaypointStore(file());
		reloaded.load();
		assertEquals(List.of("New Base"), reloaded.all().stream().map(Waypoint::name).toList());
	}

	@Test
	void listIsGroupedByCategoryThenName() {
		WaypointStore store = new WaypointStore(file());
		store.add(waypoint("zeta", Category.OTHER));
		store.add(waypoint("Beta", Category.STORAGE));
		store.add(waypoint("alpha", Category.STORAGE));
		store.add(waypoint("Nether Hub", Category.PORTALS));
		assertEquals(List.of("alpha", "Beta", "Nether Hub", "zeta"),
				store.all().stream().map(Waypoint::name).toList());
	}

	@Test
	void handEditedEntriesGetDefaults() throws IOException {
		Files.createDirectories(file().getParent());
		Files.writeString(file(), """
				{"waypoints": [
				  {"name": "Shop", "category": "shops", "x": 5, "y": 70, "z": 5, "dimension": "minecraft:overworld",
				   "created": 1790000000000},
				  {"name": "", "dimension": "minecraft:overworld"}
				]}
				""", StandardCharsets.UTF_8);
		WaypointStore store = new WaypointStore(file());
		store.load();

		Waypoint shop = store.get("shop").orElseThrow();
		assertEquals(Category.OTHER, shop.category(), "unknown category falls back to other");
		assertEquals(Waypoint.SERVER_UUID, shop.creatorUuid());
		assertEquals(Instant.ofEpochMilli(1790000000000L), shop.created());
		assertEquals(1, store.size(), "the entry with an empty name is skipped");
	}

	@Test
	void brokenFileIsKeptAsBackup() throws IOException {
		Files.createDirectories(file().getParent());
		Files.writeString(file(), "{ this is not json", StandardCharsets.UTF_8);
		WaypointStore store = new WaypointStore(file());
		store.load();

		assertEquals(0, store.size());
		try (Stream<Path> files = Files.list(file().getParent())) {
			assertTrue(files.anyMatch(path -> path.getFileName().toString().startsWith("waypoints.json.broken-")));
		}
	}
}
