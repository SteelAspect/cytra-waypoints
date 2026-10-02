package io.github.steelaspect.sharedwaypoints.text;

import io.github.steelaspect.sharedwaypoints.nav.NavMath;
import io.github.steelaspect.sharedwaypoints.permission.WaypointPermissions;
import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import io.github.steelaspect.sharedwaypoints.waypoint.FavoritesStore;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Who is reading a chat line, so it can be personalised: distance and direction from them, their favourite stars,
 * and whether to show player-only buttons like [Go] and [Teleport].
 *
 * @param playerId    the reading player, or null for the console / command blocks
 * @param dimension   dimension id the reader is in
 * @param position    where the reader is, or null if distances make no sense (console)
 * @param favorites   the reader's favourite waypoint ids
 * @param canTeleport whether to show [Teleport]
 */
public record Viewer(UUID playerId, String dimension, Vec3 position, Set<UUID> favorites, boolean canTeleport) {

	public static Viewer of(CommandSourceStack source, FavoritesStore favorites) {
		ServerPlayer player = source.getPlayer();
		return new Viewer(
				player != null ? player.getUUID() : null,
				Dimensions.id(source.getLevel().dimension()),
				source.getEntity() != null ? source.getPosition() : null,
				player != null ? favorites.of(player.getUUID()) : Set.of(),
				player != null && WaypointPermissions.canTeleport(source));
	}

	/** For messages pushed to a player who didn't run a command (announcements). */
	public static Viewer of(ServerPlayer player, FavoritesStore favorites) {
		return of(player.createCommandSourceStack(), favorites);
	}

	public boolean isPlayer() {
		return playerId != null;
	}

	public boolean isFavorite(Waypoint waypoint) {
		return favorites.contains(waypoint.id());
	}

	/** Where to head for the waypoint from here (possibly via a Nether portal), if it's reachable. */
	public Optional<NavMath.Target> target(Waypoint waypoint) {
		return position == null ? Optional.empty() : NavMath.project(waypoint, dimension, (int) Math.floor(position.y));
	}

	/** Horizontal distance to the waypoint (or its portal spot), if the viewer has a position. */
	public Optional<Double> distance(Waypoint waypoint) {
		return target(waypoint).map(target -> target.horizontalDistance(position.x, position.z));
	}
}
