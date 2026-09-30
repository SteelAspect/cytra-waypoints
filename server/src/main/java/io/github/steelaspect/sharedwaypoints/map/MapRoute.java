package io.github.steelaspect.sharedwaypoints.map;

import io.github.steelaspect.sharedwaypoints.waypoint.Route;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.util.ArrayList;
import java.util.List;

/**
 * A line a web map draws for (part of) a route. A route that changes dimension becomes one line per stretch of
 * consecutive stops in the same dimension; a stretch with a single stop draws nothing.
 *
 * @param key        stable id ({@code route-<route id>-<part>}); only {@code [a-z0-9-]}
 * @param dimension  dimension id the line belongs to
 * @param points     block-centre points {x, y, z} in visiting order (at least two)
 * @param label      plain-text route name
 * @param labelHtml  HTML-escaped route name
 * @param detailHtml HTML popup: route name, stop count, description and creator (all escaped)
 */
public record MapRoute(String key, String dimension, List<double[]> points, String label, String labelHtml,
		String detailHtml) {

	/** Line colour: gold, so routes stand out from the category-coloured pins. */
	public static final int RGB = 0xFFAA00;

	/** The lines for a route whose stops are {@code stops} (in order). */
	public static List<MapRoute> of(Route route, List<Waypoint> stops) {
		StringBuilder html = new StringBuilder()
				.append("<div class=\"sharedwaypoints-route\">")
				.append("<b>").append(MapMarker.escape(route.name())).append("</b><br>")
				.append("Route · ").append(stops.size()).append(stops.size() == 1 ? " stop" : " stops");
		route.descriptionText().ifPresent(description ->
				html.append("<br><i>").append(MapMarker.escape(description)).append("</i>"));
		html.append("<br><small>Created by ").append(MapMarker.escape(route.creatorName())).append("</small></div>");

		List<MapRoute> lines = new ArrayList<>();
		List<double[]> points = new ArrayList<>();
		String dimension = null;
		for (Waypoint stop : stops) {
			if (!stop.dimension().equals(dimension)) {
				addLine(lines, route, dimension, points, html.toString());
				points = new ArrayList<>();
				dimension = stop.dimension();
			}
			points.add(new double[] {stop.x() + 0.5, stop.y(), stop.z() + 0.5});
		}
		addLine(lines, route, dimension, points, html.toString());
		return lines;
	}

	private static void addLine(List<MapRoute> lines, Route route, String dimension, List<double[]> points, String html) {
		if (points.size() >= 2) {
			lines.add(new MapRoute("route-" + route.id() + "-" + lines.size(), dimension, List.copyOf(points),
					route.name(), MapMarker.escape(route.name()), html));
		}
	}
}
