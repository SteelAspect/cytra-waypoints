package io.github.steelaspect.sharedwaypoints.prodtest;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.PauseScreen;

/**
 * Runs in a production game (real Fabric Loader, remapped jars) whose mods folder holds only what a player installs:
 * the built sharedwaypoints-client jar, Fabric API and (unless run with -PwithoutXaero) Xaero's Minimap. Checks that
 * this one jar gives the player the waypoint menu: the J keybind, the Esc menu button, and the menu itself; and,
 * without Xaero, that the server tells the player to add it.
 */
public class InstalledClientJarTest implements FabricClientGameTest {
	private static final String XAERO_TIP = "Your sharedwaypoints-client can't reach Xaero's Minimap.";
	/** Chat lines from the server, as the player sees them. */
	private final List<String> chat = new CopyOnWriteArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> chat.add(message.getString()));
		boolean xaero = FabricLoader.getInstance().isModLoaded("xaerominimap");
		for (String mod : new String[] {"sharedwaypoints-client", "sharedwaypoints"}) {
			if (!FabricLoader.getInstance().isModLoaded(mod)) {
				throw new AssertionError("Mod not loaded from the installed jars: " + mod);
			}
		}
		log("the client jar loads SharedWaypoints (menu) and the Xaero sync" + (xaero ? "" : "; no Xaero's Minimap installed"));

		boolean hasKey = context.computeOnClient(client -> Arrays.stream(client.options.keyMappings)
				.anyMatch(key -> key.getName().equals("key.sharedwaypoints.open_menu")));
		if (!hasKey) {
			throw new AssertionError("No \"open waypoint menu\" keybind under Options > Controls");
		}
		log("the J keybind is registered");

		// Same load settings as the other client tests: CI renders in software on 2 vCPUs.
		context.runOnClient(client -> {
			client.options.renderDistance().set(2);
			client.options.simulationDistance().set(5);
			client.options.menuBackgroundBlurriness().set(0);
			client.options.framerateLimit().set(10);
		});
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.runOnClient(client -> client.options.framerateLimit().set(60));
			world.getClientWorld().waitForChunksRender();
			if (xaero) {
				context.waitTicks(140); // past the server's follow-up check: synced players get no tip
				if (chat.stream().anyMatch(line -> line.contains(XAERO_TIP))) {
					throw new AssertionError("Synced player was told to install Xaero's Minimap: " + chat);
				}
			} else {
				// The server sees the client mod on its sync channel, but no hello follows: it suggests Xaero.
				context.waitFor(client -> chat.stream().anyMatch(line -> line.contains(XAERO_TIP)), 600);
				log("without Xaero, the server tells the player to add it");
			}

			context.setScreen(() -> new PauseScreen(true));
			context.waitTicks(3);
			context.getInput().setCursorPos(1, 1);
			System.out.println("[SharedWaypoints install test] screenshot: "
					+ context.takeScreenshot("sharedwaypoints-installed-pause-menu"));
			context.clickScreenButton("✦ Waypoints");
			context.waitFor(client -> client.screen != null
					&& client.screen.getClass().getSimpleName().equals("WaypointMenuScreen"));
			log("the Esc menu button opens the waypoint menu");
			context.setScreen(() -> null);
		}
		log("PASSED");
	}

	private static void log(String message) {
		System.out.println("[SharedWaypoints install test] " + message);
	}
}
