package io.github.steelaspect.sharedwaypoints.protocol;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: every shared waypoint. Sent after the handshake and after {@code /waypoints reload}. The client
 * makes its "Shared" set match this list exactly (adding, updating and removing as needed).
 *
 * @param protocolVersion the server's {@link SyncProtocol#VERSION}
 * @param waypoints       all shared waypoints
 */
public record FullSyncPayload(int protocolVersion, List<SyncedWaypoint> waypoints) implements CustomPacketPayload {
	public static final Type<FullSyncPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath(SyncProtocol.NAMESPACE, "sync_full"));
	/** A few thousand waypoints fit easily. */
	public static final int MAX_SIZE = 4 * 1024 * 1024;
	/** More than any real server has; stops a broken payload from allocating a huge list. */
	static final int MAX_WAYPOINTS = 20_000;

	public static final StreamCodec<FriendlyByteBuf, FullSyncPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeVarInt(payload.protocolVersion);
				buf.writeVarInt(payload.waypoints.size());
				payload.waypoints.forEach(waypoint -> waypoint.write(buf));
			},
			buf -> {
				int version = buf.readVarInt();
				int count = buf.readVarInt();
				if (count < 0 || count > MAX_WAYPOINTS) {
					throw new IllegalArgumentException("Too many waypoints: " + count);
				}
				List<SyncedWaypoint> waypoints = new ArrayList<>(count);
				for (int i = 0; i < count; i++) {
					waypoints.add(SyncedWaypoint.read(buf));
				}
				return new FullSyncPayload(version, List.copyOf(waypoints));
			});

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
