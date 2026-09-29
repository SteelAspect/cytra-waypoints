package io.github.steelaspect.sharedwaypoints.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import io.github.steelaspect.sharedwaypoints.waypoint.Category;
import io.github.steelaspect.sharedwaypoints.waypoint.CategoryRegistry;
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

	/** {@link #GSON} plus categories stored by id and resolved against {@code categories} when read. */
	public static Gson withCategories(CategoryRegistry categories) {
		return GSON.newBuilder()
				.registerTypeAdapter(Category.class, new CategoryAdapter(categories).nullSafe())
				.create();
	}

	/** Writes a category as its id; reads an id back into the configured category (or a stand-in). */
	private static final class CategoryAdapter extends TypeAdapter<Category> {
		private final CategoryRegistry categories;

		CategoryAdapter(CategoryRegistry categories) {
			this.categories = categories;
		}

		@Override
		public void write(JsonWriter out, Category value) throws IOException {
			out.value(value.id());
		}

		@Override
		public Category read(JsonReader in) throws IOException {
			return categories.resolve(in.nextString());
		}
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
