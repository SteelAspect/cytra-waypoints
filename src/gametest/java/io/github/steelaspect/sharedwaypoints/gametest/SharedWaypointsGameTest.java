package io.github.steelaspect.sharedwaypoints.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import io.github.steelaspect.sharedwaypoints.ModContext;
import io.github.steelaspect.sharedwaypoints.SharedWaypoints;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;

/**
 * End-to-end test on a real (headless) 1.21.11 server: runs the /waypoints commands as two ordinary players and
 * a moderator, and checks chat output, click events, tab completion, permissions, navigation, favourites and the
 * JSON files.
 */
public class SharedWaypointsGameTest {
	/** Collects everything a command sends back to its source. */
	private static final class Recorder implements CommandSource {
		final List<Component> messages = new ArrayList<>();

		@Override
		public void sendSystemMessage(Component message) {
			messages.add(message);
		}

		@Override
		public boolean acceptsSuccess() {
			return true;
		}

		@Override
		public boolean acceptsFailure() {
			return true;
		}

		@Override
		public boolean shouldInformAdmins() {
			return false;
		}

		String text() {
			return String.join("\n", messages.stream().map(Component::getString).toList());
		}

		/** Clears the log and returns what was in it. */
		String take() {
			String text = text();
			messages.clear();
			return text;
		}
	}

	private record Source(CommandSourceStack stack, Recorder out) {
	}

	@GameTest
	@SuppressWarnings("removal") // makeMockServerPlayerInLevel is the only ServerPlayer mock in the 1.21.11 GameTest API
	public void commandsWorkEndToEnd(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
		ModContext mod = SharedWaypoints.context();

		ServerPlayer aliceEntity = helper.makeMockServerPlayerInLevel();
		ServerPlayer bobEntity = helper.makeMockServerPlayerInLevel();
		helper.assertFalse(aliceEntity.getUUID().equals(bobEntity.getUUID()), "mock players must differ");
		aliceEntity.teleportTo(0.5, 64, 0.5); // a known spot, so distances are predictable
		// Two ordinary players (no op level) and a level-2 moderator.
		Source alice = source(aliceEntity.createCommandSourceStack().withPermission(LevelBasedPermissionSet.ALL));
		Source bob = source(bobEntity.createCommandSourceStack().withPermission(LevelBasedPermissionSet.ALL));
		Source moderator = source(server.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER));

		// --- add
		helper.assertValueEqual(run(helper, dispatcher, alice, "waypoints add \"Main Storage\" storage"), 1, "add here");
		helper.assertTrue(alice.out.take().contains("Added waypoint Main Storage"), "add confirmation");
		run(helper, dispatcher, alice, "waypoints add Hub portals 10 70 -20 minecraft:the_nether");
		run(helper, dispatcher, bob, "waypoints add Iron-Farm farms 100 64 200");
		expectError(helper, dispatcher, alice, "waypoints add \"main storage\" bases", "already exists");
		expectError(helper, dispatcher, alice, "waypoints add Thing shops", "Unknown category");
		expectError(helper, dispatcher, moderator, "waypoints add Console bases", "Give coordinates");
		helper.assertTrue(Files.exists(configDir().resolve("config.json")), "config.json created with defaults");

		// --- list all: grouped by category, each line in the required format with working buttons
		alice.out.take();
		helper.assertValueEqual(run(helper, dispatcher, alice, "waypoints"), 3, "list all");
		String listing = alice.out.text();
		helper.assertTrue(listing.contains("[Portals] Hub — 10 70 -20 (the_nether) [Add to Xaero] [Copy coords] [Go]"), listing);
		helper.assertTrue(listing.contains("[Farms] Iron-Farm — 100 64 200 (overworld) [Add to Xaero] [Copy coords] [Go]"), listing);
		helper.assertTrue(listing.indexOf("[Storage]") < listing.indexOf("[Farms]")
				&& listing.indexOf("[Farms]") < listing.indexOf("[Portals]"), "grouped in category order:\n" + listing);
		Component hubLine = alice.out.messages.stream()
				.filter(line -> line.getString().startsWith("[Portals] Hub")).findFirst().orElseThrow();
		helper.assertValueEqual(clickOf(hubLine, "[Add to Xaero]"),
				Optional.of(new ClickEvent.RunCommand("/waypoints xaero Hub")), "Add to Xaero click");
		helper.assertValueEqual(clickOf(hubLine, "[Copy coords]"),
				Optional.of(new ClickEvent.CopyToClipboard("10 70 -20")), "Copy coords click");
		helper.assertValueEqual(clickOf(hubLine, "[Go]"),
				Optional.of(new ClickEvent.RunCommand("/waypoints go Hub")), "Go click");
		helper.assertTrue(!listing.contains("xaero-waypoint:"), "listing must not trigger Xaero's parser");
		alice.out.take();

