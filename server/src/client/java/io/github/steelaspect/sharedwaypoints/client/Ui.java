package io.github.steelaspect.sharedwaypoints.client;

import io.github.steelaspect.sharedwaypoints.nav.NavMath;
import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import io.github.steelaspect.sharedwaypoints.waypoint.Category;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Small helpers shared by the menu screens: translated text, category colours, distances. */
final class Ui {
	private Ui() {
	}

	/** {@code sharedwaypoints.<key>} from the mod's lang file. */
	static MutableComponent tr(String key, Object... args) {
		return Component.translatable("sharedwaypoints." + key, args);
	}

	/** The category's colour as opaque ARGB (only used for the small dot in front of a name). */
	static int argb(Category category) {
		Integer color = category.color().getColor();
		return 0xFF000000 | (color != null ? color : 0xFFFFFF);
	}

	/** Distance and compass direction from the player, e.g. "340m NE", or null if it can't be reached from here. */
	static String distanceLabel(Waypoint waypoint) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return null;
		}
		String here = Dimensions.id(player.level().dimension());
		return NavMath.project(waypoint, here, player.getBlockY()).map(target -> {
			double distance = target.horizontalDistance(player.getX(), player.getZ());
			String direction = NavMath.compass(player.getX(), player.getZ(), target.x() + 0.5, target.z() + 0.5);
			return NavMath.formatDistance(distance) + " " + direction + (target.viaPortal() ? " ⟳" : "");
		}).orElse(null);
	}

	/** Distance from the player, or infinity if it can't be reached from this dimension. */
	static double distance(Waypoint waypoint) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return Double.MAX_VALUE;
		}
		return NavMath.project(waypoint, Dimensions.id(player.level().dimension()), player.getBlockY())
				.map(target -> target.horizontalDistance(player.getX(), player.getZ()))
				.orElse(Double.MAX_VALUE);
	}

	/** "5 stops · 1.2km", "1 stop", "no stops yet". */
	static Component routeSummary(List<Waypoint> stops) {
		if (stops.isEmpty()) {
			return tr("route.no_stops");
		}
		Component count = tr(stops.size() == 1 ? "route.stops.one" : "route.stops", stops.size());
		return stops.size() < 2 ? count
				: tr("route.summary", count, NavMath.formatDistance(NavMath.routeLength(stops)));
	}

	static boolean xaeroInstalled() {
		return FabricLoader.getInstance().isModLoaded("xaerominimap")
				|| FabricLoader.getInstance().isModLoaded("xaerominimapfair");
	}
}
