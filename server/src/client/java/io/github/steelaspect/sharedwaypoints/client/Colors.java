package io.github.steelaspect.sharedwaypoints.client;

import io.github.steelaspect.sharedwaypoints.waypoint.Category;

/** Colour helpers for the menu (1.21.6+ text needs an explicit alpha channel). */
final class Colors {
	static final int WHITE = 0xFFFFFFFF;
	static final int GRAY = 0xFFAAAAAA;
	static final int DARK_GRAY = 0xFF777777;
	static final int AQUA = 0xFF55FFFF;
	static final int GREEN = 0xFF55FF55;
	static final int RED = 0xFFFF5555;
	static final int GOLD = 0xFFFFAA00;
	static final int PURPLE = 0xFFFF55FF;

	private Colors() {
	}

	/** The category's chat colour as 0xRRGGBB. */
	static int rgb(Category category) {
		Integer color = category.color().getColor();
		return color != null ? color : 0xFFFFFF;
	}

	/** The category's chat colour as opaque ARGB, for drawing text. */
	static int argb(Category category) {
		return 0xFF000000 | rgb(category);
	}
}
