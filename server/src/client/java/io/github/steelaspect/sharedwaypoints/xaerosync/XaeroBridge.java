package io.github.steelaspect.sharedwaypoints.xaerosync;

import io.github.steelaspect.sharedwaypoints.protocol.SyncedWaypoint;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Everything that touches Xaero's Minimap, in one place. Xaero has no public API for adding waypoints, so this uses
 * Xaero's own classes through reflection. Checked against Xaero's Minimap 26.5.0 for Fabric 1.21.11; when Xaero
 * changes, this is the only class to update. See docs/DEVELOPMENT.md ("Xaero's Minimap auto-sync") for how each
 * step maps to Xaero's code.
 *
 * <p>Every method and class is looked up once, up front ({@link #create()}). If anything is missing, or a call fails
 * later, one warning is logged and the bridge switches itself off: the game never crashes because of it.
 *
 * <p>The mod owns the waypoint set called {@value #SET_NAME}. It rewrites that set and never reads or writes any
 * other set, so players' own waypoints are never touched.
 */
final class XaeroBridge {
	static final Logger LOGGER = LoggerFactory.getLogger("Cytra Waypoints Client");
	/** The waypoint set the mod manages, in every dimension of the current server. */
	static final String SET_NAME = "Shared";

	// Xaero, resolved once
	private final Object minimapModule;
	private final Method getCurrentSession;
	private final Method getWorldManager;
	private final Method getWorldManagerIO;
	private final Method getWorldState;
	private final Method getWorldStateUpdater;
	private final Method getDimensionHelper;
	private final Method getAutoWorldPath;
	private final Method getAutoRootContainerPath;
	private final Method getDimensionDirectoryName;
	private final Method resolvePath;
	private final Method getAutoRootContainer;
	private final Method renameOldContainer;
	private final Method getWorldContainer;
	private final Method getAutoWorld;
	private final Method worldGetContainer;
	private final Method getFirstWorldConnectedTo;
	private final Method getFirstWorld;
	private final Method containerAddWorld;
	private final Method getPotentialWorldNode;
	private final Method getWaypointSet;
	private final Method addWaypointSet;
	private final Method builderBegin;
	private final Method builderSetName;
	private final Method builderBuild;
	private final Method setGetWaypoints;
	private final Method setClear;
	private final Method setAdd;
	private final Method waypointGetName;
	private final Method waypointIsDisabled;
	private final Method waypointSetDisabled;
	private final java.lang.reflect.Constructor<?> newWaypoint;
	private final Method colorFromIndex;
	private final int colorCount;
	private final Object purposeNormal;
	private final Method saveWorld;

	private boolean broken;

	private XaeroBridge() throws ReflectiveOperationException {
		ClassLoader loader = XaeroBridge.class.getClassLoader();
		Class<?> modules = Class.forName("xaero.hud.minimap.BuiltInHudModules", false, loader);
		Class<?> hudModule = Class.forName("xaero.hud.module.HudModule", false, loader);
		Class<?> session = Class.forName("xaero.hud.minimap.module.MinimapSession", false, loader);
		Class<?> worldManager = Class.forName("xaero.hud.minimap.world.MinimapWorldManager", false, loader);
		Class<?> worldManagerIO = Class.forName("xaero.hud.minimap.world.io.MinimapWorldManagerIO", false, loader);
		Class<?> worldState = Class.forName("xaero.hud.minimap.world.state.MinimapWorldState", false, loader);
		Class<?> worldStateUpdater = Class.forName("xaero.hud.minimap.world.state.MinimapWorldStateUpdater", false, loader);
		Class<?> dimensionHelper = Class.forName("xaero.hud.minimap.world.MinimapDimensionHelper", false, loader);
		Class<?> path = Class.forName("xaero.hud.path.XaeroPath", false, loader);
		Class<?> rootContainer = Class.forName("xaero.hud.minimap.world.container.MinimapWorldRootContainer", false, loader);
		Class<?> container = Class.forName("xaero.hud.minimap.world.container.MinimapWorldContainer", false, loader);
		Class<?> world = Class.forName("xaero.hud.minimap.world.MinimapWorld", false, loader);
		Class<?> set = Class.forName("xaero.hud.minimap.waypoint.set.WaypointSet", false, loader);
		Class<?> builder = Class.forName("xaero.hud.minimap.waypoint.set.WaypointSet$Builder", false, loader);
		Class<?> waypoint = Class.forName("xaero.common.minimap.waypoints.Waypoint", false, loader);
		Class<?> color = Class.forName("xaero.hud.minimap.waypoint.WaypointColor", false, loader);
		Class<?> purpose = Class.forName("xaero.hud.minimap.waypoint.WaypointPurpose", false, loader);

		Field minimapField = modules.getField("MINIMAP");
		minimapModule = minimapField.get(null);
		getCurrentSession = hudModule.getMethod("getCurrentSession");
		getWorldManager = session.getMethod("getWorldManager");
		getWorldManagerIO = session.getMethod("getWorldManagerIO");
		getWorldState = session.getMethod("getWorldState");
		getWorldStateUpdater = session.getMethod("getWorldStateUpdater");
		getDimensionHelper = session.getMethod("getDimensionHelper");
		getAutoWorldPath = worldState.getMethod("getAutoWorldPath");
		getAutoRootContainerPath = worldState.getMethod("getAutoRootContainerPath");
		getDimensionDirectoryName = dimensionHelper.getMethod("getDimensionDirectoryName", ResourceKey.class);
		resolvePath = path.getMethod("resolve", String.class);
		getAutoRootContainer = worldManager.getMethod("getAutoRootContainer");
		renameOldContainer = rootContainer.getMethod("renameOldContainer", path);
		getWorldContainer = worldManager.getMethod("getWorldContainer", path);
		getAutoWorld = worldManager.getMethod("getAutoWorld");
		worldGetContainer = world.getMethod("getContainer");
		getFirstWorldConnectedTo = container.getMethod("getFirstWorldConnectedTo", world);
		getFirstWorld = container.getMethod("getFirstWorld");
		containerAddWorld = container.getMethod("addWorld", String.class);
		getPotentialWorldNode = worldStateUpdater.getMethod("getPotentialWorldNode", ResourceKey.class, boolean.class);
		getWaypointSet = world.getMethod("getWaypointSet", String.class);
		addWaypointSet = world.getMethod("addWaypointSet", set);
		builderBegin = builder.getMethod("begin");
		builderSetName = builder.getMethod("setName", String.class);
		builderBuild = builder.getMethod("build");
		setGetWaypoints = set.getMethod("getWaypoints");
		setClear = set.getMethod("clear");
		setAdd = set.getMethod("add", waypoint);
		waypointGetName = waypoint.getMethod("getName");
		waypointIsDisabled = waypoint.getMethod("isDisabled");
		waypointSetDisabled = waypoint.getMethod("setDisabled", boolean.class);
		newWaypoint = waypoint.getConstructor(int.class, int.class, int.class, String.class, String.class, color,
				purpose, boolean.class, boolean.class);
		colorFromIndex = color.getMethod("fromIndex", int.class);
		colorCount = ((Object[]) color.getMethod("values").invoke(null)).length;
		purposeNormal = purpose.getField("NORMAL").get(null);
		saveWorld = worldManagerIO.getMethod("saveWorld", world);
	}

	/**
	 * The bridge, or empty (after one log line) when Xaero's Minimap isn't installed or doesn't have what's needed.
	 */
	static Optional<XaeroBridge> create() {
		FabricLoader loader = FabricLoader.getInstance();
		if (!loader.isModLoaded("xaerominimap") && !loader.isModLoaded("xaerominimapfair")) {
			LOGGER.warn("Xaero's Minimap is not installed, so shared waypoints won't be synced into it. "
					+ "The waypoint menu and the server's chat buttons still work.");
			return Optional.empty();
		}
		try {
			return Optional.of(new XaeroBridge());
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			LOGGER.warn("This version of Xaero's Minimap ({}) isn't supported by Cytra Waypoints' Xaero sync (tested with 26.5.0), "
					+ "so waypoint sync is off: {}", xaeroVersion(), e.toString());
			return Optional.empty();
		}
	}

	static String xaeroVersion() {
		return FabricLoader.getInstance().getModContainer("xaerominimap")
				.or(() -> FabricLoader.getInstance().getModContainer("xaerominimapfair"))
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
	}

	/** False after a failed call; the bridge then does nothing for the rest of the game. */
	boolean isWorking() {
		return !broken;
	}

	/** Whether Xaero has a session for the current server and knows which world it's in (it can take a moment). */
	boolean isReady() {
		if (broken) {
			return false;
		}
		try {
			Object session = getCurrentSession.invoke(minimapModule);
			if (session == null) {
				return false;
			}
			Object state = getWorldState.invoke(session);
			return getAutoWorldPath.invoke(state) != null && getAutoRootContainerPath.invoke(state) != null;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			fail("checking Xaero's session", e);
			return false;
		}
	}

	/**
	 * Makes the "Shared" set of one dimension (of the current server) contain exactly {@code waypoints}, and saves
	 * it. The set is only created when there's something to put in it. Returns false if it couldn't be written.
	 */
	boolean replaceSharedSet(String dimension, List<SyncedWaypoint> waypoints) {
		if (broken) {
			return false;
		}
		try {
			Object session = getCurrentSession.invoke(minimapModule);
			if (session == null) {
				return false;
			}
			Object world = worldFor(session, dimensionKey(dimension));
			Object set = getWaypointSet.invoke(world, SET_NAME);
			if (set == null) {
				if (waypoints.isEmpty()) {
					return true; // nothing to remove, don't create an empty set
				}
				Object builder = builderSetName.invoke(builderBegin.invoke(null), SET_NAME);
				set = builderBuild.invoke(builder);
				// Returns the set previously stored under that name (Map.put), so keep our own reference.
				addWaypointSet.invoke(world, set);
			}

			// Keep what the player chose in Xaero (hiding a shared waypoint), matched by name.
			Map<String, Boolean> disabled = new HashMap<>();
			for (Object existing : (Iterable<?>) setGetWaypoints.invoke(set)) {
				disabled.put((String) waypointGetName.invoke(existing), (Boolean) waypointIsDisabled.invoke(existing));
			}
			List<Object> fresh = new ArrayList<>(waypoints.size());
			for (SyncedWaypoint shared : waypoints) {
				Object color = colorFromIndex.invoke(null, Math.floorMod(shared.colorIndex(), colorCount));
				Object waypoint = newWaypoint.newInstance(shared.x(), shared.y(), shared.z(), shared.name(),
						initials(shared), color, purposeNormal, false, true);
				if (Boolean.TRUE.equals(disabled.get(shared.name()))) {
					waypointSetDisabled.invoke(waypoint, true);
				}
				fresh.add(waypoint);
			}
			setClear.invoke(set);
			for (Object waypoint : fresh) {
				setAdd.invoke(set, waypoint);
			}
			saveWorld.invoke(getWorldManagerIO.invoke(session), world);
			return true;
		} catch (InvocationTargetException e) {
			fail("updating the \"" + SET_NAME + "\" waypoints for " + dimension, e.getCause() != null ? e.getCause() : e);
			return false;
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			fail("updating the \"" + SET_NAME + "\" waypoints for " + dimension, e);
			return false;
		}
	}

	/** For tests and checks: the names in a dimension's "Shared" set, or empty if there's no such set. */
	Optional<List<String>> sharedNames(String dimension) {
		try {
			Object session = getCurrentSession.invoke(minimapModule);
			Object set = session == null ? null : getWaypointSet.invoke(worldFor(session, dimensionKey(dimension)), SET_NAME);
			if (set == null) {
				return Optional.empty();
			}
			List<String> names = new ArrayList<>();
			for (Object waypoint : (Iterable<?>) setGetWaypoints.invoke(set)) {
				names.add((String) waypointGetName.invoke(waypoint));
			}
			return Optional.of(names);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return Optional.empty();
		}
	}

	/**
	 * Xaero's world (waypoint file) for a dimension of the current server. The same lookup Xaero itself uses when a
	 * shared waypoint is added (WaypointSharingHandler.getReceivedDestinationWorld in 26.5.0).
	 */
	private Object worldFor(Object session, ResourceKey<Level> dimension) throws ReflectiveOperationException {
		Object worldManager = getWorldManager.invoke(session);
		Object state = getWorldState.invoke(session);
		String directory = (String) getDimensionDirectoryName.invoke(getDimensionHelper.invoke(session), dimension);
		Object containerPath = resolvePath.invoke(getAutoRootContainerPath.invoke(state), directory);
		renameOldContainer.invoke(getAutoRootContainer.invoke(worldManager), containerPath);
		Object container = getWorldContainer.invoke(worldManager, containerPath);
		Object autoWorld = getAutoWorld.invoke(worldManager);
		if (autoWorld != null && worldGetContainer.invoke(autoWorld) == container) {
			return autoWorld;
		}
		Object world = autoWorld == null ? null : getFirstWorldConnectedTo.invoke(container, autoWorld);
		if (world == null) {
			world = getFirstWorld.invoke(container);
		}
		if (world == null) {
			String node = (String) getPotentialWorldNode.invoke(getWorldStateUpdater.invoke(session), dimension, false);
			world = containerAddWorld.invoke(container, node);
		}
		return world;
	}

	private static ResourceKey<Level> dimensionKey(String dimension) {
		return ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension));
	}

	/** Xaero wants 1-3 characters; the server sends the same initials as its chat share line. */
	static String initials(SyncedWaypoint waypoint) {
		String initials = waypoint.initials() == null ? "" : waypoint.initials().strip();
		if (initials.isEmpty()) {
			initials = waypoint.name().isEmpty() ? "?" : waypoint.name().substring(0, 1);
		}
		return initials.length() > 3 ? initials.substring(0, 3) : initials;
	}

	private void fail(String what, Throwable e) {
		if (!broken) {
			broken = true;
			LOGGER.warn("Stopped syncing shared waypoints into Xaero's Minimap ({}) after an error while {}. "
					+ "This version may not be supported (tested with 26.5.0). The server's chat buttons still work.",
					xaeroVersion(), what, e);
		}
	}
}