		// --- one category, categories, info, xaero
		helper.assertValueEqual(run(helper, dispatcher, alice, "waypoints storage"), 1, "list storage");
		helper.assertTrue(alice.out.take().contains("[Storage] Main Storage"), "storage listing");
		expectError(helper, dispatcher, alice, "waypoints shops", "Unknown category");
		run(helper, dispatcher, alice, "waypoints categories");
		String categories = alice.out.take();
		helper.assertTrue(categories.contains("[Portals] portals — 1 waypoint · Xaero colour: Purple (13)"), categories);
		run(helper, dispatcher, alice, "waypoints info Hub");
		String hubInfo = alice.out.take();
		helper.assertTrue(hubInfo.contains("Overworld side: 80 70 -160"), "portal conversion in info:\n" + hubInfo);
		helper.assertTrue(hubInfo.contains("via Nether portal"), "distance through the portal:\n" + hubInfo);
		helper.assertTrue(!hubInfo.contains("[Teleport]"), "no teleport button for ordinary players");
		helper.assertValueEqual(run(helper, dispatcher, alice, "waypoints xaero \"Main Storage\""), 1, "xaero share");
		expectError(helper, dispatcher, moderator, "waypoints xaero Hub", "player");

		// --- around you (alice stands at 0 64 0)
		run(helper, dispatcher, alice, "waypoints nearest");
		helper.assertTrue(alice.out.take().contains("Main Storage"), "nearest is the one at your feet");
		run(helper, dispatcher, alice, "waypoints nearest farms");
		String nearestFarm = alice.out.take();
		helper.assertTrue(nearestFarm.contains("224m SE") && nearestFarm.contains("Iron-Farm"), nearestFarm);
		helper.assertValueEqual(run(helper, dispatcher, alice, "waypoints near 150"), 1, "only Main Storage within 150m");
		helper.assertValueEqual(run(helper, dispatcher, alice, "waypoints near"), 3, "Hub counts via its portal spot");
		helper.assertTrue(alice.out.take().contains("⟳"), "portal marker in near list");

		// --- search, descriptions
		helper.assertValueEqual(run(helper, dispatcher, alice, "waypoints search iron"), 1, "search by name");
		run(helper, dispatcher, alice, "waypoints describe \"Main Storage\" Sorted chests, bring shulkers");
		expectError(helper, dispatcher, bob, "waypoints describe \"Main Storage\" mine now", "only edit waypoints you created");
		helper.assertValueEqual(run(helper, dispatcher, bob, "waypoints search shulkers"), 1, "search by description");
		alice.out.take();
		run(helper, dispatcher, alice, "waypoints info \"Main Storage\"");
		helper.assertTrue(alice.out.take().contains("“Sorted chests, bring shulkers”"), "description in info");

		// --- favourites (by id, so they survive renames)
		run(helper, dispatcher, alice, "waypoints favorite Hub");
		helper.assertTrue(alice.out.take().contains("added to your favourites"), "favourite added");
		helper.assertValueEqual(run(helper, dispatcher, alice, "waypoints favorites"), 1, "one favourite");
		run(helper, dispatcher, alice, "waypoints portals");
		helper.assertTrue(alice.out.take().contains("Hub ★"), "star next to favourites");
		run(helper, dispatcher, bob, "waypoints portals");
		helper.assertTrue(!bob.out.take().contains("★"), "favourites are personal");

		// --- paging
		run(helper, dispatcher, moderator, "waypoints add Spawn other 300 64 300");
		int pageSize = mod.config().pageSize;
		mod.config().pageSize = 3;
		helper.assertValueEqual(run(helper, dispatcher, alice, "waypoints"), 3, "page 1 holds 3");
		helper.assertTrue(alice.out.take().contains("Page 1/2"), "page footer");
		helper.assertValueEqual(run(helper, dispatcher, alice, "waypoints page 2"), 1, "page 2 holds the rest");
		mod.config().pageSize = pageSize;

		// --- navigation
		run(helper, dispatcher, alice, "waypoints go Iron-Farm");
		Waypoint ironFarm = mod.waypoints().get("Iron-Farm").orElseThrow();
		helper.assertValueEqual(mod.navigation().destinationOf(aliceEntity.getUUID()), Optional.of(ironFarm.id()), "navigating");
		helper.assertValueEqual(run(helper, dispatcher, alice, "waypoints stop"), 1, "stop");
		helper.assertTrue(mod.navigation().destinationOf(aliceEntity.getUUID()).isEmpty(), "stopped");
		run(helper, dispatcher, alice, "waypoints go \"Main Storage\"");
		helper.assertTrue(mod.navigation().destinationOf(aliceEntity.getUUID()).isEmpty(), "already there -> arrived at once");
		run(helper, dispatcher, alice, "waypoints go Iron-Farm");

		// --- tab completion
		helper.assertTrue(suggestions(dispatcher, alice, "waypoints info ").contains("\"Main Storage\""),
				"name suggestions are quoted when needed");
		helper.assertTrue(suggestions(dispatcher, alice, "waypoints add X ").containsAll(
				List.of("storage", "farms", "bases", "portals", "other")), "category suggestions");
		helper.assertValueEqual(suggestions(dispatcher, bob, "waypoints remove "), List.of("Iron-Farm"),
				"remove only suggests your own waypoints");

