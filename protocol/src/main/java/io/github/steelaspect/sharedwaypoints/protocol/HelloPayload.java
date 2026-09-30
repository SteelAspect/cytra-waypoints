package io.github.steelaspect.sharedwaypoints.protocol;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server, once per join: "I have the client mod and speak this protocol version". Only players who send
 * this ever receive sync payloads.
 *
 * @param protocolVersion the client's {@link SyncProtocol#VERSION}
 * @param modVersion      the client mod's version, for the server log
 */
public record HelloPayload(int protocolVersion, String modVersion) implements CustomPacketPayload {
	public static final Type<HelloPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath(SyncProtocol.NAMESPACE, "sync_hello"));
	public static final StreamCodec<FriendlyByteBuf, HelloPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeVarInt(payload.protocolVersion);
				buf.writeUtf(payload.modVersion, 64);
			},
			buf -> new HelloPayload(buf.readVarInt(), buf.readUtf(64)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
