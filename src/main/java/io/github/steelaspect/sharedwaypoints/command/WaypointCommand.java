package io.github.steelaspect.sharedwaypoints.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.steelaspect.sharedwaypoints.ModContext;
import io.github.steelaspect.sharedwaypoints.permission.WaypointPermissions;
import io.github.steelaspect.sharedwaypoints.text.Viewer;
import io.github.steelaspect.sharedwaypoints.text.WaypointText;
import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import io.github.steelaspect.sharedwaypoints.util.Page;
import io.github.steelaspect.sharedwaypoints.waypoint.Category;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import io.github.steelaspect.sharedwaypoints.xaero.XaeroShareFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The {@code /waypoints} command tree.
 *
 * <pre>
 * Browsing                  /waypoints [page &lt;n&gt;] | &lt;category&gt; [page] | categories | search &lt;text&gt; | info &lt;name&gt;
 * Around you                /waypoints near [radius] | nearest [category]
 * Navigation                /waypoints go &lt;name&gt; | stop | tp &lt;name&gt; (op)
 * Personal                  /waypoints favorite &lt;name&gt; | favorites
 * Editing                   /waypoints add &lt;name&gt; &lt;category&gt; [x y z] [dimension] | remove | rename | describe
 * Xaero                     /waypoints xaero &lt;name&gt;   (what [Add to Xaero] runs)
 * </pre>
 *
 * <p>Only vanilla argument types are used (string, word, greedy string, integer, block_pos, dimension) so
 * unmodded clients can parse and tab-complete the tree.
 */
public final class WaypointCommand {
	private static final int MAX_NEAR_RESULTS = 15;
	private static final int MAX_SEARCH_RESULTS = 20;

	private static final DynamicCommandExceptionType UNKNOWN_WAYPOINT = new DynamicCommandExceptionType(
			name -> Component.literal("No waypoint named \"" + name + "\""));
	private static final DynamicCommandExceptionType UNKNOWN_CATEGORY = new DynamicCommandExceptionType(
			name -> Component.literal("Unknown category \"" + name + "\". Use one of: "
					+ String.join(", ", Category.ids())));
	private static final DynamicCommandExceptionType NAME_TAKEN = new DynamicCommandExceptionType(
			name -> Component.literal("A waypoint named \"" + name + "\" already exists"));
	private static final DynamicCommandExceptionType INVALID_TEXT = new DynamicCommandExceptionType(
			reason -> Component.literal(String.valueOf(reason)));
	private static final SimpleCommandExceptionType CANNOT_REMOVE = new SimpleCommandExceptionType(
			Component.literal("You can only remove waypoints you created"));
	private static final SimpleCommandExceptionType CANNOT_EDIT = new SimpleCommandExceptionType(
			Component.literal("You can only edit waypoints you created"));
	private static final SimpleCommandExceptionType NEEDS_POSITION = new SimpleCommandExceptionType(
			Component.literal("Give coordinates (x y z) when not running this as a player"));
	private static final SimpleCommandExceptionType NOTHING_REACHABLE = new SimpleCommandExceptionType(
			Component.literal("No waypoints in this dimension (or through a Nether portal from here)"));
	private static final DynamicCommandExceptionType DIMENSION_MISSING = new DynamicCommandExceptionType(
			dimension -> Component.literal("Dimension " + dimension + " isn't loaded on this server"));

	private static final SuggestionProvider<CommandSourceStack> CATEGORY_SUGGESTIONS =
			(context, builder) -> SharedSuggestionProvider.suggest(Category.ids(), builder);

	private final ModContext mod;

