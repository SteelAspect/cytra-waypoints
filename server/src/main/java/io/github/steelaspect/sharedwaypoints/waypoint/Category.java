package io.github.steelaspect.sharedwaypoints.waypoint;

import java.util.Locale;
import net.minecraft.ChatFormatting;

/**
 * A waypoint category, e.g. {@code storage}. Categories come from {@code config.json} (see
 * {@link CategoryRegistry}); the five built-in ones are the defaults.
 *
 * <p>The chat colour also decides the Xaero's Minimap colour: Xaero's colour index is the position in its
 * {@code WaypointColor} list, and indexes 0-15 follow the vanilla {@code §0}-{@code §f} order (checked against
 * Xaero's Minimap 26.5.0 for 1.21.11), which is exactly {@link ChatFormatting#getId()}.
 *
 * @param id          lower-case id used in commands and in the JSON file
 * @param displayName name shown in chat, e.g. "Storage"
 * @param color       one of the 16 chat colours
 * @param order       position in the config list; lists are sorted by it
 */
public record Category(String id, String displayName, ChatFormatting color, int order) {
	/** Xaero's names for its first 16 colours (it calls {@code §d} "Purple"). */
	private static final String[] XAERO_COLOR_NAMES = {
			"Black", "Dark Blue", "Dark Green", "Dark Aqua", "Dark Red", "Dark Purple", "Gold", "Gray",
			"Dark Gray", "Blue", "Green", "Aqua", "Red", "Purple", "Yellow", "White"};

	/** Index into Xaero's Minimap waypoint colour list. */
	public int xaeroColorIndex() {
		return color.getId();
	}

	public String xaeroColorName() {
		return XAERO_COLOR_NAMES[xaeroColorIndex()];
	}

	/**
	 * Stand-in for a category id that is stored on a waypoint but no longer configured. The waypoint keeps its id
	 * (so nothing is lost if the category is added back) and is shown in gray after all configured categories.
	 */
	public static Category unknown(String id) {
		String name = id.isEmpty() ? "Other" : id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1);
		return new Category(id, name, ChatFormatting.GRAY, Integer.MAX_VALUE);
	}
}
