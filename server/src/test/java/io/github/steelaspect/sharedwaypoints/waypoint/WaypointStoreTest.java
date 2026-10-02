package io.github.steelaspect.sharedwaypoints.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
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
		WaypointStore store = new WaypointStore(file(), () -> CategoryRegistry.DEFAULT);
		store.load();
		return store;
	}

	@Test
	void savesOnChangeAndLoadsBack() throws IOException {
		WaypointStore store = loaded();
		Waypoint original = waypoint("Main Storage", TestCategories.STORAGE).withDescription("Sorted chests");
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
		store.add(waypoint("Iron Farm", TestCategories.FARMS));
		assertFalse(store.add(waypoint("iron farm", TestCategories.FARMS)));
		assertTrue(store.get("IRON FARM").isPresent());
	}

	@Test
	void updateKeepsIdAndPersists() {
		WaypointStore store = loaded();
		Waypoint old = waypoint("Old", TestCategories.BASES);
		store.add(old);
		store.add(waypoint("Gone", TestCategories.OTHER));
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
		store.add(waypoint("Temp", TestCategories.OTHER));
		store.remove("TEMP");
		store.remove("missing");
		assertEquals(List.of("Temp"), removed);
	}

	@Test
	void listIsGroupedByCategoryThenName() {
		WaypointStore store = loaded();
		store.add(waypoint("zeta", TestCategories.OTHER));
		store.add(waypoint("Beta", TestCategories.STORAGE));
		store.add(waypoint("alpha", TestCategories.STORAGE));
		store.add(waypoint("Nether Hub", TestCategories.PORTALS));
		assertEquals(List.of("alpha", "Beta", "Nether Hub", "zeta"),
				store.all().stream().map(Waypoint::name).toList());
	}

	@Test
	void searchLooksAtNameDescriptionAndCreator() {
		WaypointStore store = loaded();
		store.add(waypoint("Iron Farm", TestCategories.FARMS));
		store.add(waypoint("Blaze", TestCategories.FARMS).withDescription("XP and rods"));
		store.add(new Waypoint(UUID.randomUUID(), "Shop", TestCategories.BASES, 0, 0, 0, "minecraft:overworld", null,
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
		assertEquals("shops", shop.category().id(), "a category missing from config.json keeps its id");
		assertEquals(ChatFormatting.GRAY, shop.category().color(), "and is shown in gray");
		assertEquals(Waypoint.SERVER_UUID, shop.creatorUuid());
		assertEquals(Instant.ofEpochMilli(1790000000000L), shop.created());
		assertEquals(1, store.size(), "the entry with an empty name is skipped");
		assertEquals(shop.id(), loaded().get("shop").orElseThrow().id(), "the generated id was saved");
		assertTrue(Files.readString(file()).contains("\"category\": \"shops\""), "the unknown category id is kept on disk");
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

	private static Waypoint addedAt(String name, Instant created) {
		return new Waypoint(UUID.randomUUID(), name, TestCategories.OTHER, 0, 64, 0, "minecraft:overworld", null, STEVE,
				"Steve", created);
	}

	@Test
	void lastSeenIsSavedWithTheWaypoints() throws IOException {
		WaypointStore store = loaded();
		UUID alex = UUID.randomUUID();
		assertTrue(store.lastSeen(alex).isEmpty(), "never seen");
		Instant seen = Instant.parse("2026-09-30T08:15:30.250Z");
		store.markSeen(alex, seen);
		assertTrue(loaded().lastSeen(alex).isEmpty(), "not written on every join or leave");
		store.saveSeenIfChanged();

		WaypointStore reloaded = loaded();
		assertEquals(seen, reloaded.lastSeen(alex).orElseThrow());
		JsonObject json = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
		assertEquals("2026-09-30T08:15:30.250Z", json.getAsJsonObject("lastSeen").get(alex.toString()).getAsString());
	}

	@Test
	void olderFilesWithoutLastSeenStillLoad() throws IOException {
		Files.createDirectories(file().getParent());
		Files.writeString(file(), "{\"version\": 2, \"waypoints\": [{\"name\": \"Base\", \"dimension\": \"minecraft:overworld\"}]}");
		WaypointStore store = loaded();
		assertEquals(1, store.size());
		assertTrue(store.lastSeen(STEVE).isEmpty());
	}

	@Test
	void addedSinceIsNewestFirstAndIncludesTheSameSecond() {
		WaypointStore store = loaded();
		store.add(addedAt("Old", Instant.parse("2026-09-30T08:00:00Z")));
		store.add(addedAt("Same second", Instant.parse("2026-09-30T09:00:00Z")));
		store.add(addedAt("Newest", Instant.parse("2026-09-30T10:00:00Z")));
		// Left at 09:00:00.600; waypoint times are whole seconds, so the 09:00:00 one might be newer: include it.
		List<String> names = store.addedSince(Instant.parse("2026-09-30T09:00:00.600Z")).stream().map(Waypoint::name).toList();
		assertEquals(List.of("Newest", "Same second"), names);
		assertTrue(store.addedSince(Instant.parse("2026-09-30T11:00:00Z")).isEmpty());
	}

	@Test
	void statusIsSavedLoadedAndListed() throws IOException {
		WaypointStore store = loaded();
		Waypoint farm = waypoint("Gold Farm", TestCategories.FARMS);
		Waypoint base = waypoint("Base", TestCategories.BASES);
		Waypoint storage = waypoint("Storage", TestCategories.STORAGE);
		store.add(farm);
		store.add(base);
		store.add(storage);
		assertTrue(store.withStatus(null).isEmpty());
		assertFalse(Files.readString(file()).contains("\"status\""), "no status, no key in the file");

		ProjectStatus broken = new ProjectStatus(ProjectStatus.State.BROKEN, "out of bonemeal", STEVE, "Steve", WHEN);
		store.update(farm.withStatus(broken));
		store.update(base.withStatus(new ProjectStatus(ProjectStatus.State.DONE, null, STEVE, "Steve", WHEN)));
		store.update(storage.withStatus(new ProjectStatus(ProjectStatus.State.BROKEN, null, STEVE, "Steve",
				WHEN.plusSeconds(60))));

		JsonObject entry = JsonParser.parseString(Files.readString(file())).getAsJsonObject()
				.getAsJsonArray("waypoints").asList().stream().map(element -> element.getAsJsonObject())
				.filter(object -> object.get("name").getAsString().equals("Gold Farm")).findFirst().orElseThrow();
		JsonObject status = entry.getAsJsonObject("status");
		assertEquals("broken", status.get("state").getAsString());
		assertEquals("out of bonemeal", status.get("note").getAsString());
		assertEquals("Steve", status.get("setByName").getAsString());
		assertEquals("2026-09-29T12:00:00Z", status.get("setAt").getAsString());

		WaypointStore reloaded = loaded();
		assertEquals(broken, reloaded.get("Gold Farm").orElseThrow().status());
		// Broken first, the most recently changed one first within a status.
		assertEquals(List.of("Storage", "Gold Farm", "Base"),
				reloaded.withStatus(null).stream().map(Waypoint::name).toList());
		assertEquals(List.of("Storage", "Gold Farm"),
				reloaded.withStatus(ProjectStatus.State.BROKEN).stream().map(Waypoint::name).toList());
		assertEquals(2, reloaded.countsByStatus().get(ProjectStatus.State.BROKEN));
		assertEquals(0, reloaded.countsByStatus().get(ProjectStatus.State.WIP));
		assertEquals(List.of("Gold Farm"), reloaded.search("bonemeal").stream().map(Waypoint::name).toList());

		reloaded.update(reloaded.get("Gold Farm").orElseThrow().withStatus(null));
		assertEquals(null, loaded().get("Gold Farm").orElseThrow().status());
	}

	@Test
	void renamesAndDescriptionsKeepTheStatus() {
		ProjectStatus wip = new ProjectStatus(ProjectStatus.State.WIP, null, STEVE, "Steve", WHEN);
		Waypoint farm = waypoint("Farm", TestCategories.FARMS).withStatus(wip);
		assertEquals(wip, farm.withName("Big Farm").withDescription("note").status());
	}

	@Test
	void unknownOrIncompleteStatusesFromAHandEditAreTidied() throws IOException {
		Files.createDirectories(file().getParent());
		Files.writeString(file(), """
				{"version": 2, "waypoints": [
				  {"name": "Typo", "category": "farms", "x": 0, "y": 64, "z": 0, "dimension": "minecraft:overworld",
				   "status": {"state": "brokn"}},
				  {"name": "Bare", "category": "farms", "x": 0, "y": 64, "z": 0, "dimension": "minecraft:overworld",
				   "status": {"state": "wip", "note": "  "}}
				]}
				""", StandardCharsets.UTF_8);
		WaypointStore store = loaded();
		assertEquals(null, store.get("Typo").orElseThrow().status(), "unknown state is dropped");
		ProjectStatus bare = store.get("Bare").orElseThrow().status();
		assertEquals(ProjectStatus.State.WIP, bare.state());
		assertEquals(null, bare.note());
		assertEquals("Unknown", bare.setByName());
		assertEquals(Waypoint.SERVER_UUID, bare.setByUuid());
	}
}
