package io.github.steelaspect.sharedwaypoints.client;

import io.github.steelaspect.sharedwaypoints.network.ActionPayload.Action;
import io.github.steelaspect.sharedwaypoints.network.ResultPayload;
import io.github.steelaspect.sharedwaypoints.network.SyncPayload;
import io.github.steelaspect.sharedwaypoints.waypoint.Route;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Routes: the list of routes on the left; the selected route's stops and every action on the right. Opened from
 * the waypoint menu's Routes button.
 */
public final class RoutesScreen extends Screen {
	private static final int MARGIN = 10;
	private static final int TOP = 44;

	// Remembered while the game runs, so the screen reopens where you left it.
	private static UUID selectedId;
	private static int selectedStop = -1;
	/** A route created from this screen gets selected once the server lists it. */
	private static String pendingSelectName;

	private final Screen parent;
	private RouteLists.RouteList routeList;
	private RouteLists.StopList stopList;
	private Button goButton;
	private Button skipButton;
	private Button addStopButton;
	private Button dropStopButton;
	private Button upButton;
	private Button downButton;
	private Button editButton;
	private Button deleteButton;
	private int panelLeft;
	private int bottom;
	private int buttonsTop;
	private int stopsTop;

	public RoutesScreen(Screen parent) {
		super(Component.literal("Routes"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ClientWaypoints.setListener(this::refresh);
		SharedWaypointsClient.send(Action.SYNC);

		int listRight = Math.max(170, (int) (width * 0.45));
		panelLeft = listRight + 8;
		bottom = height - 32;
		int panelWidth = width - MARGIN - panelLeft;

		Button newButton = addRenderableWidget(Button.builder(Component.literal("+ New route"),
						button -> minecraft.setScreen(new EditRouteScreen(this, null)))
				.bounds(width - MARGIN - 90, 19, 90, 20)
				.tooltip(Tooltip.create(Component.literal("Create a route, then add stops to it")))
				.build());
		newButton.active = ClientWaypoints.canAddRoute();

		routeList = addRenderableWidget(new RouteLists.RouteList(minecraft, route -> {
			UUID newId = route == null ? null : route.id();
			if (newId == null || !newId.equals(selectedId)) {
				selectedStop = -1;
			}
			selectedId = newId;
			refreshStops();
		}, route -> go()));
		routeList.updateSizeAndPosition(listRight - MARGIN, bottom - TOP, MARGIN, TOP);

		// --- actions: three rows at the bottom of the panel
		buttonsTop = bottom - 3 * 22 + 2;
		int half = (panelWidth - 4) / 2;
		int quarter = (panelWidth - 12) / 4;
		goButton = button("▶ Start", panelLeft, 0, half, "Follow the route stop by stop", this::go);
		skipButton = button("Skip stop", panelLeft + half + 4, 0, half, "Go straight to the next stop",
				() -> SharedWaypointsClient.send(Action.ROUTE_SKIP));
		addStopButton = button("+ Stop", panelLeft, 1, quarter, "Add a waypoint as the last stop", this::addStop);
		dropStopButton = button("− Stop", panelLeft + quarter + 4, 1, quarter,
				"Remove the selected stop (the waypoint stays)", this::dropStop);
		upButton = button("↑", panelLeft + 2 * (quarter + 4), 1, quarter, "Move the selected stop up", () -> moveStop(-1));
		downButton = button("↓", panelLeft + 3 * (quarter + 4), 1, quarter, "Move the selected stop down", () -> moveStop(1));
		editButton = button("Edit", panelLeft, 2, half, "Rename it or change its description", this::edit);
		deleteButton = button("Delete", panelLeft + half + 4, 2, half, "Delete the route (its waypoints stay)", this::delete);

		// --- stops: between the route details and the buttons
		stopsTop = TOP + 36;
		stopList = addRenderableWidget(new RouteLists.StopList(minecraft, () -> {
			selectedStop = stopList.selectedIndex();
			updateButtons();
		}));
		stopList.updateSizeAndPosition(panelWidth, Math.max(24, buttonsTop - 4 - stopsTop), panelLeft, stopsTop);

		addRenderableWidget(Button.builder(Component.literal("Waypoints"), button -> minecraft.setScreen(parent))
				.bounds(width - MARGIN - 80 - 4 - 80, height - 26, 80, 20)
				.tooltip(Tooltip.create(Component.literal("Back to the waypoint list")))
				.build());
		addRenderableWidget(Button.builder(Component.literal("Done"), button -> minecraft.setScreen(null))
				.bounds(width - MARGIN - 80, height - 26, 80, 20).build());

		refresh();
	}

	private Button button(String label, int x, int row, int buttonWidth, String tooltip, Runnable action) {
		return addRenderableWidget(Button.builder(Component.literal(label), pressed -> action.run())
				.bounds(x, buttonsTop + row * 22, buttonWidth, 20)
				.tooltip(Tooltip.create(Component.literal(tooltip)))
				.build());
	}

	// ---------------------------------------------------------------- updates

	private void refresh() {
		if (routeList == null) {
			return;
		}
		List<SyncPayload.RouteData> routes = ClientWaypoints.routes();
		if (pendingSelectName != null) {
			routes.stream().filter(route -> route.name().equals(pendingSelectName)).findFirst().ifPresent(route -> {
				selectedId = route.id();
				selectedStop = -1;
				pendingSelectName = null;
			});
		}
		routeList.show(routes, selectedId);
		if (routeList.getSelected() == null && !routes.isEmpty()) {
			routeList.setSelected(routeList.children().get(0));
		} else {
			refreshStops();
		}
	}

	private void refreshStops() {
		if (stopList == null) {
			return;
		}
		Optional<SyncPayload.RouteData> route = selected();
		SyncPayload.RouteProgressData progress = ClientWaypoints.onRoute();
		int current = route.isPresent() && progress != null && progress.routeId().equals(route.get().id())
				? progress.stopIndex() : -1;
		List<Waypoint> stops = route.map(ClientWaypoints::stops).orElse(List.of());
		if (selectedStop >= stops.size()) {
			selectedStop = stops.size() - 1;
		}
		stopList.show(stops, selectedStop, current);
		updateButtons();
	}

	private void updateButtons() {
		Optional<SyncPayload.RouteData> route = selected();
		boolean any = route.isPresent();
		boolean canEdit = route.map(SyncPayload.RouteData::canEdit).orElse(false);
		int stops = route.map(r -> r.stops().size()).orElse(0);
		int stop = stopList.selectedIndex();
		boolean following = any && isFollowing(route.get());

		goButton.active = any && (following || stops > 0);
		goButton.setMessage(Component.literal(following ? "■ Stop" : stop > 0 ? "▶ From stop " + (stop + 1) : "▶ Start"));
		skipButton.active = following;
		addStopButton.active = canEdit && stops < Route.MAX_STOPS;
		dropStopButton.active = canEdit && stop >= 0;
		upButton.active = canEdit && stop > 0;
		downButton.active = canEdit && stop >= 0 && stop < stops - 1;
		editButton.active = canEdit;
		deleteButton.active = route.map(SyncPayload.RouteData::canRemove).orElse(false);
		String onlyCreator = "Only its creator or an op can change it";
		if (any && !canEdit) {
			addStopButton.setTooltip(Tooltip.create(Component.literal(onlyCreator)));
			editButton.setTooltip(Tooltip.create(Component.literal(onlyCreator)));
		}
	}

	private Optional<SyncPayload.RouteData> selected() {
		return routeList == null ? Optional.empty() : routeList.selectedRoute();
	}

	private static boolean isFollowing(SyncPayload.RouteData route) {
		SyncPayload.RouteProgressData progress = ClientWaypoints.onRoute();
		return progress != null && progress.routeId().equals(route.id());
	}

	/** Selects a newly created route once the server's update lists it. */
	void selectWhenListed(String name) {
		pendingSelectName = name;
	}

	// ---------------------------------------------------------------- actions

	/** Start (from the selected stop, if one is selected), or Stop when already following this route. */
	private void go() {
		selected().ifPresent(route -> {
			if (isFollowing(route)) {
				SharedWaypointsClient.send(Action.STOP);
			} else if (!route.stops().isEmpty()) {
				int from = Math.max(stopList.selectedIndex(), 0) + 1;
				SharedWaypointsClient.send(Action.ROUTE_GO, route.id().toString(), String.valueOf(from));
				minecraft.setScreen(null);
			}
		});
	}

	private void addStop() {
		selected().ifPresent(route -> minecraft.setScreen(new WaypointPickerScreen(this, route)));
	}

	private void dropStop() {
		int stop = stopList.selectedIndex();
		selected().ifPresent(route -> {
			if (stop >= 0) {
				SharedWaypointsClient.send(Action.ROUTE_DROP, route.id().toString(), String.valueOf(stop + 1));
			}
		});
	}

	private void moveStop(int by) {
		int stop = stopList.selectedIndex();
		selected().ifPresent(route -> {
			int to = stop + by;
			if (stop >= 0 && to >= 0 && to < route.stops().size()) {
				SharedWaypointsClient.send(Action.ROUTE_MOVE, route.id().toString(), String.valueOf(stop + 1),
						String.valueOf(to + 1));
				selectedStop = to;
			}
		});
	}

	private void edit() {
		selected().ifPresent(route -> minecraft.setScreen(new EditRouteScreen(this, route)));
	}

	private void delete() {
		selected().ifPresent(route -> minecraft.setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				SharedWaypointsClient.send(Action.ROUTE_DELETE, route.id().toString());
			}
			minecraft.setScreen(this);
		}, Component.literal("Delete the route \"" + route.name() + "\"?"),
				Component.literal("This deletes it for everyone. Its waypoints stay."))));
	}

	/** Selects a route by name and optionally a stop (0-based, -1 for none); used by the client test. */
	public void selectForTest(String name, int stop) {
		routeList.children().stream().filter(entry -> entry.route.name().equals(name)).findFirst().ifPresent(entry -> {
			routeList.setSelected(entry);
			if (stop >= 0 && stop < stopList.children().size()) {
				stopList.setSelected(stopList.children().get(stop));
			}
		});
	}

	// -------------------------------------------------------------- rendering

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(graphics, mouseX, mouseY, partialTick);
		graphics.fill(panelLeft, TOP, width - MARGIN, bottom, 0x66000000);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, 6, Colors.WHITE);
		int listCentre = MARGIN + (panelLeft - 8 - MARGIN) / 2;
		if (!ClientWaypoints.hasData()) {
			graphics.drawCenteredString(font, "Loading…", listCentre, TOP + 20, Colors.GRAY);
		} else if (routeList.children().isEmpty()) {
			graphics.drawCenteredString(font, "No routes yet.", listCentre, TOP + 20, Colors.GRAY);
			graphics.drawCenteredString(font, "Click + New route.", listCentre, TOP + 32, Colors.GRAY);
		}
		selected().ifPresentOrElse(route -> renderDetails(graphics, route),
				() -> graphics.drawString(font, "Select a route", panelLeft + 6, TOP + 6, Colors.GRAY));
		renderStatus(graphics);
	}

	private void renderDetails(GuiGraphics graphics, SyncPayload.RouteData route) {
		int x = panelLeft + 6;
		int maxWidth = width - MARGIN - x - 6;
		int y = TOP + 5;
		graphics.drawString(font, Component.literal(font.plainSubstrByWidth(route.name(), maxWidth))
				.withStyle(style -> style.withBold(true)), x, y, Colors.GOLD);
		y += 11;
		String line = route.description() != null ? "“" + route.description() + "”" : "by " + route.creatorName();
		graphics.drawString(font, font.plainSubstrByWidth(line, maxWidth), x, y, Colors.GRAY);
		y += 10;
		graphics.drawString(font, RouteLists.summary(ClientWaypoints.stops(route)), x, y, Colors.DARK_GRAY);
		if (route.stops().isEmpty() && route.canEdit()) {
			graphics.drawString(font, "Click + Stop to add waypoints.", x, stopsTop + 4, Colors.GRAY);
		}
	}

	private void renderStatus(GuiGraphics graphics) {
		int y = height - 20;
		Optional<ResultPayload> result = ClientWaypoints.recentResult();
		result.ifPresent(r -> graphics.drawString(font, font.plainSubstrByWidth(r.message(), width - 190), MARGIN, y,
				r.success() ? Colors.GREEN : Colors.RED));
		if (result.isEmpty()) {
			graphics.drawString(font, ClientWaypoints.routes().size() + " routes", MARGIN, y, Colors.DARK_GRAY);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public void removed() {
		ClientWaypoints.setListener(null);
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
