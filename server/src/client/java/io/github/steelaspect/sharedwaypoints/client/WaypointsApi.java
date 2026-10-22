package io.github.steelaspect.sharedwaypoints.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * For other client mods (Alloy Map's world map). Call on the client thread; other mods call these by reflection when
 * {@code sharedwaypoints} is loaded, so neither side needs the other. Each returns false when the server doesn't run
 * Alloy Waypoints (or the player may not add), so the caller can fall back.
 *
 * <p>Stable since 2.10.0.
 */
public final class WaypointsApi {
	private WaypointsApi() {
	}

	/** True when the server runs Alloy Waypoints and this player may add shared waypoints. */
	public static boolean canAdd() {
		return ClientWaypoints.hasData() && ClientWaypoints.canAdd();
	}

	/**
	 * Opens the Add waypoint form at this spot ({@code dimension} like {@code minecraft:the_nether}). Cancel and Add
	 * go back to {@code parent}.
	 */
	public static boolean openAdd(int x, int y, int z, String dimension, @Nullable Screen parent) {
		if (!canAdd()) {
			return false;
		}
		Minecraft.getInstance().setScreen(new AddWaypointScreen(parent, new int[] {x, y, z}, dimension));
		return true;
	}

	/**
	 * Opens the waypoint menu on the shared waypoint with this name (Go, Copy coords, ★ and the ⋯ menu with Edit and
	 * Remove). Done and Esc go back to {@code parent}.
	 */
	public static boolean openWaypoint(String name, @Nullable Screen parent) {
		if (!ClientWaypoints.hasData() || ClientWaypoints.waypoints().stream().noneMatch(waypoint -> waypoint.name().equals(name))) {
			return false;
		}
		WaypointMenuScreen.openOn(name, parent);
		return true;
	}
}
