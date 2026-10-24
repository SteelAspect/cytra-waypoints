package io.github.steelaspect.sharedwaypoints.protocol;

import net.minecraft.network.FriendlyByteBuf;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: a waypoint was added or edited (renamed, described, recategorised). Matched by id, so a rename
 * replaces the old entry instead of adding a second one.
 *
 * @param protocolVersion the server's {@link SyncProtocol#VERSION}
 * @param waypoint        the waypoint as it is now
 */
public record UpsertPayload(int protocolVersion, SyncedWaypoint waypoint) implements FabricPacket {
	public static final PacketType<UpsertPayload> TYPE =
			PacketType.create(new ResourceLocation(SyncProtocol.NAMESPACE, "sync_upsert"), UpsertPayload::read);

	@Override
	public PacketType<UpsertPayload> getType() {
		return TYPE;
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(protocolVersion);
		waypoint.write(buf);
	}

	public static UpsertPayload read(FriendlyByteBuf buf) {
		return new UpsertPayload(buf.readVarInt(), SyncedWaypoint.read(buf));
	}
}
