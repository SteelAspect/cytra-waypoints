package io.github.steelaspect.sharedwaypoints.config;

import io.github.steelaspect.sharedwaypoints.SharedWaypoints;
import io.github.steelaspect.sharedwaypoints.util.Gsons;
import io.github.steelaspect.sharedwaypoints.util.JsonFiles;
import io.github.steelaspect.sharedwaypoints.waypoint.CategoryRegistry;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings from {@code config/sharedwaypoints/config.json}. The file is created with defaults on first start
 * and rewritten on every load, so newly added options show up in it automatically. {@code /cway reload}
 * re-reads it without a restart.
 */
public final class ModConfig {
	/** Live broadcast: tell everyone online when a waypoint is added (with the usual buttons). */
	public boolean announceNewWaypoints = true;
	/** On join, tell players which waypoints were added since they last played (with the usual buttons). */
	public boolean joinSummary = true;
	/**
	 * Keep players who have the optional sharedwaypoints-client mod in sync: their Xaero's Minimap gets the shared
	 * waypoints in its own "Shared" set. Players without the client mod are never affected.
	 */
	public boolean syncToClientMod = true;
	/**
	 * On their first join, tell players who don't have sharedwaypoints-client how to get the Xaero sync
	 * (once per player). {@code /cway sync} shows the steps any time.
	 */
	public boolean clientModTip = true;
	/** Where [Download] in that tip and in {@code /cway sync} points. Empty: no link ("ask an admin"). */
	public String clientModUrl = "https://github.com/SteelAspect/sharedwaypoints/releases/latest";
	/** Show a particle beacon at the destination while navigating. */
	public boolean navigationParticles = true;
	/** Navigation ends when you get this close (blocks). */
	public int arrivalRadius = 6;
	/** Waypoint lines per page in /cway listings. */
	public int pageSize = 8;
	/** Default radius (blocks) for /cway near. */
	public int nearRadius = 512;
	/** Show waypoints on BlueMap / squaremap when one of them is installed. */
	public boolean webMapMarkers = true;
	/** Name of the marker layer on the web map. */
	public String webMapLayerName = "Shared Waypoints";
	/** Categories in display order: id (a single lower-case word), name, and one of the 16 chat colours. */
	public List<CategoryRegistry.Definition> categories = new ArrayList<>(CategoryRegistry.DEFAULT_DEFINITIONS);

	public static ModConfig load(Path file) {
		ModConfig config = JsonFiles.read(file, ModConfig.class, Gsons.GSON).orElseGet(ModConfig::new);
		config.clamp();
		try {
			JsonFiles.writeAtomically(file, config, Gsons.GSON);
		} catch (IOException e) {
			SharedWaypoints.LOGGER.warn("Could not write {}", file, e);
		}
		return config;
	}

	/** The categories as a registry; invalid entries are logged and skipped. */
	public CategoryRegistry categoryRegistry() {
		return CategoryRegistry.fromDefinitions(categories, SharedWaypoints.LOGGER::warn);
	}

	/** Keeps hand-edited values sensible. */
	private void clamp() {
		arrivalRadius = Math.clamp(arrivalRadius, 1, 64);
		pageSize = Math.clamp(pageSize, 3, 30);
		nearRadius = Math.clamp(nearRadius, 16, 30_000_000);
		if (clientModUrl == null) {
			clientModUrl = "";
		}
		if (webMapLayerName == null || webMapLayerName.isBlank()) {
			webMapLayerName = "Shared Waypoints";
		}
		if (categories == null || categories.isEmpty()) {
			categories = new ArrayList<>(CategoryRegistry.DEFAULT_DEFINITIONS);
		}
	}
}
