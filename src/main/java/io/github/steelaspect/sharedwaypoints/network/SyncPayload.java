package io.github.steelaspect.sharedwaypoints.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: everything the optional client menu shows, already filtered and personalised for that player
 * (favourites, what they may edit). The server stays the only source of truth; the client just displays this.
 *
 * @param categories    configured categories in display order
 * @param waypoints     every waypoint the player may see
 * @param canAdd        whether the player may add waypoints
 * @param canTeleport   whether to show Teleport
 * @param navigatingTo  waypoint the player is navigating to, or null
 * @param routes        every route, sorted by name
 * @param canAddRoute   whether the player may create routes
 * @param onRoute       the route the player is following and which stop they're heading to, or null
 */
public record SyncPayload(List<CategoryData> categories, List<WaypointData> waypoints, boolean canAdd,
		boolean canTeleport, UUID navigatingTo, List<RouteData> routes, boolean canAddRoute, RouteProgressData onRoute)
		implements CustomPacketPayload {

	/**
	 * The channel names carry the menu protocol version ({@code sync2} since routes were added in 1.5.0), so a
	 * client and server with different versions never try to read each other's data: the menu just isn't offered.
	 */
	public static final Type<SyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("sharedwaypoints", "sync2"));
	public static final StreamCodec<FriendlyByteBuf, SyncPayload> CODEC = StreamCodec.of(SyncPayload::write, SyncPayload::read);
	/** Largest payload accepted (a few thousand waypoints fit easily). */
	public static final int MAX_SIZE = 4 * 1024 * 1024;

	/** A category: id, display name and chat colour id (0-15). */
	public record CategoryData(String id, String name, int colorId) {
	}

	/**
	 * One waypoint as the menu shows it.
	 *
	 * @param canEdit   may rename / describe it
	 * @param canRemove may remove it
	 * @param favorite  is one of this player's favourites
	 */
	public record WaypointData(UUID id, String name, String categoryId, int x, int y, int z, String dimension,
			String description, String creatorName, long createdEpochSecond, boolean canEdit, boolean canRemove,
			boolean favorite) {
	}

	/**
	 * One route as the menu shows it.
	 *
	 * @param stops     waypoint ids in visiting order
	 * @param canEdit   may change stops, name and description
	 * @param canRemove may delete it
	 */
	public record RouteData(UUID id, String name, List<UUID> stops, String description, String creatorName,
			boolean canEdit, boolean canRemove) {
	}

	/** The route a player is following and the 0-based index of the stop they're heading to. */
	public record RouteProgressData(UUID routeId, int stopIndex) {
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static void write(FriendlyByteBuf buf, SyncPayload payload) {
		buf.writeVarInt(payload.categories.size());
		for (CategoryData category : payload.categories) {
			buf.writeUtf(category.id());
			buf.writeUtf(category.name());
			buf.writeVarInt(category.colorId());
		}
		buf.writeVarInt(payload.waypoints.size());
		for (WaypointData waypoint : payload.waypoints) {
			buf.writeUUID(waypoint.id());
			buf.writeUtf(waypoint.name());
			buf.writeUtf(waypoint.categoryId());
			buf.writeVarInt(waypoint.x());
			buf.writeVarInt(waypoint.y());
			buf.writeVarInt(waypoint.z());
			buf.writeUtf(waypoint.dimension());
			buf.writeNullable(waypoint.description(), FriendlyByteBuf::writeUtf);
			buf.writeUtf(waypoint.creatorName());
			buf.writeVarLong(waypoint.createdEpochSecond());
			buf.writeByte((waypoint.canEdit() ? 1 : 0) | (waypoint.canRemove() ? 2 : 0) | (waypoint.favorite() ? 4 : 0));
		}
		buf.writeBoolean(payload.canAdd);
		buf.writeBoolean(payload.canTeleport);
		buf.writeNullable(payload.navigatingTo, (out, id) -> out.writeUUID(id));
		buf.writeVarInt(payload.routes.size());
		for (RouteData route : payload.routes) {
			buf.writeUUID(route.id());
			buf.writeUtf(route.name());
			buf.writeVarInt(route.stops().size());
			route.stops().forEach(buf::writeUUID);
			buf.writeNullable(route.description(), FriendlyByteBuf::writeUtf);
			buf.writeUtf(route.creatorName());
			buf.writeByte((route.canEdit() ? 1 : 0) | (route.canRemove() ? 2 : 0));
		}
		buf.writeBoolean(payload.canAddRoute);
		buf.writeNullable(payload.onRoute, (out, progress) -> {
			out.writeUUID(progress.routeId());
			out.writeVarInt(progress.stopIndex());
		});
	}

	private static SyncPayload read(FriendlyByteBuf buf) {
		int categoryCount = buf.readVarInt();
		List<CategoryData> categories = new ArrayList<>(categoryCount);
		for (int i = 0; i < categoryCount; i++) {
			categories.add(new CategoryData(buf.readUtf(), buf.readUtf(), buf.readVarInt()));
		}
		int waypointCount = buf.readVarInt();
		List<WaypointData> waypoints = new ArrayList<>(waypointCount);
		for (int i = 0; i < waypointCount; i++) {
			UUID id = buf.readUUID();
			String name = buf.readUtf();
			String categoryId = buf.readUtf();
			int x = buf.readVarInt();
			int y = buf.readVarInt();
			int z = buf.readVarInt();
			String dimension = buf.readUtf();
			String description = buf.readNullable(FriendlyByteBuf::readUtf);
			String creator = buf.readUtf();
			long created = buf.readVarLong();
			byte flags = buf.readByte();
			waypoints.add(new WaypointData(id, name, categoryId, x, y, z, dimension, description, creator, created,
					(flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0));
		}
		boolean canAdd = buf.readBoolean();
		boolean canTeleport = buf.readBoolean();
		UUID navigatingTo = buf.readNullable(in -> in.readUUID());
		int routeCount = buf.readVarInt();
		List<RouteData> routes = new ArrayList<>(routeCount);
		for (int i = 0; i < routeCount; i++) {
			UUID id = buf.readUUID();
			String name = buf.readUtf();
			int stopCount = buf.readVarInt();
			List<UUID> stops = new ArrayList<>(stopCount);
			for (int j = 0; j < stopCount; j++) {
				stops.add(buf.readUUID());
			}
			String description = buf.readNullable(FriendlyByteBuf::readUtf);
			String creator = buf.readUtf();
			byte flags = buf.readByte();
			routes.add(new RouteData(id, name, List.copyOf(stops), description, creator, (flags & 1) != 0, (flags & 2) != 0));
		}
		boolean canAddRoute = buf.readBoolean();
		RouteProgressData onRoute = buf.readNullable(in -> new RouteProgressData(in.readUUID(), in.readVarInt()));
		return new SyncPayload(List.copyOf(categories), List.copyOf(waypoints), canAdd, canTeleport, navigatingTo,
				List.copyOf(routes), canAddRoute, onRoute);
	}
}
