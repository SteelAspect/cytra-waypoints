package io.github.steelaspect.sharedwaypoints.permission;

import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.util.function.Predicate;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

/**
 * Permission nodes, checked through fabric-permissions-api.
 *
 * <p>With a permissions provider installed (e.g. LuckPerms) the nodes decide. Without one, the API falls back to
 * the defaults below: {@code true} means everyone, a {@link PermissionLevel} means that vanilla op level.
 */
public final class WaypointPermissions {
	/** List waypoints, view info, and get the Xaero share line. Default: everyone. */
	public static final String VIEW = "sharedwaypoints.view";
	/** Add waypoints. Default: everyone. */
	public static final String ADD = "sharedwaypoints.add";
	/** Remove any waypoint. Default: op level 2. Creators can always remove their own. */
	public static final String REMOVE = "sharedwaypoints.remove";
	/** Rename any waypoint. Default: op level 2. Creators can always rename their own. */
	public static final String RENAME = "sharedwaypoints.rename";

	/** Op level 2 ("gamemasters"), the level vanilla uses for commands like /tp and /give. */
	private static final PermissionLevel MODERATE_DEFAULT = PermissionLevel.GAMEMASTERS;

	private WaypointPermissions() {
	}

	public static Predicate<CommandSourceStack> requireView() {
		return Permissions.require(VIEW, true);
	}

	public static Predicate<CommandSourceStack> requireAdd() {
		return Permissions.require(ADD, true);
	}

	/** Whether the source may remove this waypoint (has the node, or created it). */
	public static boolean canRemove(CommandSourceStack source, Waypoint waypoint) {
		return Permissions.check(source, REMOVE, MODERATE_DEFAULT) || isCreator(source, waypoint);
	}

	/** Whether the source may rename this waypoint (has the node, or created it). */
	public static boolean canRename(CommandSourceStack source, Waypoint waypoint) {
		return Permissions.check(source, RENAME, MODERATE_DEFAULT) || isCreator(source, waypoint);
	}

	private static boolean isCreator(CommandSourceStack source, Waypoint waypoint) {
		ServerPlayer player = source.getPlayer();
		return player != null && player.getUUID().equals(waypoint.creatorUuid());
	}
}
