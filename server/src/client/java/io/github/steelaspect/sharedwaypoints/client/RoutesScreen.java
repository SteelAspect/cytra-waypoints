package io.github.steelaspect.sharedwaypoints.client;

import dev.steelaspect.alloyui.UiContext;
import dev.steelaspect.alloyui.render.Draw;
import dev.steelaspect.alloyui.widget.Button;
import dev.steelaspect.alloyui.widget.Container;
import dev.steelaspect.alloyui.widget.Dialog;
import dev.steelaspect.alloyui.widget.Menu;
import io.github.steelaspect.sharedwaypoints.network.ActionPayload.Action;
import io.github.steelaspect.sharedwaypoints.network.SyncPayload;
import io.github.steelaspect.sharedwaypoints.waypoint.Route;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Routes: the list of routes on the left; the selected route's details, its stops and the actions on the right.
 * Opened from the waypoint menu's Routes button.
 */
public final class RoutesScreen extends WaypointsScreen {
	/** One stop in the stop list: its 0-based position and the waypoint. */
	private record Stop(int index, Waypoint waypoint) {}

	// Remembered while the game runs, so the screen reopens where you left it.
	private static @Nullable UUID selectedId;
	private static int selectedStop = -1;
	/** A route created from this screen gets selected once the server lists it. */
	private static @Nullable String pendingSelectName;

	private final SelectList<SyncPayload.RouteData> routeList;
	private final SelectList<Stop> stopList;
	private boolean detailOpen;

	public RoutesScreen(@Nullable Screen parent) {
		super(Ui.tr("routes.title"), parent);
		routeList = new SelectList<SyncPayload.RouteData>(24, SyncPayload.RouteData::id, RoutesScreen::renderRoute)
				.onSelect(route -> {
					UUID newId = route == null ? null : route.id();
					if (newId == null || !newId.equals(selectedId)) {
						selectedStop = -1;
					}
					selectedId = newId;
					if (route != null) {
						detailOpen = true;
					}
					refreshStops();
				})
				.onActivate(route -> go())
				.emptyText(() -> !ClientWaypoints.hasData() ? Ui.tr("menu.loading") : Ui.tr("routes.empty"));
		stopList = new SelectList<Stop>(13, Stop::index, RoutesScreen::renderStop)
				.onSelect(stop -> selectedStop = stop == null ? -1 : stop.index())
				.emptyText(() -> selected().map(route -> route.canEdit() ? Ui.tr("routes.add_stops") : Ui.tr("route.no_stops"))
						.orElse(Component.empty()));

		Frame frame = new Frame(Ui.tr("routes.title"));
		Button create = frame.header(new Button(Ui.tr("routes.new"), () -> minecraft.setScreen(new EditRouteScreen(this, null))), -1);
		create.enabledWhen(ClientWaypoints::canAddRoute);
		create.tooltip(Ui.tr("routes.new.tooltip"));
		frame.body(new Body());
		frame.footerText(() -> Ui.tr("routes.footer", ClientWaypoints.routes().size()));
		frame.footer(new Button(Ui.tr("routes.waypoints"), () -> minecraft.setScreen(parent != null ? parent : new WaypointMenuScreen()))).tooltip(Ui.tr("routes.waypoints.tooltip"));
		frame.footer(new Button(Component.translatable("gui.done"), () -> minecraft.setScreen(null)));
		setRoot(frame);

		testAction(Ui.tr("routes.edit"), this::edit);
		testAction(Ui.tr("routes.delete"), this::delete);
	}

	@Override
	protected void init() {
		super.init();
		SharedWaypointsClient.send(Action.SYNC);
		refresh();
	}

	private final class Body extends Container {
		private static final float ROW = 16, GAP = 4;
		private final TextLines text;
		private final Button goButton;
		private final Button skipButton;
		private final Button addStopButton;
		private final Button dropStopButton;
		private final Button upButton;
		private final Button downButton;
		private final Button moreButton;
		private final Button back;