		// --- permissions: players can only change their own; op level 2 can change any, and teleport
		expectError(helper, dispatcher, bob, "waypoints remove \"Main Storage\"", "only remove waypoints you created");
		expectError(helper, dispatcher, bob, "waypoints rename Hub Nope", "only edit waypoints you created");
		expectError(helper, dispatcher, alice, "waypoints tp Hub", "");
		Source aliceOp = source(aliceEntity.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER));
		run(helper, dispatcher, aliceOp, "waypoints info Spawn");
		helper.assertTrue(aliceOp.out.take().contains("[Teleport]"), "teleport button for ops");
		run(helper, dispatcher, aliceOp, "waypoints tp Spawn");
		helper.assertTrue(Math.abs(aliceEntity.getX() - 300.5) < 0.01 && Math.abs(aliceEntity.getZ() - 300.5) < 0.01,
				"teleported to Spawn, now at " + aliceEntity.position());

		run(helper, dispatcher, alice, "waypoints rename \"Main Storage\" \"Sorting Room\"");
		run(helper, dispatcher, bob, "waypoints remove Iron-Farm");
		run(helper, dispatcher, moderator, "waypoints remove Hub");
		run(helper, dispatcher, moderator, "waypoints remove Spawn");
		expectError(helper, dispatcher, alice, "waypoints info Hub", "No waypoint named");
		helper.assertValueEqual(mod.favorites().of(aliceEntity.getUUID()), java.util.Set.<UUID>of(), "removed waypoint left favourites");
		for (int i = 0; i < 5; i++) {
			mod.navigation().tick(server);
		}
		helper.assertTrue(mod.navigation().destinationOf(aliceEntity.getUUID()).isEmpty(),
				"navigation ends when its waypoint is removed");

		// --- the JSON file follows every change
		JsonArray saved = readSavedWaypoints(helper);
		helper.assertValueEqual(saved.size(), 1, "one waypoint left on disk");
		JsonObject entry = saved.get(0).getAsJsonObject();
		helper.assertValueEqual(entry.get("name").getAsString(), "Sorting Room", "renamed on disk");
		helper.assertValueEqual(entry.get("category").getAsString(), "storage", "category on disk");
		helper.assertValueEqual(entry.get("dimension").getAsString(), "minecraft:overworld", "dimension on disk");
		helper.assertValueEqual(entry.get("description").getAsString(), "Sorted chests, bring shulkers", "description on disk");
		helper.assertValueEqual(entry.get("creatorUuid").getAsString(), aliceEntity.getUUID().toString(), "creator");
		helper.assertTrue(entry.has("id") && entry.has("created"), "id and timestamp on disk");

		helper.succeed();
	}

	// ------------------------------------------------------------------ helpers

	private static Source source(CommandSourceStack stack) {
		Recorder recorder = new Recorder();
		return new Source(stack.withSource(recorder), recorder);
	}

	private static int run(GameTestHelper helper, CommandDispatcher<CommandSourceStack> dispatcher, Source source,
			String command) {
		try {
			return dispatcher.execute(command, source.stack);
		} catch (CommandSyntaxException e) {
			helper.fail("/" + command + " failed: " + e.getMessage());
			return 0;
		}
	}

	private static void expectError(GameTestHelper helper, CommandDispatcher<CommandSourceStack> dispatcher,
			Source source, String command, String expected) {
		try {
			dispatcher.execute(command, source.stack);
			helper.fail("/" + command + " should have failed with \"" + expected + "\"");
		} catch (CommandSyntaxException e) {
			helper.assertTrue(e.getMessage().contains(expected),
					"/" + command + ": expected \"" + expected + "\" but got \"" + e.getMessage() + "\"");
		}
	}

	private static List<String> suggestions(CommandDispatcher<CommandSourceStack> dispatcher, Source source,
			String input) {
		return dispatcher.getCompletionSuggestions(dispatcher.parse(input, source.stack)).join()
				.getList().stream().map(Suggestion::getText).toList();
	}

	/** Click event of the part of a chat line whose text is exactly {@code label}. */
	private static Optional<ClickEvent> clickOf(Component line, String label) {
		return line.toFlatList().stream()
				.filter(part -> part.getString().equals(label))
				.map(part -> part.getStyle().getClickEvent())
				.findFirst();
	}

	private static Path configDir() {
		return FabricLoader.getInstance().getConfigDir().resolve("sharedwaypoints");
	}

	private static JsonArray readSavedWaypoints(GameTestHelper helper) {
		Path file = configDir().resolve("waypoints.json");
		try {
			return JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonArray("waypoints");
		} catch (IOException e) {
			helper.fail("could not read " + file + ": " + e);
			return new JsonArray();
		}
	}
}
