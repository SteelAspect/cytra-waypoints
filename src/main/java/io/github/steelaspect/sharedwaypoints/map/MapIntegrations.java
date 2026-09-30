package io.github.steelaspect.sharedwaypoints.map;

import io.github.steelaspect.sharedwaypoints.ModContext;
import io.github.steelaspect.sharedwaypoints.SharedWaypoints;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;

/**
 * Shows the shared waypoints on web maps: a toggleable "Shared Waypoints" layer on BlueMap and/or squaremap,
 * whichever is installed. Both are optional: their classes are only touched after checking the mod is loaded,
 * and any failure only disables that one map (the rest of the mod keeps working).
 */
public final class MapIntegrations {
	/** One web map's marker layer. */
	interface Layer {
		/** Replaces all markers with these. Called on the server thread. */
		void update(List<MapMarker> markers);

		/** Removes the layer from the map. */
		void close();
	}

	private final ModContext mod;
	private final List<Layer> layers = new ArrayList<>();

	public MapIntegrations(ModContext mod) {
		this.mod = mod;
	}

	/** (Re)creates the layers for whatever maps are installed. Runs when the server has started and on reload. */
	public void start(MinecraftServer server) {
		stop();
		if (!mod.config().webMapMarkers) {
			return;
		}
		String label = mod.config().webMapLayerName;
		if (FabricLoader.getInstance().isModLoaded("bluemap")) {
			tryAdd("BlueMap", () -> new BlueMapLayer(server, label));
		}
		if (FabricLoader.getInstance().isModLoaded("squaremap")) {
			tryAdd("squaremap", () -> new SquaremapLayer(label));
		}
		refresh();
	}

	/** Pushes the current waypoints to every layer. Called after every change. */
	public void refresh() {
		if (layers.isEmpty()) {
			return;
		}
		List<MapMarker> markers = mod.waypoints().all().stream().map(MapMarker::of).toList();
		for (Layer layer : layers) {
			try {
				layer.update(markers);
			} catch (RuntimeException | LinkageError e) {
				SharedWaypoints.LOGGER.warn("Could not update web map markers ({})", layer.getClass().getSimpleName(), e);
			}
		}
	}

	public void stop() {
		for (Layer layer : layers) {
			try {
				layer.close();
			} catch (RuntimeException | LinkageError e) {
				SharedWaypoints.LOGGER.warn("Could not remove web map layer ({})", layer.getClass().getSimpleName(), e);
			}
		}
		layers.clear();
	}

	/** Names of the maps currently showing markers, for /waypoints reload. */
	public List<String> activeMaps() {
		return layers.stream().map(layer -> layer instanceof BlueMapLayer ? "BlueMap" : "squaremap").toList();
	}

	private void tryAdd(String name, Supplier<Layer> factory) {
		try {
			layers.add(factory.get());
			SharedWaypoints.LOGGER.info("Showing shared waypoints on {}", name);
		} catch (RuntimeException | LinkageError e) {
			// An incompatible map version shouldn't take the whole mod down.
			SharedWaypoints.LOGGER.warn("Could not add shared waypoints to {}", name, e);
		}
	}
}
