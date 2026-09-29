package io.github.steelaspect.sharedwaypoints.util;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.github.steelaspect.sharedwaypoints.SharedWaypoints;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/** Safe JSON file reading and writing shared by all of the mod's files. */
public final class JsonFiles {
	private JsonFiles() {
	}

	/**
	 * Reads {@code file} as {@code type}.
	 *
	 * @return empty if the file doesn't exist or can't be parsed. An unparseable file is copied to
	 *         {@code <name>.broken-<millis>} first, so the next save can't silently destroy it.
	 */
	public static <T> Optional<T> read(Path file, Class<T> type, Gson gson) {
		if (!Files.exists(file)) {
			return Optional.empty();
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return Optional.ofNullable(gson.fromJson(reader, type));
		} catch (IOException | JsonParseException e) {
			Path backup = file.resolveSibling(file.getFileName() + ".broken-" + System.currentTimeMillis());
			try {
				Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException copyError) {
				e.addSuppressed(copyError);
			}
			SharedWaypoints.LOGGER.error("Could not read {}; using defaults (original kept as {})", file, backup, e);
			return Optional.empty();
		}
	}

	/** Writes to a temporary file and moves it over {@code file}, so a crash can't leave half a file. */
	public static void writeAtomically(Path file, Object data, Gson gson) throws IOException {
		Files.createDirectories(file.getParent());
		Path temp = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
			gson.toJson(data, writer);
		}
		try {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
