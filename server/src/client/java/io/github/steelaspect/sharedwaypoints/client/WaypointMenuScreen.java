package io.github.steelaspect.sharedwaypoints.client;

import dev.steelaspect.alloyui.Toast;
import dev.steelaspect.alloyui.UiContext;
import dev.steelaspect.alloyui.render.Draw;
import dev.steelaspect.alloyui.widget.Button;
import dev.steelaspect.alloyui.widget.Container;
import dev.steelaspect.alloyui.widget.Dialog;
import dev.steelaspect.alloyui.widget.Dropdown;
import dev.steelaspect.alloyui.widget.Menu;
import dev.steelaspect.alloyui.widget.Segmented;
import dev.steelaspect.alloyui.widget.TextField;
import io.github.steelaspect.sharedwaypoints.nav.NavMath;
import io.github.steelaspect.sharedwaypoints.network.ActionPayload.Action;
import io.github.steelaspect.sharedwaypoints.text.Formats;
import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import io.github.steelaspect.sharedwaypoints.waypoint.Category;
import io.github.steelaspect.sharedwaypoints.waypoint.ProjectStatus;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import io.github.steelaspect.sharedwaypoints.xaero.XaeroShareFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The waypoint menu: search, + Add and settings in the header; filter chips, a category (or status) choice and the
 * sort above the list; the selected waypoint's details and actions on the right. On a narrow window the details
 * replace the list (with a back button). Routes opens {@link RoutesScreen}.
 */
public final class WaypointMenuScreen extends WaypointsScreen {
	private enum Chip { ALL, FAVORITES, PROJECTS }

	/** "Any" in the category / status dropdown. */
	private static final String ANY = "";

	// The view, remembered while the game runs when "Remember last view" is on.
	private static String search = "";
	private static Chip chip = Chip.ALL;
	/** A category id, or a status id while the Projects chip is on; {@link #ANY} for all. */
	private static String refine = ANY;
	private static ClientSettings.Sort sort = ClientSettings.defaultSort();
	private static @Nullable UUID selectedId;
	private static boolean viewRemembered;

	private final SelectList<Waypoint> list;
	private final Frame frame;
	/** On a narrow window: true while the details are shown instead of the list. */
	private boolean detailOpen;

	public WaypointMenuScreen() {
		this(null);
	}

	public WaypointMenuScreen(net.minecraft.client.gui.screens.@Nullable Screen parent) {
		super(Ui.tr("menu.title"), parent);
		if (!ClientSettings.rememberView() || !viewRemembered) {
			search = "";
			chip = Chip.ALL;
			refine = ANY;
			sort = ClientSettings.defaultSort();
			selectedId = null;
		}
		viewRemembered = true;

		list = new SelectList<Waypoint>(24, Waypoint::id, WaypointMenuScreen::renderRow)
				.onSelect(this::onSelect)
				.onActivate(waypoint -> go())
				.emptyText(() -> !ClientWaypoints.hasData() ? Ui.tr("menu.loading")
						: ClientWaypoints.waypoints().isEmpty() ? Ui.tr("menu.empty") : Ui.tr("menu.no_match"));

		frame = new Frame(Ui.tr("menu.title"));
		frame.headerFill(new TextField(() -> search, value -> {
			search = value;
			refresh();
		}).live().maxLength(64).placeholder(Ui.tr("menu.search")), 200);
		Button add = frame.header(new Button(Ui.tr("menu.add"), () -> minecraft.setScreen(new AddWaypointScreen(this))), -1);
		add.enabledWhen(ClientWaypoints::canAdd);
		add.tooltip(Ui.tr("menu.add.tooltip"));
		frame.header(new Button(Component.literal("⚙"), () -> minecraft.setScreen(new SettingsScreen(this))), 18)
				.tooltip(Ui.tr("settings.title"));
		frame.body(new Body());
		frame.footerText(this::footerText);
		frame.footer(new Button(Ui.tr("menu.routes"), () -> minecraft.setScreen(new RoutesScreen(this))))
				.tooltip(Ui.tr("menu.routes.tooltip"));
		frame.footer(new Button(Component.translatable("gui.done"), this::onClose));
		setRoot(frame);
	}

