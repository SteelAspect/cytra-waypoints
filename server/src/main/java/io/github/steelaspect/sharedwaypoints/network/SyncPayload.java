package io.github.steelaspect.sharedwaypoints.network;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.resources.ResourceLocation;

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
		implements FabricPacket {

	/**
	 * The channel names carry the menu protocol version ({@code sync3} since project status in 2.2.0), so a
	 * client and server with different versions never try to read each other's data: the menu just isn't offered.
	 */
	public static final PacketType<SyncPayload> TYPE =
			PacketType.create(new ResourceLocation("sharedwaypoints", "sync3"), SyncPayload::read);
	/** 1.20.4 caps a custom payload at 1 MiB (no split packets); a few thousand waypoints still fit, see {@link #fitted()}. */
	public static final int MAX_SIZE = 1024 * 1024;

	/** What a snapshot may take, leaving room for the packet's own header inside {@link #MAX_SIZE}. */
	static final int BUDGET = MAX_SIZE - 16 * 1024;

	/** A category: id, display name and chat colour id (0-15). */
	public record CategoryData(String id, String name, int colorId) {
	}

	/**
	 * One waypoint as the menu shows it.
	 *
	 * @param canEdit   may rename / describe it
	 * @param canRemove may remove it
	 * @param favorite  is one of this player's favourites
	 * @param status    its project status, or null
	 * @param canSetStatus may set or clear the status
	 */
	public record WaypointData(UUID id, String name, String categoryId, int x, int y, int z, String dimension,
			String description, String creatorName, long createdEpochSecond, boolean canEdit, boolean canRemove,
			boolean favorite, StatusData status, boolean canSetStatus) {
	}

	/** A project status: state id ({@code broken}, {@code wip}, ...), optional note, who set it and when. */
	public record StatusData(String state, String note, String setByName, long setAtEpochSecond) {
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

	/** Bytes this snapshot takes on the wire. */
	public int encodedSize() {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		try {
			write(buf, this);
			return buf.writerIndex();
		} finally {
			buf.release();
		}
	}

	/**
	 * This snapshot, small enough to send on 1.20.4 (a bigger one would fail to encode and disconnect the player).
	 * Over {@link #BUDGET} it first leaves out descriptions and status notes, then the last waypoints and routes.
	 * Returns this snapshot unchanged when it already fits.
	 */
	public SyncPayload fitted() {
		if (encodedSize() <= BUDGET) {
			return this;
		}
		List<WaypointData> slim = waypoints.stream().map(w -> new WaypointData(w.id(), w.name(), w.categoryId(), w.x(),
				w.y(), w.z(), w.dimension(), null, w.creatorName(), w.createdEpochSecond(), w.canEdit(), w.canRemove(),
				w.favorite(), w.status() == null ? null
						: new StatusData(w.status().state(), null, w.status().setByName(), w.status().setAtEpochSecond()),
				w.canSetStatus())).toList();
		List<RouteData> slimRoutes = routes.stream().map(r -> new RouteData(r.id(), r.name(), r.stops(), null,
				r.creatorName(), r.canEdit(), r.canRemove())).toList();
		SyncPayload fitted = new SyncPayload(categories, slim, canAdd, canTeleport, navigatingTo, slimRoutes,
				canAddRoute, onRoute);
		while (fitted.encodedSize() > BUDGET && !(fitted.waypoints.isEmpty() && fitted.routes.isEmpty())) {
			List<WaypointData> fewer = fitted.waypoints.subList(0, fitted.waypoints.size() * 9 / 10);
			List<RouteData> fewerRoutes = fitted.routes.subList(0, fitted.routes.size() * 9 / 10);
			fitted = new SyncPayload(categories, List.copyOf(fewer), canAdd, canTeleport, navigatingTo,
					List.copyOf(fewerRoutes), canAddRoute, onRoute);
		}
		return fitted;
	}

	@Override
	public PacketType<SyncPayload> getType() {
		return TYPE;
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		write(buf, this);
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
			buf.writeByte((waypoint.canEdit() ? 1 : 0) | (waypoint.canRemove() ? 2 : 0) | (waypoint.favorite() ? 4 : 0)
					| (waypoint.canSetStatus() ? 8 : 0));
			buf.writeNullable(waypoint.status(), (out, status) -> {
				out.writeUtf(status.state());
				out.writeNullable(status.note(), FriendlyByteBuf::writeUtf);
				out.writeUtf(status.setByName());
				out.writeVarLong(status.setAtEpochSecond());
			});
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

	public static SyncPayload read(FriendlyByteBuf buf) {
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
			StatusData status = buf.readNullable(in -> new StatusData(in.readUtf(),
					in.readNullable(FriendlyByteBuf::readUtf), in.readUtf(), in.readVarLong()));
			waypoints.add(new WaypointData(id, name, categoryId, x, y, z, dimension, description, creator, created,
					(flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, status, (flags & 8) != 0));
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
