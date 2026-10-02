package io.github.steelaspect.sharedwaypoints.client;

import io.github.steelaspect.sharedwaypoints.nav.NavMath;
import io.github.steelaspect.sharedwaypoints.network.ActionPayload.Action;
import io.github.steelaspect.sharedwaypoints.network.ResultPayload;
import io.github.steelaspect.sharedwaypoints.text.Formats;
import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import io.github.steelaspect.sharedwaypoints.waypoint.Category;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import io.github.steelaspect.sharedwaypoints.xaero.XaeroShareFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The waypoint menu: search, category/favourite filter and sort along the top, the list on the left, details and
 * every action for the selected waypoint on the right. The Routes button opens {@link RoutesScreen}.
 */
public final class WaypointMenuScreen extends Screen {
	private static final int MARGIN = 10;
	private static final int TOP = 44;
	private static final String ALL = "\u0000all";
	private static final String FAVORITES = "\u0000favorites";

	/** Sorting options. */
	private enum Sort {
		CATEGORY("By category"), NAME("By name"), DISTANCE("By distance");

		final String label;

		Sort(String label) {
			this.label = label;
		}
	}

	// Remembered while the game runs, so the menu reopens where you left it.
	private static String search = "";
	private static String filter = ALL;
	private static Sort sort = Sort.CATEGORY;
	private static UUID selectedId;

	private WaypointList list;
	private Button goButton;
	private Button xaeroButton;
	private Button copyButton;
	private Button favoriteButton;
	private Button editButton;
	private Button teleportButton;
	private Button removeButton;
	private List<String> filterIds = List.of();
	/** Whether the current layout has the Teleport button (it changes the last row). */
	private boolean builtWithTeleport;
	private int panelLeft;
	private int bottom;
	private int buttonsTop;
	private String localStatus;
	private long localStatusAt;

	public WaypointMenuScreen() {
		super(Component.literal("Shared Waypoints"));
	}

	@Override
	protected void init() {
		ClientWaypoints.setListener(this::onServerUpdate);
		SharedWaypointsClient.send(Action.SYNC);

		int listRight = Math.max(170, (int) (width * 0.52));
		panelLeft = listRight + 8;
		bottom = height - 32;
		int panelWidth = width - MARGIN - panelLeft;

		// --- toolbar: search on the left, filter / sort / add on the right
		EditBox searchBox = new EditBox(font, MARGIN, 20, listRight - MARGIN, 18, Component.literal("Search"));
		searchBox.setHint(Component.literal("Search names, notes, creators…"));
		searchBox.setMaxLength(64);
		searchBox.setValue(search);
		searchBox.setResponder(value -> {
			search = value;
			refreshList();
		});
		addRenderableWidget(searchBox);

		filterIds = new ArrayList<>(List.of(ALL, FAVORITES));
		ClientWaypoints.categories().forEach(category -> filterIds.add(category.id()));
		if (!filterIds.contains(filter)) {
			filter = ALL;
		}
		int addWidth = 48;
		int cycleWidth = Math.max(60, (panelWidth - addWidth - 8) / 2);
		addRenderableWidget(CycleButton.builder(WaypointMenuScreen::filterLabel, filter)
				.withValues(filterIds)
				.displayOnlyValue()
				.create(panelLeft, 19, cycleWidth, 20, Component.literal("Show"), (button, value) -> {
					filter = value;
					refreshList();
				}));
		addRenderableWidget(CycleButton.builder((Sort value) -> Component.literal(value.label), sort)
				.withValues(Sort.values())
				.displayOnlyValue()
				.create(panelLeft + cycleWidth + 4, 19, cycleWidth, 20, Component.literal("Sort"), (button, value) -> {
					sort = value;
					refreshList();
				}));
		Button addButton = addRenderableWidget(Button.builder(Component.literal("+ Add"),
						button -> minecraft.setScreen(new AddWaypointScreen(this)))
				.bounds(width - MARGIN - addWidth, 19, addWidth, 20)
				.tooltip(Tooltip.create(Component.literal("Add a waypoint here or at any coordinates")))
				.build());
		addButton.active = ClientWaypoints.canAdd();

		// --- list
		list = addRenderableWidget(new WaypointList(minecraft, this::onSelect, waypoint -> go()));
		list.updateSizeAndPosition(listRight - MARGIN, bottom - TOP, MARGIN, TOP);

		// --- action buttons: three rows at the bottom of the details panel (the last row has a third
		// button, Teleport, for players allowed to teleport)
		int half = (panelWidth - 4) / 2;
		int third = (panelWidth - 8) / 3;
		boolean teleport = ClientWaypoints.canTeleport();
		builtWithTeleport = teleport;
		buttonsTop = bottom - 3 * 22 + 2;
		goButton = actionButton("▶ Go", panelLeft, 0, half, "Start the on-screen compass", this::go);
		xaeroButton = actionButton("Add to Xaero", panelLeft + half + 4, 0, half, "", this::addToXaero);
		copyButton = actionButton("Copy coords", panelLeft, 1, half, "Copy \"X Y Z\" to the clipboard", this::copyCoordinates);
		favoriteButton = actionButton("☆ Favourite", panelLeft + half + 4, 1, half, "Only you see your favourites",
				() -> act(Action.FAVORITE));
		int lastWidth = teleport ? third : half;
		editButton = actionButton("Edit", panelLeft, 2, lastWidth, "Rename it or change its description", this::edit);
		removeButton = actionButton("Remove", panelLeft + lastWidth + 4, 2, lastWidth, "Remove it for everyone", this::remove);
		teleportButton = actionButton("Teleport", panelLeft + 2 * (lastWidth + 4), 2, lastWidth, "Teleport there", this::teleport);
		teleportButton.visible = teleport;

		addRenderableWidget(Button.builder(Component.literal("Routes"), button -> minecraft.setScreen(new RoutesScreen(this)))
				.bounds(width - MARGIN - 80 - 4 - 80, height - 26, 80, 20)
				.tooltip(Tooltip.create(Component.literal("Routes: waypoints to visit one after another")))
				.build());
		addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
				.bounds(width - MARGIN - 80, height - 26, 80, 20).build());

