package io.github.steelaspect.sharedwaypoints.protocol;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: a waypoint was removed.
 *
 * @param protocolVersion the server's {@link SyncProtocol#VERSION}
 * @param id              id of the removed waypoint
 */
public record DeletePayload(int protocolVersion, UUID id) implements FabricPacket {
	public static final PacketType<DeletePayload> TYPE =
			PacketType.create(new ResourceLocation(SyncProtocol.NAMESPACE, "sync_delete"), DeletePayload::read);

	@Override
	public PacketType<DeletePayload> getType() {
		return TYPE;
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(protocolVersion);
		buf.writeUUID(id);
	}

	public static DeletePayload read(FriendlyByteBuf buf) {
		return new DeletePayload(buf.readVarInt(), buf.readUUID());
	}
}