	private WaypointCommand(ModContext mod) {
		this.mod = mod;
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ModContext mod) {
		WaypointCommand command = new WaypointCommand(mod);
		BiPredicate<CommandSourceStack, Waypoint> anyWaypoint = (source, waypoint) -> true;

		dispatcher.register(Commands.literal("waypoints")
				.requires(WaypointPermissions.requireView())
				.executes(context -> command.listAll(context.getSource(), 1))

				// ---- browsing
				.then(Commands.literal("page")
						.then(Commands.argument("page", IntegerArgumentType.integer(1))
								.executes(context -> command.listAll(context.getSource(), page(context)))))
				.then(Commands.literal("categories")
						.executes(context -> command.listCategories(context.getSource())))
				.then(Commands.literal("search")
						.then(Commands.argument("query", StringArgumentType.greedyString())
								.executes(context -> command.search(context.getSource(), string(context, "query")))))
				.then(Commands.literal("info")
						.then(command.nameArgument("name", anyWaypoint)
								.executes(context -> command.info(context.getSource(), string(context, "name")))))

				// ---- around you
				.then(Commands.literal("near")
						.executes(context -> command.near(context.getSource(), mod.config().nearRadius))
						.then(Commands.argument("radius", IntegerArgumentType.integer(1, 30_000_000))
								.executes(context -> command.near(context.getSource(),
										IntegerArgumentType.getInteger(context, "radius")))))
				.then(Commands.literal("nearest")
						.executes(context -> command.nearest(context.getSource(), null))
						.then(Commands.argument("category", StringArgumentType.word())
								.suggests(CATEGORY_SUGGESTIONS)
								.executes(context -> command.nearest(context.getSource(), category(context)))))

				// ---- navigation
				.then(Commands.literal("go")
						.then(command.nameArgument("name", anyWaypoint)
								.executes(context -> command.go(context.getSource(), string(context, "name")))))
				.then(Commands.literal("stop")
						.executes(context -> command.stop(context.getSource())))
				.then(Commands.literal("tp")
						.requires(WaypointPermissions.requireTeleport())
						.then(command.nameArgument("name", anyWaypoint)
								.executes(context -> command.teleport(context.getSource(), string(context, "name")))))

				// ---- personal
				.then(Commands.literal("favorite")
						.then(command.nameArgument("name", anyWaypoint)
								.executes(context -> command.toggleFavorite(context.getSource(), string(context, "name")))))
				.then(Commands.literal("favorites")
						.executes(context -> command.listFavorites(context.getSource())))

				// ---- xaero
				.then(Commands.literal("xaero")
						.then(command.nameArgument("name", anyWaypoint)
								.executes(context -> command.shareToXaero(context.getSource(), string(context, "name")))))

				// ---- editing
				.then(Commands.literal("add")
						.requires(WaypointPermissions.requireAdd())
						.then(Commands.argument("name", StringArgumentType.string())
								.then(Commands.argument("category", StringArgumentType.word())
										.suggests(CATEGORY_SUGGESTIONS)
										// No coordinates: the executing entity's block position and dimension.
										.executes(command::addHere)
										.then(Commands.argument("pos", BlockPosArgument.blockPos())
												// Coordinates in the source's current dimension.
												.executes(context -> command.add(context,
														BlockPosArgument.getBlockPos(context, "pos"),
														context.getSource().getLevel()))
												.then(Commands.argument("dimension", DimensionArgument.dimension())
														.executes(context -> command.add(context,
																BlockPosArgument.getBlockPos(context, "pos"),
																DimensionArgument.getDimension(context, "dimension"))))))))
				.then(Commands.literal("remove")
						.then(command.nameArgument("name", WaypointPermissions::canRemove)
								.executes(context -> command.remove(context.getSource(), string(context, "name")))))
				.then(Commands.literal("rename")
						.then(command.nameArgument("old", WaypointPermissions::canEdit)
								.then(Commands.argument("new", StringArgumentType.string())
										.executes(context -> command.rename(context.getSource(),
												string(context, "old"), string(context, "new"))))))
				.then(Commands.literal("describe")
						.then(command.nameArgument("name", WaypointPermissions::canEdit)
								// No text clears the description.
								.executes(context -> command.describe(context.getSource(), string(context, "name"), ""))
								.then(Commands.argument("text", StringArgumentType.greedyString())
										.executes(context -> command.describe(context.getSource(),
												string(context, "name"), string(context, "text"))))))

				// Brigadier always prefers a matching literal (add, info, ...) over this argument,
				// so a category name can never shadow a subcommand.
				.then(Commands.argument("category", StringArgumentType.word())
						.suggests(CATEGORY_SUGGESTIONS)
						.executes(context -> command.listCategory(context.getSource(), category(context), 1))
						.then(Commands.argument("page", IntegerArgumentType.integer(1))
								.executes(context -> command.listCategory(context.getSource(),
										category(context), page(context))))));
	}

