package io.github.steelaspect.sharedwaypoints.servertest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

/**
 * Runs on a production dedicated server whose mods folder holds the built SharedWaypoints jar (the same one players
 * install) and Fabric API, and nothing else. Once the server has started it checks that SharedWaypoints loaded, that
 * Xaero's Minimap isn't needed, and that /cway is registered, then stops the server. Any problem exits with status 1.
 */
public class InstalledServerJarTest implements ModInitializer {
	/** A server that never finishes starting fails the test instead of hanging the build. */
	private static final long TIMEOUT_MILLIS = 10 * 60 * 1000;

	@Override
	public void onInitialize() {
		Thread watchdog = new Thread(() -> {
			try {
				Thread.sleep(TIMEOUT_MILLIS);
				fail("the server didn't finish starting within 10 minutes");
			} catch (InterruptedException e) {
				// The test finished.
			}
		}, "SharedWaypoints server test watchdog");
		watchdog.setDaemon(true);
		watchdog.start();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				check(server);
				log("PASSED");
				watchdog.interrupt();
				server.halt(false);
			} catch (RuntimeException | AssertionError e) {
				e.printStackTrace();
				fail(e.getMessage());
			}
		});
	}

	private static void check(MinecraftServer server) {
		FabricLoader loader = FabricLoader.getInstance();
		if (!loader.isModLoaded("sharedwaypoints")) {
			throw new AssertionError("SharedWaypoints didn't load from the installed jar");
		}
		if (loader.isModLoaded("xaerominimap")) {
			throw new AssertionError("this test must run without Xaero's Minimap");
		}
		log("SharedWaypoints " + loader.getModContainer("sharedwaypoints").orElseThrow().getMetadata().getVersion()
				.getFriendlyString() + " loaded on a dedicated server without Xaero's Minimap");
		if (server.getCommands().getDispatcher().getRoot().getChild("cway") == null) {
			throw new AssertionError("/cway isn't registered");
		}
		log("/cway is registered");
	}

	private static void fail(String reason) {
		log("FAILED: " + reason);
		Runtime.getRuntime().halt(1);
	}

	private static void log(String message) {
		System.out.println("[SharedWaypoints server install test] " + message);
	}
}
