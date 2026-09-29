package io.github.steelaspect.sharedwaypoints.client;

import io.github.steelaspect.sharedwaypoints.network.ActionPayload.Action;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Edit a waypoint's name and description. Enter saves, Esc cancels. Only changed fields are sent. */
final class EditWaypointScreen extends Screen {
	private static final int FIELD_WIDTH = 260;

	private final Screen parent;
	private final Waypoint waypoint;
	private EditBox name;
	private EditBox description;
	private String error;

	EditWaypointScreen(Screen parent, Waypoint waypoint) {
		super(Component.literal("Edit " + waypoint.name()));
		this.parent = parent;
		this.waypoint = waypoint;
	}

	@Override
	protected void init() {
		int left = (width - FIELD_WIDTH) / 2;
		int top = height / 2 - 50;
		name = addRenderableWidget(new EditBox(font, left, top + 12, FIELD_WIDTH, 20, Component.literal("Name")));
		name.setMaxLength(Waypoint.MAX_NAME_LENGTH);
		name.setValue(waypoint.name());
		description = addRenderableWidget(new EditBox(font, left, top + 50, FIELD_WIDTH, 20, Component.literal("Description")));
		description.setMaxLength(Waypoint.MAX_DESCRIPTION_LENGTH);
		description.setValue(waypoint.descriptionText().orElse(""));
		description.setHint(Component.literal("Optional note, e.g. \"bring shulkers\""));
		setInitialFocus(name);
		addRenderableWidget(Button.builder(Component.literal("Save"), button -> save())
				.bounds(left, top + 82, FIELD_WIDTH / 2 - 2, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
				.bounds(left + FIELD_WIDTH / 2 + 2, top + 82, FIELD_WIDTH / 2 - 2, 20).build());
	}

	private void save() {
		String newName = name.getValue().trim();
		var problem = Waypoint.validateName(newName);
		if (problem.isPresent()) {
			error = problem.get();
			return;
		}
		String id = waypoint.id().toString();
		String newDescription = description.getValue().trim();
		if (!newDescription.equals(waypoint.descriptionText().orElse(""))) {
			SharedWaypointsClient.send(Action.DESCRIBE, id, newDescription);
		}
		if (!newName.equals(waypoint.name())) {
			SharedWaypointsClient.send(Action.RENAME, id, newName);
		}
		minecraft.setScreen(parent);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
			save();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int left = (width - FIELD_WIDTH) / 2;
		int top = height / 2 - 50;
		graphics.drawCenteredString(font, title, width / 2, top - 16, Colors.WHITE);
		graphics.drawString(font, "Name", left, top, Colors.GRAY);
		graphics.drawString(font, "Description", left, top + 38, Colors.GRAY);
		if (error != null) {
			graphics.drawCenteredString(font, error, width / 2, top + 110, Colors.RED);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
