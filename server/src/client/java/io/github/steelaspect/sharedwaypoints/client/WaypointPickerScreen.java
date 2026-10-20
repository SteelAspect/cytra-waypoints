package io.github.steelaspect.sharedwaypoints.client;

import dev.steelaspect.alloyui.UiContext;
import dev.steelaspect.alloyui.render.Draw;
import dev.steelaspect.alloyui.widget.Button;
import dev.steelaspect.alloyui.widget.TextField;
import io.github.steelaspect.sharedwaypoints.network.ActionPayload.Action;
import io.github.steelaspect.sharedwaypoints.network.SyncPayload;
import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import io.github.steelaspect.sharedwaypoints.waypoint.WaypointStore;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Pick a waypoint to add as the last stop of a route: search, then double-click or press Add stop. */
final class WaypointPickerScreen extends WaypointsScreen {
	private final SyncPayload.RouteData route;
	private final SelectList<Waypoint> list;
	private final TextField searchField;
	private String search = "";

	WaypointPickerScreen(Screen parent, SyncPayload.RouteData route) {
		super(Ui.tr("picker.title", route.name()), parent);
		this.route = route;
		list = new SelectList<Waypoint>(24, Waypoint::id, WaypointPickerScreen::renderRow)
				.onActivate(this::add)
				.emptyText(() -> Ui.tr("menu.no_match"));
		Frame frame = new Frame(Ui.tr("picker.title", route.name())).maxSize(380, 340);
		searchField = frame.headerFill(new TextField(() -> search, value -> {
			search = value;
			refresh();
		}).live().maxLength(64).placeholder(Ui.tr("menu.search")), 160);
		frame.body(list);
		frame.footer(new Button(Ui.tr("form.cancel"), this::onClose));
		Button add = frame.footer(new Button(Ui.tr("picker.add"), () -> {
			Waypoint selected = list.selected();
			if (selected != null) {
				add(selected);
			}
		}));
		add.style(Button.Style.PRIMARY);
		add.enabledWhen(() -> list.selected() != null);
		setRoot(frame);
	}

	@Override
	protected void init() {
		super.init();
		refresh();
		setFocus(searchField);
	}

	private static void renderRow(UiContext ctx, Waypoint waypoint, float x, float y, float w, float h, boolean selected) {
		var t = ctx.theme;
		Draw.roundRect(ctx.g, x + 4, y + 4, 6, 6, 1, Ui.argb(waypoint.category()));
		String distance = Ui.distanceLabel(waypoint);
		float distanceW = distance == null ? 0 : Draw.textWidth(Component.literal(distance)) + 8;
		Draw.textClipped(ctx.g, Component.literal(waypoint.name()), x + 14, y + 2, w - 14 - distanceW, t.color("text"), Draw.Role.STRONG);
		if (distance != null) {
			Draw.text(ctx.g, Component.literal(distance), x + w - distanceW + 4, y + 2, t.color("textMuted"));
		}
		Draw.textClipped(ctx.g, Component.literal(waypoint.coordinates() + "  ·  " + Dimensions.shortName(waypoint.dimension())),
				x + 14, y + 12, w - 18, t.color("textMuted"));
	}

	@Override
	protected void onServerUpdate() {
		refresh();
	}

	private void refresh() {
		String needle = search.trim().toLowerCase(Locale.ROOT);
		List<Waypoint> shown = ClientWaypoints.waypoints().stream()
				.filter(waypoint -> needle.isEmpty() || waypoint.name().toLowerCase(Locale.ROOT).contains(needle)
						|| waypoint.category().displayName().toLowerCase(Locale.ROOT).contains(needle))
				.sorted(WaypointStore.DISPLAY_ORDER)
				.toList();
		Waypoint keep = list.selected();
		list.show(shown);
		list.selectQuietly(keep != null && shown.contains(keep) ? keep : shown.isEmpty() ? null : shown.get(0));
	}

	private void add(Waypoint waypoint) {
		SharedWaypointsClient.send(Action.ROUTE_ADD, route.id().toString(), waypoint.id().toString());
		minecraft.setScreen(parent);
	}

	/** Picks a waypoint by name (used by the client test). */
	public void pickForTest(String name) {
		ClientWaypoints.waypoints().stream().filter(waypoint -> waypoint.name().equals(name)).findFirst().ifPresent(this::add);
	}
}
