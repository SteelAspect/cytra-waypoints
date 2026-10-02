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
import io.github.steelaspect.sharedwaypoints.waypoint.ProjectStatus;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import io.github.steelaspect.sharedwaypoints.xaero.XaeroShareFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
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
	/**
	 * Waypoints in the first packet of a full sync; the rest follow as upserts. Keeps every packet far below the
	 * protocol's size and count limits however many waypoints the server has (one waypoint is under 1 KB).
	 */
	public static final int FULL_SYNC_CHUNK = 1000;

	/** Subscribed players and how to send to them. Only touched on the server thread. */
	private final Map<UUID, Consumer<CustomPacketPayload>> subscribers = new LinkedHashMap<>();
	/** Players whose client said hello this session, whatever its protocol version. */
	private final Set<UUID> greeted = new HashSet<>();
	/** Whether a subscribed player may still see the waypoints. Replaceable so the GameTest can revoke access. */
	private Predicate<ServerPlayer> canView = WaypointPermissions::canView;

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
			greeted.clear();
		}
	}

	/**
	 * Handles a client's hello. Public so the GameTest can drive it with a recording {@code sender} instead of a
	 * real network connection.
	 */
	public void onHello(ServerPlayer player, HelloPayload hello, Consumer<CustomPacketPayload> sender) {
		String name = player.getGameProfile().name();
		greeted.add(player.getUUID());
		if (hello.protocolVersion() != SyncProtocol.VERSION) {
			SharedWaypoints.LOGGER.info("{} has sharedwaypoints-client {} (sync protocol {}); this server speaks protocol {}, "
					+ "so their Xaero's Minimap won't be synced", name, hello.modVersion(), hello.protocolVersion(), SyncProtocol.VERSION);
			sender.accept(new WelcomePayload(SyncProtocol.VERSION, false));
			return;
		}
		boolean enabled = mod.config().syncToClientMod && canView.test(player);
		sender.accept(new WelcomePayload(SyncProtocol.VERSION, enabled));
		if (!enabled) {
			return;
		}
		subscribers.put(player.getUUID(), sender);
		fullSync().forEach(sender);
		SharedWaypoints.LOGGER.info("Syncing shared waypoints to {}'s Xaero's Minimap (sharedwaypoints-client {})",
				name, hello.modVersion());
	}

	/** The player left: stop sending to them. */
	public void forget(UUID playerId) {
		subscribers.remove(playerId);
		greeted.remove(playerId);
	}

	public boolean isSubscribed(UUID playerId) {
		return subscribers.containsKey(playerId);
	}

	/** Whether this player's client said hello this session (subscribed or not, e.g. a protocol mismatch). */
	public boolean hasSaidHello(UUID playerId) {
		return greeted.contains(playerId);
	}

	/** For the GameTest: who counts as allowed to see the waypoints. */
	public void setViewCheckForTest(Predicate<ServerPlayer> check) {
		canView = check == null ? WaypointPermissions::canView : check;
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
			fullSync().forEach(this::broadcast);
		}
	}

	// ---------------------------------------------------------------- helpers

	/**
	 * The whole list: a {@link FullSyncPayload} with the first {@link #FULL_SYNC_CHUNK} waypoints (it replaces what
	 * the client had), then one {@link UpsertPayload} for each of the rest. Clients from 2.0.0 handle this unchanged.
	 */
	public List<CustomPacketPayload> fullSync() {
		return fullSync(FULL_SYNC_CHUNK);
	}

	/** {@link #fullSync()} with another first-packet size, so the GameTest can check the split with a few waypoints. */
	public List<CustomPacketPayload> fullSync(int chunk) {
		List<SyncedWaypoint> all = mod.waypoints().all().stream().map(SyncService::toSynced).toList();
		List<CustomPacketPayload> packets = new ArrayList<>();
		packets.add(new FullSyncPayload(SyncProtocol.VERSION, all.subList(0, Math.min(all.size(), chunk))));
		all.stream().skip(chunk).forEach(waypoint -> packets.add(new UpsertPayload(SyncProtocol.VERSION, waypoint)));
		return packets;
	}

	/**
	 * Everything the client needs for Xaero, including what depends on the server's categories. A waypoint marked
	 * Broken gets {@code !} as its Xaero symbol, so it stands out on the minimap.
	 */
	public static SyncedWaypoint toSynced(Waypoint waypoint) {
		boolean broken = waypoint.status() != null && waypoint.status().state() == ProjectStatus.State.BROKEN;
		String initials = broken ? BROKEN_INITIALS : XaeroShareFormat.initials(waypoint.name());
		return new SyncedWaypoint(waypoint.id(), clip(waypoint.name()), clip(initials),
				clip(waypoint.category().id()), clip(waypoint.category().displayName()), waypoint.category().xaeroColorIndex(),
				waypoint.x(), waypoint.y(), waypoint.z(), clip(waypoint.dimension()),
				waypoint.description() == null ? null : clip(waypoint.description()));
	}

	/** Xaero symbol of a waypoint marked Broken. */
	public static final String BROKEN_INITIALS = "!";

	/** Never more than the protocol allows, even from a hand-edited waypoints.json (too long would kick the player). */
	private static String clip(String text) {
		return text.length() <= SyncedWaypoint.MAX_TEXT ? text : text.substring(0, SyncedWaypoint.MAX_TEXT);
	}

	private void broadcast(CustomPacketPayload payload) {
		if (!mod.config().syncToClientMod) {
			return;
		}
		dropPlayersWhoLostAccess();
		subscribers.values().forEach(sender -> sender.accept(payload));
	}

	/**
	 * Someone whose view permission was taken away while online (e.g. with LuckPerms) gets no more waypoints: their
	 * client is told sync is off and they're unsubscribed.
	 */
	private void dropPlayersWhoLostAccess() {
		if (server == null) {
			return;
		}
		var iterator = subscribers.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player != null && !canView.test(player)) {
				entry.getValue().accept(new WelcomePayload(SyncProtocol.VERSION, false));
				iterator.remove();
			}
		}
	}

	private void send(UUID playerId, CustomPacketPayload payload) {
		// Look the player up every time: respawning creates a new ServerPlayer object.
		ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
		if (player != null && ServerPlayNetworking.canSend(player, payload.type())) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
