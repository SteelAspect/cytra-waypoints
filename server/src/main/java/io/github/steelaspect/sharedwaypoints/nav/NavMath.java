package io.github.steelaspect.sharedwaypoints.nav;

import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Pure navigation maths (no game state), so it can be unit tested: projecting waypoints between the Overworld
 * and the Nether, distances, and the arrow shown in the boss-bar compass.
 */
public final class NavMath {
	/** Blocks in the Overworld per block in the Nether. */
	public static final int NETHER_SCALE = 8;

	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	private NavMath() {
	}

	/**
	 * Where to head for a waypoint when you are in {@code viewerDimension}.
	 *
	 * @param x         target block X in the viewer's dimension
	 * @param y         target block Y
	 * @param z         target block Z
	 * @param viaPortal true when the waypoint is in the other one of Overworld / Nether and this is the matching
	 *                  portal spot (coordinates scaled by 8)
	 */
	public record Target(int x, int y, int z, boolean viaPortal) {
		/** Horizontal distance from a position to the centre of the target block. */
		public double horizontalDistance(double fromX, double fromZ) {
			double dx = x + 0.5 - fromX;
			double dz = z + 0.5 - fromZ;
			return Math.sqrt(dx * dx + dz * dz);
		}
	}

	/** The target in {@code viewerDimension}, or empty if the waypoint can't be reached from there. */
	public static Optional<Target> project(Waypoint waypoint, String viewerDimension) {
		String dimension = waypoint.dimension();
		if (dimension.equals(viewerDimension)) {
			return Optional.of(new Target(waypoint.x(), waypoint.y(), waypoint.z(), false));
		}
		if (dimension.equals(Dimensions.OVERWORLD) && viewerDimension.equals(Dimensions.NETHER)) {
			return Optional.of(new Target(Math.floorDiv(waypoint.x(), NETHER_SCALE), waypoint.y(),
					Math.floorDiv(waypoint.z(), NETHER_SCALE), true));
		}
		if (dimension.equals(Dimensions.NETHER) && viewerDimension.equals(Dimensions.OVERWORLD)) {
			return Optional.of(new Target(waypoint.x() * NETHER_SCALE, waypoint.y(), waypoint.z() * NETHER_SCALE, true));
		}
		return Optional.empty();
	}

	/**
	 * Coordinates of the matching spot on the other side of a Nether portal: Overworld / 8 or Nether * 8.
	 * Empty for any other dimension.
	 */
	public static Optional<Target> portalEquivalent(Waypoint waypoint) {
		if (waypoint.dimension().equals(Dimensions.OVERWORLD)) {
			return project(waypoint, Dimensions.NETHER);
		}
		if (waypoint.dimension().equals(Dimensions.NETHER)) {
			return project(waypoint, Dimensions.OVERWORLD);
		}
		return Optional.empty();
	}

	/**
	 * Arrow pointing from a player (facing {@code yaw}) towards the target, relative to where they look.
	 * Minecraft yaw: 0 = south (+Z), 90 = west (-X), and it increases clockwise seen from above.
	 */
	public static String arrow(float yaw, double fromX, double fromZ, double toX, double toZ) {
		double targetYaw = Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ));
		double relative = wrapDegrees(targetYaw - yaw);
		int sector = (int) Math.round(relative / 45.0);
		return ARROWS[Math.floorMod(sector, ARROWS.length)];
	}

	/** Compass direction (N, NE, ...) from one point to another, for chat where the viewer's facing is unknown. */
	public static String compass(double fromX, double fromZ, double toX, double toZ) {
		String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
		double yaw = Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ));
		return names[Math.floorMod((int) Math.round(yaw / 45.0), names.length)];
	}

	/**
	 * Horizontal length of a route: the sum of each leg, measured in the dimension the leg starts in (a leg into
	 * the Nether counts to the portal spot). Legs that can't be measured, e.g. into the End, count as 0.
	 */
	public static double routeLength(List<Waypoint> stops) {
		double total = 0;
		for (int i = 0; i + 1 < stops.size(); i++) {
			Waypoint from = stops.get(i);
			total += project(stops.get(i + 1), from.dimension())
					.map(target -> target.horizontalDistance(from.x() + 0.5, from.z() + 0.5))
					.orElse(0.0);
		}
		return total;
	}

	/** "85m", "1.2km". */
	public static String formatDistance(double blocks) {
		if (blocks < 1000) {
			return Math.round(blocks) + "m";
		}
		return String.format(Locale.ROOT, "%.1fkm", blocks / 1000.0);
	}

	/** Wraps an angle to [-180, 180). */
	static double wrapDegrees(double degrees) {
		double wrapped = degrees % 360.0;
		if (wrapped >= 180.0) {
			wrapped -= 360.0;
		}
		if (wrapped < -180.0) {
			wrapped += 360.0;
		}
		return wrapped;
	}
}