		refreshList();
	}

	private Button actionButton(String label, int x, int row, int buttonWidth, String tooltip, Runnable action) {
		Button button = addRenderableWidget(Button.builder(Component.literal(label), pressed -> action.run())
				.bounds(x, buttonsTop + row * 22, buttonWidth, 20)
				.build());
		if (!tooltip.isEmpty()) {
			button.setTooltip(Tooltip.create(Component.literal(tooltip)));
		}
		return button;
	}

	private static Component filterLabel(String id) {
		if (ALL.equals(id)) {
			return Component.literal("All");
		}
		if (FAVORITES.equals(id)) {
			return Component.literal("★ Favourites");
		}
		Category category = ClientWaypoints.category(id);
		return Component.literal(category.displayName()).withStyle(category.color());
	}

	// ---------------------------------------------------------------- updates

	/** A new snapshot or action result arrived from the server. */
	private void onServerUpdate() {
		List<String> ids = new ArrayList<>(List.of(ALL, FAVORITES));
		ClientWaypoints.categories().forEach(category -> ids.add(category.id()));
		if (!ids.equals(filterIds) || ClientWaypoints.canTeleport() != builtWithTeleport) {
			// Categories or the player's rights changed: the filter values or the button row must be rebuilt.
			rebuildWidgets();
		} else {
			refreshList();
		}
	}

	private void refreshList() {
		if (list == null) {
			return;
		}
		String needle = search.trim().toLowerCase(Locale.ROOT);
		List<Waypoint> shown = ClientWaypoints.waypoints().stream()
				.filter(waypoint -> needle.isEmpty() || matches(waypoint, needle))
				.filter(waypoint -> switch (filter) {
					case ALL -> true;
					case FAVORITES -> isFavorite(waypoint);
					default -> waypoint.category().id().equals(filter);
				})
				.sorted(comparator())
				.toList();
		list.show(shown, selectedId);
		if (list.getSelected() == null && !shown.isEmpty() && selectedId == null) {
			list.setSelected(list.children().get(0));
		}
		updateButtons();
	}

	private static boolean matches(Waypoint waypoint, String needle) {
		return waypoint.name().toLowerCase(Locale.ROOT).contains(needle)
				|| waypoint.creatorName().toLowerCase(Locale.ROOT).contains(needle)
				|| waypoint.category().displayName().toLowerCase(Locale.ROOT).contains(needle)
				|| waypoint.descriptionText().map(text -> text.toLowerCase(Locale.ROOT).contains(needle)).orElse(false);
	}

	private static Comparator<Waypoint> comparator() {
		Comparator<Waypoint> byName = Comparator.comparing(Waypoint::name, String.CASE_INSENSITIVE_ORDER);
		return switch (sort) {
			case CATEGORY -> Comparator.comparingInt((Waypoint waypoint) -> waypoint.category().order()).thenComparing(byName);
			case NAME -> byName;
			case DISTANCE -> Comparator.comparingDouble(WaypointMenuScreen::distance).thenComparing(byName);
		};
	}

	/** Distance from the player, or infinity if it can't be reached from this dimension. */
	private static double distance(Waypoint waypoint) {
		var player = net.minecraft.client.Minecraft.getInstance().player;
		if (player == null) {
			return Double.MAX_VALUE;
		}
		return NavMath.project(waypoint, Dimensions.id(player.level().dimension()), player.getBlockY())
				.map(target -> target.horizontalDistance(player.getX(), player.getZ()))
				.orElse(Double.MAX_VALUE);
	}

	private void onSelect(Waypoint waypoint) {
		selectedId = waypoint == null ? null : waypoint.id();
		updateButtons();
	}

	/** Shows, hides and relabels the action buttons for the selected waypoint and the player's rights. */
	private void updateButtons() {
		Optional<Waypoint> selected = selected();
		boolean any = selected.isPresent();
		var data = selected.flatMap(waypoint -> ClientWaypoints.data(waypoint.id()));
		boolean navigating = any && selected.get().id().equals(ClientWaypoints.navigatingTo());

		goButton.active = any;
		goButton.setMessage(Component.literal(navigating ? "■ Stop" : "▶ Go"));
		xaeroButton.active = any && xaeroInstalled();
		xaeroButton.setTooltip(Tooltip.create(Component.literal(xaeroInstalled()
				? "Open Xaero's Minimap's add-waypoint screen"
				: "Needs Xaero's Minimap on your client")));
		copyButton.active = any;
		favoriteButton.active = any;
		favoriteButton.setMessage(Component.literal(data.map(d -> d.favorite()).orElse(false) ? "★ Unfavourite" : "☆ Favourite"));
		editButton.active = data.map(d -> d.canEdit()).orElse(false);
		editButton.setTooltip(Tooltip.create(Component.literal(editButton.active
				? "Rename it or change its description" : "Only its creator or an op can edit it")));
		removeButton.active = data.map(d -> d.canRemove()).orElse(false);
		removeButton.setTooltip(Tooltip.create(Component.literal(removeButton.active
				? "Remove it for everyone" : "Only its creator or an op can remove it")));
		teleportButton.active = any;
	}

	private Optional<Waypoint> selected() {
		return list == null ? Optional.empty() : list.selectedWaypoint();
	}

	private static boolean isFavorite(Waypoint waypoint) {
		return ClientWaypoints.data(waypoint.id()).map(data -> data.favorite()).orElse(false);
	}

	private static boolean xaeroInstalled() {
		return FabricLoader.getInstance().isModLoaded("xaerominimap")
				|| FabricLoader.getInstance().isModLoaded("xaerominimapfair");
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
				onClose();
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
			showLocalStatus("Copied " + waypoint.coordinates());
		});
	}

	private void teleport() {
		act(Action.TELEPORT);
		onClose();
	}

	private void edit() {
		selected().ifPresent(waypoint -> minecraft.setScreen(new EditWaypointScreen(this, waypoint)));
	}

	private void remove() {
		selected().ifPresent(waypoint -> minecraft.setScreen(new ConfirmScreen(confirmed -> {
			if (confirmed) {
				act(Action.REMOVE);
			}
			minecraft.setScreen(this);
		}, Component.literal("Remove \"" + waypoint.name() + "\"?"),
				Component.literal("This removes it for everyone on the server."))));
	}

	/**
	 * Sets the view (used by the client test): search text, filter ("all", "favorites" or a category id) and sort
	 * ("category", "name" or "distance").
	 */
	public void setViewForTest(String query, String filterId, String sortName) {
		search = query;
		filter = switch (filterId) {
			case "all" -> ALL;
			case "favorites" -> FAVORITES;
			default -> filterId;
		};
		sort = Sort.valueOf(sortName.toUpperCase(Locale.ROOT));
		rebuildWidgets();
	}

	/** Opens the routes screen (used by the client test; players click Routes). */
	public void openRoutesForTest() {
		minecraft.setScreen(new RoutesScreen(this));
	}

	/** Selects a waypoint by name (used by the client test). */
	public void selectForTest(String name) {
		list.children().stream().filter(entry -> entry.waypoint.name().equals(name)).findFirst().ifPresent(list::setSelected);
	}

	private void showLocalStatus(String message) {
		localStatus = message;
		localStatusAt = System.currentTimeMillis();
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

		if (!ClientWaypoints.hasData()) {
			graphics.drawCenteredString(font, "Loading…", MARGIN + (panelLeft - 8 - MARGIN) / 2, TOP + 20, Colors.GRAY);
		} else if (list.children().isEmpty()) {
			String empty = ClientWaypoints.waypoints().isEmpty() ? "No waypoints yet. Click + Add." : "Nothing matches.";
			graphics.drawCenteredString(font, empty, MARGIN + (panelLeft - 8 - MARGIN) / 2, TOP + 20, Colors.GRAY);
		}
		selected().ifPresentOrElse(waypoint -> renderDetails(graphics, waypoint),
				() -> graphics.drawString(font, "Select a waypoint", panelLeft + 6, TOP + 6, Colors.GRAY));
		renderStatus(graphics);
	}

	private void renderDetails(GuiGraphics graphics, Waypoint waypoint) {
		int x = panelLeft + 6;
		int maxWidth = width - MARGIN - x - 6;
		int y = TOP + 6;
		int limit = buttonsTop - 4;
		Category category = waypoint.category();

		String title = waypoint.name() + (isFavorite(waypoint) ? " ★" : "");
		graphics.drawString(font, Component.literal(title).withStyle(style -> style.withBold(true)), x, y, Colors.argb(category));
		y += 12;
		graphics.drawString(font, category.displayName() + " · " + Dimensions.shortName(waypoint.dimension()), x, y,
				Colors.argb(category));
		y += 11;
		graphics.drawString(font, waypoint.coordinates(), x, y, Colors.WHITE);
		String distance = WaypointList.distanceLabel(waypoint);
		String where = distance != null ? distance.replace(" ⟳", " via portal") : "in " + Dimensions.shortName(waypoint.dimension());
		graphics.drawString(font, where, x + maxWidth - font.width(where), y, Colors.AQUA);
		y += 11;
		// The other side at the player's own height: the waypoint's Y belongs to its own dimension.
		var player = net.minecraft.client.Minecraft.getInstance().player;
		Optional<NavMath.Target> other = player == null ? Optional.empty()
				: NavMath.portalEquivalent(waypoint, player.getBlockY());
		if (other.isPresent()) {
			String side = waypoint.dimension().equals(Dimensions.OVERWORLD) ? "Nether side: " : "Overworld side: ";
			graphics.drawString(font, side + other.get().x() + " " + other.get().y() + " " + other.get().z(), x, y, Colors.PURPLE);
			y += 11;
		}
		if (waypoint.id().equals(ClientWaypoints.navigatingTo())) {
			graphics.drawString(font, "▶ Navigating here", x, y, Colors.GREEN);
			y += 11;
		}
		// The creator line sits at the bottom of the text area; the description fills the space above it.
		int creatorY = limit - 9;
		if (waypoint.description() != null) {
			var lines = font.split(Component.literal("“" + waypoint.description() + "”"), maxWidth);
			for (int i = 0; i < lines.size() && y + 9 <= creatorY - 2; i++) {
				graphics.drawString(font, lines.get(i), x, y + 1, Colors.GRAY);
				y += 9;
			}
		}
		if (creatorY > y) {
			graphics.drawString(font, font.plainSubstrByWidth("Added by " + waypoint.creatorName() + " · "
					+ Formats.relativeAge(waypoint.created(), Instant.now()), maxWidth), x, creatorY, Colors.DARK_GRAY);
		}
	}

	private void renderStatus(GuiGraphics graphics) {
		int y = height - 20;
		if (localStatus != null && System.currentTimeMillis() - localStatusAt < 4000) {
			graphics.drawString(font, localStatus, MARGIN, y, Colors.GREEN);
			return;
		}
		Optional<ResultPayload> result = ClientWaypoints.recentResult();
		result.ifPresent(r -> graphics.drawString(font, font.plainSubstrByWidth(r.message(), width - 190), MARGIN, y,
				r.success() ? Colors.GREEN : Colors.RED));
		if (result.isEmpty()) {
			graphics.drawString(font, font.plainSubstrByWidth(ClientWaypoints.waypoints().size()
					+ " waypoints · J opens this menu", width - 190), MARGIN, y, Colors.DARK_GRAY);
		}
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
