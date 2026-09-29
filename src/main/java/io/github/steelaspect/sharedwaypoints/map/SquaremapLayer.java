package io.github.steelaspect.sharedwaypoints.map;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.Point;
import xyz.jpenilla.squaremap.api.SimpleLayerProvider;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.api.SquaremapProvider;
import xyz.jpenilla.squaremap.api.marker.Marker;
import xyz.jpenilla.squaremap.api.marker.MarkerOptions;

/**
 * squaremap layer: one layer per map world with an icon marker per waypoint. Only loaded when squaremap is
 * installed. squaremap's worlds exist once the server has started, which is when this is created.
 */
final class SquaremapLayer implements MapIntegrations.Layer {
	static final Key LAYER_KEY = Key.of("sharedwaypoints");

	private final Squaremap api;
	/** Keyed by dimension id, e.g. {@code minecraft:overworld}. */
	private final Map<String, MapWorld> worlds = new HashMap<>();
	private final Map<String, SimpleLayerProvider> layers = new HashMap<>();
	private final Set<Key> icons = new HashSet<>();

	SquaremapLayer(String label) {
		this.api = SquaremapProvider.get();
		for (MapWorld world : api.mapWorlds()) {
			SimpleLayerProvider layer = SimpleLayerProvider.builder(label)
					.showControls(true)
					.defaultHidden(false)
					.layerPriority(5)
					.zIndex(250)
					.build();
			world.layerRegistry().register(LAYER_KEY, layer);
			worlds.put(world.identifier().asString(), world);
			layers.put(world.identifier().asString(), layer);
		}
	}

	@Override
	public void update(List<MapMarker> markers) {
		layers.values().forEach(SimpleLayerProvider::clearMarkers);
		for (MapMarker marker : markers) {
			SimpleLayerProvider layer = layers.get(marker.dimension());
			if (layer == null) {
				continue; // dimension not rendered by squaremap
			}
			Marker icon = Marker.icon(Point.of(marker.x(), marker.z()), iconFor(marker.rgb()), MarkerIcons.SIZE)
					.markerOptions(MarkerOptions.builder()
							.hoverTooltip(marker.labelHtml())
							.clickTooltip(marker.detailHtml()));
			layer.addMarker(Key.of(marker.key()), icon);
		}
	}

	private Key iconFor(int rgb) {
		Key key = Key.of(String.format("sharedwaypoints_%06x", rgb & 0xFFFFFF));
		if (icons.add(key) && !api.iconRegistry().hasEntry(key)) {
			api.iconRegistry().register(key, MarkerIcons.image(rgb));
		}
		return key;
	}

	@Override
	public void close() {
		worlds.values().forEach(world -> {
			if (world.layerRegistry().hasEntry(LAYER_KEY)) {
				world.layerRegistry().unregister(LAYER_KEY);
			}
		});
		for (Key key : icons) {
			if (api.iconRegistry().hasEntry(key)) {
				api.iconRegistry().unregister(key);
			}
		}
		worlds.clear();
		layers.clear();
		icons.clear();
	}
}
