package io.github.steelaspect.sharedwaypoints.map;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * BlueMap layer: one marker set per map with a POI marker per waypoint. Only loaded when BlueMap is installed.
 *
 * <p>BlueMap drops marker sets when it reloads, so the set is re-added from the {@code onEnable} callback.
 */
final class BlueMapLayer implements MapIntegrations.Layer {
	private static final String SET_ID = "sharedwaypoints";

	private final MinecraftServer server;
	private final String label;
	private volatile List<MapMarker> markers = List.of();
	/** BlueMap may call this from its own thread; the work is moved to the server thread. */
	private final Consumer<BlueMapAPI> onEnable;

	BlueMapLayer(MinecraftServer server, String label) {
		this.server = server;
		this.label = label;
		this.onEnable = api -> server.execute(() -> apply(api));
		BlueMapAPI.onEnable(onEnable); // also runs right away if BlueMap is already enabled
	}

	@Override
	public void update(List<MapMarker> newMarkers) {
		markers = newMarkers;
		BlueMapAPI.getInstance().ifPresent(this::apply);
	}

	private void apply(BlueMapAPI api) {
		for (ServerLevel level : server.getAllLevels()) {
			String dimension = Dimensions.id(level.dimension());
			MarkerSet set = MarkerSet.builder().label(label).toggleable(true).defaultHidden(false).build();
			for (MapMarker marker : markers) {
				if (marker.dimension().equals(dimension)) {
					set.put(marker.key(), POIMarker.builder()
							.label(marker.label())
							.position(marker.x(), marker.y(), marker.z())
							.detail(marker.detailHtml())
							.icon(MarkerIcons.dataUri(marker.rgb()), MarkerIcons.SIZE / 2, MarkerIcons.SIZE / 2)
							.build());
				}
			}
			api.getWorld(level).ifPresent(world -> world.getMaps().forEach(map -> map.getMarkerSets().put(SET_ID, set)));
		}
	}

	@Override
	public void close() {
		BlueMapAPI.unregisterListener(onEnable);
		BlueMapAPI.getInstance().ifPresent(api -> api.getMaps().forEach(map -> map.getMarkerSets().remove(SET_ID)));
	}
}
