package io.github.steelaspect.sharedwaypoints.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-only settings, {@code config/sharedwaypoints/client.json}. {@code enabled} off: the menu key and the
 * Esc-menu button do nothing and syncing into Xaero's Minimap pauses (changes are kept and written when it's
 * turned back on). Navigation particles are sent by the server and aren't affected. Alloy Hub uses this as the
 * mod's on/off switch. The other settings are shown in the settings screen and on the Hub page
 * ({@link WaypointOptions}). Missing keys keep their defaults, so 2.8 files load unchanged.
 */
public final class ClientSettings {
	/** How the waypoint list is sorted when the menu opens. */
	public enum Sort {
		CATEGORY, NAME, DISTANCE;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		static Sort byId(String id) {
			for (Sort sort : values()) {
				if (sort.id().equals(id)) {
					return sort;
				}
			}
			return CATEGORY;
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger("sharedwaypoints");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static boolean enabled = true;
	private static boolean xaeroSync = true;
	private static boolean pauseButton = true;
	private static Sort defaultSort = Sort.CATEGORY;
	private static boolean rememberView = true;
	private static boolean loaded;

	private ClientSettings() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("sharedwaypoints").resolve("client.json");
	}

	private static void ensureLoaded() {
		if (!loaded) {
			load();
		}
	}

	public static boolean enabled() {
		ensureLoaded();
		return enabled;
	}

	public static void setEnabled(boolean on) {
		ensureLoaded();
		if (enabled != on) {
			enabled = on;
			save();
		}
	}

	/** Sync into Xaero's Minimap (only matters while {@link #enabled()}). */
	public static boolean xaeroSync() {
		ensureLoaded();
		return xaeroSync;
	}

	public static void setXaeroSync(boolean on) {
		ensureLoaded();
		if (xaeroSync != on) {
			xaeroSync = on;
			save();
		}
	}

	/** The ✦ Waypoints button on the Esc menu. */
	public static boolean pauseButton() {
		ensureLoaded();
		return pauseButton;
	}

	public static void setPauseButton(boolean on) {
		ensureLoaded();
		if (pauseButton != on) {
			pauseButton = on;
			save();
		}
	}

	public static Sort defaultSort() {
		ensureLoaded();
		return defaultSort;
	}

	public static void setDefaultSort(Sort sort) {
		ensureLoaded();
		if (defaultSort != sort) {
			defaultSort = sort;
			save();
		}
	}

	/** Reopen the menu on the last search, filter, sort and selection (until the game closes). */
	public static boolean rememberView() {
		ensureLoaded();
		return rememberView;
	}

	public static void setRememberView(boolean on) {
		ensureLoaded();
		if (rememberView != on) {
			rememberView = on;
			save();
		}
	}

	private static void load() {
		loaded = true;
		Path f = file();
		if (!Files.isRegularFile(f)) {
			return;
		}
		try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
			JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
			if (o.has("enabled")) {
				enabled = o.get("enabled").getAsBoolean();
			}
			if (o.has("xaeroSync")) {
				xaeroSync = o.get("xaeroSync").getAsBoolean();
			}
			if (o.has("pauseButton")) {
				pauseButton = o.get("pauseButton").getAsBoolean();
			}
			if (o.has("defaultSort")) {
				defaultSort = Sort.byId(o.get("defaultSort").getAsString());
			}
			if (o.has("rememberView")) {
				rememberView = o.get("rememberView").getAsBoolean();
			}
		} catch (Exception e) {
			LOGGER.warn("{} is invalid, using defaults: {}", f, e.toString());
		}
	}

	private static void save() {
		Path f = file();
		JsonObject o = new JsonObject();
		o.addProperty("enabled", enabled);
		o.addProperty("xaeroSync", xaeroSync);
		o.addProperty("pauseButton", pauseButton);
		o.addProperty("defaultSort", defaultSort.id());
		o.addProperty("rememberView", rememberView);
		try {
			Files.createDirectories(f.getParent());
			Files.writeString(f, GSON.toJson(o));
		} catch (Exception e) {
			LOGGER.warn("Could not save {}: {}", f, e.toString());
		}
	}
}