	@Override
	protected void init() {
		super.init();
		SharedWaypointsClient.send(Action.SYNC);
		refresh();
	}

	// ---------------------------------------------------------------- layout

	/** The body: the filter bar, then the list and the details side by side (or one of them when narrow). */
	private final class Body extends Container {
		private static final float BAR = 16, GAP = 4;
		private final Segmented chips;
		private final Dropdown<String> refineChoice;
		private final Dropdown<ClientSettings.Sort> sortChoice;
		private final Details details;
		private final Button back;

		Body() {
			chips = add(new Segmented(() -> List.of(Ui.tr("filter.all"), Ui.tr("filter.favorites"), Ui.tr("filter.projects")),
					() -> chip.ordinal(), i -> {
						chip = Chip.values()[i];
						refine = ANY;
						refresh();
					}).compact());
			refineChoice = add(new Dropdown<>(WaypointMenuScreen::refineValues, WaypointMenuScreen::refineLabel,
					() -> refine, value -> {
						refine = value;
						refresh();
					}));
			sortChoice = add(new Dropdown<>(() -> List.of(ClientSettings.Sort.values()),
					value -> Ui.tr("sort." + value.id()), () -> sort, value -> {
						sort = value;
						refresh();
					}));
			add(list);
			details = add(new Details());
			back = add(new Button(Ui.tr("menu.back"), () -> detailOpen = false));
			back.style(Button.Style.GHOST);
		}

		boolean narrow() {
			return w < 300;
		}

		@Override
		public float measure(float width) {
			return 0;
		}

		@Override
		protected void layout() {
			boolean narrow = narrow();
			if (!narrow) {
				detailOpen = false;
			}
			boolean showList = !narrow || !detailOpen;
			boolean showDetails = !narrow || detailOpen;
			chips.visible(showList);
			refineChoice.visible(showList);
			sortChoice.visible(showList);
			list.visible(showList);
			details.visible(showDetails);
			back.visible(narrow && detailOpen);

			// The filter bar spans the whole body; the list and the details share the room below it.
			float top = y + GAP;
			if (showList) {
				float chipsW = Math.min(chips.preferredWidth(), w * 0.45f);
				chips.setBounds(x, top, chipsW, BAR);
				float dw = Math.min(130, (w - chipsW - 2 * GAP) / 2);
				sortChoice.setBounds(x + w - dw, top, dw, BAR);
				refineChoice.setBounds(x + w - 2 * dw - GAP, top, dw, BAR);
			} else if (back.visible()) {
				back.setBounds(x, top, back.preferredWidth(), BAR);
			}
			float below = top + BAR + GAP + 2;
			float listW = narrow ? w : (float) Math.floor(w * 0.5f);
			if (showList) {
				list.setBounds(x, below, listW, y + h - below - GAP);
			}
			if (showDetails) {
				float dx = narrow ? x : x + listW + 3 * GAP;
				float dw = narrow ? w : w - listW - 3 * GAP;
				details.setBounds(dx, below + 2, dw, y + h - below - GAP - 2);
			}
		}
	}

	/** The selected waypoint's details, with its actions at the bottom. */
	private final class Details extends Container {
		private static final float ROW = 16, GAP = 4;
		private final TextLines text;
		private final Button goButton;
		private final Button copyButton;
		private final Button favoriteButton;
		private final Button moreButton;