		Body() {
			add(routeList);
			text = add(new TextLines(RoutesScreen.this::detailLines));
			add(stopList);
			goButton = add(new Button(RoutesScreen.this::goLabel, RoutesScreen.this::go));
			goButton.style(Button.Style.PRIMARY);
			goButton.tooltip(Ui.tr("routes.start.tooltip"));
			skipButton = add(new Button(Ui.tr("routes.skip"), () -> SharedWaypointsClient.send(Action.ROUTE_SKIP)));
			skipButton.tooltip(Ui.tr("routes.skip.tooltip"));
			addStopButton = add(new Button(Ui.tr("routes.add_stop"), RoutesScreen.this::addStop));
			addStopButton.tooltip(Ui.tr("routes.add_stop.tooltip"));
			dropStopButton = add(new Button(Ui.tr("routes.drop_stop"), RoutesScreen.this::dropStop));
			dropStopButton.tooltip(Ui.tr("routes.drop_stop.tooltip"));
			upButton = add(new Button(Component.literal("↑"), () -> moveStop(-1)));
			upButton.tooltip(Ui.tr("routes.up.tooltip"));
			downButton = add(new Button(Component.literal("↓"), () -> moveStop(1)));
			downButton.tooltip(Ui.tr("routes.down.tooltip"));
			moreButton = add(new Button(Component.literal("⋯"), this::openMore));
			moreButton.tooltip(Ui.tr("action.more"));
			back = add(new Button(Ui.tr("menu.back"), () -> detailOpen = false));
			back.style(Button.Style.GHOST);

			goButton.enabledWhen(() -> selected().map(route -> isFollowing(route) || !route.stops().isEmpty()).orElse(false));
			skipButton.enabledWhen(() -> selected().map(RoutesScreen::isFollowing).orElse(false));
			addStopButton.enabledWhen(() -> canEdit() && selected().get().stops().size() < Route.MAX_STOPS);
			dropStopButton.enabledWhen(() -> canEdit() && stopList.selectedIndex() >= 0);
			upButton.enabledWhen(() -> canEdit() && stopList.selectedIndex() > 0);
			downButton.enabledWhen(() -> canEdit() && stopList.selectedIndex() >= 0
					&& stopList.selectedIndex() < selected().get().stops().size() - 1);
			moreButton.enabledWhen(() -> selected().isPresent());
		}

		private void openMore() {
			new Menu()
					.item(Ui.tr("routes.edit"), RoutesScreen.this::edit)
					.disabledWhen(() -> !canEdit())
					.separator()
					.item(Ui.tr("routes.delete"), RoutesScreen.this::delete)
					.disabledWhen(() -> !selected().map(SyncPayload.RouteData::canRemove).orElse(false))
					.danger()
					.open(ctx, moreButton.x(), moreButton.y() + moreButton.h() + 2);
		}

		@Override
		public float measure(float width) {
			return 0;
		}

		@Override
		protected void layout() {
			boolean narrow = w < 300;
			if (!narrow) {
				detailOpen = false;
			}
			boolean showList = !narrow || !detailOpen;
			boolean showDetails = !narrow || detailOpen;
			routeList.visible(showList);
			for (var widget : List.of(text, stopList, goButton, skipButton, addStopButton, dropStopButton, upButton, downButton, moreButton)) {
				widget.visible(showDetails);
			}
			back.visible(narrow && detailOpen);

			float top = y + GAP;
			float listW = narrow ? w : (float) Math.floor(w * 0.45f);
			if (showList) {
				routeList.setBounds(x, top, listW, h - 2 * GAP);
			}
			if (!showDetails) {
				return;
			}
			float dx = narrow ? x : x + listW + 2 * GAP;
			float dw = narrow ? w : w - listW - 2 * GAP;
			float dy = top;
			if (back.visible()) {
				back.setBounds(dx, dy, back.preferredWidth(), ROW);
				dy += ROW + GAP;
			}
			float bottom = y + h - GAP;
			float half = (dw - GAP) / 2;
			goButton.setBounds(dx, bottom - ROW, half, ROW);
			skipButton.setBounds(dx + half + GAP, bottom - ROW, half, ROW);
			float editY = bottom - 2 * ROW - GAP;
			float small = 18;
			float wide = (dw - 3 * small - 4 * GAP) / 2;
			addStopButton.setBounds(dx, editY, wide, ROW);
			dropStopButton.setBounds(dx + wide + GAP, editY, wide, ROW);
			upButton.setBounds(dx + 2 * (wide + GAP), editY, small, ROW);
			downButton.setBounds(dx + 2 * (wide + GAP) + small + GAP, editY, small, ROW);
			moreButton.setBounds(dx + dw - small, editY, small, ROW);
			float textH = text.measure(dw);
			text.setBounds(dx, dy, dw, textH);
			stopList.setBounds(dx, dy + textH + GAP, dw, Math.max(0, editY - GAP - (dy + textH + GAP)));
		}

		@Override
		protected void renderWidget(UiContext ctx) {
			if (text.visible() && selected().isEmpty()) {
				Draw.text(ctx.g, Ui.tr("routes.select"), text.x(), text.y() + 2, ctx.theme.color("textMuted"));
			}
			super.renderWidget(ctx);
		}
	}

	private static void renderRoute(UiContext ctx, SyncPayload.RouteData route, float x, float y, float w, float h, boolean selected) {
		var t = ctx.theme;
		SyncPayload.RouteProgressData progress = ClientWaypoints.onRoute();
		boolean following = progress != null && progress.routeId().equals(route.id());
		Draw.textClipped(ctx.g, Component.literal((following ? "▶ " : "") + route.name()), x + 4, y + 2, w - 8,
				t.color(following ? "good" : "text"), Draw.Role.STRONG);
		Draw.textClipped(ctx.g, Ui.tr("routes.row", Ui.routeSummary(ClientWaypoints.stops(route)), route.creatorName()),
				x + 4, y + 12, w - 8, t.color("textMuted"));
	}

