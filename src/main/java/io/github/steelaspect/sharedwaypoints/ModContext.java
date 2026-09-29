package io.github.steelaspect.sharedwaypoints;

import io.github.steelaspect.sharedwaypoints.config.ModConfig;
import io.github.steelaspect.sharedwaypoints.nav.NavigationManager;
import io.github.steelaspect.sharedwaypoints.waypoint.FavoritesStore;
import io.github.steelaspect.sharedwaypoints.waypoint.WaypointStore;
import java.nio.file.Path;

/** Everything the mod keeps while a server runs: config, waypoints, favourites and live navigation. */
public final class ModContext {
	private final Path configDir;
	private final WaypointStore waypoints;
	private final FavoritesStore favorites;
	private final NavigationManager navigation;
	private ModConfig config = new ModConfig();

	public ModContext(Path configDir) {
		this.configDir = configDir;
		this.waypoints = new WaypointStore(configDir.resolve("waypoints.json"));
		this.favorites = new FavoritesStore(configDir.resolve("favorites.json"));
		this.navigation = new NavigationManager(this);
		// Deleted waypoints disappear from favourites; navigation notices on its next tick.
		waypoints.onRemoved(waypoint -> favorites.forget(waypoint.id()));
	}

	/** (Re)loads every file. Runs when a server starts. */
	public void load() {
		config = ModConfig.load(configDir.resolve("config.json"));
		waypoints.load();
		favorites.load();
	}

	public ModConfig config() {
		return config;
	}

	public WaypointStore waypoints() {
		return waypoints;
	}

	public FavoritesStore favorites() {
		return favorites;
	}

	public NavigationManager navigation() {
		return navigation;
	}
}
