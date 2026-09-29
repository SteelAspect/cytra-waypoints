package io.github.steelaspect.sharedwaypoints.config;

import io.github.steelaspect.sharedwaypoints.SharedWaypoints;
import io.github.steelaspect.sharedwaypoints.util.Gsons;
import io.github.steelaspect.sharedwaypoints.util.JsonFiles;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Settings from {@code config/sharedwaypoints/config.json}. The file is created with defaults on first start
 * and rewritten on every load, so newly added options show up in it automatically.
 */
public final class ModConfig {
	/** Tell everyone online when a waypoint is added (with the usual buttons). */
	public boolean announceNewWaypoints = true;
	/** Show a particle beacon at the destination while navigating. */
	public boolean navigationParticles = true;
	/** Navigation ends when you get this close (blocks). */
	public int arrivalRadius = 6;
	/** Waypoint lines per page in /waypoints listings. */
	public int pageSize = 8;
	/** Default radius (blocks) for /waypoints near. */
	public int nearRadius = 512;

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

	/** Keeps hand-edited values sensible. */
	private void clamp() {
		arrivalRadius = Math.clamp(arrivalRadius, 1, 64);
		pageSize = Math.clamp(pageSize, 3, 30);
		nearRadius = Math.clamp(nearRadius, 16, 30_000_000);
	}
}
