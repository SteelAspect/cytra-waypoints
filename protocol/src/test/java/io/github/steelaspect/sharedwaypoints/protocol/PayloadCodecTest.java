package io.github.steelaspect.sharedwaypoints.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import java.util.function.Function;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import org.junit.jupiter.api.Test;

class PayloadCodecTest {
	private static final SyncedWaypoint HUB = new SyncedWaypoint(UUID.randomUUID(), "Hub", "H", "portals", "Portals",
			13, 10, 70, -20, "minecraft:the_nether", null);
	private static final SyncedWaypoint STORAGE = new SyncedWaypoint(UUID.randomUUID(), "Main Storage", "MS", "storage",
			"Storage", 11, 120, 64, -340, "minecraft:overworld", "Sorted chests");

	private static <T extends FabricPacket> T roundTrip(Function<FriendlyByteBuf, T> reader, T value) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		value.write(buf);
		T decoded = reader.apply(buf);
		assertEquals(0, buf.readableBytes(), "every byte is read back");
		return decoded;
	}

	@Test
	void everyPayloadSurvivesARoundTrip() {
		assertEquals(new HelloPayload(SyncProtocol.VERSION, "2.0.0"),
				roundTrip(HelloPayload::read, new HelloPayload(SyncProtocol.VERSION, "2.0.0")));
		assertEquals(new WelcomePayload(1, false), roundTrip(WelcomePayload::read, new WelcomePayload(1, false)));
		FullSyncPayload full = new FullSyncPayload(1, List.of(HUB, STORAGE));
		assertEquals(full, roundTrip(FullSyncPayload::read, full));
		assertEquals(new UpsertPayload(1, STORAGE), roundTrip(UpsertPayload::read, new UpsertPayload(1, STORAGE)));
		assertEquals(new DeletePayload(1, HUB.id()), roundTrip(DeletePayload::read, new DeletePayload(1, HUB.id())));
	}

	@Test
	void anAbsurdWaypointCountIsRejected() {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		buf.writeVarInt(1);
		buf.writeVarInt(FullSyncPayload.MAX_WAYPOINTS + 1);
		assertThrows(IllegalArgumentException.class, () -> FullSyncPayload.read(buf));
	}
}