	// ------------------------------------------------------------------ browsing

	private int listAll(CommandSourceStack source, int requestedPage) {
		List<Waypoint> all = mod.waypoints().all();
		if (all.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No shared waypoints yet. ")
					.withStyle(ChatFormatting.GRAY)
					.append(Component.literal("[Add one]").withStyle(style -> style
							.withColor(ChatFormatting.YELLOW)
							.withClickEvent(new ClickEvent.SuggestCommand("/waypoints add "))
							.withHoverEvent(new HoverEvent.ShowText(
									Component.literal("/waypoints add <name> <category> [x y z]"))))), false);
			return 0;
		}
		Page<Waypoint> page = Page.of(all, requestedPage, mod.config().pageSize);
		source.sendSuccess(() -> WaypointText.header("Shared Waypoints (" + all.size() + ")"), false);
		sendGrouped(source, page.items());
		if (page.count() > 1) {
			source.sendSuccess(() -> WaypointText.pageFooter(page, "/waypoints page"), false);
		}
		return page.items().size();
	}

	private int listCategory(CommandSourceStack source, Category category, int requestedPage) {
		List<Waypoint> waypoints = mod.waypoints().inCategory(category);
		Page<Waypoint> page = Page.of(waypoints, requestedPage, mod.config().pageSize);
		Viewer viewer = viewer(source);
		source.sendSuccess(() -> WaypointText.categoryHeader(category, waypoints.size()), false);
		if (waypoints.isEmpty()) {
			source.sendSuccess(() -> WaypointText.muted("No waypoints in this category yet."), false);
		}
		for (Waypoint waypoint : page.items()) {
			source.sendSuccess(() -> WaypointText.line(waypoint, viewer), false);
		}
		if (page.count() > 1) {
			source.sendSuccess(() -> WaypointText.pageFooter(page, "/waypoints " + category.id()), false);
		}
		return page.items().size();
	}

	private int listCategories(CommandSourceStack source) {
		Map<Category, Integer> counts = mod.waypoints().countsByCategory();
		source.sendSuccess(() -> WaypointText.header("Categories"), false);
		for (Category category : Category.values()) {
			source.sendSuccess(() -> WaypointText.categorySummary(category, counts.get(category)), false);
		}
		return Category.values().length;
	}

	private int search(CommandSourceStack source, String query) {
		List<Waypoint> results = mod.waypoints().search(query);
		source.sendSuccess(() -> WaypointText.header("Search: " + query.trim() + " (" + results.size() + ")"), false);
		if (results.isEmpty()) {
			source.sendSuccess(() -> WaypointText.muted("Nothing matches. Searches names, descriptions and creators."), false);
			return 0;
		}
		Viewer viewer = viewer(source);
		results.stream().limit(MAX_SEARCH_RESULTS)
				.forEach(waypoint -> source.sendSuccess(() -> WaypointText.line(waypoint, viewer), false));
		if (results.size() > MAX_SEARCH_RESULTS) {
			source.sendSuccess(() -> WaypointText.muted("…and " + (results.size() - MAX_SEARCH_RESULTS)
					+ " more. Try a longer search."), false);
		}
		return results.size();
	}

	private int info(CommandSourceStack source, String name) throws CommandSyntaxException {
		Waypoint waypoint = find(name);
		boolean canEdit = WaypointPermissions.canEdit(source, waypoint);
		for (Component line : WaypointText.info(waypoint, viewer(source), canEdit)) {
			source.sendSuccess(() -> line, false);
		}
		return 1;
	}

	// ----------------------------------------------------------------- around you

	private int near(CommandSourceStack source, int radius) throws CommandSyntaxException {
		source.getPlayerOrException();
		Viewer viewer = viewer(source);
		List<Waypoint> nearby = byDistance(viewer).stream()
				.filter(waypoint -> viewer.distance(waypoint).orElse(Double.MAX_VALUE) <= radius)
				.toList();
		source.sendSuccess(() -> WaypointText.header("Within " + radius + "m (" + nearby.size() + ")"), false);
		if (nearby.isEmpty()) {
			source.sendSuccess(() -> WaypointText.muted("Nothing that close. Try /waypoints nearest."), false);
			return 0;
		}
		nearby.stream().limit(MAX_NEAR_RESULTS)
				.forEach(waypoint -> source.sendSuccess(() -> WaypointText.distanceLine(waypoint, viewer), false));
		return nearby.size();
	}

	private int nearest(CommandSourceStack source, Category category) throws CommandSyntaxException {
		source.getPlayerOrException();
		Viewer viewer = viewer(source);
		Waypoint nearest = byDistance(viewer).stream()
				.filter(waypoint -> category == null || waypoint.category() == category)
				.findFirst()
				.orElseThrow(NOTHING_REACHABLE::create);
		source.sendSuccess(() -> WaypointText.header("Nearest" + (category == null ? "" : " " + category.id())), false);
		source.sendSuccess(() -> WaypointText.distanceLine(nearest, viewer), false);
		return 1;
	}

	/** Waypoints reachable from the viewer's dimension, closest first. */
	private List<Waypoint> byDistance(Viewer viewer) {
		return mod.waypoints().all().stream()
				.filter(waypoint -> viewer.distance(waypoint).isPresent())
				.sorted(Comparator.comparingDouble(waypoint -> viewer.distance(waypoint).orElseThrow()))
				.toList();
	}

	// ----------------------------------------------------------------- navigation

	private int go(CommandSourceStack source, String name) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		mod.navigation().start(player, find(name));
		return 1;
	}

	private int stop(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		boolean wasNavigating = mod.navigation().stop(player.getUUID());
		source.sendSuccess(() -> wasNavigating
				? WaypointText.success("Navigation stopped.")
				: WaypointText.muted("You weren't navigating anywhere."), false);
		return wasNavigating ? 1 : 0;
	}

	private int teleport(CommandSourceStack source, String name) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Waypoint waypoint = find(name);
		ServerLevel level = Optional.ofNullable(Dimensions.key(waypoint.dimension()))
				.map(key -> source.getServer().getLevel(key))
				.orElseThrow(() -> DIMENSION_MISSING.create(waypoint.dimension()));
		player.teleportTo(level, waypoint.x() + 0.5, waypoint.y(), waypoint.z() + 0.5, Set.of(),
				player.getYRot(), player.getXRot(), true);
		source.sendSuccess(() -> WaypointText.success("Teleported to " + waypoint.name()), false);
		return 1;
	}

	// ------------------------------------------------------------------- personal

	private int toggleFavorite(CommandSourceStack source, String name) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Waypoint waypoint = find(name);
		boolean nowFavorite = mod.favorites().toggle(player.getUUID(), waypoint.id());
		source.sendSuccess(() -> nowFavorite
				? Component.literal("★ ").withStyle(ChatFormatting.GOLD)
						.append(WaypointText.success(waypoint.name() + " added to your favourites. "))
						.append(Component.literal("[View favourites]").withStyle(style -> style
								.withColor(ChatFormatting.YELLOW)
								.withClickEvent(new ClickEvent.RunCommand("/waypoints favorites"))))
				: WaypointText.muted("☆ " + waypoint.name() + " removed from your favourites."), false);
		return 1;
	}

	private int listFavorites(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Viewer viewer = viewer(source);
		List<Waypoint> favorites = mod.favorites().of(player.getUUID()).stream()
				.map(id -> mod.waypoints().get(id))
				.flatMap(Optional::stream)
				.sorted(Comparator.comparing(Waypoint::category).thenComparing(Waypoint::name, String.CASE_INSENSITIVE_ORDER))
				.toList();
		source.sendSuccess(() -> WaypointText.header("★ Your favourites (" + favorites.size() + ")"), false);
		if (favorites.isEmpty()) {
			source.sendSuccess(() -> WaypointText.muted("None yet. Open a waypoint with /waypoints info and click [☆ Favourite]."), false);
		}
		favorites.forEach(waypoint -> source.sendSuccess(() -> WaypointText.line(waypoint, viewer), false));
		return favorites.size();
	}

	/**
	 * Sends the raw {@code xaero-waypoint:...} line to the player as a system message. Xaero's Minimap (26.5.0,
	 * 1.21.11) parses system messages, hides this line and shows its own message with an [Add] button.
	 * Nothing else may be in the message: Xaero treats everything after the prefix as share fields.
	 */
	private int shareToXaero(CommandSourceStack source, String name) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Waypoint waypoint = find(name);
		player.sendSystemMessage(Component.literal(XaeroShareFormat.shareMessage(waypoint)));
		return 1;
	}

	// -------------------------------------------------------------------- editing

	private int addHere(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		// The console and command blocks have no sensible "here" (the console would use world spawn).
		if (source.getEntity() == null) {
			throw NEEDS_POSITION.create();
		}
		return add(context, BlockPos.containing(source.getPosition()), source.getLevel());
	}

	private int add(CommandContext<CommandSourceStack> context, BlockPos pos, ServerLevel level)
			throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		String name = checkedName(string(context, "name"));
		Category category = category(context);
		if (mod.waypoints().contains(name)) {
			throw NAME_TAKEN.create(name);
		}

		ServerPlayer player = source.getPlayer();
		UUID creatorUuid = player != null ? player.getUUID() : Waypoint.SERVER_UUID;
		String creatorName = player != null ? player.getGameProfile().name() : source.getTextName();

		Waypoint waypoint = new Waypoint(UUID.randomUUID(), name, category, pos.getX(), pos.getY(), pos.getZ(),
				Dimensions.id(level.dimension()), null, creatorUuid, creatorName,
				Instant.now().truncatedTo(ChronoUnit.SECONDS));
		mod.waypoints().add(waypoint);

		source.sendSuccess(() -> WaypointText.success("Added waypoint " + name), false);
		source.sendSuccess(() -> WaypointText.line(waypoint, viewer(source)), false);
		warnIfUnsaved(source);
		announce(source, creatorName, waypoint);
		return 1;
	}

	/** Tells everyone else online (who may view waypoints) about a new waypoint. */
	private void announce(CommandSourceStack source, String creatorName, Waypoint waypoint) {
		if (!mod.config().announceNewWaypoints) {
			return;
		}
		ServerPlayer creator = source.getPlayer();
		for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
			if (player != creator && WaypointPermissions.canView(player)) {
				player.sendSystemMessage(WaypointText.announcement(creatorName, waypoint,
						Viewer.of(player, mod.favorites())));
			}
		}
	}

	private int remove(CommandSourceStack source, String name) throws CommandSyntaxException {
		Waypoint waypoint = find(name);
		if (!WaypointPermissions.canRemove(source, waypoint)) {
			throw CANNOT_REMOVE.create();
		}
		mod.waypoints().remove(waypoint.name());
		source.sendSuccess(() -> WaypointText.success("Removed waypoint " + waypoint.name()), false);
		warnIfUnsaved(source);
		return 1;
	}

	private int rename(CommandSourceStack source, String oldName, String rawNewName) throws CommandSyntaxException {
		Waypoint waypoint = find(oldName);
		if (!WaypointPermissions.canEdit(source, waypoint)) {
			throw CANNOT_EDIT.create();
		}
		String newName = checkedName(rawNewName);
		// A different waypoint already uses the name? (Changing only the case of its own name is fine.)
		if (mod.waypoints().contains(newName) && !newName.equalsIgnoreCase(waypoint.name())) {
			throw NAME_TAKEN.create(newName);
		}
		Waypoint renamed = mod.waypoints().update(waypoint.withName(newName));
		source.sendSuccess(() -> WaypointText.success("Renamed " + waypoint.name() + " to " + renamed.name()), false);
		warnIfUnsaved(source);
		return 1;
	}

	private int describe(CommandSourceStack source, String name, String text) throws CommandSyntaxException {
		Waypoint waypoint = find(name);
		if (!WaypointPermissions.canEdit(source, waypoint)) {
			throw CANNOT_EDIT.create();
		}
		String description = text.trim();
		Optional<String> problem = Waypoint.validateDescription(description);
		if (problem.isPresent()) {
			throw INVALID_TEXT.create(problem.get());
		}
		mod.waypoints().update(waypoint.withDescription(description));
		source.sendSuccess(() -> description.isEmpty()
				? WaypointText.success("Cleared the description of " + waypoint.name())
				: WaypointText.success("Updated the description of " + waypoint.name()), false);
		warnIfUnsaved(source);
		return 1;
	}

	// -------------------------------------------------------------------- helpers

	/** Sends waypoint lines with a category header in front of each new category. */
	private void sendGrouped(CommandSourceStack source, List<Waypoint> waypoints) {
		Map<Category, Integer> counts = mod.waypoints().countsByCategory();
		Viewer viewer = viewer(source);
		Category current = null;
		for (Waypoint waypoint : waypoints) {
			if (waypoint.category() != current) {
				current = waypoint.category();
				Category header = current;
				source.sendSuccess(() -> WaypointText.categoryHeader(header, counts.get(header)), false);
			}
			source.sendSuccess(() -> WaypointText.line(waypoint, viewer), false);
		}
	}

	private Viewer viewer(CommandSourceStack source) {
		return Viewer.of(source, mod.favorites());
	}

	private Waypoint find(String name) throws CommandSyntaxException {
		return mod.waypoints().get(name).orElseThrow(() -> UNKNOWN_WAYPOINT.create(name));
	}

	private static String checkedName(String raw) throws CommandSyntaxException {
		String name = raw.trim();
		Optional<String> problem = Waypoint.validateName(name);
		if (problem.isPresent()) {
			throw INVALID_TEXT.create(problem.get());
		}
		return name;
	}

	private void warnIfUnsaved(CommandSourceStack source) {
		if (mod.waypoints().lastSaveFailed()) {
			source.sendSuccess(() -> WaypointText.warning(
					"Warning: could not write waypoints.json, the change is only in memory. See the server log."), false);
		}
	}

	/**
	 * A waypoint-name argument that suggests names (quoted when needed) passing {@code filter}, with coordinates
	 * as the tooltip. remove/rename/describe only suggest waypoints the player may change.
	 */
	private RequiredArgumentBuilder<CommandSourceStack, String> nameArgument(String argument,
			BiPredicate<CommandSourceStack, Waypoint> filter) {
		return Commands.argument(argument, StringArgumentType.string())
				.suggests((context, builder) -> SharedSuggestionProvider.suggest(
						mod.waypoints().all().stream().filter(waypoint -> filter.test(context.getSource(), waypoint)).toList(),
						builder,
						waypoint -> StringArgumentType.escapeIfRequired(waypoint.name()),
						waypoint -> Component.literal(waypoint.coordinates() + " (" + Dimensions.shortName(waypoint.dimension()) + ")")));
	}

	private static Category category(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String id = string(context, "category");
		return Category.byId(id).orElseThrow(() -> UNKNOWN_CATEGORY.create(id));
	}

	private static int page(CommandContext<CommandSourceStack> context) {
		return IntegerArgumentType.getInteger(context, "page");
	}

	private static String string(CommandContext<CommandSourceStack> context, String argument) {
		return StringArgumentType.getString(context, argument);
	}
}
