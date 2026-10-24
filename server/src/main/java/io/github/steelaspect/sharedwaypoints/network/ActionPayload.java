package io.github.steelaspect.sharedwaypoints.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: a button pressed in the client menu. The server turns it into the matching /cway command
 * and runs it as the player, so the same permissions and validation apply as when typing it in chat.
 *
 * @param action what to do
 * @param args   arguments; waypoints are referred to by id (see {@link Action} for each action's arguments)
 */
public record ActionPayload(Action action, List<String> args) implements FabricPacket {
	/** Versioned like {@link SyncPayload#TYPE}. */
	public static final PacketType<ActionPayload> TYPE =
			PacketType.create(new ResourceLocation("sharedwaypoints", "action3"), ActionPayload::read);

	private static final int MAX_ARGS = 8;
	private static final int MAX_ARG_LENGTH = 256;

	/** Menu actions. Arguments are listed in order. */
	public enum Action {
		/** Send me a fresh {@link SyncPayload}. No arguments. */
		SYNC,
		/** Navigate: waypoint id. */
		GO,
		/** Stop navigating. No arguments. */
		STOP,
		/** Toggle favourite: waypoint id. */
		FAVORITE,
		/** Teleport: waypoint id. */
		TELEPORT,
		/** Add: name, category id, x, y, z, dimension id. */
		ADD,
		/** Rename: waypoint id, new name. */
		RENAME,
		/** Set the description: waypoint id, text (empty clears it). */
		DESCRIBE,
		/** Remove: waypoint id. */
		REMOVE,
		/** Follow a route: route id, first stop (1-based). */
		ROUTE_GO,
		/** Skip to the next stop of the route being followed. No arguments. */
		ROUTE_SKIP,
		/** Create a route: name. */
		ROUTE_CREATE,
		/** Append a stop: route id, waypoint id. */
		ROUTE_ADD,
		/** Remove a stop: route id, stop number (1-based). */
		ROUTE_DROP,
		/** Move a stop: route id, from, to (1-based). */
		ROUTE_MOVE,
		/** Rename a route: route id, new name. */
		ROUTE_RENAME,
		/** Set a route's description: route id, text (empty clears it). */
		ROUTE_DESCRIBE,
		/** Delete a route: route id. */
		ROUTE_DELETE,
		/** Set the project status: waypoint id, state id ({@code planned}, {@code wip}, {@code done}, {@code broken} or {@code clear}), note (may be empty). */
		STATUS
	}

	public static ActionPayload of(Action action, String... args) {
		return new ActionPayload(action, List.of(args));
	}

	@Override
	public PacketType<ActionPayload> getType() {
		return TYPE;
	}

	@Override
	public void write(FriendlyByteBuf buf) {
		write(buf, this);
	}

	private static void write(FriendlyByteBuf buf, ActionPayload payload) {
		buf.writeEnum(payload.action);
		buf.writeVarInt(payload.args.size());
		payload.args.forEach(arg -> buf.writeUtf(arg, MAX_ARG_LENGTH));
	}

	public static ActionPayload read(FriendlyByteBuf buf) {
		Action action = buf.readEnum(Action.class);
		int count = buf.readVarInt();
		if (count < 0 || count > MAX_ARGS) {
			throw new IllegalArgumentException("Too many arguments: " + count);
		}
		List<String> args = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			args.add(buf.readUtf(MAX_ARG_LENGTH));
		}
		return new ActionPayload(action, List.copyOf(args));
	}
}
