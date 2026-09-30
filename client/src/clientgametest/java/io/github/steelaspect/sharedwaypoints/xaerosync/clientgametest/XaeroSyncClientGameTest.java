package io.github.steelaspect.sharedwaypoints.xaerosync.clientgametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.steelaspect.sharedwaypoints.protocol.SyncedWaypoint;
import io.github.steelaspect.sharedwaypoints.xaerosync.SharedWaypointsSync;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The real game with sharedwaypoints (server side of singleplayer), sharedwaypoints-client and Xaero's Minimap
 * 26.5.0: checks that shared waypoints end up in Xaero's "Shared" set, follow adds, edits and deletes, and are
 * reconciled on rejoin, without touching the player's own waypoints.
 */
public class XaeroSyncClientGameTest implements FabricClientGameTest {
	private static final String OVERWORLD = "minecraft:overworld";
	private static final String NETHER = "minecraft:the_nether";

	@Override
	public void runTest(ClientGameTestContext context) {
		// Small view and a frame cap while loading: CI renders in software on 2 vCPUs (see the server's client test).
		// No menu blur either: it is the costly part of the loading screen in software rendering.
		context.runOnClient(client -> {
			client.options.renderDistance().set(2);
			client.options.simulationDistance().set(5);
			client.options.menuBackgroundBlurriness().set(0);
			client.options.framerateLimit().set(10);
		});

		TestWorldSave save;
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.runOnClient(client -> client.options.framerateLimit().set(60));
			save = world.getWorldSave();
			world.getClientWorld().waitForChunksRender();
			context.waitFor(client -> SharedWaypointsSync.isActive());
			log("handshake done: the server syncs this client");
			// This player already has the client mod, so the first join has no "get the client mod" tip.
			if (context.computeOnClient(client -> chatText(client).contains("Want these waypoints in Xaero's Minimap"))) {
				throw new AssertionError("The client-mod tip was shown to a player who has the client mod");
			}

			// Added on the server: appear in Xaero, in the right dimension's Shared set.
			world.getServer().runCommand("cway add \"Main Storage\" storage 12 -60 -30");
			world.getServer().runCommand("cway add Farm farms 140 -60 210");
			world.getServer().runCommand("cway add Hub portals 10 70 -20 minecraft:the_nether");
			world.getServer().runCommand("cway add \"Gone While Away\" other 5 -60 5");
			waitForNames(context, OVERWORLD, List.of("Farm", "Gone While Away", "Main Storage"));
			waitForNames(context, NETHER, List.of("Hub"));
			log("adds synced");

			// Edits: rename (matched by id, no duplicate), recategorise (new colour), delete.
			world.getServer().runCommand("cway rename Farm \"Iron Farm\"");
			waitForNames(context, OVERWORLD, List.of("Gone While Away", "Iron Farm", "Main Storage"));
			world.getServer().runCommand("cway remove \"Main Storage\"");
			waitForNames(context, OVERWORLD, List.of("Gone While Away", "Iron Farm"));
			log("rename and delete synced");

			// A personal waypoint set is never touched: the mod only writes the "Shared" set.
			// (Xaero's default set exists but the mod never wrote to it, so it has no shared names.)
			requireFileContains("sets:", "Shared");

			// Leftovers the server doesn't know about (e.g. from an older session) are cleaned up on rejoin.
			context.runOnClient(client -> SharedWaypointsSync.writeSharedSetForTest(OVERWORLD, List.of(
					stale("Gone While Away"), stale("Iron Farm"), stale("Stale From Last Time"))));
			waitForNames(context, OVERWORLD, List.of("Gone While Away", "Iron Farm", "Stale From Last Time"));
		}

		// While the player is away, a waypoint is deleted on the server (by editing its data file).
		removeFromWaypointFile("Gone While Away");

		context.runOnClient(client -> client.options.framerateLimit().set(10));
		try (TestSingleplayerContext world = save.open()) {
			context.runOnClient(client -> client.options.framerateLimit().set(60));
			world.getClientWorld().waitForChunksRender();
			context.waitFor(client -> SharedWaypointsSync.isActive());
			// Rejoin: the full list replaces the Shared set, so both the stale entry and the one deleted while away go.
			waitForNames(context, OVERWORLD, List.of("Iron Farm"));
			waitForNames(context, NETHER, List.of("Hub"));
			log("rejoin reconciled");

			// Saved to Xaero's waypoint files on disk, not just in memory.
			requireFileContains("Iron Farm", "Shared");
			log("PASSED");
		}
	}

	/** Everything in the chat HUD so far, one message per line. */
	private static String chatText(net.minecraft.client.Minecraft client) {
		try {
			var field = net.minecraft.client.gui.components.ChatComponent.class.getDeclaredField("allMessages");
			field.setAccessible(true);
			@SuppressWarnings("unchecked")
			var messages = (List<net.minecraft.client.GuiMessage>) field.get(client.gui.getChat());
			return String.join("\n", messages.stream().map(message -> message.content().getString()).toList());
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("can't read the chat HUD", e);
		}
	}

	private static SyncedWaypoint stale(String name) {
		return new SyncedWaypoint(UUID.randomUUID(), name, name.substring(0, 1), "other", "Other", 15, 1, 64, 1, OVERWORLD, null);
	}

	private static void waitForNames(ClientGameTestContext context, String dimension, List<String> expected) {
		try {
			context.waitFor(client -> SharedWaypointsSync.isUpToDate()
					&& SharedWaypointsSync.sharedWaypointNames(dimension).map(names -> names.equals(expected)).orElse(false));
		} catch (AssertionError e) {
			Optional<List<String>> actual = context.computeOnClient(client -> SharedWaypointsSync.sharedWaypointNames(dimension));
			throw new AssertionError("Shared set in " + dimension + ": expected " + expected + " but was " + actual, e);
		}
	}

	private static void removeFromWaypointFile(String name) {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("sharedwaypoints/waypoints.json");
		try {
			JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			JsonArray kept = new JsonArray();
			for (JsonElement entry : json.getAsJsonArray("waypoints")) {
				if (!entry.getAsJsonObject().get("name").getAsString().equals(name)) {
					kept.add(entry);
				}
			}
			json.add("waypoints", kept);
			Files.writeString(file, json.toString());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Some file under Xaero's minimap data folder contains all of {@code texts}. */
	private static void requireFileContains(String... texts) {
		Path xaero = FabricLoader.getInstance().getGameDir().resolve("xaero");
		try (Stream<Path> files = Files.walk(xaero)) {
			boolean found = files.filter(Files::isRegularFile).anyMatch(file -> {
				try {
					String content = Files.readString(file);
					return Stream.of(texts).allMatch(content::contains);
				} catch (IOException e) {
					return false;
				}
			});
			if (!found) {
				throw new AssertionError("No Xaero file under " + xaero + " contains " + List.of(texts));
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void log(String message) {
		System.out.println("[SharedWaypoints Xaero sync test] " + message);
	}
}
