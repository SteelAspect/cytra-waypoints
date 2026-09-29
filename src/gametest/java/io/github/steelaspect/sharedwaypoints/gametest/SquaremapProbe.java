package io.github.steelaspect.sharedwaypoints.gametest;

import java.util.Optional;
import xyz.jpenilla.squaremap.api.Key;
import xyz.jpenilla.squaremap.api.LayerProvider;
import xyz.jpenilla.squaremap.api.MapWorld;
import xyz.jpenilla.squaremap.api.SimpleLayerProvider;
import xyz.jpenilla.squaremap.api.SquaremapProvider;
import xyz.jpenilla.squaremap.api.WorldIdentifier;
import xyz.jpenilla.squaremap.api.marker.Marker;

/**
 * Reads SharedWaypoints' layer on squaremap's overworld map. Kept in its own class, and only called after
 * checking squaremap is installed, so the GameTest still loads on a server without squaremap.
 */
final class SquaremapProbe {
	private SquaremapProbe() {
	}

	/** Our layer, or empty if it isn't registered. */
	private static Optional<SimpleLayerProvider> layer() {
		MapWorld world = SquaremapProvider.get().getWorldIfEnabled(WorldIdentifier.parse("minecraft:overworld"))
				.orElseThrow(() -> new IllegalStateException("squaremap has no overworld map"));
		Key key = Key.of("sharedwaypoints");
		if (!world.layerRegistry().hasEntry(key)) {
			return Optional.empty();
		}
		LayerProvider provider = world.layerRegistry().get(key);
		return Optional.of((SimpleLayerProvider) provider);
	}

	static boolean layerRegistered() {
		return layer().isPresent();
	}

	static String layerLabel() {
		return layer().map(SimpleLayerProvider::getLabel).orElse(null);
	}

	static boolean hasMarker(String markerKey) {
		return layer().map(layer -> layer.hasMarker(Key.of(markerKey))).orElse(false);
	}

	/** The click popup HTML of a marker, or null. */
	static String clickTooltip(String markerKey) {
		return layer()
				.map(layer -> layer.registeredMarkers().get(Key.of(markerKey)))
				.map(Marker::markerOptions)
				.map(options -> options.clickTooltip())
				.orElse(null);
	}
}
