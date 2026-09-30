package io.github.steelaspect.sharedwaypoints.protocol;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: a waypoint was removed.
 *
 * @param protocolVersion the server's {@link SyncProtocol#VERSION}
 * @param id              id of the removed waypoint
 */
public record DeletePayload(int protocolVersion, UUID id) implements CustomPacketPayload {
	public static final Type<DeletePayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath(SyncProtocol.NAMESPACE, "sync_delete"));
	public static final StreamCodec<FriendlyByteBuf, DeletePayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeVarInt(payload.protocolVersion);
				buf.writeUUID(payload.id);
			},
			buf -> new DeletePayload(buf.readVarInt(), buf.readUUID()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
