package io.github.steelaspect.sharedwaypoints.client;

import io.github.steelaspect.sharedwaypoints.network.ActionPayload.Action;
import io.github.steelaspect.sharedwaypoints.waypoint.ProjectStatus;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Set a waypoint's project status: one button per status, an optional note, and Clear. */
final class StatusScreen extends Screen {
	private static final int FIELD_WIDTH = 260;

	private final Screen parent;
	private final Waypoint waypoint;
	private EditBox note;
	private String error;

	StatusScreen(Screen parent, Waypoint waypoint) {
		super(Component.literal("Status of " + waypoint.name()));
		this.parent = parent;
		this.waypoint = waypoint;
	}

	@Override
	protected void init() {
		int left = (width - FIELD_WIDTH) / 2;
		int top = height / 2 - 50;
		note = addRenderableWidget(new EditBox(font, left, top + 12, FIELD_WIDTH, 20, Component.literal("Note")));
		note.setMaxLength(ProjectStatus.MAX_NOTE_LENGTH);
		note.setValue(waypoint.statusInfo().flatMap(ProjectStatus::noteText).orElse(""));
		note.setHint(Component.literal("Optional note, e.g. \"out of bonemeal\""));

		ProjectStatus.State[] states = ProjectStatus.State.values();
		int buttonWidth = (FIELD_WIDTH - 4 * (states.length - 1)) / states.length;
		for (int i = 0; i < states.length; i++) {
			ProjectStatus.State state = states[i];
			boolean current = waypoint.status() != null && waypoint.status().state() == state;
			addRenderableWidget(Button.builder(Component.literal(state.symbol() + " " + state.displayName())
							.withStyle(state.color()), button -> set(state.id()))
					.bounds(left + i * (buttonWidth + 4), top + 40, buttonWidth, 20)
					.tooltip(Tooltip.create(Component.literal(current
							? "It's " + state.displayName() + " now. Press to save a new note."
							: "Mark it " + state.displayName())))
					.build());
		}
		Button clear = addRenderableWidget(Button.builder(Component.literal("Clear status"), button -> set("clear"))
				.bounds(left, top + 68, FIELD_WIDTH / 2 - 2, 20).build());
		clear.active = waypoint.status() != null;
		addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
				.bounds(left + FIELD_WIDTH / 2 + 2, top + 68, FIELD_WIDTH / 2 - 2, 20).build());
	}

	private void set(String state) {
		String text = note.getValue().trim();
		var problem = ProjectStatus.validateNote(text);
		if (problem.isPresent()) {
			error = problem.get();
			return;
		}
		SharedWaypointsClient.send(Action.STATUS, waypoint.id().toString(), state, text);
		minecraft.setScreen(parent);
	}

	/** Always start in the note field. */
	@Override
	protected void setInitialFocus() {
		setInitialFocus(note);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int left = (width - FIELD_WIDTH) / 2;
		int top = height / 2 - 50;
		graphics.drawCenteredString(font, title, width / 2, top - 16, Colors.WHITE);
		graphics.drawString(font, "Note (optional), then pick a status", left, top, Colors.GRAY);
		if (error != null) {
			graphics.drawCenteredString(font, error, width / 2, top + 96, Colors.RED);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
