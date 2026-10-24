package io.github.steelaspect.sharedwaypoints.protocol;

import net.minecraft.network.FriendlyByteBuf;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server, once per join: "I have the mod and speak this protocol version". Only players who send
 * this ever receive sync payloads.
 *
 * @param protocolVersion the client's {@link SyncProtocol#VERSION}
 * @param modVersion      the player's Cytra Waypoints version, for the server log
 */
public record HelloPayload(int protocolVersion, String modVersion) implements FabricPacket {
	public static final PacketType<HelloPayload> TYPE =
			PacketType.create(new ResourceLocation(SyncProtocol.NAMESPACE, "sync_hello"), HelloPayload::read);

	@Override
	public PacketType<HelloPayload> getType() {
		return TYPE;
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(protocolVersion);
		buf.writeUtf(modVersion, 64);
	}

	public static HelloPayload read(FriendlyByteBuf buf) {
		return new HelloPayload(buf.readVarInt(), buf.readUtf(64));
	}
}
