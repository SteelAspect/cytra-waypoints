package io.github.steelaspect.sharedwaypoints.protocol;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: every shared waypoint. Sent after the handshake and after {@code /waypoints reload}. The client
 * makes its "Shared" set match this list exactly (adding, updating and removing as needed).
 *
 * @param protocolVersion the server's {@link SyncProtocol#VERSION}
 * @param waypoints       all shared waypoints
 */
public record FullSyncPayload(int protocolVersion, List<SyncedWaypoint> waypoints) implements FabricPacket {
	public static final PacketType<FullSyncPayload> TYPE =
			PacketType.create(new ResourceLocation(SyncProtocol.NAMESPACE, "sync_full"), FullSyncPayload::read);
	/** 1.20.4 caps a custom payload at 1 MiB (no split packets); a few thousand waypoints still fit. */
	public static final int MAX_SIZE = 1024 * 1024;
	/** More than any real server has; stops a broken payload from allocating a huge list. */
	static final int MAX_WAYPOINTS = 20_000;

	@Override
	public PacketType<FullSyncPayload> getType() {
		return TYPE;
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(protocolVersion);
		buf.writeVarInt(waypoints.size());
		waypoints.forEach(waypoint -> waypoint.write(buf));
	}

	public static FullSyncPayload read(FriendlyByteBuf buf) {
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
	}
}
