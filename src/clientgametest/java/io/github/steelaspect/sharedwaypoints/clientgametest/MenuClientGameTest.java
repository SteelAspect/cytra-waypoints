package io.github.steelaspect.sharedwaypoints.clientgametest;

import io.github.steelaspect.sharedwaypoints.SharedWaypoints;
import io.github.steelaspect.sharedwaypoints.client.AddWaypointScreen;
import io.github.steelaspect.sharedwaypoints.client.ClientWaypoints;
import io.github.steelaspect.sharedwaypoints.client.SharedWaypointsClient;
import io.github.steelaspect.sharedwaypoints.client.WaypointMenuScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.ConfirmScreen;
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
		System.out.println("[SharedWaypoints test] screenshot: " + context.takeScreenshot("sharedwaypoints-" + name));
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
			world.getServer().runCommand("waypoints add \"Main Storage\" storage 12 -60 -30");
			world.getServer().runCommand("waypoints describe \"Main Storage\" Sorted chests, bring shulkers");
			world.getServer().runCommand("waypoints add \"Iron Farm\" farms 140 -60 210");
			world.getServer().runCommand("waypoints add Hub portals 10 70 -20 minecraft:the_nether");
			world.getServer().runCommand("waypoints add \"Spawn Base\" bases 0 -60 0");

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
