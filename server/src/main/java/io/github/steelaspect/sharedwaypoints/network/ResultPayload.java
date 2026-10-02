package io.github.steelaspect.sharedwaypoints.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: the outcome of an {@link ActionPayload}, shown as the status line in the menu.
 *
 * @param success whether the action worked
 * @param message what the command replied (e.g. "Added waypoint Farm" or the error)
 */
public record ResultPayload(boolean success, String message) implements CustomPacketPayload {
	public static final Type<ResultPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("sharedwaypoints", "result3"));
	public static final StreamCodec<FriendlyByteBuf, ResultPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeBoolean(payload.success);
				buf.writeUtf(payload.message, 1024);
			},
			buf -> new ResultPayload(buf.readBoolean(), buf.readUtf(1024)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
