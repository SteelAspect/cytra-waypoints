package io.github.steelaspect.sharedwaypoints.protocol;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/**
 * One shared waypoint as the client mod needs it for Xaero's Minimap. The server fills in everything that depends
 * on its config (category name and colour, Xaero initials), so the client never has to know the categories.
 *
 * @param id           stable waypoint id (survives renames)
 * @param name         display name, 1-32 characters
 * @param initials     Xaero's 1-3 character symbol
 * @param categoryId   category id, e.g. {@code storage}
 * @param categoryName category display name, e.g. {@code Storage}
 * @param colorIndex   Xaero colour index 0-15, the same as the category's chat colour id
 * @param x            block X
 * @param y            block Y
 * @param z            block Z
 * @param dimension    dimension id, e.g. {@code minecraft:the_nether}
 * @param description  optional note, or null
 */
public record SyncedWaypoint(UUID id, String name, String initials, String categoryId, String categoryName,
		int colorIndex, int x, int y, int z, String dimension, String description) {

	/** Longest strings accepted when reading, so a broken or hostile server can't make the client allocate much. */
	public static final int MAX_TEXT = 256;

	void write(FriendlyByteBuf buf) {
		buf.writeUUID(id);
		buf.writeUtf(name, MAX_TEXT);
		buf.writeUtf(initials, MAX_TEXT);
		buf.writeUtf(categoryId, MAX_TEXT);
		buf.writeUtf(categoryName, MAX_TEXT);
		buf.writeVarInt(colorIndex);
		buf.writeVarInt(x);
		buf.writeVarInt(y);
		buf.writeVarInt(z);
		buf.writeUtf(dimension, MAX_TEXT);
		buf.writeNullable(description, (out, text) -> out.writeUtf(text, MAX_TEXT));
	}

	static SyncedWaypoint read(FriendlyByteBuf buf) {
		return new SyncedWaypoint(buf.readUUID(), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT),
				buf.readUtf(MAX_TEXT), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readUtf(MAX_TEXT), buf.readNullable(in -> in.readUtf(MAX_TEXT)));
	}
}
