package io.github.steelaspect.sharedwaypoints.waypoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;

/** The configured categories, in display order. */
public final class CategoryRegistry {
	/**
	 * Words that are /waypoints subcommands. A category with one of these ids could never be listed with
	 * {@code /waypoints <category>}, because Brigadier prefers the subcommand.
	 */
	public static final Set<String> RESERVED_IDS = Set.of(
			"add", "remove", "rename", "describe", "info", "categories", "search", "near", "nearest", "go", "stop",
			"tp", "favorite", "favorites", "xaero", "page", "reload", "route", "sync");

	/** What a category id may look like: it must fit in a single command word. */
	private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,24}");
	private static final int MAX_NAME_LENGTH = 24;

	/** One configured category, as written in config.json. */
	public record Definition(String id, String name, String color) {
	}

	/** The built-in categories, also the defaults written to a new config.json. */
	public static final List<Definition> DEFAULT_DEFINITIONS = List.of(
			new Definition("storage", "Storage", "aqua"),
			new Definition("farms", "Farms", "green"),
			new Definition("bases", "Bases", "gold"),
			new Definition("portals", "Portals", "light_purple"),
			new Definition("other", "Other", "white"));

	public static final CategoryRegistry DEFAULT = fromDefinitions(DEFAULT_DEFINITIONS, warning -> {
	});

	private final Map<String, Category> byId;

	private CategoryRegistry(List<Category> categories) {
		Map<String, Category> map = new LinkedHashMap<>();
		categories.forEach(category -> map.put(category.id(), category));
		this.byId = map;
	}

	/**
	 * Builds the registry from config entries. Invalid entries are skipped with a warning; if nothing valid is
	 * left, the defaults are used.
	 */
	public static CategoryRegistry fromDefinitions(List<Definition> definitions, Consumer<String> warn) {
		List<Category> categories = new ArrayList<>();
		for (Definition definition : definitions == null ? List.<Definition>of() : definitions) {
			Optional<String> problem = problem(definition, categories);
			if (problem.isPresent()) {
				warn.accept("Ignoring category " + definition + ": " + problem.get());
				continue;
			}
			categories.add(new Category(definition.id().toLowerCase(Locale.ROOT), definition.name().trim(),
					ChatFormatting.getByName(definition.color().toLowerCase(Locale.ROOT)), categories.size()));
		}
		if (categories.isEmpty()) {
			if (definitions == DEFAULT_DEFINITIONS) {
				throw new IllegalStateException("The built-in categories are invalid");
			}
			warn.accept("No valid categories configured; using the defaults");
			return fromDefinitions(DEFAULT_DEFINITIONS, warn);
		}
		return new CategoryRegistry(categories);
	}

	private static Optional<String> problem(Definition definition, List<Category> accepted) {
		if (definition == null || definition.id() == null || definition.name() == null || definition.color() == null) {
			return Optional.of("id, name and color are all required");
		}
		String id = definition.id().toLowerCase(Locale.ROOT);
		if (!ID_PATTERN.matcher(id).matches()) {
			return Optional.of("id must be 1-24 characters of a-z, 0-9, _ or -");
		}
		if (RESERVED_IDS.contains(id)) {
			return Optional.of("\"" + id + "\" is a /waypoints subcommand");
		}
		if (accepted.stream().anyMatch(category -> category.id().equals(id))) {
			return Optional.of("duplicate id");
		}
		String name = definition.name().trim();
		if (name.isEmpty() || name.length() > MAX_NAME_LENGTH || name.chars().anyMatch(c -> c == '§' || Character.isISOControl(c))) {
			return Optional.of("name must be 1-" + MAX_NAME_LENGTH + " plain characters");
		}
		ChatFormatting color = ChatFormatting.getByName(definition.color().toLowerCase(Locale.ROOT));
		if (color == null || !color.isColor()) {
			return Optional.of("color must be one of " + String.join(", ", colorNames()));
		}
		return Optional.empty();
	}

	/** The 16 chat colour names accepted in config.json. */
	public static List<String> colorNames() {
		List<String> names = new ArrayList<>();
		for (ChatFormatting formatting : ChatFormatting.values()) {
			if (formatting.isColor()) {
				names.add(formatting.getName());
			}
		}
		return names;
	}

	/** Configured categories in display order. */
	public List<Category> all() {
		return List.copyOf(byId.values());
	}

	public List<String> ids() {
		return List.copyOf(byId.keySet());
	}

	/** A configured category, case-insensitive. */
	public Optional<Category> byId(String id) {
		return Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
	}

	/** The configured category, or a gray stand-in that keeps the id (see {@link Category#unknown}). */
	public Category resolve(String id) {
		if (id == null || id.isBlank()) {
			return byId.getOrDefault("other", Category.unknown("other"));
		}
		return byId(id).orElseGet(() -> Category.unknown(id.toLowerCase(Locale.ROOT)));
	}
}
