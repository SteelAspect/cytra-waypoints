package io.github.steelaspect.sharedwaypoints;

import io.github.steelaspect.sharedwaypoints.command.WaypointCommand;
import io.github.steelaspect.sharedwaypoints.network.MenuNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrypoint for Cytra Waypoints.
 *
 * <p>Everything here runs on the server only: the mod registers vanilla Brigadier commands and sends vanilla chat
 * components, boss bars, particles and titles, so unmodded clients (and Xaero's Minimap clients) can join without
 * installing it. BlueMap and squaremap are optional: markers are added only when one of them is installed.
 */
public final class SharedWaypoints implements ModInitializer {
	/** Last-seen times are written at most once a minute (and when the server stops). */
	private static final int SAVE_SEEN_EVERY_TICKS = 20 * 60;
	public static final String MOD_ID = "sharedwaypoints";
	public static final Logger LOGGER = LoggerFactory.getLogger("Cytra Waypoints");

	private static ModContext context;

	@Override
	public void onInitialize() {
		context = new ModContext(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID));

		// Optional client menu: payload types must be registered on both sides; only modded clients use them.
		MenuNetworking.registerPayloads();
		context.menus().registerReceiver();
		// Optional Xaero's Minimap sync for players with Cytra Waypoints on their game (handshake first; see SyncService).
		context.sync().registerReceiver();

		// (Re)load from disk every time a server starts. In singleplayer this runs for each world opened.
		ServerLifecycleEvents.SERVER_STARTING.register(server -> context.load());
		// Web maps (BlueMap, squaremap) set up their worlds while the server starts, so hook in afterwards.
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			context.menus().setServer(server);
			context.sync().setServer(server);
			context.maps().start(server);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			context.navigation().clear();
			context.portalGuide().clear();
			context.maps().stop();
			context.menus().setServer(null);
			context.sync().setServer(null);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			context.navigation().tick(server);
			context.clientModTip().tick(server);
			context.portalGuide().tick(server);
			if (server.getTickCount() % SAVE_SEEN_EVERY_TICKS == 0) {
				context.waypoints().saveSeenIfChanged();
			}
		});
		// Players are disconnected after SERVER_STOPPING, so their last-seen times are written once they're all gone.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> context.waypoints().saveSeenIfChanged());
		// "N new waypoints since you last played" (last-seen times are kept in waypoints.json).
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> context.joinSummary().onJoin(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			context.navigation().stop(handler.getPlayer().getUUID());
			context.sync().forget(handler.getPlayer().getUUID());
			context.clientModTip().forget(handler.getPlayer().getUUID());
			context.portalGuide().forget(handler.getPlayer().getUUID());
			context.joinSummary().onLeave(handler.getPlayer().getUUID());
		});

		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registryAccess, environment) -> WaypointCommand.register(dispatcher, context));

		LOGGER.info("Cytra Waypoints initialised");
	}

	/** The running mod state, for integrations and tests. */
	public static ModContext context() {
		return context;
	}
}