		Details() {
			text = add(new TextLines(WaypointMenuScreen.this::detailLines));
			goButton = add(new Button(() -> isNavigating() ? Ui.tr("action.stop") : Ui.tr("action.go"), WaypointMenuScreen.this::go));
			goButton.style(Button.Style.PRIMARY);
			goButton.tooltip(Ui.tr("action.go.tooltip"));
			copyButton = add(new Button(Ui.tr("action.copy"), WaypointMenuScreen.this::copyCoordinates));
			copyButton.tooltip(Ui.tr("action.copy.tooltip"));
			favoriteButton = add(new Button(() -> selectedData().map(d -> d.favorite()).orElse(false)
					? Ui.tr("action.unfavorite") : Ui.tr("action.favorite"), () -> act(Action.FAVORITE)));
			favoriteButton.tooltip(Ui.tr("action.favorite.tooltip"));
			moreButton = add(new Button(Component.literal("⋯"), this::openMore));
			moreButton.tooltip(Ui.tr("action.more"));

			goButton.enabledWhen(() -> selected().isPresent());
			copyButton.enabledWhen(() -> selected().isPresent());
			favoriteButton.enabledWhen(() -> selected().isPresent());
			moreButton.enabledWhen(() -> selected().isPresent());

			// The menu items, so the client test can reach them by label.
			testAction(Ui.tr("action.xaero"), WaypointMenuScreen.this::addToXaero);
			testAction(Ui.tr("action.edit"), WaypointMenuScreen.this::edit);
			testAction(Ui.tr("action.status"), WaypointMenuScreen.this::status);
			testAction(Ui.tr("action.teleport"), WaypointMenuScreen.this::teleport);
			testAction(Ui.tr("action.remove"), WaypointMenuScreen.this::remove);
		}

		private void openMore() {
			var data = selectedData();
			Menu menu = new Menu()
					.item(Ui.tr("action.xaero"), WaypointMenuScreen.this::addToXaero)
					.disabledWhen(() -> !Ui.xaeroInstalled())
					.separator()
					.item(Ui.tr("action.edit"), WaypointMenuScreen.this::edit)
					.disabledWhen(() -> !data.map(d -> d.canEdit()).orElse(false))
					.item(Ui.tr("action.status"), WaypointMenuScreen.this::status)
					.disabledWhen(() -> !data.map(d -> d.canSetStatus()).orElse(false));
			if (ClientWaypoints.canTeleport()) {
				menu.item(Ui.tr("action.teleport"), WaypointMenuScreen.this::teleport);
			}
			menu.separator()
					.item(Ui.tr("action.remove"), WaypointMenuScreen.this::remove)
					.disabledWhen(() -> !data.map(d -> d.canRemove()).orElse(false))
					.danger();
			menu.open(ctx, moreButton.x(), moreButton.y() + moreButton.h() + 2);
		}

		@Override
		public float measure(float width) {
			return 0;
		}

		@Override
		protected void layout() {
			float bottom = y + h;
			float rowY = bottom - ROW;
			float moreW = 18;
			float half = (w - moreW - 2 * GAP) / 2;
			copyButton.setBounds(x, rowY, half, ROW);
			favoriteButton.setBounds(x + half + GAP, rowY, half, ROW);
			moreButton.setBounds(x + w - moreW, rowY, moreW, ROW);
			goButton.setBounds(x, rowY - GAP - ROW, w, ROW);
			text.setBounds(x, y + 2, w, Math.max(0, rowY - GAP - ROW - GAP - (y + 2)));
		}

		@Override
		protected void renderWidget(UiContext ctx) {
			Draw.roundRect(ctx.g, x - 4, y - 2, w + 8, h + 2, ctx.theme.metricF("radius"), ctx.theme.color("panelAlt"));
			if (selected().isEmpty()) {
				Draw.text(ctx.g, Ui.tr("menu.select"), x, y + 4, ctx.theme.color("textMuted"));
			}
			super.renderWidget(ctx);
		}
	}

