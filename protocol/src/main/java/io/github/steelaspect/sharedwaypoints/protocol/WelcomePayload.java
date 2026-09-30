package io.github.steelaspect.sharedwaypoints.protocol;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client, the answer to {@link HelloPayload}.
 *
 * @param protocolVersion the server's {@link SyncProtocol#VERSION}; the client only syncs when it equals its own
 * @param syncEnabled     whether the server owner has sync turned on; if not, nothing else follows
 */
public record WelcomePayload(int protocolVersion, boolean syncEnabled) implements CustomPacketPayload {
	public static final Type<WelcomePayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath(SyncProtocol.NAMESPACE, "sync_welcome"));
	public static final StreamCodec<FriendlyByteBuf, WelcomePayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeVarInt(payload.protocolVersion);
				buf.writeBoolean(payload.syncEnabled);
			},
			buf -> new WelcomePayload(buf.readVarInt(), buf.readBoolean()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
