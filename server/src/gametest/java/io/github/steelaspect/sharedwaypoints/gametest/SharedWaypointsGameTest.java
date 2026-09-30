package io.github.steelaspect.sharedwaypoints.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import io.github.steelaspect.sharedwaypoints.ModContext;
import io.github.steelaspect.sharedwaypoints.SharedWaypoints;
import io.github.steelaspect.sharedwaypoints.network.ActionPayload;
import io.github.steelaspect.sharedwaypoints.network.MenuNetworking;
import io.github.steelaspect.sharedwaypoints.network.ResultPayload;
import io.github.steelaspect.sharedwaypoints.network.SyncPayload;
import io.github.steelaspect.sharedwaypoints.protocol.DeletePayload;
import io.github.steelaspect.sharedwaypoints.protocol.FullSyncPayload;
import io.github.steelaspect.sharedwaypoints.protocol.HelloPayload;
import io.github.steelaspect.sharedwaypoints.protocol.SyncProtocol;
import io.github.steelaspect.sharedwaypoints.protocol.SyncedWaypoint;
import io.github.steelaspect.sharedwaypoints.protocol.UpsertPayload;
import io.github.steelaspect.sharedwaypoints.protocol.WelcomePayload;
import io.github.steelaspect.sharedwaypoints.waypoint.CategoryRegistry;
import io.github.steelaspect.sharedwaypoints.waypoint.Route;
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
 * End-to-end test on a real (headless) 1.21.11 server: runs the /cway commands as two ordinary players and
 * a moderator, and checks chat output, click events, tab completion, permissions, navigation, favourites, routes,
 * the client menu's network handler and the JSON files.
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
		helper.assertValueEqual(run(helper, dispatcher, alice, "cway add \"Main Storage\" storage"), 1, "add here");
		helper.assertTrue(alice.out.take().contains("Added waypoint Main Storage"), "add confirmation");
		run(helper, dispatcher, alice, "cway add Hub portals 10 70 -20 minecraft:the_nether");
		run(helper, dispatcher, bob, "cway add Iron-Farm farms 100 64 200");
		expectError(helper, dispatcher, alice, "cway add \"main storage\" bases", "already exists");
		expectError(helper, dispatcher, alice, "cway add Thing shops", "Unknown category");
		expectError(helper, dispatcher, moderator, "cway add Console bases", "Give coordinates");
		helper.assertTrue(Files.exists(configDir().resolve("config.json")), "config.json created with defaults");

		// --- list all: grouped by category, each line in the required format with working buttons
		alice.out.take();
		helper.assertValueEqual(run(helper, dispatcher, alice, "cway"), 3, "list all");
		String listing = alice.out.text();
		helper.assertTrue(listing.contains("[Portals] Hub — 10 70 -20 (the_nether) [Add to Xaero] [Copy coords] [Go]"), listing);
		helper.assertTrue(listing.contains("[Farms] Iron-Farm — 100 64 200 (overworld) [Add to Xaero] [Copy coords] [Go]"), listing);
		helper.assertTrue(listing.indexOf("[Storage]") < listing.indexOf("[Farms]")
				&& listing.indexOf("[Farms]") < listing.indexOf("[Portals]"), "grouped in category order:\n" + listing);
		Component hubLine = alice.out.messages.stream()
				.filter(line -> line.getString().startsWith("[Portals] Hub")).findFirst().orElseThrow();
		helper.assertValueEqual(clickOf(hubLine, "[Add to Xaero]"),
				Optional.of(new ClickEvent.RunCommand("/cway xaero Hub")), "Add to Xaero click");
		helper.assertValueEqual(clickOf(hubLine, "[Copy coords]"),
				Optional.of(new ClickEvent.CopyToClipboard("10 70 -20")), "Copy coords click");
		helper.assertValueEqual(clickOf(hubLine, "[Go]"),
				Optional.of(new ClickEvent.RunCommand("/cway go Hub")), "Go click");
		helper.assertTrue(!listing.contains("xaero-waypoint:"), "listing must not trigger Xaero's parser");
		alice.out.take();

		// --- one category, categories, info, xaero
		helper.assertValueEqual(run(helper, dispatcher, alice, "cway storage"), 1, "list storage");
		helper.assertTrue(alice.out.take().contains("[Storage] Main Storage"), "storage listing");
		expectError(helper, dispatcher, alice, "cway shops", "Unknown category");
		run(helper, dispatcher, alice, "cway categories");
		String categories = alice.out.take();
		helper.assertTrue(categories.contains("[Portals] portals — 1 waypoint · Xaero colour: Purple (13)"), categories);
		run(helper, dispatcher, alice, "cway info Hub");
		String hubInfo = alice.out.take();
		helper.assertTrue(hubInfo.contains("Overworld side: 80 70 -160"), "portal conversion in info:\n" + hubInfo);
		helper.assertTrue(hubInfo.contains("via Nether portal"), "distance through the portal:\n" + hubInfo);
		helper.assertTrue(!hubInfo.contains("[Teleport]"), "no teleport button for ordinary players");
		helper.assertValueEqual(run(helper, dispatcher, alice, "cway xaero \"Main Storage\""), 1, "xaero share");
		expectError(helper, dispatcher, moderator, "cway xaero Hub", "player");

		// --- around you (alice stands at 0 64 0)
		run(helper, dispatcher, alice, "cway nearest");
		helper.assertTrue(alice.out.take().contains("Main Storage"), "nearest is the one at your feet");
		run(helper, dispatcher, alice, "cway nearest farms");
		String nearestFarm = alice.out.take();
		helper.assertTrue(nearestFarm.contains("224m SE") && nearestFarm.contains("Iron-Farm"), nearestFarm);
		helper.assertValueEqual(run(helper, dispatcher, alice, "cway near 150"), 1, "only Main Storage within 150m");
		helper.assertValueEqual(run(helper, dispatcher, alice, "cway near"), 3, "Hub counts via its portal spot");
		helper.assertTrue(alice.out.take().contains("⟳"), "portal marker in near list");

		// --- search, descriptions
		helper.assertValueEqual(run(helper, dispatcher, alice, "cway search iron"), 1, "search by name");
		run(helper, dispatcher, alice, "cway describe \"Main Storage\" Sorted chests, bring shulkers");
		expectError(helper, dispatcher, bob, "cway describe \"Main Storage\" mine now", "only edit waypoints you created");
		helper.assertValueEqual(run(helper, dispatcher, bob, "cway search shulkers"), 1, "search by description");
		alice.out.take();
		run(helper, dispatcher, alice, "cway info \"Main Storage\"");
		helper.assertTrue(alice.out.take().contains("“Sorted chests, bring shulkers”"), "description in info");

		// --- favourites (by id, so they survive renames)
		run(helper, dispatcher, alice, "cway favorite Hub");
		helper.assertTrue(alice.out.take().contains("added to your favourites"), "favourite added");
		helper.assertValueEqual(run(helper, dispatcher, alice, "cway favorites"), 1, "one favourite");
		run(helper, dispatcher, alice, "cway portals");
		helper.assertTrue(alice.out.take().contains("Hub ★"), "star next to favourites");
		run(helper, dispatcher, bob, "cway portals");
		helper.assertTrue(!bob.out.take().contains("★"), "favourites are personal");

		// --- paging
		run(helper, dispatcher, moderator, "cway add Spawn other 300 64 300");
		int pageSize = mod.config().pageSize;
		mod.config().pageSize = 3;
		helper.assertValueEqual(run(helper, dispatcher, alice, "cway"), 3, "page 1 holds 3");
		helper.assertTrue(alice.out.take().contains("Page 1/2"), "page footer");
		helper.assertValueEqual(run(helper, dispatcher, alice, "cway page 2"), 1, "page 2 holds the rest");
		mod.config().pageSize = pageSize;

		// --- navigation
		run(helper, dispatcher, alice, "cway go Iron-Farm");
		Waypoint ironFarm = mod.waypoints().get("Iron-Farm").orElseThrow();
		helper.assertValueEqual(mod.navigation().destinationOf(aliceEntity.getUUID()), Optional.of(ironFarm.id()), "navigating");
		helper.assertValueEqual(run(helper, dispatcher, alice, "cway stop"), 1, "stop");
		helper.assertTrue(mod.navigation().destinationOf(aliceEntity.getUUID()).isEmpty(), "stopped");
		run(helper, dispatcher, alice, "cway go \"Main Storage\"");
		helper.assertTrue(mod.navigation().destinationOf(aliceEntity.getUUID()).isEmpty(), "already there -> arrived at once");
		run(helper, dispatcher, alice, "cway go Iron-Farm");

		// --- web map: squaremap runs on this test server, so its layer must hold our markers
		Waypoint hub = mod.waypoints().get("Hub").orElseThrow();
		if (FabricLoader.getInstance().isModLoaded("squaremap")) {
			helper.assertTrue(SquaremapProbe.layerRegistered(), "our squaremap layer is registered");
			helper.assertValueEqual(SquaremapProbe.layerLabel(), "Shared Waypoints", "layer name");
			helper.assertTrue(SquaremapProbe.hasMarker("wp-" + ironFarm.id()), "Iron-Farm on the overworld map");
			helper.assertTrue(!SquaremapProbe.hasMarker("wp-" + hub.id()), "Hub is in the Nether, not on the overworld map");
			String popup = SquaremapProbe.clickTooltip("wp-" + ironFarm.id());
			helper.assertTrue(popup != null && popup.contains("<b>Iron-Farm</b>"), "click popup: " + popup);
		}

		// --- tab completion
		helper.assertTrue(suggestions(dispatcher, alice, "cway info ").contains("\"Main Storage\""),
				"name suggestions are quoted when needed");
		helper.assertTrue(suggestions(dispatcher, alice, "cway add X ").containsAll(
				List.of("storage", "farms", "bases", "portals", "other")), "category suggestions");
		helper.assertValueEqual(suggestions(dispatcher, bob, "cway remove "), List.of("Iron-Farm"),
				"remove only suggests your own waypoints");

		// --- permissions: players can only change their own; op level 2 can change any, and teleport
		expectError(helper, dispatcher, bob, "cway remove \"Main Storage\"", "only remove waypoints you created");
		expectError(helper, dispatcher, bob, "cway rename Hub Nope", "only edit waypoints you created");
		expectError(helper, dispatcher, alice, "cway tp Hub", "");
		Source aliceOp = source(aliceEntity.createCommandSourceStack().withPermission(LevelBasedPermissionSet.GAMEMASTER));
		run(helper, dispatcher, aliceOp, "cway info Spawn");
		helper.assertTrue(aliceOp.out.take().contains("[Teleport]"), "teleport button for ops");
		run(helper, dispatcher, aliceOp, "cway tp Spawn");
		helper.assertTrue(Math.abs(aliceEntity.getX() - 300.5) < 0.01 && Math.abs(aliceEntity.getZ() - 300.5) < 0.01,
				"teleported to Spawn, now at " + aliceEntity.position());

		run(helper, dispatcher, alice, "cway rename \"Main Storage\" \"Sorting Room\"");
		run(helper, dispatcher, bob, "cway remove Iron-Farm");
		run(helper, dispatcher, moderator, "cway remove Hub");
		run(helper, dispatcher, moderator, "cway remove Spawn");
		expectError(helper, dispatcher, alice, "cway info Hub", "No waypoint named");
		helper.assertValueEqual(mod.favorites().of(aliceEntity.getUUID()), java.util.Set.<UUID>of(), "removed waypoint left favourites");
		for (int i = 0; i < 5; i++) {
			mod.navigation().tick(server);
		}
		helper.assertTrue(mod.navigation().destinationOf(aliceEntity.getUUID()).isEmpty(),
				"navigation ends when its waypoint is removed");
		if (FabricLoader.getInstance().isModLoaded("squaremap")) {
			helper.assertTrue(!SquaremapProbe.hasMarker("wp-" + ironFarm.id()), "removed from the map too");
		}

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

		// --- custom categories from config.json, picked up by /cway reload
		Path config = configDir().resolve("config.json");
		try {
			JsonObject json = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
			JsonObject shops = new JsonObject();
			shops.addProperty("id", "shops");
			shops.addProperty("name", "Shops");
			shops.addProperty("color", "yellow");
			json.getAsJsonArray("categories").add(shops);
			Files.writeString(config, json.toString());
		} catch (IOException e) {
			helper.fail("could not edit " + config + ": " + e);
		}
		expectError(helper, dispatcher, alice, "cway reload", ""); // ops only
		run(helper, dispatcher, moderator, "cway reload");
		helper.assertTrue(moderator.out.take().contains("Reloaded SharedWaypoints: 1 waypoints, 0 routes, 6 categories"), "reload summary");
		run(helper, dispatcher, moderator, "cway add Market shops 5 64 5");
		helper.assertValueEqual(run(helper, dispatcher, alice, "cway shops"), 1, "list the new category");
		String shopsList = alice.out.take();
		helper.assertTrue(shopsList.contains("[Shops] Market — 5 64 5"), shopsList);
		helper.assertTrue(suggestions(dispatcher, alice, "cway add X ").contains("shops"), "new category suggested");
		run(helper, dispatcher, alice, "cway categories");
		helper.assertTrue(alice.out.take().contains("[Shops] shops — 1 waypoint · Xaero colour: Yellow (14)"), "category summary");

		// --- the optional client menu's network handler (driven directly, no real client needed)
		MenuNetworking menus = mod.menus();
		SyncPayload aliceView = menus.snapshot(aliceEntity);
		helper.assertValueEqual(aliceView.categories().stream().map(SyncPayload.CategoryData::id).toList(),
				List.of("storage", "farms", "bases", "portals", "other", "shops"), "menu categories in config order");
		SyncPayload.WaypointData sorting = aliceView.waypoints().stream()
				.filter(data -> data.name().equals("Sorting Room")).findFirst().orElseThrow();
		helper.assertTrue(sorting.canEdit() && sorting.canRemove(), "creator may edit/remove own waypoint");
		helper.assertTrue(aliceView.canAdd() && !aliceView.canTeleport(), "ordinary player: can add, no teleport");
		SyncPayload.WaypointData sortingForBob = menus.snapshot(bobEntity).waypoints().stream()
				.filter(data -> data.name().equals("Sorting Room")).findFirst().orElseThrow();
		helper.assertTrue(!sortingForBob.canEdit() && !sortingForBob.canRemove(), "others may not edit/remove it");

		helper.assertTrue(menus.handle(aliceEntity, ActionPayload.of(ActionPayload.Action.SYNC)) == null, "sync has no reply");
		ResultPayload added = menus.handle(aliceEntity, ActionPayload.of(ActionPayload.Action.ADD,
				"Menu \"Spot\" x", "farms", "7", "65", "-8", "minecraft:overworld"));
		helper.assertTrue(added.success() && added.message().contains("Added waypoint"), "menu add: " + added.message());
		Waypoint menuSpot = mod.waypoints().get("Menu \"Spot\" x").orElseThrow();
		helper.assertValueEqual(menuSpot.coordinates(), "7 65 -8", "menu add coordinates");
		String spotId = menuSpot.id().toString();

		ResultPayload injected = menus.handle(aliceEntity, ActionPayload.of(ActionPayload.Action.RENAME, spotId,
				"Nice\nwaypoints remove Hub"));
		helper.assertTrue(injected.success(), "a newline can't start a second command: " + injected.message());
		helper.assertTrue(mod.waypoints().contains("Sorting Room"), "Sorting Room still exists");
		helper.assertValueEqual(mod.waypoints().get(menuSpot.id()).orElseThrow().name(), "Nice waypoints remove Hub",
				"renamed as one plain line");

		menus.handle(aliceEntity, ActionPayload.of(ActionPayload.Action.FAVORITE, spotId));
		helper.assertTrue(menus.snapshot(aliceEntity).waypoints().stream()
				.anyMatch(data -> data.id().equals(menuSpot.id()) && data.favorite()), "menu favourite");
		menus.handle(aliceEntity, ActionPayload.of(ActionPayload.Action.DESCRIBE, spotId, "From the menu"));
		helper.assertValueEqual(mod.waypoints().get(menuSpot.id()).orElseThrow().description(), "From the menu", "menu describe");

		ResultPayload denied = menus.handle(bobEntity, ActionPayload.of(ActionPayload.Action.REMOVE, spotId));
		helper.assertTrue(!denied.success() && denied.message().contains("only remove waypoints you created"),
				"permissions still apply: " + denied.message());
		ResultPayload teleportDenied = menus.handle(aliceEntity, ActionPayload.of(ActionPayload.Action.TELEPORT, spotId));
		helper.assertTrue(!teleportDenied.success(), "no teleport for ordinary players via the menu");
		ResultPayload badCategory = menus.handle(aliceEntity, ActionPayload.of(ActionPayload.Action.ADD,
				"X", "farms add Y other", "1", "2", "3", "minecraft:overworld"));
		helper.assertTrue(!badCategory.success(), "category must be a single id");
		ResultPayload removed = menus.handle(aliceEntity, ActionPayload.of(ActionPayload.Action.REMOVE, spotId));
		helper.assertTrue(removed.success(), "creator removes via menu: " + removed.message());
		ResultPayload gone = menus.handle(aliceEntity, ActionPayload.of(ActionPayload.Action.GO, spotId));
		helper.assertTrue(!gone.success() && gone.message().contains("no longer exists"), "stale id: " + gone.message());

		routes(helper, server, dispatcher, mod, aliceEntity, bobEntity, alice, bob, moderator);
		clientModSync(helper, dispatcher, mod, aliceEntity, bobEntity, alice, moderator);
		joinSummary(helper, dispatcher, mod, aliceEntity, moderator);

		// Every /cway subcommand must be a reserved word, so no category can ever hide one.
		for (var child : dispatcher.getRoot().getChild("cway").getChildren()) {
			if (child instanceof com.mojang.brigadier.tree.LiteralCommandNode<?>) {
				helper.assertTrue(CategoryRegistry.RESERVED_IDS.contains(child.getName()),
						"subcommand \"" + child.getName() + "\" missing from CategoryRegistry.RESERVED_IDS");
			}
		}
		// 2.0.0 renamed the command: the old /waypoints is gone, not kept as an alias.
		helper.assertTrue(dispatcher.getRoot().getChild("waypoints") == null, "/waypoints must no longer exist");

		helper.succeed();
	}

	// ------------------------------------------------------------------- routes

	private static void routes(GameTestHelper helper, MinecraftServer server, CommandDispatcher<CommandSourceStack> dispatcher,
			ModContext mod, ServerPlayer aliceEntity, ServerPlayer bobEntity, Source alice, Source bob, Source moderator) {
		alice.out.take();
		helper.assertValueEqual(run(helper, dispatcher, alice, "cway route"), 0, "no routes yet");
		helper.assertTrue(alice.out.take().contains("No routes yet"), "empty route list");

		// Stops: three in the overworld along +X, one in the Nether in between.
		run(helper, dispatcher, alice, "cway add R1 other 20 64 0");
		run(helper, dispatcher, alice, "cway add R2 other 40 64 0");
		run(helper, dispatcher, alice, "cway add R3 portals 5 70 0 minecraft:the_nether");
		run(helper, dispatcher, alice, "cway add R4 other 60 64 0");
		Waypoint r1 = mod.waypoints().get("R1").orElseThrow();
		Waypoint r2 = mod.waypoints().get("R2").orElseThrow();
		Waypoint r3 = mod.waypoints().get("R3").orElseThrow();
		Waypoint r4 = mod.waypoints().get("R4").orElseThrow();
		Waypoint market = mod.waypoints().get("Market").orElseThrow();

		// --- create and fill
		run(helper, dispatcher, alice, "cway route create \"Grand Tour\"");
		helper.assertTrue(alice.out.take().contains("Created route Grand Tour"), "route created");
		expectError(helper, dispatcher, bob, "cway route create \"grand tour\"", "already exists");
		for (String stop : List.of("R1", "R2", "R3", "R4", "Market")) {
			run(helper, dispatcher, alice, "cway route add \"Grand Tour\" " + stop);
		}
		helper.assertTrue(alice.out.take().contains("Added Market to Grand Tour as stop 5"), "stop numbering");
		expectError(helper, dispatcher, bob, "cway route add \"Grand Tour\" R1", "only change routes you created");
		expectError(helper, dispatcher, bob, "cway route delete \"Grand Tour\"", "only delete routes you created");
		expectError(helper, dispatcher, alice, "cway route add \"Grand Tour\" Nowhere", "No waypoint named");
		expectError(helper, dispatcher, alice, "cway route drop \"Grand Tour\" 9", "Stop numbers go from 1 to 5");
		helper.assertTrue(suggestions(dispatcher, alice, "cway route add ").contains("\"Grand Tour\""),
				"route names suggested (quoted) to its creator");
		helper.assertTrue(suggestions(dispatcher, bob, "cway route add ").isEmpty(),
				"no editable routes suggested to others");
		helper.assertTrue(suggestions(dispatcher, bob, "cway route go ").contains("\"Grand Tour\""),
				"anyone may follow it");

		// --- list and info
		helper.assertValueEqual(run(helper, dispatcher, bob, "cway route list"), 1, "one route");
		Component routeLine = bob.out.messages.stream()
				.filter(line -> line.getString().startsWith("[Route] Grand Tour")).findFirst().orElseThrow();
		helper.assertTrue(routeLine.getString().contains("5 stops ·"), "stop count and length: " + routeLine.getString());
		helper.assertValueEqual(clickOf(routeLine, "[Go]"),
				Optional.of(new ClickEvent.RunCommand("/cway route go \"Grand Tour\"")), "route Go click");
		bob.out.take();
		run(helper, dispatcher, bob, "cway route info \"Grand Tour\"");
		String info = bob.out.take();
		helper.assertTrue(info.contains("1. [Other] R1") && info.contains("3. [Portals] R3") && info.contains("5. [Shops] Market"),
				"numbered stops:\n" + info);
		helper.assertTrue(info.contains("[Go from here]") && !info.contains("[✕]") && !info.contains("[Delete]"),
				"no editing buttons for others:\n" + info);
		run(helper, dispatcher, alice, "cway route info \"Grand Tour\"");
		String aliceInfo = alice.out.take();
		helper.assertTrue(aliceInfo.contains("[✕]") && aliceInfo.contains("[↑]") && aliceInfo.contains("[Delete]"),
				"editing buttons for the creator:\n" + aliceInfo);

		// --- move and drop
		run(helper, dispatcher, alice, "cway route move \"Grand Tour\" 5 1");
		helper.assertValueEqual(route(mod).stops(), List.of(market.id(), r1.id(), r2.id(), r3.id(), r4.id()), "moved to the front");
		run(helper, dispatcher, alice, "cway route drop \"Grand Tour\" 1");
		helper.assertValueEqual(route(mod).stops(), List.of(r1.id(), r2.id(), r3.id(), r4.id()), "dropped");
		helper.assertTrue(mod.waypoints().get(market.id()).isPresent(), "dropping a stop keeps the waypoint");
		run(helper, dispatcher, alice, "cway route describe \"Grand Tour\" Bring fire resistance");
		helper.assertValueEqual(route(mod).description(), "Bring fire resistance", "route description");

		// --- web map: R1 -> R2 is an overworld stretch, drawn as a line
		if (FabricLoader.getInstance().isModLoaded("squaremap")) {
			helper.assertTrue(SquaremapProbe.hasMarker("route-" + route(mod).id() + "-0"), "route line on the overworld map");
			String popup = SquaremapProbe.clickTooltip("route-" + route(mod).id() + "-0");
			helper.assertTrue(popup != null && popup.contains("<b>Grand Tour</b>") && popup.contains("Bring fire resistance"),
					"route popup: " + popup);
		}

		// --- following the route: arrive, skip, finish
		aliceEntity.teleportTo(0.5, 64, 0.5);
		run(helper, dispatcher, alice, "cway route go \"Grand Tour\"");
		helper.assertValueEqual(mod.navigation().destinationOf(aliceEntity.getUUID()), Optional.of(r1.id()), "heading to stop 1");
		helper.assertValueEqual(mod.navigation().routeOf(aliceEntity.getUUID()).map(p -> p.index()), Optional.of(0), "stop index 0");
		aliceEntity.teleportTo(20.5, 64, 0.5);
		tick(mod, server);
		helper.assertValueEqual(mod.navigation().destinationOf(aliceEntity.getUUID()), Optional.of(r2.id()),
				"arriving at stop 1 moves on to stop 2");
		run(helper, dispatcher, alice, "cway route skip");
		helper.assertValueEqual(mod.navigation().destinationOf(aliceEntity.getUUID()), Optional.of(r3.id()), "skipped to stop 3");
		run(helper, dispatcher, alice, "cway route skip");
		helper.assertValueEqual(mod.navigation().destinationOf(aliceEntity.getUUID()), Optional.of(r4.id()), "skipped to stop 4");
		aliceEntity.teleportTo(60.5, 64, 0.5);
		tick(mod, server);
		helper.assertTrue(mod.navigation().destinationOf(aliceEntity.getUUID()).isEmpty()
				&& mod.navigation().routeOf(aliceEntity.getUUID()).isEmpty(), "arriving at the last stop finishes the route");
		expectError(helper, dispatcher, alice, "cway route skip", "aren't following a route");

		// Start from a later stop.
		run(helper, dispatcher, alice, "cway route go \"Grand Tour\" 3");
		helper.assertValueEqual(mod.navigation().destinationOf(aliceEntity.getUUID()), Optional.of(r3.id()), "go from stop 3");
		run(helper, dispatcher, alice, "cway stop");

		// Deleting the waypoint you're heading to moves on to the next stop (the list shifts under the session).
		aliceEntity.teleportTo(0.5, 64, 0.5);
		run(helper, dispatcher, alice, "cway route go \"Grand Tour\" 2");
		run(helper, dispatcher, moderator, "cway remove R2");
		helper.assertValueEqual(route(mod).stops(), List.of(r1.id(), r3.id(), r4.id()), "deleted waypoint left the route");
		tick(mod, server);
		helper.assertValueEqual(mod.navigation().destinationOf(aliceEntity.getUUID()), Optional.of(r3.id()),
				"deleted stop is skipped, not the one after it");
		helper.assertValueEqual(mod.navigation().routeOf(aliceEntity.getUUID()).map(p -> p.index()), Optional.of(1),
				"index follows the shorter route");
		run(helper, dispatcher, alice, "cway stop");

		// --- the client menu drives routes through the same commands
		MenuNetworking menus = mod.menus();
		SyncPayload aliceView = menus.snapshot(aliceEntity);
		SyncPayload.RouteData tour = aliceView.routes().stream().filter(r -> r.name().equals("Grand Tour")).findFirst().orElseThrow();
		helper.assertTrue(tour.canEdit() && tour.canRemove() && aliceView.canAddRoute(), "creator's rights in the menu");
		helper.assertValueEqual(tour.stops(), List.of(r1.id(), r3.id(), r4.id()), "menu sees the stops");
		SyncPayload.RouteData tourForBob = menus.snapshot(bobEntity).routes().stream()
				.filter(r -> r.name().equals("Grand Tour")).findFirst().orElseThrow();
		helper.assertTrue(!tourForBob.canEdit() && !tourForBob.canRemove(), "others may not change it");

		ResultPayload created = menus.handle(bobEntity, ActionPayload.of(ActionPayload.Action.ROUTE_CREATE, "Bob's \"Loop\""));
		helper.assertTrue(created.success(), "menu create: " + created.message());
		Route loop = mod.routes().get("Bob's \"Loop\"").orElseThrow();
		String loopId = loop.id().toString();
		menus.handle(bobEntity, ActionPayload.of(ActionPayload.Action.ROUTE_ADD, loopId, r4.id().toString()));
		menus.handle(bobEntity, ActionPayload.of(ActionPayload.Action.ROUTE_ADD, loopId, r1.id().toString()));
		menus.handle(bobEntity, ActionPayload.of(ActionPayload.Action.ROUTE_MOVE, loopId, "2", "1"));
		helper.assertValueEqual(mod.routes().get(loop.id()).orElseThrow().stops(), List.of(r1.id(), r4.id()), "menu add + move");
		menus.handle(bobEntity, ActionPayload.of(ActionPayload.Action.ROUTE_RENAME, loopId, "Loop\nroute delete Grand"));
		helper.assertValueEqual(mod.routes().get(loop.id()).orElseThrow().name(), "Loop route delete Grand",
				"a newline can't start a second command");
		helper.assertTrue(mod.routes().contains("Grand Tour"), "Grand Tour untouched");
		menus.handle(bobEntity, ActionPayload.of(ActionPayload.Action.ROUTE_DESCRIBE, loopId, "Quick one"));
		helper.assertValueEqual(mod.routes().get(loop.id()).orElseThrow().description(), "Quick one", "menu describe");
		ResultPayload go = menus.handle(bobEntity, ActionPayload.of(ActionPayload.Action.ROUTE_GO, loopId, "1"));
		helper.assertTrue(go.success(), "menu go: " + go.message());
		SyncPayload.RouteProgressData onRoute = menus.snapshot(bobEntity).onRoute();
		helper.assertTrue(onRoute != null && onRoute.routeId().equals(loop.id()) && onRoute.stopIndex() == 0,
				"menu shows the route being followed");
		menus.handle(bobEntity, ActionPayload.of(ActionPayload.Action.ROUTE_SKIP));
		helper.assertValueEqual(mod.navigation().destinationOf(bobEntity.getUUID()), Optional.of(r4.id()), "menu skip");
		menus.handle(bobEntity, ActionPayload.of(ActionPayload.Action.STOP));
		ResultPayload dropDenied = menus.handle(bobEntity, ActionPayload.of(ActionPayload.Action.ROUTE_DROP,
				tour.id().toString(), "1"));
		helper.assertTrue(!dropDenied.success() && dropDenied.message().contains("only change routes you created"),
				"permissions apply to routes: " + dropDenied.message());
		menus.handle(bobEntity, ActionPayload.of(ActionPayload.Action.ROUTE_DROP, loopId, "2"));
		helper.assertValueEqual(mod.routes().get(loop.id()).orElseThrow().stops(), List.of(r1.id()), "menu drop");

		// --- saved to routes.json, and survives a reload
		try {
			String json = Files.readString(configDir().resolve("routes.json"));
			helper.assertTrue(json.contains("\"Grand Tour\"") && json.contains(r3.id().toString())
					&& json.contains("Bring fire resistance"), "routes.json:\n" + json);
		} catch (IOException e) {
			helper.fail("could not read routes.json: " + e);
		}
		run(helper, dispatcher, moderator, "cway reload");
		helper.assertTrue(moderator.out.take().contains("2 routes"), "routes reloaded");
		helper.assertValueEqual(route(mod).stops(), List.of(r1.id(), r3.id(), r4.id()), "stops survive a reload");

		// --- delete: creators and ops only; the waypoints stay
		ResultPayload deleteDenied = menus.handle(aliceEntity, ActionPayload.of(ActionPayload.Action.ROUTE_DELETE, loopId));
		helper.assertTrue(!deleteDenied.success(), "alice can't delete bob's route");
		run(helper, dispatcher, moderator, "cway route delete \"Loop route delete Grand\"");
		run(helper, dispatcher, alice, "cway route delete \"Grand Tour\"");
		helper.assertValueEqual(mod.routes().size(), 0, "both routes deleted");
		helper.assertTrue(mod.waypoints().get(r1.id()).isPresent(), "deleting a route keeps its waypoints");
		if (FabricLoader.getInstance().isModLoaded("squaremap")) {
			helper.assertTrue(!SquaremapProbe.hasMarker("route-" + loop.id() + "-0"), "route lines removed from the map");
		}
		for (String name : List.of("R1", "R3", "R4")) {
			run(helper, dispatcher, moderator, "cway remove " + name);
		}
	}

	// ---------------------------------------------------------- join summary

	/** "N new waypoints since you last played", with the usual chat buttons. */
	private static void joinSummary(GameTestHelper helper, CommandDispatcher<CommandSourceStack> dispatcher,
			ModContext mod, ServerPlayer aliceEntity, Source moderator) {
		var viewer = io.github.steelaspect.sharedwaypoints.text.Viewer.of(aliceEntity, mod.favorites());
		// Explicit times, so the check doesn't depend on how fast the test runs: alice "left" an hour from now, and
		// the new waypoints are stamped after that.
		java.time.Instant leftAt = java.time.Instant.now().plusSeconds(3600);
		helper.assertTrue(mod.joinSummary().lines(Optional.of(leftAt), viewer).isEmpty(), "nothing new, nothing said");
		java.util.function.BiConsumer<String, Integer> addLater = (name, x) -> mod.waypoints().add(new Waypoint(
				UUID.randomUUID(), name, mod.categories().resolve(x == 30 ? "farms" : x == 40 ? "bases" : "other"),
				x, 64, x, "minecraft:overworld", null, Waypoint.SERVER_UUID, "Server", leftAt.plusSeconds(60)));

		addLater.accept("New Farm", 30);
		addLater.accept("New Base", 40);
		List<Component> lines = mod.joinSummary().lines(Optional.of(leftAt), viewer);
		String text = String.join("\n", lines.stream().map(Component::getString).toList());
		helper.assertTrue(lines.get(0).getString().equals("✦ 2 new waypoints since you last played:"), text);
		helper.assertTrue(text.contains("[Farms] New Farm — 30 64 30 (overworld) [Add to Xaero] [Copy coords] [Go]"), text);
		helper.assertTrue(text.contains("[Bases] New Base"), text);
		Component farmLine = lines.stream().filter(line -> line.getString().contains("New Farm")).findFirst().orElseThrow();
		helper.assertValueEqual(clickOf(farmLine, "[Add to Xaero]"),
				Optional.of(new ClickEvent.RunCommand("/cway xaero \"New Farm\"")), "the usual Xaero add button");

		// First visit: no "new" list, just a pointer to the waypoints.
		List<Component> first = mod.joinSummary().lines(Optional.empty(), viewer);
		helper.assertTrue(first.size() == 1 && first.get(0).getString().startsWith("✦ This server shares ")
				&& first.get(0).getString().endsWith("[Show all]"), "first visit: " + first);

		// Long lists are cut off with a button to the full list.
		List<String> extra = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			addLater.accept("Extra" + i, i);
			extra.add("Extra" + i);
		}
		List<Component> many = mod.joinSummary().lines(Optional.of(leftAt), viewer);
		helper.assertValueEqual(many.size(), 1 + 8 + 1, "header, 8 lines, and the rest");
		helper.assertTrue(many.get(9).getString().equals("…and 2 more. [Show all]"), many.get(9).getString());

		clientModTip(helper, dispatcher, mod, aliceEntity);

		// Joining records the time in waypoints.json.
		mod.joinSummary().onJoin(aliceEntity);
		helper.assertTrue(mod.waypoints().lastSeen(aliceEntity.getUUID()).isPresent(), "last seen recorded");
		try {
			String json = Files.readString(configDir().resolve("waypoints.json"));
			helper.assertTrue(json.contains("\"lastSeen\"") && json.contains(aliceEntity.getUUID().toString()),
					"lastSeen saved in waypoints.json");
		} catch (IOException e) {
			helper.fail("could not read waypoints.json: " + e);
		}
		for (String name : extra) {
			run(helper, dispatcher, moderator, "cway remove " + name);
		}
		run(helper, dispatcher, moderator, "cway remove \"New Farm\"");
		run(helper, dispatcher, moderator, "cway remove \"New Base\"");
		moderator.out.take();
	}

	// ------------------------------------------------------ client-mod sync

	/**
	 * The sharedwaypoints-client handshake and live updates, with a recording "connection" per player instead of a
	 * real client.
	 */
	private static void clientModSync(GameTestHelper helper, CommandDispatcher<CommandSourceStack> dispatcher,
			ModContext mod, ServerPlayer aliceEntity, ServerPlayer bobEntity, Source alice, Source moderator) {
		List<net.minecraft.network.protocol.common.custom.CustomPacketPayload> toAlice = new ArrayList<>();
		List<net.minecraft.network.protocol.common.custom.CustomPacketPayload> toBob = new ArrayList<>();

		// Alice has the client mod: welcome, then the full list.
		mod.sync().onHello(aliceEntity, new HelloPayload(SyncProtocol.VERSION, "test"), toAlice::add);
		helper.assertValueEqual(toAlice.get(0), new WelcomePayload(SyncProtocol.VERSION, true), "welcome, sync on");
		FullSyncPayload full = (FullSyncPayload) toAlice.get(1);
		helper.assertValueEqual(full.waypoints().size(), mod.waypoints().size(), "full list sent");
		SyncedWaypoint sorting = full.waypoints().stream().filter(w -> w.name().equals("Sorting Room")).findFirst().orElseThrow();
		helper.assertValueEqual(sorting.categoryId(), "storage", "category id");
		helper.assertValueEqual(sorting.colorIndex(), 11, "storage is Xaero colour 11 (aqua)");
		helper.assertValueEqual(sorting.initials(), "S", "Xaero initials, as in the chat share line");
		helper.assertValueEqual(sorting.dimension(), "minecraft:overworld", "dimension");
		helper.assertTrue(mod.sync().isSubscribed(aliceEntity.getUUID()), "alice subscribed");

		// Bob's client mod speaks another protocol version: told so, never subscribed.
		mod.sync().onHello(bobEntity, new HelloPayload(SyncProtocol.VERSION + 1, "future"), toBob::add);
		helper.assertValueEqual(toBob, List.<Object>of(new WelcomePayload(SyncProtocol.VERSION, false)), "version mismatch");
		helper.assertTrue(!mod.sync().isSubscribed(bobEntity.getUUID()), "bob not subscribed");

		// Live: add, edit, delete.
		toAlice.clear();
		run(helper, dispatcher, moderator, "cway add \"Sync Test\" farms 1 64 2");
		Waypoint syncTest = mod.waypoints().get("Sync Test").orElseThrow();
		helper.assertValueEqual(toAlice.size(), 1, "one update for an add");
		helper.assertValueEqual(((UpsertPayload) toAlice.get(0)).waypoint().id(), syncTest.id(), "add sent as upsert");
		helper.assertValueEqual(((UpsertPayload) toAlice.get(0)).waypoint().colorIndex(), 10, "farms is Xaero colour 10");
		run(helper, dispatcher, moderator, "cway rename \"Sync Test\" \"Sync Renamed\"");
		UpsertPayload renamed = (UpsertPayload) toAlice.get(1);
		helper.assertTrue(renamed.waypoint().id().equals(syncTest.id()) && renamed.waypoint().name().equals("Sync Renamed"),
				"rename keeps the id");
		run(helper, dispatcher, moderator, "cway describe \"Sync Renamed\" A note");
		helper.assertValueEqual(((UpsertPayload) toAlice.get(2)).waypoint().description(), "A note", "describe sent");
		run(helper, dispatcher, moderator, "cway remove \"Sync Renamed\"");
		helper.assertValueEqual(toAlice.get(3), new DeletePayload(SyncProtocol.VERSION, syncTest.id()), "delete sent");
		helper.assertTrue(toBob.size() == 1, "bob (not subscribed) got nothing more");

		// Reload resends everything (categories or colours may have changed).
		toAlice.clear();
		run(helper, dispatcher, moderator, "cway reload");
		moderator.out.take();
		helper.assertTrue(toAlice.size() == 1 && toAlice.get(0) instanceof FullSyncPayload, "full list after reload");

		// Turned off in config: nothing more is sent, and new clients are told sync is off.
		mod.config().syncToClientMod = false;
		toAlice.clear();
		run(helper, dispatcher, moderator, "cway add Quiet other 3 64 3");
		helper.assertTrue(toAlice.isEmpty(), "no updates while sync is off");
		List<net.minecraft.network.protocol.common.custom.CustomPacketPayload> toLate = new ArrayList<>();
		mod.sync().onHello(bobEntity, new HelloPayload(SyncProtocol.VERSION, "test"), toLate::add);
		helper.assertValueEqual(toLate, List.<Object>of(new WelcomePayload(SyncProtocol.VERSION, false)), "sync off at hello");
		mod.config().syncToClientMod = true;
		run(helper, dispatcher, moderator, "cway remove Quiet");

		// Leaving ends the subscription.
		mod.sync().forget(aliceEntity.getUUID());
		toAlice.clear();
		run(helper, dispatcher, moderator, "cway add Gone other 4 64 4");
		helper.assertTrue(toAlice.isEmpty(), "no updates after leaving");
		run(helper, dispatcher, moderator, "cway remove Gone");
		alice.out.take();
	}

	/** How players learn about the Xaero sync: the first-join tip and /cway sync. */
	private static void clientModTip(GameTestHelper helper, CommandDispatcher<CommandSourceStack> dispatcher,
			ModContext mod, ServerPlayer aliceEntity) {
		var tips = mod.clientModTip();
		var config = mod.config();
		// alice is a mock player without the client mod: tipped on her first visit only.
		helper.assertTrue(tips.shouldTip(aliceEntity, true), "first visit without the client mod gets the tip");
		helper.assertFalse(tips.shouldTip(aliceEntity, false), "no tip on later visits");
		config.clientModTip = false;
		helper.assertFalse(tips.shouldTip(aliceEntity, true), "clientModTip off: no tip");
		config.clientModTip = true;
		config.syncToClientMod = false;
		helper.assertFalse(tips.shouldTip(aliceEntity, true), "sync off: no tip");
		config.syncToClientMod = true;

		Component tip = tips.tip();
		helper.assertTrue(tip.getString().startsWith("✦ Want these waypoints in Xaero's Minimap automatically? "
				+ "Install the sharedwaypoints-client mod. [Download] [How it works]"), tip.getString());
		helper.assertValueEqual(clickOf(tip, "[How it works]"), Optional.of(new ClickEvent.RunCommand("/cway sync")),
				"How it works runs /cway sync");
		helper.assertValueEqual(clickOf(tip, "[Download]"), Optional.of(new ClickEvent.OpenUrl(
				java.net.URI.create("https://github.com/SteelAspect/sharedwaypoints/releases/latest"))), "Download link");

		Source alice = source(aliceEntity.createCommandSourceStack().withPermission(LevelBasedPermissionSet.ALL));
		run(helper, dispatcher, alice, "cway sync");
		String steps = alice.out.take();
		helper.assertTrue(steps.contains("Automatic Xaero's Minimap sync")
				&& steps.contains("1. Put sharedwaypoints-client-2.0.0.jar in your .minecraft/mods folder.")
				&& steps.contains("2. Also install Xaero's Minimap and Fabric API")
				&& steps.contains("3. Rejoin.") && steps.contains("[Download]"), steps);

		// No link configured (or not a web link): no button, ask an admin instead.
		config.clientModUrl = "not a link";
		helper.assertFalse(tips.tip().getString().contains("[Download]"), "no Download button without a link");
		run(helper, dispatcher, alice, "cway sync");
		helper.assertTrue(alice.out.take().contains("Ask a server admin for the file."), "no link: ask an admin");
		config.clientModUrl = "https://github.com/SteelAspect/sharedwaypoints/releases/latest";

		// With the client mod synced, /cway sync says so instead of the steps.
		mod.sync().onHello(aliceEntity, new HelloPayload(SyncProtocol.VERSION, "test"), payload -> { });
		run(helper, dispatcher, alice, "cway sync");
		String synced = alice.out.take();
		helper.assertTrue(synced.contains("You have it: your Xaero's Minimap is in sync") && !synced.contains("1. Put"), synced);
		mod.sync().forget(aliceEntity.getUUID());

		config.syncToClientMod = false;
		run(helper, dispatcher, alice, "cway sync");
		helper.assertTrue(alice.out.take().contains("This server has the sync turned off."), "sync off explained");
		config.syncToClientMod = true;
	}

	private static Route route(ModContext mod) {
		return mod.routes().get("Grand Tour").orElseThrow();
	}

	/** Enough navigation ticks for one compass update. */
	private static void tick(ModContext mod, MinecraftServer server) {
		for (int i = 0; i < 5; i++) {
			mod.navigation().tick(server);
		}
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