	private static void renderRow(UiContext ctx, Waypoint waypoint, float x, float y, float w, float h, boolean selected) {
		var t = ctx.theme;
		Draw.roundRect(ctx.g, x + 4, y + 4, 6, 6, 1, Ui.argb(waypoint.category()));
		boolean favorite = ClientWaypoints.data(waypoint.id()).map(data -> data.favorite()).orElse(false);
		boolean navigating = waypoint.id().equals(ClientWaypoints.navigatingTo());
		String distance = Ui.distanceLabel(waypoint);
		float distanceW = distance == null ? 0 : Draw.textWidth(Component.literal(distance)) + 8;
		String name = (navigating ? "▶ " : "") + waypoint.name() + (favorite ? " ★" : "")
				+ (waypoint.status() != null ? " " + waypoint.status().state().symbol() : "");
		Draw.textClipped(ctx.g, Component.literal(name), x + 14, y + 2, w - 14 - distanceW,
				t.color(navigating ? "good" : "text"), Draw.Role.STRONG);
		if (distance != null) {
			Draw.text(ctx.g, Component.literal(distance), x + w - distanceW + 4, y + 2, t.color("textMuted"));
		}
		Draw.textClipped(ctx.g, Component.literal(waypoint.coordinates() + "  ·  " + Dimensions.shortName(waypoint.dimension())),
				x + 14, y + 12, w - 18, t.color("textMuted"));
	}

	private List<TextLines.Line> detailLines() {
		Optional<Waypoint> selected = selected();
		if (selected.isEmpty()) {
			return List.of();
		}
		Waypoint waypoint = selected.get();
		Category category = waypoint.category();
		List<TextLines.Line> lines = new ArrayList<>();
		boolean favorite = selectedData().map(d -> d.favorite()).orElse(false);
		lines.add(TextLines.Line.heading(Component.literal(waypoint.name() + (favorite ? " ★" : "")), Ui.argb(category)));
		lines.add(TextLines.Line.of(Component.literal(category.displayName() + " · " + Dimensions.shortName(waypoint.dimension())),
				"textMuted"));
		String distance = Ui.distanceLabel(waypoint);
		Component where = distance != null ? Component.literal(distance.replace(" ⟳", "")).append(distance.endsWith("⟳")
				? Ui.tr("details.via_portal") : Component.empty()) : Ui.tr("details.in", Dimensions.shortName(waypoint.dimension()));
		lines.add(TextLines.Line.of(Component.literal(waypoint.coordinates()), "text").withRight(where, "info"));
		// The other side at the player's own height: the waypoint's Y belongs to its own dimension.
		var player = Minecraft.getInstance().player;
		Optional<NavMath.Target> other = player == null ? Optional.empty() : NavMath.portalEquivalent(waypoint, player.getBlockY());
		other.ifPresent(target -> lines.add(TextLines.Line.of(Ui.tr(waypoint.dimension().equals(Dimensions.OVERWORLD)
				? "details.nether_side" : "details.overworld_side", target.x() + " " + target.y() + " " + target.z()), "accent")));
		if (isNavigating()) {
			lines.add(TextLines.Line.of(Ui.tr("details.navigating"), "good"));
		}
		if (waypoint.status() != null) {
			ProjectStatus status = waypoint.status();
			lines.add(TextLines.Line.space());
			lines.add(new TextLines.Line(Component.literal(status.summary()), null, 0xFF000000 | status.state().rgb(), false, 2, 0, null, null));
			lines.add(TextLines.Line.of(Ui.tr("details.status_by", status.setByName(), Formats.relativeAge(status.setAt(), Instant.now())),
					"textMuted"));
		}
		if (waypoint.description() != null) {
			lines.add(TextLines.Line.space());
			lines.add(TextLines.Line.wrapped(Component.literal("“" + waypoint.description() + "”"), "textMuted", 3));
		}
		lines.add(TextLines.Line.space());
		lines.add(TextLines.Line.of(Ui.tr("details.added_by", waypoint.creatorName(), Formats.relativeAge(waypoint.created(), Instant.now())),
				"textMuted"));
		return lines;
	}

	private Component footerText() {
		return Ui.tr("menu.footer", ClientWaypoints.waypoints().size(), SharedWaypointsClient.openMenuKey().getTranslatedKeyMessage());
	}

	// ---------------------------------------------------------------- filters

