package io.github.steelaspect.sharedwaypoints.clientgametest;

import io.github.steelaspect.sharedwaypoints.SharedWaypoints;
import io.github.steelaspect.sharedwaypoints.client.AddWaypointScreen;
import io.github.steelaspect.sharedwaypoints.client.ClientWaypoints;
import io.github.steelaspect.sharedwaypoints.client.SharedWaypointsClient;
import io.github.steelaspect.sharedwaypoints.client.WaypointMenuScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Runs the real client: creates a singleplayer world (so the mod is on both sides), adds waypoints, opens the menu
 * with its keybind, adds a waypoint through the form, and saves screenshots of each step.
 */
public class MenuClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
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
			System.out.println("[SharedWaypoints test] PASSED");
			context.setScreen(() -> null);
		}
	}
}
