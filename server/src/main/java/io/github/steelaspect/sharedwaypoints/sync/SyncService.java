package io.github.steelaspect.sharedwaypoints.sync;

import io.github.steelaspect.sharedwaypoints.ModContext;
import io.github.steelaspect.sharedwaypoints.SharedWaypoints;
import io.github.steelaspect.sharedwaypoints.permission.WaypointPermissions;
import io.github.steelaspect.sharedwaypoints.protocol.DeletePayload;
import io.github.steelaspect.sharedwaypoints.protocol.FullSyncPayload;
import io.github.steelaspect.sharedwaypoints.protocol.HelloPayload;
import io.github.steelaspect.sharedwaypoints.protocol.SyncProtocol;
import io.github.steelaspect.sharedwaypoints.protocol.SyncedWaypoint;
import io.github.steelaspect.sharedwaypoints.protocol.UpsertPayload;
import io.github.steelaspect.sharedwaypoints.protocol.WelcomePayload;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import io.github.steelaspect.sharedwaypoints.xaero.XaeroShareFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of the optional client-mod sync (see {@link SyncProtocol}).
 *
 * <p>Only players whose client sent a matching {@link HelloPayload} are subscribed. They get the full list right
 * away and then every add, edit and delete. Everyone else gets nothing from here, so vanilla and Xaero-only players
 * keep exactly the v1 behaviour (chat buttons).
 */
public final class SyncService {
	private final ModContext mod;
	private MinecraftServer server;
	/** Subscribed players and how to send to them. Only touched on the server thread. */
	private final Map<UUID, Consumer<CustomPacketPayload>> subscribers = new LinkedHashMap<>();

	public SyncService(ModContext mod) {
		this.mod = mod;
	}

	/** Registers the payload types and the hello receiver. Called once from the mod initializer. */
	public void registerReceiver() {
		SyncProtocol.register();
		ServerPlayNetworking.registerGlobalReceiver(HelloPayload.TYPE, (payload, context) -> {
			UUID playerId = context.player().getUUID();
			onHello(context.player(), payload, out -> send(playerId, out));
		});
	}

	public void setServer(MinecraftServer server) {
		this.server = server;
		if (server == null) {
			subscribers.clear();
		}
	}

	/**
	 * Handles a client's hello. Public so the GameTest can drive it with a recording {@code sender} instead of a
	 * real network connection.
	 */
	public void onHello(ServerPlayer player, HelloPayload hello, Consumer<CustomPacketPayload> sender) {
		String name = player.getGameProfile().name();
		if (hello.protocolVersion() != SyncProtocol.VERSION) {
			SharedWaypoints.LOGGER.info("{} has sharedwaypoints-client {} (sync protocol {}); this server speaks protocol {}, "
					+ "so their Xaero's Minimap won't be synced", name, hello.modVersion(), hello.protocolVersion(), SyncProtocol.VERSION);
			sender.accept(new WelcomePayload(SyncProtocol.VERSION, false));
			return;
		}
		boolean enabled = mod.config().syncToClientMod && WaypointPermissions.canView(player);
		sender.accept(new WelcomePayload(SyncProtocol.VERSION, enabled));
		if (!enabled) {
			return;
		}
		subscribers.put(player.getUUID(), sender);
		sender.accept(fullSync());
		SharedWaypoints.LOGGER.info("Syncing shared waypoints to {}'s Xaero's Minimap (sharedwaypoints-client {})",
				name, hello.modVersion());
	}

	/** The player left: stop sending to them. */
	public void forget(UUID playerId) {
		subscribers.remove(playerId);
	}

	public boolean isSubscribed(UUID playerId) {
		return subscribers.containsKey(playerId);
	}

	// ----------------------------------------------------------------- events

	/** A waypoint was added or edited. */
	public void onSaved(Waypoint waypoint) {
		broadcast(new UpsertPayload(SyncProtocol.VERSION, toSynced(waypoint)));
	}

	/** A waypoint was removed. */
	public void onRemoved(Waypoint waypoint) {
		broadcast(new DeletePayload(SyncProtocol.VERSION, waypoint.id()));
	}

	/** The list was reloaded ({@code /cway reload}): categories or colours may have changed, so resend it all. */
	public void onLoaded() {
		if (!subscribers.isEmpty()) {
			broadcast(fullSync());
		}
	}

	// ---------------------------------------------------------------- helpers

	public FullSyncPayload fullSync() {
		return new FullSyncPayload(SyncProtocol.VERSION, mod.waypoints().all().stream().map(SyncService::toSynced).toList());
	}

	/** Everything the client needs for Xaero, including what depends on the server's categories. */
	public static SyncedWaypoint toSynced(Waypoint waypoint) {
		return new SyncedWaypoint(waypoint.id(), waypoint.name(), XaeroShareFormat.initials(waypoint.name()),
				waypoint.category().id(), waypoint.category().displayName(), waypoint.category().xaeroColorIndex(),
				waypoint.x(), waypoint.y(), waypoint.z(), waypoint.dimension(), waypoint.description());
	}

	private void broadcast(CustomPacketPayload payload) {
		if (!mod.config().syncToClientMod) {
			return;
		}
		subscribers.values().forEach(sender -> sender.accept(payload));
	}

	private void send(UUID playerId, CustomPacketPayload payload) {
		// Look the player up every time: respawning creates a new ServerPlayer object.
		ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
		if (player != null && ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
