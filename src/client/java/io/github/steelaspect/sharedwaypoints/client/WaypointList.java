package io.github.steelaspect.sharedwaypoints.client;

import io.github.steelaspect.sharedwaypoints.nav.NavMath;
import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/** The scrollable waypoint list on the left of the menu: two lines per waypoint. */
final class WaypointList extends ObjectSelectionList<WaypointList.Entry> {
	static final int ITEM_HEIGHT = 24;

	private final Consumer<Waypoint> onSelect;
	private final Consumer<Waypoint> onDoubleClick;

	WaypointList(Minecraft minecraft, Consumer<Waypoint> onSelect, Consumer<Waypoint> onDoubleClick) {
		super(minecraft, 200, 100, 0, ITEM_HEIGHT);
		this.onSelect = onSelect;
		this.onDoubleClick = onDoubleClick;
	}

	/** Replaces the rows, keeping the selection if that waypoint is still listed. */
	void show(List<Waypoint> waypoints, UUID selectedId) {
		replaceEntries(waypoints.stream().map(Entry::new).toList());
		children().stream().filter(entry -> entry.waypoint.id().equals(selectedId)).findFirst().ifPresent(entry -> {
			super.setSelected(entry);
			scrollToEntry(entry);
		});
	}

	Optional<Waypoint> selectedWaypoint() {
		return Optional.ofNullable(getSelected()).map(entry -> entry.waypoint);
	}

	@Override
	public void setSelected(Entry entry) {
		super.setSelected(entry);
		onSelect.accept(entry == null ? null : entry.waypoint);
	}

	@Override
	public int getRowWidth() {
		return width - 14; // leave room for the scroll bar
	}

	/** Distance and compass direction from the player, e.g. "340m NE", or null if it can't be reached from here. */
	static String distanceLabel(Waypoint waypoint) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return null;
		}
		String here = Dimensions.id(player.level().dimension());
		return NavMath.project(waypoint, here).map(target -> {
			double distance = target.horizontalDistance(player.getX(), player.getZ());
			String direction = NavMath.compass(player.getX(), player.getZ(), target.x() + 0.5, target.z() + 0.5);
			return NavMath.formatDistance(distance) + " " + direction + (target.viaPortal() ? " ⟳" : "");
		}).orElse(null);
	}

	final class Entry extends ObjectSelectionList.Entry<Entry> {
		final Waypoint waypoint;

		Entry(Waypoint waypoint) {
			this.waypoint = waypoint;
		}

		@Override
		public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			var font = minecraft.font;
			int x = getContentX();
			int y = getContentY();
			int right = getContentRight();
			int color = 0xFF000000 | Colors.rgb(waypoint.category());

			// Category colour chip
			graphics.fill(x + 1, y + 3, x + 7, y + 9, color);

			boolean favorite = ClientWaypoints.data(waypoint.id()).map(data -> data.favorite()).orElse(false);
			boolean navigating = waypoint.id().equals(ClientWaypoints.navigatingTo());
			String title = (navigating ? "▶ " : "") + waypoint.name() + (favorite ? " ★" : "");
			graphics.drawString(font, title, x + 11, y + 2, navigating ? 0xFF55FF55 : 0xFFFFFFFF);

			String distance = distanceLabel(waypoint);
			if (distance != null) {
				graphics.drawString(font, distance, right - font.width(distance) - 2, y + 2, 0xFF55FFFF);
			}
			String details = waypoint.coordinates() + "  ·  " + Dimensions.shortName(waypoint.dimension());
			graphics.drawString(font, details, x + 11, y + 13, 0xFF9A9A9A);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			WaypointList.this.setSelected(this);
			if (doubleClick) {
				onDoubleClick.accept(waypoint);
			}
			return true;
		}

		@Override
		public Component getNarration() {
			return Component.literal(waypoint.name());
		}
	}
}
