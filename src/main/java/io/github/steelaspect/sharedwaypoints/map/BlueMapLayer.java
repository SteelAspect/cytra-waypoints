package io.github.steelaspect.sharedwaypoints.map;

import de.bluecolored.bluemap.api.BlueMapAPI;
import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.api.markers.LineMarker;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Line;
import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * BlueMap layer: one marker set per map with a POI marker per waypoint and a line marker per route. Only loaded when BlueMap is installed.
 *
 * <p>BlueMap drops marker sets when it reloads, so the set is re-added from the {@code onEnable} callback.
 */
final class BlueMapLayer implements MapIntegrations.Layer {
	private static final String SET_ID = "sharedwaypoints";

	private final MinecraftServer server;
	private final String label;
	private volatile List<MapMarker> markers = List.of();
	private volatile List<MapRoute> routes = List.of();
	/** BlueMap may call this from its own thread; the work is moved to the server thread. */
	private final Consumer<BlueMapAPI> onEnable;

	BlueMapLayer(MinecraftServer server, String label) {
		this.server = server;
		this.label = label;
		this.onEnable = api -> server.execute(() -> apply(api));
		BlueMapAPI.onEnable(onEnable); // also runs right away if BlueMap is already enabled
	}

	@Override
	public void update(List<MapMarker> newMarkers, List<MapRoute> newRoutes) {
		markers = newMarkers;
		routes = newRoutes;
		BlueMapAPI.getInstance().ifPresent(this::apply);
	}

	private void apply(BlueMapAPI api) {
		for (ServerLevel level : server.getAllLevels()) {
			String dimension = Dimensions.id(level.dimension());
			MarkerSet set = MarkerSet.builder().label(label).toggleable(true).defaultHidden(false).build();
			for (MapRoute route : routes) {
				if (route.dimension().equals(dimension)) {
					Line.Builder line = Line.builder();
					route.points().forEach(point -> line.addPoint(new Vector3d(point[0], point[1] + 1, point[2])));
					set.put(route.key(), LineMarker.builder()
							.label(route.label())
							.detail(route.detailHtml())
							.line(line.build())
							.centerPosition()
							.lineWidth(4)
							.lineColor(new Color((MapRoute.RGB >> 16) & 0xFF, (MapRoute.RGB >> 8) & 0xFF, MapRoute.RGB & 0xFF, 0.9f))
							.depthTestEnabled(false)
							.build());
				}
			}
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
