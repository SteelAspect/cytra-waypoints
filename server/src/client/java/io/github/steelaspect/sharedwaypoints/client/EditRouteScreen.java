package io.github.steelaspect.sharedwaypoints.client;

import io.github.steelaspect.sharedwaypoints.network.ActionPayload.Action;
import io.github.steelaspect.sharedwaypoints.network.SyncPayload;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Create a route (name only), or change an existing route's name and description. Enter saves, Esc cancels. When
 * editing, only changed fields are sent.
 */
final class EditRouteScreen extends Screen {
	private static final int FIELD_WIDTH = 260;

	private final Screen parent;
	/** The route being edited, or null when creating one. */
	private final SyncPayload.RouteData route;
	private EditBox name;
	private EditBox description;
	private String error;

	EditRouteScreen(Screen parent, SyncPayload.RouteData route) {
		super(Component.literal(route == null ? "New route" : "Edit route " + route.name()));
		this.parent = parent;
		this.route = route;
	}

	@Override
	protected void init() {
		int left = (width - FIELD_WIDTH) / 2;
		int top = height / 2 - 50;
		name = addRenderableWidget(new EditBox(font, left, top + 12, FIELD_WIDTH, 20, Component.literal("Name")));
		name.setMaxLength(Waypoint.MAX_NAME_LENGTH);
		name.setValue(route == null ? "" : route.name());
		name.setHint(Component.literal("e.g. Nether highway tour"));
		int buttonsTop = top + 44;
		if (route != null) {
			description = addRenderableWidget(new EditBox(font, left, top + 50, FIELD_WIDTH, 20, Component.literal("Description")));
			description.setMaxLength(Waypoint.MAX_DESCRIPTION_LENGTH);
			description.setValue(route.description() == null ? "" : route.description());
			description.setHint(Component.literal("Optional note, e.g. \"bring fire resistance\""));
			buttonsTop = top + 82;
		}
		addRenderableWidget(Button.builder(Component.literal(route == null ? "Create" : "Save"), button -> save())
				.bounds(left, buttonsTop, FIELD_WIDTH / 2 - 2, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
				.bounds(left + FIELD_WIDTH / 2 + 2, buttonsTop, FIELD_WIDTH / 2 - 2, 20).build());
	}

	private void save() {
		String newName = name.getValue().trim();
		var problem = Waypoint.validateName(newName);
		if (problem.isPresent()) {
			error = problem.get();
			return;
		}
		if (route == null) {
			SharedWaypointsClient.send(Action.ROUTE_CREATE, newName);
			if (parent instanceof RoutesScreen routes) {
				routes.selectWhenListed(newName);
			}
		} else {
			String id = route.id().toString();
			String newDescription = description.getValue().trim();
			if (!newDescription.equals(route.description() == null ? "" : route.description())) {
				SharedWaypointsClient.send(Action.ROUTE_DESCRIBE, id, newDescription);
			}
			if (!newName.equals(route.name())) {
				SharedWaypointsClient.send(Action.ROUTE_RENAME, id, newName);
			}
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

	/** Always start in the text field (Minecraft would otherwise tab past it after keyboard input). */
	@Override
	protected void setInitialFocus() {
		setInitialFocus(name);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int left = (width - FIELD_WIDTH) / 2;
		int top = height / 2 - 50;
		graphics.drawCenteredString(font, title, width / 2, top - 16, Colors.WHITE);
		graphics.drawString(font, "Name", left, top, Colors.GRAY);
		if (description != null) {
			graphics.drawString(font, "Description", left, top + 38, Colors.GRAY);
		}
		if (error != null) {
			graphics.drawCenteredString(font, error, width / 2, top + (description != null ? 110 : 72), Colors.RED);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
