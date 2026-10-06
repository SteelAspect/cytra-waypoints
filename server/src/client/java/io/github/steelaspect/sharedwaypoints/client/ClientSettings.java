package io.github.steelaspect.sharedwaypoints.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-only settings, {@code config/sharedwaypoints/client.json}. {@code enabled} off: the menu key and the
 * Esc-menu button do nothing and syncing into Xaero's Minimap pauses (changes are kept and written when it's
 * turned back on). Navigation particles are sent by the server and aren't affected. Cytra Hub uses this as the
 * mod's on/off switch.
 */
public final class ClientSettings {
	private static final Logger LOGGER = LoggerFactory.getLogger("sharedwaypoints");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static boolean enabled = true;
	private static boolean loaded;

	private ClientSettings() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("sharedwaypoints").resolve("client.json");
	}

	public static boolean enabled() {
		if (!loaded) {
			load();
		}
		return enabled;
	}

	public static void setEnabled(boolean on) {
		enabled();
		if (enabled == on) {
			return;
		}
		enabled = on;
		save();
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
		} catch (Exception e) {
			LOGGER.warn("{} is invalid, using defaults: {}", f, e.toString());
		}
	}

	private static void save() {
		Path f = file();
		JsonObject o = new JsonObject();
		o.addProperty("enabled", enabled);
		try {
			Files.createDirectories(f.getParent());
			Files.writeString(f, GSON.toJson(o));
		} catch (Exception e) {
			LOGGER.warn("Could not save {}: {}", f, e.toString());
		}
	}
}
