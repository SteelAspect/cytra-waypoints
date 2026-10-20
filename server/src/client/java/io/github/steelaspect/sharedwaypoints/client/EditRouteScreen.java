package io.github.steelaspect.sharedwaypoints.client;

import dev.steelaspect.alloyui.widget.TextField;
import io.github.steelaspect.sharedwaypoints.network.ActionPayload.Action;
import io.github.steelaspect.sharedwaypoints.network.SyncPayload;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Create a route (name only), or change an existing route's name and description. Enter saves, Esc cancels. When
 * editing, only changed fields are sent.
 */
final class EditRouteScreen extends FormScreen {
	/** The route being edited, or null when creating one. */
	private final SyncPayload.@Nullable RouteData route;
	private String name;
	private String description;

	EditRouteScreen(Screen parent, SyncPayload.@Nullable RouteData route) {
		super(route == null ? Ui.tr("route_form.new") : Ui.tr("route_form.edit", route.name()), parent, 320, route == null ? 130 : 170);
		this.route = route;
		this.name = route == null ? "" : route.name();
		this.description = route == null || route.description() == null ? "" : route.description();
		label(Ui.tr("form.name"));
		field(new TextField(() -> name, value -> name = value).live().maxLength(Waypoint.MAX_NAME_LENGTH)
				.placeholder(Ui.tr("route_form.name.hint")).onEnter(this::save));
		if (route != null) {
			label(Ui.tr("form.description"));
			field(new TextField(() -> description, value -> description = value).live().maxLength(Waypoint.MAX_DESCRIPTION_LENGTH)
					.placeholder(Ui.tr("route_form.description.hint")).onEnter(this::save));
		}
		mainButton(route == null ? Ui.tr("route_form.create") : Ui.tr("form.save"), this::save);
	}

	private void save() {
		String newName = name.trim();
		var problem = Waypoint.validateName(newName);
		if (problem.isPresent()) {
			error(Component.literal(problem.get()));
			return;
		}
		if (route == null) {
			SharedWaypointsClient.send(Action.ROUTE_CREATE, newName);
			if (parent instanceof RoutesScreen routes) {
				routes.selectWhenListed(newName);
			}
		} else {
			String id = route.id().toString();
			String newDescription = description.trim();
			if (!newDescription.equals(route.description() == null ? "" : route.description())) {
				SharedWaypointsClient.send(Action.ROUTE_DESCRIBE, id, newDescription);
			}
			if (!newName.equals(route.name())) {
				SharedWaypointsClient.send(Action.ROUTE_RENAME, id, newName);
			}
		}
		minecraft.setScreen(parent);
	}
}
