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
 */
public record SyncPayload(List<CategoryData> categories, List<WaypointData> waypoints, boolean canAdd,
		boolean canTeleport, UUID navigatingTo) implements CustomPacketPayload {

	public static final Type<SyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("sharedwaypoints", "sync"));
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
		return new SyncPayload(List.copyOf(categories), List.copyOf(waypoints), canAdd, canTeleport, navigatingTo);
	}
}
