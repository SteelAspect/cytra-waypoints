package io.github.steelaspect.sharedwaypoints.client;

import dev.steelaspect.alloyui.widget.Button;
import dev.steelaspect.alloyui.widget.TextField;
import io.github.steelaspect.sharedwaypoints.network.ActionPayload.Action;
import io.github.steelaspect.sharedwaypoints.waypoint.ProjectStatus;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Set a waypoint's project status: an optional note, then one button per status (it saves), or Clear. */
final class StatusScreen extends FormScreen {
	private final Waypoint waypoint;
	private String note;

	StatusScreen(Screen parent, Waypoint waypoint) {
		super(Ui.tr("status.title", waypoint.name()), parent, 340, 150);
		this.waypoint = waypoint;
		this.note = waypoint.statusInfo().flatMap(ProjectStatus::noteText).orElse("");
		label(Ui.tr("status.note"));
		field(new TextField(() -> note, value -> note = value).live().maxLength(ProjectStatus.MAX_NOTE_LENGTH)
				.placeholder(Ui.tr("status.note.hint")));
		label(Ui.tr("status.pick"));
		Row states = field(new Row());
		for (ProjectStatus.State state : ProjectStatus.State.values()) {
			boolean current = waypoint.status() != null && waypoint.status().state() == state;
			Button b = states.add(new Button(Component.literal(state.symbol() + " " + state.displayName()), () -> set(state.id())), 1);
			b.style(current ? Button.Style.PRIMARY : Button.Style.SECONDARY);
			b.tooltip(current ? Ui.tr("status.current", state.displayName()) : Ui.tr("status.mark", state.displayName()));
		}
		Button clear = frame.footer(new Button(Ui.tr("status.clear"), () -> set("clear")));
		clear.enabledWhen(() -> waypoint.status() != null);
	}

	private void set(String state) {
		String text = note.trim();
		var problem = ProjectStatus.validateNote(text);
		if (problem.isPresent()) {
			error(Component.literal(problem.get()));
			return;
		}
		SharedWaypointsClient.send(Action.STATUS, waypoint.id().toString(), state, text);
		minecraft.setScreen(parent);
	}
}
