package io.github.steelaspect.sharedwaypoints.xaerosync;

import io.github.steelaspect.sharedwaypoints.protocol.DeletePayload;
import io.github.steelaspect.sharedwaypoints.protocol.FullSyncPayload;
import io.github.steelaspect.sharedwaypoints.protocol.HelloPayload;
import io.github.steelaspect.sharedwaypoints.protocol.SyncProtocol;
import io.github.steelaspect.sharedwaypoints.protocol.UpsertPayload;
import io.github.steelaspect.sharedwaypoints.protocol.WelcomePayload;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * Client entrypoint of the optional Xaero's Minimap sync mod.
 *
 * <p>On joining a server it says hello (see {@link SyncProtocol}). Servers running SharedWaypoints 2.0+ answer
 * with the full waypoint list and then every change; everything else ignores the hello. What arrives is kept in
 * {@link SyncState} and written into Xaero's "Shared" waypoint set by {@link XaeroBridge} on the game thread, as
 * soon as Xaero is ready for the current world.
 */
public final class SharedWaypointsSync implements ClientModInitializer {
	private static final SyncState STATE = new SyncState();
	private static Optional<XaeroBridge> bridge = Optional.empty();
	/** The server accepted our hello with the same protocol version and has sync turned on. */
	private static boolean active;
	private static boolean mismatchLogged;
	/** The "where are my waypoints?" hint is shown once per game launch. */
	private static boolean hintShown;

	@Override
	public void onInitializeClient() {
		SyncProtocol.register();
		bridge = XaeroBridge.create();
		String version = FabricLoader.getInstance().getModContainer("sharedwaypoints-client")
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("?");

		// The receivers below are registered even without Xaero: listening on the sync channel tells the server this
		// client mod is installed, so it can suggest adding Xaero's Minimap. Only the hello asks for the waypoints,
		// and it's only sent when Xaero is there to write them into.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			reset();
			if (bridge.isPresent() && ClientPlayNetworking.canSend(HelloPayload.TYPE)) {
				ClientPlayNetworking.send(new HelloPayload(SyncProtocol.VERSION, version));
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());

		ClientPlayNetworking.registerGlobalReceiver(WelcomePayload.TYPE, (payload, context) -> {
			active = payload.protocolVersion() == SyncProtocol.VERSION && payload.syncEnabled();
			if (payload.protocolVersion() != SyncProtocol.VERSION && !mismatchLogged) {
				mismatchLogged = true;
				XaeroBridge.LOGGER.warn("This server's SharedWaypoints speaks sync protocol {} and this client mod speaks {}; "
						+ "update both to the same version to sync waypoints into Xaero's Minimap", payload.protocolVersion(),
						SyncProtocol.VERSION);
			} else if (!payload.syncEnabled()) {
				XaeroBridge.LOGGER.info("This server has waypoint sync turned off");
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(FullSyncPayload.TYPE, (payload, context) -> {
			if (accepts(payload.protocolVersion())) {
				STATE.acceptFull(payload.waypoints(), serverDimensions(context.client()));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(UpsertPayload.TYPE, (payload, context) -> {
			if (accepts(payload.protocolVersion())) {
				STATE.upsert(payload.waypoint());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(DeletePayload.TYPE, (payload, context) -> {
			if (accepts(payload.protocolVersion())) {
				STATE.delete(payload.id());
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> apply());
	}

	private static boolean accepts(int protocolVersion) {
		return active && protocolVersion == SyncProtocol.VERSION;
	}

	private static void reset() {
		STATE.clear();
		active = false;
	}

	/** Writes pending changes once Xaero is ready. Runs every client tick; cheap when there's nothing to do. */
	private static void apply() {
		if (!STATE.hasWork() || bridge.isEmpty()) {
			return;
		}
		XaeroBridge xaero = bridge.get();
		if (!xaero.isWorking()) {
			reset(); // already logged once
			return;
		}
		if (!xaero.isReady()) {
			return; // Xaero is still working out which world this is; try again next tick
		}
		for (String dimension : STATE.dirtyDimensions()) {
			if (xaero.replaceSharedSet(dimension, STATE.inDimension(dimension))) {
				STATE.done(dimension);
			}
		}
		if (!hintShown && STATE.size() > 0 && xaero.isWorking()) {
			hintShown = true;
			showHint();
		}
	}

	/**
	 * Xaero only draws the selected waypoint set unless "Render All WP Sets" is on (off by default), and the mod
	 * doesn't change Xaero's settings. So say once where the shared waypoints are.
	 */
	private static void showHint() {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			String count = STATE.size() == 1 ? "1 shared waypoint is" : STATE.size() + " shared waypoints are";
			client.player.displayClientMessage(net.minecraft.network.chat.Component.literal("✦ " + count
							+ " in Xaero's \"Shared\" waypoint set. ")
					.withStyle(net.minecraft.ChatFormatting.GOLD)
					.append(net.minecraft.network.chat.Component.literal("Select that set in Xaero's waypoint menu, or turn "
							+ "on \"Render All WP Sets\" in Xaero's settings to see them next to your own.")
							.withStyle(net.minecraft.ChatFormatting.GRAY)), false);
		}
	}

	/** Every dimension of the current server, so stale "Shared" waypoints are removed everywhere on join. */
	private static Set<String> serverDimensions(Minecraft client) {
		return client.getConnection() == null ? Set.of() : client.getConnection().levels().stream()
				.map(key -> key.identifier().toString()).collect(Collectors.toSet());
	}

	// ------------------------------------------------------------------ for tests and troubleshooting

	/** Whether this client is currently being synced by the server. */
	public static boolean isActive() {
		return active;
	}

	/** Whether every received change has been written into Xaero. */
	public static boolean isUpToDate() {
		return !STATE.hasWork();
	}

	/** The names in a dimension's "Shared" set in Xaero, or empty if there's no set (or no Xaero). */
	public static Optional<List<String>> sharedWaypointNames(String dimension) {
		return bridge.flatMap(xaero -> xaero.sharedNames(dimension));
	}

	/** Writes waypoints straight into a dimension's "Shared" set, bypassing the server. Only for the client test. */
	public static boolean writeSharedSetForTest(String dimension, List<io.github.steelaspect.sharedwaypoints.protocol.SyncedWaypoint> waypoints) {
		return bridge.map(xaero -> xaero.replaceSharedSet(dimension, waypoints)).orElse(false);
	}
}
