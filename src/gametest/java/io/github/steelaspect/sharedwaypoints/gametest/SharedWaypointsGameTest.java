package io.github.steelaspect.sharedwaypoints.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
 * a moderator, and checks chat output, click events, tab completion, permissions and the JSON file.
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
	}

	private record Source(CommandSourceStack stack, Recorder out) {
	}

	@GameTest
	@SuppressWarnings("removal") // makeMockServerPlayerInLevel is the only ServerPlayer mock in the 1.21.11 GameTest API
	public void commandsWorkEndToEnd(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();

		ServerPlayer aliceEntity = helper.makeMockServerPlayerInLevel();
		ServerPlayer bobEntity = helper.makeMockServerPlayerInLevel();
		helper.assertFalse(aliceEntity.getUUID().equals(bobEntity.getUUID()), "mock players must differ");
		// Two ordinary players (no op level) and a level-2 moderator.
		Source alice = source(aliceEntity.createCommandSourceStack().withPermission(LevelBasedPermissionSet.ALL));
		Source bob = source(bobEntity.createCommandSourceStack().withPermission(LevelBasedPermissionSet.ALL));
		Source moderator = source(server.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER));

		// --- add
		int added = run(helper, dispatcher, alice, "waypoints add \"Main Storage\" storage");
		helper.assertValueEqual(added, 1, "add at own position");
		helper.assertTrue(alice.out.text().contains("Added waypoint Main Storage"), alice.out.text());
		run(helper, dispatcher, alice, "waypoints add Hub portals 10 70 -20 minecraft:the_nether");
		run(helper, dispatcher, bob, "waypoints add Iron-Farm farms 100 64 200");
		expectError(helper, dispatcher, alice, "waypoints add \"main storage\" bases", "already exists");
		expectError(helper, dispatcher, alice, "waypoints add Thing shops", "Unknown category");
		expectError(helper, dispatcher, moderator, "waypoints add Console bases", "Give coordinates");

		// --- list all: grouped by category, each line in the required format with working buttons
		alice.out.messages.clear();
		helper.assertValueEqual(run(helper, dispatcher, alice, "waypoints"), 3, "list all");
		String listing = alice.out.text();
		helper.assertTrue(listing.contains("[Portals] Hub — 10 70 -20 (the_nether) [Add to Xaero] [Copy coords]"), listing);
		helper.assertTrue(listing.contains("[Farms] Iron-Farm — 100 64 200 (overworld) [Add to Xaero] [Copy coords]"), listing);
		helper.assertTrue(listing.indexOf("[Storage]") < listing.indexOf("[Farms]")
				&& listing.indexOf("[Farms]") < listing.indexOf("[Portals]"), "grouped in category order:\n" + listing);
		Component hubLine = alice.out.messages.stream()
				.filter(line -> line.getString().startsWith("[Portals] Hub")).findFirst().orElseThrow();
		helper.assertValueEqual(clickOf(hubLine, "[Add to Xaero]"),
				Optional.of(new ClickEvent.RunCommand("/waypoints xaero Hub")), "Add to Xaero click");
		helper.assertValueEqual(clickOf(hubLine, "[Copy coords]"),
				Optional.of(new ClickEvent.CopyToClipboard("10 70 -20")), "Copy coords click");
		helper.assertTrue(!listing.contains("xaero-waypoint:"), "listing must not trigger Xaero's parser");

		// --- one category, categories, info, xaero
		alice.out.messages.clear();
		helper.assertValueEqual(run(helper, dispatcher, alice, "waypoints storage"), 1, "list storage");
		helper.assertTrue(alice.out.text().contains("[Storage] Main Storage"), alice.out.text());
		expectError(helper, dispatcher, alice, "waypoints shops", "Unknown category");
		alice.out.messages.clear();
		run(helper, dispatcher, alice, "waypoints categories");
		helper.assertTrue(alice.out.text().contains("[Portals] portals — 1 waypoint · Xaero colour: Purple (13)"),
				alice.out.text());
		alice.out.messages.clear();
		run(helper, dispatcher, alice, "waypoints info Iron-Farm");
		helper.assertTrue(alice.out.text().contains("Created by: " + bobEntity.getGameProfile().name()), alice.out.text());
		helper.assertValueEqual(run(helper, dispatcher, alice, "waypoints xaero \"Main Storage\""), 1, "xaero share");
		expectError(helper, dispatcher, moderator, "waypoints xaero Hub", "player");

		// --- tab completion
		helper.assertTrue(suggestions(dispatcher, alice, "waypoints info ").contains("\"Main Storage\""),
				"name suggestions are quoted when needed");
		helper.assertTrue(suggestions(dispatcher, alice, "waypoints add X ").containsAll(
				List.of("storage", "farms", "bases", "portals", "other")), "category suggestions");
		helper.assertValueEqual(suggestions(dispatcher, bob, "waypoints remove "), List.of("Iron-Farm"),
				"remove only suggests your own waypoints");

		// --- permissions: players can only change their own; op level 2 can change any
		expectError(helper, dispatcher, bob, "waypoints remove \"Main Storage\"", "only remove waypoints you created");
		expectError(helper, dispatcher, bob, "waypoints rename Hub Nope", "only rename waypoints you created");
		run(helper, dispatcher, alice, "waypoints rename \"Main Storage\" \"Sorting Room\"");
		run(helper, dispatcher, bob, "waypoints remove Iron-Farm");
		run(helper, dispatcher, moderator, "waypoints remove Hub");
		expectError(helper, dispatcher, alice, "waypoints info Hub", "No waypoint named");

		// --- the JSON file follows every change
		JsonArray saved = readSavedWaypoints(helper);
		helper.assertValueEqual(saved.size(), 1, "one waypoint left on disk");
		JsonObject entry = saved.get(0).getAsJsonObject();
		helper.assertValueEqual(entry.get("name").getAsString(), "Sorting Room", "renamed on disk");
		helper.assertValueEqual(entry.get("category").getAsString(), "storage", "category on disk");
		helper.assertValueEqual(entry.get("dimension").getAsString(), "minecraft:overworld", "dimension on disk");
		helper.assertValueEqual(entry.get("creatorUuid").getAsString(), aliceEntity.getUUID().toString(), "creator");
		helper.assertTrue(entry.has("created"), "timestamp on disk");

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

	private static JsonArray readSavedWaypoints(GameTestHelper helper) {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("sharedwaypoints").resolve("waypoints.json");
		try {
			return JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonArray("waypoints");
		} catch (IOException e) {
			helper.fail("could not read " + file + ": " + e);
			return new JsonArray();
		}
	}
}
