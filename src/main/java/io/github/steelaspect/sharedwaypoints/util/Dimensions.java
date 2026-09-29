package io.github.steelaspect.sharedwaypoints.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Helpers for turning dimension ids into the short names shown in chat. */
public final class Dimensions {
	// Ids of Level.OVERWORLD / NETHER / END. Kept as plain strings so this class (and the Xaero formatter)
	// never triggers Level's static initialiser, which needs a fully bootstrapped game.
	public static final String OVERWORLD = "minecraft:overworld";
	public static final String NETHER = "minecraft:the_nether";
	public static final String END = "minecraft:the_end";

	private Dimensions() {
	}

	/** Full dimension id, e.g. {@code minecraft:the_nether}. */
	public static String id(ResourceKey<Level> dimension) {
		return dimension.identifier().toString();
	}

	/**
	 * Short name for chat: {@code overworld}, {@code the_nether} or {@code the_end} for the vanilla dimensions,
	 * the full id for anything else (e.g. {@code mymod:mining}).
	 */
	public static String shortName(String dimensionId) {
		if (dimensionId.equals(OVERWORLD)) {
			return "overworld";
		}
		if (dimensionId.equals(NETHER)) {
			return "the_nether";
		}
		if (dimensionId.equals(END)) {
			return "the_end";
		}
		return dimensionId;
	}
}
