package io.github.steelaspect.sharedwaypoints;

import io.github.steelaspect.sharedwaypoints.command.WaypointCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrypoint for SharedWaypoints.
 *
 * <p>Everything here runs on the server only: the mod registers vanilla Brigadier commands and sends vanilla chat
 * components, boss bars, particles and titles, so unmodded clients (and Xaero's Minimap clients) can join without
 * installing it.
 */
public final class SharedWaypoints implements ModInitializer {
	public static final String MOD_ID = "sharedwaypoints";
	public static final Logger LOGGER = LoggerFactory.getLogger("SharedWaypoints");

	private static ModContext context;

	@Override
	public void onInitialize() {
		context = new ModContext(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID));

		// (Re)load from disk every time a server starts. In singleplayer this runs for each world opened.
		ServerLifecycleEvents.SERVER_STARTING.register(server -> context.load());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> context.navigation().clear());
		ServerTickEvents.END_SERVER_TICK.register(server -> context.navigation().tick(server));
		ServerPlayConnectionEvents.DISCONNECT.register(
				(handler, server) -> context.navigation().stop(handler.getPlayer().getUUID()));

		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> WaypointCommand.register(dispatcher, context));

		LOGGER.info("SharedWaypoints initialised");
	}

	/** The running mod state, for integrations and tests. */
	public static ModContext context() {
		return context;
	}
}
