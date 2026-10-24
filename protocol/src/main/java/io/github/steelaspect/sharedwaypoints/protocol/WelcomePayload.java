package io.github.steelaspect.sharedwaypoints.protocol;

import net.minecraft.network.FriendlyByteBuf;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client, the answer to {@link HelloPayload}.
 *
 * @param protocolVersion the server's {@link SyncProtocol#VERSION}; the client only syncs when it equals its own
 * @param syncEnabled     whether the server owner has sync turned on; if not, nothing else follows
 */
public record WelcomePayload(int protocolVersion, boolean syncEnabled) implements FabricPacket {
	public static final PacketType<WelcomePayload> TYPE =
			PacketType.create(new ResourceLocation(SyncProtocol.NAMESPACE, "sync_welcome"), WelcomePayload::read);

	@Override
	public PacketType<WelcomePayload> getType() {
		return TYPE;
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(protocolVersion);
		buf.writeBoolean(syncEnabled);
	}

	public static WelcomePayload read(FriendlyByteBuf buf) {
		return new WelcomePayload(buf.readVarInt(), buf.readBoolean());
	}
}
