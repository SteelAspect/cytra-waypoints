package io.github.steelaspect.sharedwaypoints;

import io.github.steelaspect.sharedwaypoints.config.ModConfig;
import io.github.steelaspect.sharedwaypoints.map.MapIntegrations;
import io.github.steelaspect.sharedwaypoints.nav.NavigationManager;
import io.github.steelaspect.sharedwaypoints.network.MenuNetworking;
import io.github.steelaspect.sharedwaypoints.waypoint.CategoryRegistry;
import io.github.steelaspect.sharedwaypoints.waypoint.FavoritesStore;
import io.github.steelaspect.sharedwaypoints.waypoint.WaypointStore;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;

/**
 * Everything the mod keeps while a server runs: config, categories, waypoints, favourites, live navigation and
 * web-map markers.
 */
public final class ModContext {
	private final Path configDir;
	private final WaypointStore waypoints;
	private final FavoritesStore favorites;
	private final NavigationManager navigation;
	private final MapIntegrations maps;
	private final MenuNetworking menus;
	private ModConfig config = new ModConfig();
	private CategoryRegistry categories = CategoryRegistry.DEFAULT;

	public ModContext(Path configDir) {
		this.configDir = configDir;
		this.waypoints = new WaypointStore(configDir.resolve("waypoints.json"), () -> categories);
		this.favorites = new FavoritesStore(configDir.resolve("favorites.json"));
		this.navigation = new NavigationManager(this);
		this.maps = new MapIntegrations(this);
		this.menus = new MenuNetworking(this);
		// Deleted waypoints disappear from favourites; navigation notices on its next tick.
		waypoints.onRemoved(waypoint -> favorites.forget(waypoint.id()));
		waypoints.onChanged(maps::refresh);
		// Players with the client menu see changes live.
		waypoints.onChanged(menus::pushToAll);
		navigation.onChange(menus::pushTo);
	}

	/** (Re)loads every file. Runs when a server starts. Categories load first: waypoints refer to them. */
	public void load() {
		config = ModConfig.load(configDir.resolve("config.json"));
		categories = config.categoryRegistry();
		waypoints.load();
		favorites.load();
	}

	/** Reloads every file on a running server and re-creates the web-map layers ({@code /waypoints reload}). */
	public void reload(MinecraftServer server) {
		load();
		maps.start(server);
		menus.pushToAll(); // categories may have changed
	}

	public ModConfig config() {
		return config;
	}

	public CategoryRegistry categories() {
		return categories;
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

	public MapIntegrations maps() {
		return maps;
	}

	public MenuNetworking menus() {
		return menus;
	}
}
