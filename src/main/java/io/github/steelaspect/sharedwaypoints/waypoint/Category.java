package io.github.steelaspect.sharedwaypoints.waypoint;

import com.google.gson.annotations.SerializedName;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;

/**
 * The fixed set of waypoint categories.
 *
 * <p>Each category has a chat colour and the matching Xaero's Minimap colour index. The index is the ordinal of
 * Xaero's {@code WaypointColor} enum (checked against Xaero's Minimap 26.5.0 for 1.21.11), which follows the
 * vanilla {@code §0}-{@code §f} colour order: 0 black ... 6 gold, 10 green, 11 aqua, 13 "Purple" ({@code §d}),
 * 15 white.
 */
public enum Category {
	@SerializedName("storage")
	STORAGE("storage", "Storage", ChatFormatting.AQUA, 11, "Aqua"),
	@SerializedName("farms")
	FARMS("farms", "Farms", ChatFormatting.GREEN, 10, "Green"),
	@SerializedName("bases")
	BASES("bases", "Bases", ChatFormatting.GOLD, 6, "Gold"),
	// Xaero calls §d "Purple" (and §5 "Dark Purple"), so LIGHT_PURPLE keeps chat and minimap colours identical.
	@SerializedName("portals")
	PORTALS("portals", "Portals", ChatFormatting.LIGHT_PURPLE, 13, "Purple"),
	@SerializedName("other")
	OTHER("other", "Other", ChatFormatting.WHITE, 15, "White");

	private final String id;
	private final String displayName;
	private final ChatFormatting color;
	private final int xaeroColorIndex;
	private final String xaeroColorName;

	Category(String id, String displayName, ChatFormatting color, int xaeroColorIndex, String xaeroColorName) {
		this.id = id;
		this.displayName = displayName;
		this.color = color;
		this.xaeroColorIndex = xaeroColorIndex;
		this.xaeroColorName = xaeroColorName;
	}

	/** Lower-case id used in commands and in the JSON file. */
	public String id() {
		return id;
	}

	public String displayName() {
		return displayName;
	}

	public ChatFormatting color() {
		return color;
	}

	/** Index into Xaero's Minimap waypoint colour list. */
	public int xaeroColorIndex() {
		return xaeroColorIndex;
	}

	public String xaeroColorName() {
		return xaeroColorName;
	}

	/** Case-insensitive lookup by id. */
	public static Optional<Category> byId(String id) {
		String wanted = id.toLowerCase(Locale.ROOT);
		return Arrays.stream(values()).filter(category -> category.id.equals(wanted)).findFirst();
	}

	public static List<String> ids() {
		return Arrays.stream(values()).map(Category::id).toList();
	}
}
