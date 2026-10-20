package io.github.steelaspect.sharedwaypoints.client;

import dev.steelaspect.alloyui.widget.TextField;
import io.github.steelaspect.sharedwaypoints.network.ActionPayload.Action;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Edit a waypoint's name and description. Enter saves, Esc cancels. Only changed fields are sent. */
final class EditWaypointScreen extends FormScreen {
	private final Waypoint waypoint;
	private String name;
	private String description;

	EditWaypointScreen(Screen parent, Waypoint waypoint) {
		super(Ui.tr("edit.title", waypoint.name()), parent, 320, 170);
		this.waypoint = waypoint;
		this.name = waypoint.name();
		this.description = waypoint.descriptionText().orElse("");
		label(Ui.tr("form.name"));
		field(new TextField(() -> name, value -> name = value).live().maxLength(Waypoint.MAX_NAME_LENGTH).onEnter(this::save));
		label(Ui.tr("form.description"));
		field(new TextField(() -> description, value -> description = value).live().maxLength(Waypoint.MAX_DESCRIPTION_LENGTH)
				.placeholder(Ui.tr("edit.description.hint")).onEnter(this::save));
		mainButton(Ui.tr("form.save"), this::save);
	}

	private void save() {
		String newName = name.trim();
		var problem = Waypoint.validateName(newName);
		if (problem.isPresent()) {
			error(Component.literal(problem.get()));
			return;
		}
		String id = waypoint.id().toString();
		String newDescription = description.trim();
		if (!newDescription.equals(waypoint.descriptionText().orElse(""))) {
			SharedWaypointsClient.send(Action.DESCRIBE, id, newDescription);
		}
		if (!newName.equals(waypoint.name())) {
			SharedWaypointsClient.send(Action.RENAME, id, newName);
		}
		minecraft.setScreen(parent);
	}
}
