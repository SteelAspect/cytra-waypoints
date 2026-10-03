package io.github.steelaspect.sharedwaypoints.clientgametest;

import io.github.steelaspect.sharedwaypoints.SharedWaypoints;
import io.github.steelaspect.sharedwaypoints.client.AddWaypointScreen;
import io.github.steelaspect.sharedwaypoints.client.ClientWaypoints;
import io.github.steelaspect.sharedwaypoints.client.RoutesScreen;
import io.github.steelaspect.sharedwaypoints.client.SharedWaypointsClient;
import io.github.steelaspect.sharedwaypoints.client.WaypointMenuScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import io.github.steelaspect.sharedwaypoints.network.SyncPayload;
import io.github.steelaspect.sharedwaypoints.waypoint.ProjectStatus;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

/**
 * Runs the real client: creates a singleplayer world (so the mod is on both sides), adds waypoints, opens the menu
 * with its keybind, adds a waypoint through the form, and saves screenshots of each step.
 */
public class MenuClientGameTest implements FabricClientGameTest {
	private static void view(ClientGameTestContext context, String query, String filter, String sort) {
		context.runOnClient(client -> ((WaypointMenuScreen) client.screen).setViewForTest(query, filter, sort));
		context.waitTicks(3);
	}

	private static void shot(ClientGameTestContext context, String name) {
		// Park the mouse in the corner and drop keyboard focus, so no tooltip covers the screen.
		context.getInput().setCursorPos(1, 1);
		context.runOnClient(client -> {
			if (client.screen != null && !(client.screen instanceof ChatScreen)) {
				client.screen.clearFocus();
			}
		});
		context.waitTicks(1);
		System.out.println("[SharedWaypoints test] screenshot: " + context.takeScreenshot("sharedwaypoints-" + name));
	}

	private static Optional<SyncPayload.RouteData> farmRun() {
		return ClientWaypoints.routes().stream().filter(route -> route.name().equals("Farm Run")).findFirst();
	}

	private static int stopCount() {
		return farmRun().map(route -> route.stops().size()).orElse(0);
	}

