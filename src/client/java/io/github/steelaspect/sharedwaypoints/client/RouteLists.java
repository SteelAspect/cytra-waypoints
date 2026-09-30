package io.github.steelaspect.sharedwaypoints.client;

import io.github.steelaspect.sharedwaypoints.nav.NavMath;
import io.github.steelaspect.sharedwaypoints.network.SyncPayload;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** The two lists on the routes screen: routes on the left, the selected route's stops on the right. */
final class RouteLists {
	private RouteLists() {
	}

	/** "5 stops · 1.2km", "1 stop", "no stops yet". */
	static String summary(List<Waypoint> stops) {
		if (stops.isEmpty()) {
			return "no stops yet";
		}
		String count = stops.size() + (stops.size() == 1 ? " stop" : " stops");
		return stops.size() < 2 ? count : count + " · " + NavMath.formatDistance(NavMath.routeLength(stops));
	}

	/** Routes, two lines each: name, then stop count and length. */
	static final class RouteList extends ObjectSelectionList<RouteList.Entry> {
		private final Consumer<SyncPayload.RouteData> onSelect;
		private final Consumer<SyncPayload.RouteData> onDoubleClick;

		RouteList(Minecraft minecraft, Consumer<SyncPayload.RouteData> onSelect,
				Consumer<SyncPayload.RouteData> onDoubleClick) {
			super(minecraft, 200, 100, 0, 24);
			this.onSelect = onSelect;
			this.onDoubleClick = onDoubleClick;
		}

		void show(List<SyncPayload.RouteData> routes, UUID selectedId) {
			replaceEntries(routes.stream().map(Entry::new).toList());
			children().stream().filter(entry -> entry.route.id().equals(selectedId)).findFirst().ifPresent(entry -> {
				super.setSelected(entry);
				scrollToEntry(entry);
			});
		}

		Optional<SyncPayload.RouteData> selectedRoute() {
			return Optional.ofNullable(getSelected()).map(entry -> entry.route);
		}

		@Override
		public void setSelected(Entry entry) {
			super.setSelected(entry);
			onSelect.accept(entry == null ? null : entry.route);
		}

		@Override
		public int getRowWidth() {
			return width - 14;
		}

		final class Entry extends ObjectSelectionList.Entry<Entry> {
			final SyncPayload.RouteData route;

			Entry(SyncPayload.RouteData route) {
				this.route = route;
			}

			@Override
			public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
				var font = minecraft.font;
				int x = getContentX();
				int y = getContentY();
				SyncPayload.RouteProgressData progress = ClientWaypoints.onRoute();
				boolean following = progress != null && progress.routeId().equals(route.id());
				graphics.drawString(font, (following ? "▶ " : "") + route.name(), x + 2, y + 2,
						following ? Colors.GREEN : Colors.GOLD);
				graphics.drawString(font, summary(ClientWaypoints.stops(route)) + "  ·  by " + route.creatorName(),
						x + 2, y + 13, 0xFF9A9A9A);
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				RouteList.this.setSelected(this);
				if (doubleClick) {
					onDoubleClick.accept(route);
				}
				return true;
			}

			@Override
			public Component getNarration() {
				return Component.literal(route.name());
			}
		}
	}

	/** A route's stops, one numbered line each, with the distance of each stop from the player. */
	static final class StopList extends ObjectSelectionList<StopList.Entry> {
		private final Runnable onSelect;

		StopList(Minecraft minecraft, Runnable onSelect) {
			super(minecraft, 200, 100, 0, 12);
			this.onSelect = onSelect;
		}

		/** Replaces the rows; keeps the selected stop number if the route still has it. */
		void show(List<Waypoint> stops, int selectedIndex, int currentIndex) {
			List<Entry> entries = new java.util.ArrayList<>();
			for (int i = 0; i < stops.size(); i++) {
				entries.add(new Entry(i, stops.get(i), i == currentIndex));
			}
			replaceEntries(entries);
			if (selectedIndex >= 0 && selectedIndex < entries.size()) {
				super.setSelected(entries.get(selectedIndex));
				scrollToEntry(entries.get(selectedIndex));
			}
		}

		/** 0-based index of the selected stop, or -1. */
		int selectedIndex() {
			return getSelected() == null ? -1 : getSelected().index;
		}

		@Override
		public void setSelected(Entry entry) {
			super.setSelected(entry);
			onSelect.run();
		}

		@Override
		public int getRowWidth() {
			return width - 14;
		}

		final class Entry extends ObjectSelectionList.Entry<Entry> {
			final int index;
			final Waypoint waypoint;
			final boolean current;

			Entry(int index, Waypoint waypoint, boolean current) {
				this.index = index;
				this.waypoint = waypoint;
				this.current = current;
			}

			@Override
			public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
				var font = minecraft.font;
				int x = getContentX();
				int y = getContentY();
				int right = getContentRight();
				String number = (current ? "▶" : (index + 1) + ".");
				graphics.drawString(font, number, x + 2, y + 1, current ? Colors.GREEN : Colors.GRAY);
				String distance = WaypointList.distanceLabel(waypoint);
				int nameRoom = right - x - 20 - (distance != null ? font.width(distance) + 6 : 0);
				graphics.drawString(font, font.plainSubstrByWidth(waypoint.name(), nameRoom), x + 18, y + 1,
						Colors.argb(waypoint.category()));
				if (distance != null) {
					graphics.drawString(font, distance, right - font.width(distance) - 2, y + 1, Colors.AQUA);
				}
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				StopList.this.setSelected(this);
				return true;
			}

			@Override
			public Component getNarration() {
				return Component.literal((index + 1) + ". " + waypoint.name());
			}
		}
	}
}
