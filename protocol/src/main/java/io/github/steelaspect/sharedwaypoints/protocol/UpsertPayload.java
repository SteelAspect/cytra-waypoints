package io.github.steelaspect.sharedwaypoints.protocol;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client: a waypoint was added or edited (renamed, described, recategorised). Matched by id, so a rename
 * replaces the old entry instead of adding a second one.
 *
 * @param protocolVersion the server's {@link SyncProtocol#VERSION}
 * @param waypoint        the waypoint as it is now
 */
public record UpsertPayload(int protocolVersion, SyncedWaypoint waypoint) implements CustomPacketPayload {
	public static final Type<UpsertPayload> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath(SyncProtocol.NAMESPACE, "sync_upsert"));
	public static final StreamCodec<FriendlyByteBuf, UpsertPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeVarInt(payload.protocolVersion);
				payload.waypoint.write(buf);
			},
			buf -> new UpsertPayload(buf.readVarInt(), SyncedWaypoint.read(buf)));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