	private static List<String> refineValues() {
		List<String> ids = new ArrayList<>();
		ids.add(ANY);
		if (chip == Chip.PROJECTS) {
			for (ProjectStatus.State state : ProjectStatus.State.values()) {
				ids.add(state.id());
			}
		} else {
			ClientWaypoints.categories().forEach(category -> ids.add(category.id()));
		}
		return ids;
	}

	private static Component refineLabel(String id) {
		if (ANY.equals(id)) {
			return Ui.tr(chip == Chip.PROJECTS ? "filter.any_status" : "filter.any_category");
		}
		if (chip == Chip.PROJECTS) {
			return ProjectStatus.State.byId(id).<Component>map(state -> Component.literal(state.symbol() + " " + state.displayName()))
					.orElse(Component.literal(id));
		}
		return Component.literal(ClientWaypoints.category(id).displayName());
	}

	@Override
	protected void onServerUpdate() {
		refresh();
	}

	private void refresh() {
		if (!refineValues().contains(refine)) {
			refine = ANY;
		}
		String needle = search.trim().toLowerCase(Locale.ROOT);
		List<Waypoint> shown = ClientWaypoints.waypoints().stream()
				.filter(waypoint -> needle.isEmpty() || matches(waypoint, needle))
				.filter(waypoint -> switch (chip) {
					case ALL -> true;
					case FAVORITES -> isFavorite(waypoint);
					case PROJECTS -> waypoint.status() != null;
				})
				.filter(waypoint -> ANY.equals(refine) || (chip == Chip.PROJECTS
						? waypoint.status() != null && waypoint.status().state().id().equals(refine)
						: waypoint.category().id().equals(refine)))
				.sorted(comparator())
				.toList();
		list.show(shown);
		Waypoint keep = shown.stream().filter(waypoint -> waypoint.id().equals(selectedId)).findFirst().orElse(null);
		if (keep == null && !shown.isEmpty() && selectedId == null) {
			keep = shown.get(0);
		}
		list.selectQuietly(keep);
		if (keep != null) {
			selectedId = keep.id();
		}
	}

	private static boolean matches(Waypoint waypoint, String needle) {
		return waypoint.name().toLowerCase(Locale.ROOT).contains(needle)
				|| waypoint.creatorName().toLowerCase(Locale.ROOT).contains(needle)
				|| waypoint.category().displayName().toLowerCase(Locale.ROOT).contains(needle)
				|| waypoint.descriptionText().map(text -> text.toLowerCase(Locale.ROOT).contains(needle)).orElse(false)
				|| waypoint.statusInfo().map(status -> status.state().displayName().toLowerCase(Locale.ROOT).contains(needle)
						|| status.noteText().map(note -> note.toLowerCase(Locale.ROOT).contains(needle)).orElse(false))
						.orElse(false);
	}

	private static Comparator<Waypoint> comparator() {
		Comparator<Waypoint> byName = Comparator.comparing(Waypoint::name, String.CASE_INSENSITIVE_ORDER);
		return switch (sort) {
			case CATEGORY -> Comparator.comparingInt((Waypoint waypoint) -> waypoint.category().order()).thenComparing(byName);
			case NAME -> byName;
			case DISTANCE -> Comparator.comparingDouble(Ui::distance).thenComparing(byName);
		};
	}

	private void onSelect(@Nullable Waypoint waypoint) {
		selectedId = waypoint == null ? null : waypoint.id();
		if (waypoint != null) {
			detailOpen = true;
		}
	}

	private Optional<Waypoint> selected() {
		return Optional.ofNullable(list.selected());
	}

	private Optional<io.github.steelaspect.sharedwaypoints.network.SyncPayload.WaypointData> selectedData() {
		return selected().flatMap(waypoint -> ClientWaypoints.data(waypoint.id()));
	}

	private boolean isNavigating() {
		return selected().map(waypoint -> waypoint.id().equals(ClientWaypoints.navigatingTo())).orElse(false);
	}

