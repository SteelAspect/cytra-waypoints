package io.github.steelaspect.sharedwaypoints.waypoint;

import io.github.steelaspect.sharedwaypoints.SharedWaypoints;
import io.github.steelaspect.sharedwaypoints.util.Gsons;
import io.github.steelaspect.sharedwaypoints.util.JsonFiles;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Each player's favourite waypoints, stored by waypoint id in {@code config/sharedwaypoints/favorites.json}.
 * Favourites are personal: they change how the lists look for that player only.
 */
public final class FavoritesStore {
	private final Path file;
	private final Map<UUID, Set<UUID>> favorites = new HashMap<>();

	public FavoritesStore(Path file) {
		this.file = file;
	}

	public boolean isFavorite(UUID player, UUID waypoint) {
		return favorites.getOrDefault(player, Set.of()).contains(waypoint);
	}

	/** The player's favourite waypoint ids (read-only view). */
	public Set<UUID> of(UUID player) {
		return Set.copyOf(favorites.getOrDefault(player, Set.of()));
	}

	/** Flips a favourite on or off, saves, and returns the new state. */
	public boolean toggle(UUID player, UUID waypoint) {
		Set<UUID> set = favorites.computeIfAbsent(player, ignored -> new LinkedHashSet<>());
		boolean nowFavorite = set.add(waypoint);
		if (!nowFavorite) {
			set.remove(waypoint);
			if (set.isEmpty()) {
				favorites.remove(player);
			}
		}
		save();
		return nowFavorite;
	}

	/** Drops a deleted waypoint from everyone's favourites. */
	public void forget(UUID waypoint) {
		boolean changed = false;
		for (Set<UUID> set : favorites.values()) {
			changed |= set.remove(waypoint);
		}
		if (changed) {
			favorites.values().removeIf(Set::isEmpty);
			save();
		}
	}

	public void load() {
		favorites.clear();
		JsonFiles.read(file, FavoritesFile.class, Gsons.GSON).ifPresent(data -> {
			if (data.favorites != null) {
				data.favorites.forEach((player, ids) -> {
					if (player != null && ids != null && !ids.isEmpty()) {
						favorites.put(player, new LinkedHashSet<>(ids));
					}
				});
			}
		});
	}

	public void save() {
		FavoritesFile data = new FavoritesFile();
		favorites.forEach((player, ids) -> data.favorites.put(player, new ArrayList<>(ids)));
		try {
			JsonFiles.writeAtomically(file, data, Gsons.GSON);
		} catch (IOException e) {
			SharedWaypoints.LOGGER.error("Could not save favourites to {}", file, e);
		}
	}

	private static final class FavoritesFile {
		int version = 1;
		Map<UUID, List<UUID>> favorites = new LinkedHashMap<>();
	}
}
