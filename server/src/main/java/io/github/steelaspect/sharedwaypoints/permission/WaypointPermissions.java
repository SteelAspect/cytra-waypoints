package io.github.steelaspect.sharedwaypoints.permission;

import io.github.steelaspect.sharedwaypoints.waypoint.Route;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.util.UUID;
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
	/** List, search, info, navigate, favourite and get the Xaero share line. Default: everyone. */
	public static final String VIEW = "sharedwaypoints.view";
	/** Add waypoints. Default: everyone. */
	public static final String ADD = "sharedwaypoints.add";
	/** Remove any waypoint. Default: op level 2. Creators can always remove their own. */
	public static final String REMOVE = "sharedwaypoints.remove";
	/** Rename or describe any waypoint. Default: op level 2. Creators can always edit their own. */
	public static final String EDIT = "sharedwaypoints.edit";
	/**
	 * Set or clear a waypoint's project status (planned / WIP / done / broken). Default: everyone, so whoever finds a
	 * broken farm can say so. Creators can always set their own.
	 */
	public static final String STATUS = "sharedwaypoints.status";
	/** Teleport to waypoints. Default: op level 2. */
	public static final String TELEPORT = "sharedwaypoints.teleport";
	/** Create routes. Default: everyone. Editing and deleting use {@link #EDIT} and {@link #REMOVE} like waypoints. */
	public static final String ROUTE = "sharedwaypoints.route";
	/** Reload config.json, waypoints.json and favorites.json without a restart. Default: op level 2. */
	public static final String RELOAD = "sharedwaypoints.reload";

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

	public static Predicate<CommandSourceStack> requireRoute() {
		return Permissions.require(ROUTE, true);
	}

	public static Predicate<CommandSourceStack> requireTeleport() {
		return Permissions.require(TELEPORT, MODERATE_DEFAULT);
	}

	public static Predicate<CommandSourceStack> requireReload() {
		return Permissions.require(RELOAD, MODERATE_DEFAULT);
	}

	/** For announcements: may this online player see waypoints at all? */
	public static boolean canView(ServerPlayer player) {
		return Permissions.check(player, VIEW, true);
	}

	public static boolean canAdd(CommandSourceStack source) {
		return Permissions.check(source, ADD, true);
	}

	public static boolean canCreateRoute(CommandSourceStack source) {
		return Permissions.check(source, ROUTE, true);
	}

	public static boolean canTeleport(CommandSourceStack source) {
		return Permissions.check(source, TELEPORT, MODERATE_DEFAULT);
	}

	/** Whether the source may remove this waypoint (has the node, or created it). */
	public static boolean canRemove(CommandSourceStack source, Waypoint waypoint) {
		return Permissions.check(source, REMOVE, MODERATE_DEFAULT) || isCreator(source, waypoint);
	}

	/** Whether the source may rename or describe this waypoint (has the node, or created it). */
	public static boolean canEdit(CommandSourceStack source, Waypoint waypoint) {
		return Permissions.check(source, EDIT, MODERATE_DEFAULT) || isCreator(source, waypoint);
	}

	/** Whether the source may set this waypoint's status (has the node, may edit it, or created it). */
	public static boolean canSetStatus(CommandSourceStack source, Waypoint waypoint) {
		return Permissions.check(source, STATUS, true) || canEdit(source, waypoint);
	}

	/** Whether the source may delete this route (has the remove node, or created it). */
	public static boolean canRemove(CommandSourceStack source, Route route) {
		return Permissions.check(source, REMOVE, MODERATE_DEFAULT) || isCreator(source, route.creatorUuid());
	}

	/** Whether the source may change this route's stops, name or description (has the edit node, or created it). */
	public static boolean canEdit(CommandSourceStack source, Route route) {
		return Permissions.check(source, EDIT, MODERATE_DEFAULT) || isCreator(source, route.creatorUuid());
	}

	private static boolean isCreator(CommandSourceStack source, Waypoint waypoint) {
		return isCreator(source, waypoint.creatorUuid());
	}

	private static boolean isCreator(CommandSourceStack source, UUID creator) {
		ServerPlayer player = source.getPlayer();
		return player != null && player.getUUID().equals(creator);
	}
}
