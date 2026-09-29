package io.github.steelaspect.sharedwaypoints;

import io.github.steelaspect.sharedwaypoints.command.WaypointCommand;
import io.github.steelaspect.sharedwaypoints.waypoint.WaypointStore;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrypoint for SharedWaypoints.
 *
 * <p>Everything here runs on the server only: the mod registers vanilla Brigadier commands and sends
 * vanilla chat components, so unmodded clients (and Xaero's Minimap clients) can join without installing it.
 */
public final class SharedWaypoints implements ModInitializer {
	public static final String MOD_ID = "sharedwaypoints";
	public static final Logger LOGGER = LoggerFactory.getLogger("SharedWaypoints");

	@Override
	public void onInitialize() {
		WaypointStore store = new WaypointStore(
				FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("waypoints.json"));

		// (Re)load from disk every time a server starts. In singleplayer this runs for each world opened.
		ServerLifecycleEvents.SERVER_STARTING.register(server -> store.load());

		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> WaypointCommand.register(dispatcher, store));

		LOGGER.info("SharedWaypoints initialised");
	}
}
