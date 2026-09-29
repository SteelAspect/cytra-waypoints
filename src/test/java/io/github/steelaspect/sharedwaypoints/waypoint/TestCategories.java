package io.github.steelaspect.sharedwaypoints.waypoint;

/** The default categories, for tests. */
public final class TestCategories {
	public static final Category STORAGE = CategoryRegistry.DEFAULT.resolve("storage");
	public static final Category FARMS = CategoryRegistry.DEFAULT.resolve("farms");
	public static final Category BASES = CategoryRegistry.DEFAULT.resolve("bases");
	public static final Category PORTALS = CategoryRegistry.DEFAULT.resolve("portals");
	public static final Category OTHER = CategoryRegistry.DEFAULT.resolve("other");

	private TestCategories() {
	}
}
