package io.github.steelaspect.sharedwaypoints.client;

import io.github.steelaspect.sharedwaypoints.network.ActionPayload.Action;
import io.github.steelaspect.sharedwaypoints.network.SyncPayload;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import io.github.steelaspect.sharedwaypoints.waypoint.WaypointStore;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Pick a waypoint to add as the last stop of a route: search, then double-click or press Add. */
final class WaypointPickerScreen extends Screen {
	private static final int MARGIN = 10;
	private static final int TOP = 44;

	private final Screen parent;
	private final SyncPayload.RouteData route;
	private WaypointList list;
	private EditBox searchBox;
	private Button addButton;
	private String search = "";

	WaypointPickerScreen(Screen parent, SyncPayload.RouteData route) {
		super(Component.literal("Add a stop to " + route.name()));
		this.parent = parent;
		this.route = route;
	}

	@Override
	protected void init() {
		int listWidth = Math.min(320, width - 2 * MARGIN);
		int left = (width - listWidth) / 2;
		searchBox = addRenderableWidget(new EditBox(font, left, 20, listWidth, 18, Component.literal("Search")));
		searchBox.setHint(Component.literal("Search waypoints…"));
		searchBox.setMaxLength(64);
		searchBox.setValue(search);
		searchBox.setResponder(value -> {
			search = value;
			refresh();
		});

		list = addRenderableWidget(new WaypointList(minecraft, waypoint -> updateButton(), this::add));
		list.updateSizeAndPosition(listWidth, height - 32 - TOP, left, TOP);

		addButton = addRenderableWidget(Button.builder(Component.literal("Add stop"),
						button -> list.selectedWaypoint().ifPresent(this::add))
				.bounds(width / 2 - 102, height - 26, 100, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
				.bounds(width / 2 + 2, height - 26, 100, 20).build());
		refresh();
	}

	private void refresh() {
		String needle = search.trim().toLowerCase(Locale.ROOT);
		List<Waypoint> shown = ClientWaypoints.waypoints().stream()
				.filter(waypoint -> needle.isEmpty() || waypoint.name().toLowerCase(Locale.ROOT).contains(needle)
						|| waypoint.category().displayName().toLowerCase(Locale.ROOT).contains(needle))
				.sorted(WaypointStore.DISPLAY_ORDER)
				.toList();
		list.show(shown, null);
		if (!shown.isEmpty()) {
			list.setSelected(list.children().get(0));
		}
		updateButton();
	}

	private void updateButton() {
		if (addButton != null) {
			addButton.active = list.selectedWaypoint().isPresent();
		}
	}

	private void add(Waypoint waypoint) {
		SharedWaypointsClient.send(Action.ROUTE_ADD, route.id().toString(), waypoint.id().toString());
		minecraft.setScreen(parent);
	}

	/** Picks a waypoint by name (used by the client test). */
	public void pickForTest(String name) {
		ClientWaypoints.waypoints().stream().filter(waypoint -> waypoint.name().equals(name)).findFirst().ifPresent(this::add);
	}

	/** Always start in the text field (Minecraft would otherwise tab past it after keyboard input). */
	@Override
	protected void setInitialFocus() {
		setInitialFocus(searchBox);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, 6, Colors.WHITE);
		if (list.children().isEmpty()) {
			graphics.drawCenteredString(font, "Nothing matches.", width / 2, TOP + 20, Colors.GRAY);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
