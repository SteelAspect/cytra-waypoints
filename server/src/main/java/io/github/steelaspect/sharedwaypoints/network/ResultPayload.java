package io.github.steelaspect.sharedwaypoints.network;

import net.minecraft.network.FriendlyByteBuf;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: the outcome of an {@link ActionPayload}, shown as the status line in the menu.
 *
 * @param success whether the action worked
 * @param message what the command replied (e.g. "Added waypoint Farm" or the error)
 */
public record ResultPayload(boolean success, String message) implements FabricPacket {
	public static final PacketType<ResultPayload> TYPE =
			PacketType.create(new ResourceLocation("sharedwaypoints", "result3"), ResultPayload::read);

	@Override
	public PacketType<ResultPayload> getType() {
		return TYPE;
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeBoolean(success);
		buf.writeUtf(message, 1024);
	}

	public static ResultPayload read(FriendlyByteBuf buf) {
		return new ResultPayload(buf.readBoolean(), buf.readUtf(1024));
	}
}