	/** Calls a test hook on a screen class that isn't public (the waypoint picker). */
	private static void invoke(Object screen, String method, String argument) {
		try {
			var hook = screen.getClass().getDeclaredMethod(method, String.class);
			hook.setAccessible(true);
			hook.invoke(screen, argument);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("no test hook " + method + " on " + screen.getClass().getName(), e);
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

	@Override
	public void runTest(ClientGameTestContext context) {
		// Fabric allows 1200 client ticks for the world to load, and the server ticks in step with the client. On a
		// 2-vCPU CI runner the software renderer used every core, so chunk generation starved and the load timed out.
		// A low frame cap while loading leaves the CPU idle between ticks for world generation; blur is costly in
		// software rendering.
		context.runOnClient(client -> {
			client.options.renderDistance().set(2);
			client.options.simulationDistance().set(5);
			client.options.menuBackgroundBlurriness().set(0);
			client.options.framerateLimit().set(10);
		});
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.runOnClient(client -> {
				client.options.framerateLimit().set(60);
				client.options.menuBackgroundBlurriness().set(5);
			});
			world.getClientWorld().waitForChunksRender();
			// The mod is in this game (one jar) but Xaero's Minimap isn't: a few seconds after the first join the server
			// suggests Xaero, and never the "install the mod" tip.
			context.waitFor(client -> chatText(client).contains("Your SharedWaypoints can't reach Xaero's Minimap."));
			if (context.computeOnClient(client -> chatText(client).contains("Want these waypoints in Xaero's Minimap"))) {
				throw new AssertionError("A player who has the mod was told to install it");
			}
			context.setScreen(() -> new ChatScreen("", false));
			context.waitTicks(3);
			shot(context, "xaero-tip");
			context.setScreen(() -> null);
			// What a player without the mod sees on their first join (sent by hand: this game has the mod).
			context.runOnClient(client -> client.gui.getChat().clearMessages(false));
			world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().get(0)
					.sendSystemMessage(SharedWaypoints.context().clientModTip().tip()));
			context.waitFor(client -> chatText(client).contains("the same jar as the server"));
			context.setScreen(() -> new ChatScreen("", false));
			context.waitTicks(3);
			shot(context, "sync-tip");
			context.setScreen(() -> null);
			// [How it works] runs /cway sync: the steps.
			context.runOnClient(client -> client.gui.getChat().clearMessages(false));
			context.runOnClient(client -> client.player.connection.sendCommand("cway sync"));
			context.waitFor(client -> chatText(client).contains("1. Put sharedwaypoints-"));
			context.setScreen(() -> new ChatScreen("", false));
			context.waitTicks(3);
			shot(context, "sync-steps");
			context.setScreen(() -> null);
			world.getServer().runCommand("cway add \"Main Storage\" storage 12 -60 -30");
			world.getServer().runCommand("cway describe \"Main Storage\" Sorted chests, bring shulkers");
			world.getServer().runCommand("cway add \"Iron Farm\" farms 140 -60 210");
			world.getServer().runCommand("cway add Hub portals 10 70 -20 minecraft:the_nether");
			world.getServer().runCommand("cway add \"Spawn Base\" bases 0 -60 0");

			// Open the menu the way a player does: the keybind.
			context.getInput().pressKey(SharedWaypointsClient.openMenuKey());
			context.waitForScreen(WaypointMenuScreen.class);
			context.waitFor(client -> ClientWaypoints.waypoints().size() == 4);
			context.waitTicks(5);
			System.out.println("[SharedWaypoints test] screenshot: " + context.takeScreenshot("sharedwaypoints-menu"));

			// Add a waypoint through the form.
			context.clickScreenButton("+ Add");
			context.waitForScreen(AddWaypointScreen.class);
			context.getInput().typeChars("Test Spot");
			context.waitTicks(2);
			System.out.println("[SharedWaypoints test] screenshot: " + context.takeScreenshot("sharedwaypoints-add"));
			context.clickScreenButton("Add");
			context.waitForScreen(WaypointMenuScreen.class);
			context.waitFor(client -> ClientWaypoints.waypoints().stream().anyMatch(w -> w.name().equals("Test Spot")));
			boolean onServer = world.getServer().computeOnServer(server ->
					SharedWaypoints.context().waypoints().contains("Test Spot"));
			if (!onServer) {
				throw new AssertionError("Test Spot was not added on the server");
			}
			context.waitTicks(5);
			System.out.println("[SharedWaypoints test] screenshot: " + context.takeScreenshot("sharedwaypoints-after-add"));

			// Favourite the selected waypoint with the button, and check the server agrees.
			context.clickScreenButton("☆ Favourite");
			context.waitFor(client -> ClientWaypoints.waypoints().stream().anyMatch(w ->
					ClientWaypoints.data(w.id()).map(data -> data.favorite()).orElse(false)));
			context.waitTicks(5);
			System.out.println("[SharedWaypoints test] screenshot: " + context.takeScreenshot("sharedwaypoints-favourite"));

			// Edit the player's own waypoint (Test Spot): select it, open Edit, change the description.
			context.runOnClient(client -> ((WaypointMenuScreen) client.screen).selectForTest("Test Spot"));
			context.clickScreenButton("Edit");
			context.waitFor(client -> client.screen != null && client.screen.getClass().getSimpleName().equals("EditWaypointScreen"));
			context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB);
			context.getInput().typeChars("Near spawn");
			context.waitTicks(2);
			System.out.println("[SharedWaypoints test] screenshot: " + context.takeScreenshot("sharedwaypoints-edit"));
			context.clickScreenButton("Save");
			context.waitForScreen(WaypointMenuScreen.class);
			context.waitFor(client -> ClientWaypoints.waypoints().stream()
					.anyMatch(w -> w.name().equals("Test Spot") && "Near spawn".equals(w.description())));
			context.waitTicks(5);
			System.out.println("[SharedWaypoints test] screenshot: " + context.takeScreenshot("sharedwaypoints-edited"));

			// --- a tour of every screen, for the screenshots
			// Remove asks for confirmation first ("No" keeps it).
			context.clickScreenButton("Remove");
			context.waitForScreen(ConfirmScreen.class);
			shot(context, "remove-confirm");
			context.clickScreenButton("No");
			context.waitForScreen(WaypointMenuScreen.class);
			if (!world.getServer().computeOnServer(server -> SharedWaypoints.context().waypoints().contains("Test Spot"))) {
				throw new AssertionError("\"No\" must keep the waypoint");
			}

			// Filters, search and sorting.
			view(context, "", "favorites", "category");
			shot(context, "filter-favourites");
			view(context, "", "storage", "category");
			shot(context, "filter-storage");
			view(context, "farm", "all", "category");
			shot(context, "search");
			view(context, "", "all", "distance");
			shot(context, "sort-distance");
			view(context, "", "all", "category");

			// --- project status: mark Iron Farm broken from the menu, with a note
			world.getServer().runCommand("cway status \"Main Storage\" done");
			world.getServer().runCommand("cway status \"Spawn Base\" wip Adding the east wing");
			context.waitFor(client -> ClientWaypoints.waypoints().stream().filter(w -> w.status() != null).count() == 2);
			context.runOnClient(client -> ((WaypointMenuScreen) client.screen).selectForTest("Iron Farm"));
			context.waitTicks(2);
			context.clickScreenButton("Set status…");
			context.waitFor(client -> client.screen != null && client.screen.getClass().getSimpleName().equals("StatusScreen"));
			context.getInput().typeChars("Out of bonemeal");
			context.waitTicks(2);
			shot(context, "status-screen");
			context.clickScreenButton("⚠ Broken");
			context.waitForScreen(WaypointMenuScreen.class);
			context.waitFor(client -> ClientWaypoints.waypoints().stream().anyMatch(w -> w.name().equals("Iron Farm")
					&& w.status() != null && w.status().state() == ProjectStatus.State.BROKEN
					&& "Out of bonemeal".equals(w.status().note())));
			boolean brokenOnServer = world.getServer().computeOnServer(server -> SharedWaypoints.context().waypoints()
					.get("Iron Farm").map(w -> w.status() != null && w.status().state() == ProjectStatus.State.BROKEN)
					.orElse(false));
			if (!brokenOnServer) {
				throw new AssertionError("Iron Farm should be Broken on the server");
			}
			context.runOnClient(client -> ((WaypointMenuScreen) client.screen).selectForTest("Iron Farm"));
			context.waitTicks(5);
			shot(context, "status-menu");
			view(context, "", "projects", "category");
			shot(context, "filter-projects");
			view(context, "", "all", "category");
			// The same in chat: /cway projects.
			context.setScreen(() -> null);
			context.runOnClient(client -> client.gui.getChat().clearMessages(false));
			context.runOnClient(client -> client.player.connection.sendCommand("cway projects"));
			context.waitFor(client -> chatText(client).contains("=== Projects (3) ==="));
			context.setScreen(() -> new ChatScreen("", false));
			context.waitTicks(3);
			shot(context, "projects-chat");
			context.setScreen(() -> null);
			context.getInput().pressKey(SharedWaypointsClient.openMenuKey());
			context.waitForScreen(WaypointMenuScreen.class);
			context.waitTicks(3);

			// --- routes: create one, add stops with the picker, reorder, then follow it
			context.runOnClient(client -> ((WaypointMenuScreen) client.screen).openRoutesForTest());
			context.waitForScreen(RoutesScreen.class);
			context.waitTicks(3);
			shot(context, "routes-empty");
			context.clickScreenButton("+ New route");
			context.waitFor(client -> client.screen != null && client.screen.getClass().getSimpleName().equals("EditRouteScreen"));
			context.getInput().typeChars("Farm Run");
			context.waitTicks(2);
			shot(context, "route-new");
			context.clickScreenButton("Create");
			context.waitForScreen(RoutesScreen.class);
			context.waitFor(client -> ClientWaypoints.routes().stream().anyMatch(route -> route.name().equals("Farm Run")));
			for (String stop : List.of("Iron Farm", "Main Storage", "Spawn Base")) {
				int before = stopCount();
				context.waitTicks(3);
				context.clickScreenButton("+ Stop");
				context.waitFor(client -> client.screen != null
						&& client.screen.getClass().getSimpleName().equals("WaypointPickerScreen"));
				if (before == 0) {
					context.waitTicks(2);
					shot(context, "route-picker");
				}
				context.runOnClient(client -> invoke(client.screen, "pickForTest", stop));
				context.waitForScreen(RoutesScreen.class);
				context.waitFor(client -> stopCount() == before + 1);
			}
			// Move stop 3 (Spawn Base) up to stop 2 with the ↑ button.
			context.runOnClient(client -> ((RoutesScreen) client.screen).selectForTest("Farm Run", 2));
			context.waitTicks(2);
			context.clickScreenButton("↑");
			context.waitFor(client -> farmRun().map(route -> ClientWaypoints.stops(route).get(1).name().equals("Spawn Base"))
					.orElse(false));
			boolean movedOnServer = world.getServer().computeOnServer(server -> SharedWaypoints.context().routes()
					.get("Farm Run").map(route -> route.stops().size() == 3).orElse(false));
			if (!movedOnServer) {
				throw new AssertionError("Farm Run should have 3 stops on the server");
			}
			context.runOnClient(client -> ((RoutesScreen) client.screen).selectForTest("Farm Run", -1));
			context.waitTicks(5);
			shot(context, "routes");

			// The same route in chat.
			context.setScreen(() -> null);
			context.runOnClient(client -> client.player.connection.sendCommand("cway route info \"Farm Run\""));
			context.waitTicks(5);
			context.setScreen(() -> new ChatScreen("", false));
			context.waitTicks(3);
			shot(context, "route-chat");

			// Follow it: the compass shows [1/3] and the first stop.
			context.setScreen(() -> null);
			context.getInput().pressKey(SharedWaypointsClient.openMenuKey());
			context.waitForScreen(WaypointMenuScreen.class);
			context.runOnClient(client -> ((WaypointMenuScreen) client.screen).openRoutesForTest());
			context.waitForScreen(RoutesScreen.class);
			context.waitTicks(3);
			context.runOnClient(client -> ((RoutesScreen) client.screen).selectForTest("Farm Run", 0));
			context.waitTicks(2);
			context.clickScreenButton("▶ Start");
			context.waitFor(client -> client.screen == null && ClientWaypoints.onRoute() != null);
			context.waitTicks(20);
			shot(context, "route-compass");
			world.getServer().runOnServer(server -> SharedWaypoints.context().navigation()
					.stop(server.getPlayerList().getPlayers().get(0).getUUID()));
			context.waitFor(client -> ClientWaypoints.onRoute() == null);
			context.getInput().pressKey(SharedWaypointsClient.openMenuKey());
			context.waitForScreen(WaypointMenuScreen.class);

			// What an op sees: Teleport, and Edit/Remove on everyone's waypoints.
			// There's no /op in singleplayer, so op the player through the player list.
			world.getServer().runOnServer(server -> {
				var player = server.getPlayerList().getPlayers().get(0);
				server.getPlayerList().op(player.nameAndId());
			});
			context.setScreen(() -> null);
			context.waitTicks(5);
			context.getInput().pressKey(SharedWaypointsClient.openMenuKey());
			context.waitForScreen(WaypointMenuScreen.class);
			context.waitFor(client -> ClientWaypoints.canTeleport());
			context.runOnClient(client -> ((WaypointMenuScreen) client.screen).selectForTest("Iron Farm"));
			context.waitTicks(3);
			if (!context.tryClickScreenButton("Teleport")) {
				throw new AssertionError("Ops must see the Teleport button");
			}
			context.waitFor(client -> client.screen == null); // Teleport closes the menu
			context.waitFor(client -> Math.abs(client.player.getX() - 140.5) < 0.01 && Math.abs(client.player.getZ() - 210.5) < 0.01);
			context.getInput().pressKey(SharedWaypointsClient.openMenuKey());
			context.waitForScreen(WaypointMenuScreen.class);
			context.runOnClient(client -> ((WaypointMenuScreen) client.screen).selectForTest("Iron Farm"));
			context.waitTicks(3);
			shot(context, "op-view");

			// No keybind needed: the Esc menu has a "✦ Waypoints" button in the top-right corner.
			context.setScreen(() -> null);
			context.setScreen(() -> new PauseScreen(true));
			context.waitTicks(3);
			shot(context, "pause-menu");
			context.clickScreenButton("✦ Waypoints");
			context.waitForScreen(WaypointMenuScreen.class);
			context.setScreen(() -> null);

			// Portal guide: look at a 2 × 3 portal, /cway portal, and the matching spot is highlighted in the Nether.
			world.getServer().runOnServer(server -> {
				var overworld = server.overworld();
				var portal = net.minecraft.world.level.block.Blocks.NETHER_PORTAL.defaultBlockState()
						.setValue(net.minecraft.world.level.block.NetherPortalBlock.AXIS, net.minecraft.core.Direction.Axis.X);
				for (int x = 1040; x <= 1041; x++) {
					for (int y = 100; y <= 102; y++) {
						overworld.setBlock(new net.minecraft.core.BlockPos(x, y, -312), portal, 18); // no frame needed
					}
				}
				// Something to stand on in front of it.
				for (int x = 1038; x <= 1044; x++) {
					for (int z = -311; z <= -306; z++) {
						overworld.setBlock(new net.minecraft.core.BlockPos(x, 99, z),
								net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 18);
					}
				}
				// An open cave on the Nether side, so the screenshot shows the highlight rather than netherrack.
				var nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
				for (int x = 122; x <= 140; x++) {
					for (int z = -46; z <= -26; z++) {
						for (int y = 69; y <= 80; y++) {
							nether.setBlock(new net.minecraft.core.BlockPos(x, y, z), y == 69
									? net.minecraft.world.level.block.Blocks.NETHERRACK.defaultBlockState()
									: net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 18);
						}
					}
				}
			});
			// Stand 3 blocks south of the portal, facing north at it, and run the command like a player.
			world.getServer().runCommand("tp @a 1041.0 100 -309 180 5");
			context.waitTicks(10);
			context.runOnClient(client -> client.player.connection.sendCommand("cway portal"));
			context.waitFor(client -> chatText(client).contains("Portal at 1040 100 -312 (2 wide × 3 tall, facing north–south)"));
			world.getServer().runCommand("execute in minecraft:the_nether run tp @a 131.0 70 -31.5 180 2");
			context.waitFor(client -> client.level != null
					&& client.level.dimension() == net.minecraft.world.level.Level.NETHER, 400);
			world.getClientWorld().waitForChunksRender();
			context.runOnClient(client -> {
				client.gui.getChat().clearMessages(false); // a clean view of the highlight
				client.getToastManager().clear();
			});
			// The spot is shown as ghost blocks: 6 for the 2 × 3 opening and 14 for the frame, only in this game.
			context.waitFor(client -> client.level.getEntitiesOfClass(net.minecraft.world.entity.Display.BlockDisplay.class,
					new net.minecraft.world.phys.AABB(125, 60, -45, 137, 80, -33)).size() == 20, 200);
			context.waitTicks(10);
			shot(context, "portal-guide");
			world.getServer().runCommand("execute in minecraft:overworld run tp @a 0 -60 0");
			context.waitFor(client -> client.level != null
					&& client.level.dimension() == net.minecraft.world.level.Level.OVERWORLD, 400);
			world.getClientWorld().waitForChunksRender();

			// The keybind under Options > Controls > Key Binds (modded categories are at the bottom).
			context.setScreen(() -> new KeyBindsScreen(null, net.minecraft.client.Minecraft.getInstance().options));
			context.waitTicks(3);
			context.runOnClient(client -> client.screen.children().stream()
					.filter(child -> child instanceof KeyBindsList)
					.forEach(child -> ((KeyBindsList) child).setScrollAmount(Double.MAX_VALUE)));
			context.waitTicks(3);
			shot(context, "keybind");
			System.out.println("[SharedWaypoints test] PASSED");
			context.setScreen(() -> null);
		}
	}
}
