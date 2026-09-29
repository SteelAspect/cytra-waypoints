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
 * every action for the selected waypoint on the right.
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
	private Button renameButton;
	private Button describeButton;
	private Button teleportButton;
	private Button removeButton;
	private List<String> filterIds = List.of();
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

		// --- action buttons, two columns at the bottom of the details panel
		int columnWidth = (panelWidth - 4) / 2;
		buttonsTop = bottom - 4 * 22 + 2;
		goButton = actionButton("Go", 0, 0, columnWidth, "Start the on-screen compass", this::go);
		xaeroButton = actionButton("Add to Xaero", 1, 0, columnWidth, "Open Xaero's Minimap's add-waypoint screen", this::addToXaero);
		copyButton = actionButton("Copy coords", 0, 1, columnWidth, "Copy \"X Y Z\" to the clipboard", this::copyCoordinates);
		favoriteButton = actionButton("☆ Favourite", 1, 1, columnWidth, "Only you see your favourites", () -> act(Action.FAVORITE));
		renameButton = actionButton("Rename", 0, 2, columnWidth, "Rename this waypoint", this::rename);
		describeButton = actionButton("Description", 1, 2, columnWidth, "Write a short note", this::describe);
		teleportButton = actionButton("Teleport", 0, 3, columnWidth, "Teleport there (ops)", this::teleport);
		removeButton = actionButton("Remove", 1, 3, columnWidth, "Remove it for everyone", this::remove);

		addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
				.bounds(width - MARGIN - 80, height - 26, 80, 20).build());

		refreshList();
	}

	private Button actionButton(String label, int column, int row, int columnWidth, String tooltip, Runnable action) {
		return addRenderableWidget(Button.builder(Component.literal(label), button -> action.run())
				.bounds(panelLeft + column * (columnWidth + 4), buttonsTop + row * 22, columnWidth, 20)
				.tooltip(Tooltip.create(Component.literal(tooltip)))
				.build());
	}

	private static Component filterLabel(String id) {
		if (ALL.equals(id)) {
			return Component.literal("All categories");
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
		if (!ids.equals(filterIds)) {
			rebuildWidgets(); // categories changed: the filter button needs new values
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
		return NavMath.project(waypoint, Dimensions.id(player.level().dimension()))
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
		renameButton.active = data.map(d -> d.canEdit()).orElse(false);
		describeButton.active = renameButton.active;
		removeButton.active = data.map(d -> d.canRemove()).orElse(false);
		teleportButton.visible = ClientWaypoints.canTeleport();
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

	private void rename() {
		selected().ifPresent(waypoint -> minecraft.setScreen(new TextInputScreen(this, "Rename waypoint",
				waypoint.name(), Waypoint.MAX_NAME_LENGTH, value -> act(Action.RENAME, value))));
	}

	private void describe() {
		selected().ifPresent(waypoint -> minecraft.setScreen(new TextInputScreen(this, "Description of " + waypoint.name(),
				waypoint.descriptionText().orElse(""), Waypoint.MAX_DESCRIPTION_LENGTH, value -> act(Action.DESCRIBE, value))));
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
		Category category = waypoint.category();

		String title = waypoint.name() + (isFavorite(waypoint) ? " ★" : "");
		graphics.drawString(font, Component.literal(title).withStyle(style -> style.withBold(true)), x, y, Colors.argb(category));
		y += 13;
		graphics.drawString(font, category.displayName(), x, y, Colors.argb(category));
		y += 12;
		graphics.drawString(font, waypoint.coordinates() + "  ·  " + Dimensions.shortName(waypoint.dimension()), x, y, Colors.WHITE);
		y += 12;
		Optional<NavMath.Target> other = NavMath.portalEquivalent(waypoint);
		if (other.isPresent()) {
			String side = waypoint.dimension().equals(Dimensions.OVERWORLD) ? "Nether side: " : "Overworld side: ";
			graphics.drawString(font, side + other.get().x() + " " + other.get().y() + " " + other.get().z(), x, y, Colors.PURPLE);
			y += 12;
		}
		String distance = WaypointList.distanceLabel(waypoint);
		graphics.drawString(font, distance != null ? distance.replace(" ⟳", " via Nether portal") : "In " + Dimensions.shortName(waypoint.dimension()),
				x, y, Colors.AQUA);
		y += 12;
		if (waypoint.id().equals(ClientWaypoints.navigatingTo())) {
			graphics.drawString(font, "▶ Navigating here", x, y, Colors.GREEN);
			y += 12;
		}
		if (waypoint.description() != null) {
			y += 2;
			int lines = font.split(Component.literal("“" + waypoint.description() + "”"), maxWidth).size();
			int room = Math.max(0, (buttonsTop - 16 - y) / 9);
			if (room > 0) {
				graphics.drawWordWrap(font, Component.literal("“" + waypoint.description() + "”"), x, y, maxWidth, Colors.GRAY);
				y += Math.min(lines, room) * 9 + 3;
			}
		}
		if (y + 10 < buttonsTop) {
			graphics.drawString(font, "Added by " + waypoint.creatorName() + " · " + Formats.relativeAge(waypoint.created(), Instant.now()),
					x, y, Colors.DARK_GRAY);
		}
	}

	private void renderStatus(GuiGraphics graphics) {
		int y = height - 20;
		if (localStatus != null && System.currentTimeMillis() - localStatusAt < 4000) {
			graphics.drawString(font, localStatus, MARGIN, y, Colors.GREEN);
			return;
		}
		Optional<ResultPayload> result = ClientWaypoints.recentResult();
		result.ifPresent(r -> graphics.drawString(font, font.plainSubstrByWidth(r.message(), width - 110), MARGIN, y,
				r.success() ? Colors.GREEN : Colors.RED));
		if (result.isEmpty()) {
			graphics.drawString(font, ClientWaypoints.waypoints().size() + " waypoints · J opens this menu", MARGIN, y, Colors.DARK_GRAY);
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