	private static void renderStop(UiContext ctx, Stop stop, float x, float y, float w, float h, boolean selected) {
		var t = ctx.theme;
		SyncPayload.RouteProgressData progress = ClientWaypoints.onRoute();
		boolean current = progress != null && selectedId != null && progress.routeId().equals(selectedId)
				&& progress.stopIndex() == stop.index();
		Draw.text(ctx.g, Component.literal(current ? "▶" : (stop.index() + 1) + "."), x + 3, y + 2, t.color(current ? "good" : "textMuted"));
		Draw.roundRect(ctx.g, x + 18, y + 3, 5, 5, 1, Ui.argb(stop.waypoint().category()));
		String distance = Ui.distanceLabel(stop.waypoint());
		float distanceW = distance == null ? 0 : Draw.textWidth(Component.literal(distance)) + 6;
		Draw.textClipped(ctx.g, Component.literal(stop.waypoint().name()), x + 27, y + 2, w - 30 - distanceW, t.color("text"));
		if (distance != null) {
			Draw.text(ctx.g, Component.literal(distance), x + w - distanceW + 2, y + 2, t.color("textMuted"));
		}
	}

	private List<TextLines.Line> detailLines() {
		Optional<SyncPayload.RouteData> route = selected();
		if (route.isEmpty()) {
			return List.of();
		}
		List<TextLines.Line> lines = new ArrayList<>();
		lines.add(TextLines.Line.heading(Component.literal(route.get().name()), 0));
		lines.add(route.get().description() != null
				? TextLines.Line.of(Component.literal("“" + route.get().description() + "”"), "textMuted")
				: TextLines.Line.of(Ui.tr("routes.by", route.get().creatorName()), "textMuted"));
		lines.add(TextLines.Line.of(Ui.routeSummary(ClientWaypoints.stops(route.get())), "textMuted"));
		return lines;
	}

	// ---------------------------------------------------------------- updates

	@Override
	protected void onServerUpdate() {
		refresh();
	}

	private void refresh() {
		List<SyncPayload.RouteData> routes = ClientWaypoints.routes();
		if (pendingSelectName != null) {
			routes.stream().filter(route -> route.name().equals(pendingSelectName)).findFirst().ifPresent(route -> {
				selectedId = route.id();
				selectedStop = -1;
				pendingSelectName = null;
			});
		}
		routeList.show(routes);
		SyncPayload.RouteData keep = routes.stream().filter(route -> route.id().equals(selectedId)).findFirst()
				.orElse(routes.isEmpty() ? null : routes.get(0));
		routeList.selectQuietly(keep);
		selectedId = keep == null ? null : keep.id();
		refreshStops();
	}

	private void refreshStops() {
		List<Waypoint> waypoints = selected().map(ClientWaypoints::stops).orElse(List.of());
		List<Stop> stops = new ArrayList<>();
		for (int i = 0; i < waypoints.size(); i++) {
			stops.add(new Stop(i, waypoints.get(i)));
		}
		stopList.show(stops);
		if (selectedStop >= stops.size()) {
			selectedStop = stops.size() - 1;
		}
		stopList.selectQuietly(selectedStop >= 0 ? stops.get(selectedStop) : null);
	}

	private Optional<SyncPayload.RouteData> selected() {
		return Optional.ofNullable(routeList.selected());
	}

	private boolean canEdit() {
		return selected().map(SyncPayload.RouteData::canEdit).orElse(false);
	}

	private static boolean isFollowing(SyncPayload.RouteData route) {
		SyncPayload.RouteProgressData progress = ClientWaypoints.onRoute();
		return progress != null && progress.routeId().equals(route.id());
	}

	private Component goLabel() {
		Optional<SyncPayload.RouteData> route = selected();
		if (route.isPresent() && isFollowing(route.get())) {
			return Ui.tr("action.stop");
		}
		int stop = stopList.selectedIndex();
		return stop > 0 ? Ui.tr("routes.start_from", stop + 1) : Ui.tr("routes.start");
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
				SharedWaypointsClient.send(Action.ROUTE_MOVE, route.id().toString(), String.valueOf(stop + 1), String.valueOf(to + 1));
				selectedStop = to;
			}
		});
	}

	private void edit() {
		selected().ifPresent(route -> minecraft.setScreen(new EditRouteScreen(this, route)));
	}

	private void delete() {
		selected().ifPresent(route -> confirm(new Dialog(Ui.tr("routes.delete.title", route.name()), Ui.tr("routes.delete.message"),
				Ui.tr("routes.delete"), true, dialog -> SharedWaypointsClient.send(Action.ROUTE_DELETE, route.id().toString()))));
	}

	/** Selects a route by name and optionally a stop (0-based, -1 for none); used by the client test. */
	public void selectForTest(String name, int stop) {
		routeList.items().stream().filter(route -> route.name().equals(name)).findFirst().ifPresent(route -> {
			routeList.select(route);
			stopList.select(stop >= 0 && stop < stopList.items().size() ? stopList.items().get(stop) : null);
		});
	}

	/** Stop names of the selected route, in order (used by the client test). */
	public List<String> stopsForTest() {
		return stopList.items().stream().map(stop -> stop.waypoint().name()).toList();
	}
}
