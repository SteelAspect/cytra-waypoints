package io.github.steelaspect.sharedwaypoints.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/** The Gson setup used for every file the mod writes. */
public final class Gsons {
	public static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.registerTypeAdapter(Instant.class, new InstantAdapter().nullSafe())
			.create();

	private Gsons() {
	}

	/** Stores {@link Instant} as an ISO-8601 string; also accepts epoch milliseconds when reading. */
	private static final class InstantAdapter extends TypeAdapter<Instant> {
		@Override
		public void write(JsonWriter out, Instant value) throws IOException {
			out.value(value.toString());
		}

		@Override
		public Instant read(JsonReader in) throws IOException {
			if (in.peek() == JsonToken.NUMBER) {
				return Instant.ofEpochMilli(in.nextLong());
			}
			String text = in.nextString();
			try {
				return Instant.parse(text);
			} catch (DateTimeParseException e) {
				throw new JsonParseException("Invalid timestamp: " + text, e);
			}
		}
	}
}
