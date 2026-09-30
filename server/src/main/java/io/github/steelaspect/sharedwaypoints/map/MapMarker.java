package io.github.steelaspect.sharedwaypoints.map;

import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;

/**
 * What a web map shows for one waypoint. Plain data, built once per change and handed to every map layer.
 *
 * @param key        stable marker id ({@code wp-<waypoint id>}); only {@code [a-z0-9-]}, valid for every map
 * @param dimension  dimension id the marker belongs to
 * @param x          block centre X
 * @param y          block Y
 * @param z          block centre Z
 * @param label      plain-text name (maps that treat labels as text)
 * @param labelHtml  HTML-escaped name (maps that treat tooltips as HTML)
 * @param detailHtml HTML popup: name, category, coordinates, description, creator (all escaped)
 * @param rgb        category colour as 0xRRGGBB
 */
public record MapMarker(String key, String dimension, double x, double y, double z, String label, String labelHtml,
		String detailHtml, int rgb) {

	public static MapMarker of(Waypoint waypoint) {
		Integer color = waypoint.category().color().getColor();
		StringBuilder html = new StringBuilder()
				.append("<div class=\"sharedwaypoints-marker\">")
				.append("<b>").append(escape(waypoint.name())).append("</b><br>")
				.append(escape(waypoint.category().displayName())).append(" · ")
				.append(escape(waypoint.coordinates())).append(" · ")
				.append(escape(Dimensions.shortName(waypoint.dimension())));
		waypoint.descriptionText().ifPresent(description ->
				html.append("<br><i>").append(escape(description)).append("</i>"));
		html.append("<br><small>Added by ").append(escape(waypoint.creatorName())).append("</small></div>");
		return new MapMarker("wp-" + waypoint.id(), waypoint.dimension(), waypoint.x() + 0.5, waypoint.y(),
				waypoint.z() + 0.5, waypoint.name(), escape(waypoint.name()), html.toString(),
				color != null ? color : 0xFFFFFF);
	}

	/**
	 * HTML-escapes text. Waypoint names and descriptions are typed by players and end up in the web map's page,
	 * so they must never be interpreted as markup or script.
	 */
	static String escape(String text) {
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (c) {
				case '&' -> out.append("&amp;");
				case '<' -> out.append("&lt;");
				case '>' -> out.append("&gt;");
				case '"' -> out.append("&quot;");
				case '\'' -> out.append("&#39;");
				default -> out.append(c);
			}
		}
		return out.toString();
	}
}