	private static boolean isFavorite(Waypoint waypoint) {
		return ClientWaypoints.data(waypoint.id()).map(data -> data.favorite()).orElse(false);
	}

	// ---------------------------------------------------------------- actions

	private void act(Action action, String... extra) {
		selected().ifPresent(waypoint -> {
			String[] args = new String[extra.length + 1];
			args[0] = waypoint.id().toString();
			System.arraycopy(extra, 0, args, 1, extra.length);
			SharedWaypointsClient.send(action, args);
		});
	}

	/** Go (and close the menu so the compass is visible), or Stop if already navigating there. */
	private void go() {
		selected().ifPresent(waypoint -> {
			if (waypoint.id().equals(ClientWaypoints.navigatingTo())) {
				SharedWaypointsClient.send(Action.STOP);
			} else {
				act(Action.GO);
				minecraft.setScreen(null);
			}
		});
	}

	/** Opens Xaero's own add-waypoint screen, pre-filled, by running the command Xaero's [Add] button runs. */
	private void addToXaero() {
		selected().ifPresent(waypoint -> {
			String command = XaeroShareFormat.addCommand(waypoint.name(), waypoint.x(), waypoint.y(), waypoint.z(),
					waypoint.category().xaeroColorIndex(), waypoint.dimension());
			minecraft.setScreen(null);
			if (minecraft.getConnection() != null) {
				minecraft.getConnection().sendUnattendedCommand(command, null);
			}
		});
	}

	private void copyCoordinates() {
		selected().ifPresent(waypoint -> {
			minecraft.keyboardHandler.setClipboard(waypoint.coordinates());
			toast(Toast.Kind.SUCCESS, Ui.tr("toast.copied", waypoint.coordinates()));
		});
	}

	private void teleport() {
		act(Action.TELEPORT);
		minecraft.setScreen(null);
	}

	private void status() {
		selected().ifPresent(waypoint -> minecraft.setScreen(new StatusScreen(this, waypoint)));
	}

	private void edit() {
		selected().ifPresent(waypoint -> minecraft.setScreen(new EditWaypointScreen(this, waypoint)));
	}

	private void remove() {
		selected().ifPresent(waypoint -> confirm(new Dialog(Ui.tr("remove.title", waypoint.name()), Ui.tr("remove.message"),
				Ui.tr("action.remove"), true, dialog -> act(Action.REMOVE))));
	}

	// ---------------------------------------------------------------- test hooks

	/**
	 * Sets the view (used by the client test): search text, filter ("all", "favorites", "projects", "status:&lt;id&gt;"
	 * or a category id) and sort ("category", "name" or "distance").
	 */
	public void setViewForTest(String query, String filterId, String sortName) {
		search = query;
		switch (filterId) {
			case "all" -> {
				chip = Chip.ALL;
				refine = ANY;
			}
			case "favorites" -> {
				chip = Chip.FAVORITES;
				refine = ANY;
			}
			case "projects" -> {
				chip = Chip.PROJECTS;
				refine = ANY;
			}
			default -> {
				chip = filterId.startsWith("status:") ? Chip.PROJECTS : Chip.ALL;
				refine = filterId.startsWith("status:") ? filterId.substring("status:".length()) : filterId;
			}
		}
		sort = ClientSettings.Sort.valueOf(sortName.toUpperCase(Locale.ROOT));
		rebuildWidgets();
	}

	/** Opens the routes screen (used by the client test; players click Routes). */
	public void openRoutesForTest() {
		minecraft.setScreen(new RoutesScreen(this));
	}

	/** Selects a waypoint by name (used by the client test). */
	public void selectForTest(String name) {
		list.items().stream().filter(waypoint -> waypoint.name().equals(name)).findFirst().ifPresent(list::select);
	}

	/** Names of the listed waypoints, in order (used by the client test). */
	public List<String> namesForTest() {
		return list.items().stream().map(Waypoint::name).toList();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent instanceof SettingsScreen ? null : parent);
	}
}
