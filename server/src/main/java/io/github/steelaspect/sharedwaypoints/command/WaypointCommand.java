package io.github.steelaspect.sharedwaypoints.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.steelaspect.sharedwaypoints.ModContext;
import io.github.steelaspect.sharedwaypoints.permission.WaypointPermissions;
import io.github.steelaspect.sharedwaypoints.portal.PortalGuide;
import io.github.steelaspect.sharedwaypoints.text.Viewer;
import io.github.steelaspect.sharedwaypoints.text.WaypointText;
import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import io.github.steelaspect.sharedwaypoints.util.Page;
import io.github.steelaspect.sharedwaypoints.waypoint.Category;
import io.github.steelaspect.sharedwaypoints.waypoint.ProjectStatus;
import io.github.steelaspect.sharedwaypoints.waypoint.Route;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import io.github.steelaspect.sharedwaypoints.waypoint.WaypointStore;
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
 * The {@code /cway} command tree.
 *
 * <pre>
 * Browsing                  /cway [page &lt;n&gt;] | &lt;category&gt; [page] | categories | search &lt;text&gt; | info &lt;name&gt;
 * Around you                /cway near [radius] | nearest [category]
 * Navigation                /cway go &lt;name&gt; | stop | tp &lt;name&gt; (op)
 * Personal                  /cway favorite &lt;name&gt; | favorites
 * Editing                   /cway add &lt;name&gt; &lt;category&gt; [x y z] [dimension] | remove | rename | describe
 * Projects                  /cway status &lt;name&gt; [planned|wip|done|broken [note] | clear] | projects [status]
 * Routes                    /cway route [list] | info | go &lt;route&gt; [stop] | skip | create | add | drop | move
 *                           | rename | describe | delete
 * Portals                   /cway portal | portal list | portal stop [number]
 * Xaero                     /cway xaero &lt;name&gt;   (what [Add to Xaero] runs)
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
	private static final DynamicCommandExceptionType NAME_TAKEN = new DynamicCommandExceptionType(
			name -> Component.literal("A waypoint named \"" + name + "\" already exists"));
	private static final DynamicCommandExceptionType INVALID_TEXT = new DynamicCommandExceptionType(
			reason -> Component.literal(String.valueOf(reason)));
	private static final SimpleCommandExceptionType CANNOT_REMOVE = new SimpleCommandExceptionType(
			Component.literal("You can only remove waypoints you created"));
	private static final SimpleCommandExceptionType CANNOT_EDIT = new SimpleCommandExceptionType(
			Component.literal("You can only edit waypoints you created"));
	private static final SimpleCommandExceptionType CANNOT_SET_STATUS = new SimpleCommandExceptionType(
			Component.literal("You can only set the status of waypoints you created"));
	private static final SimpleCommandExceptionType NEEDS_POSITION = new SimpleCommandExceptionType(
			Component.literal("Give coordinates (x y z) when not running this as a player"));
	private static final SimpleCommandExceptionType NOTHING_REACHABLE = new SimpleCommandExceptionType(
			Component.literal("No waypoints in this dimension (or through a Nether portal from here)"));
	private static final DynamicCommandExceptionType UNKNOWN_ROUTE = new DynamicCommandExceptionType(
			name -> Component.literal("No route named \"" + name + "\""));
	private static final DynamicCommandExceptionType ROUTE_NAME_TAKEN = new DynamicCommandExceptionType(
			name -> Component.literal("A route named \"" + name + "\" already exists"));
	private static final SimpleCommandExceptionType CANNOT_EDIT_ROUTE = new SimpleCommandExceptionType(
			Component.literal("You can only change routes you created"));
	private static final SimpleCommandExceptionType CANNOT_DELETE_ROUTE = new SimpleCommandExceptionType(
			Component.literal("You can only delete routes you created"));
	private static final SimpleCommandExceptionType ROUTE_FULL = new SimpleCommandExceptionType(
			Component.literal("A route can have at most " + Route.MAX_STOPS + " stops"));
	private static final DynamicCommandExceptionType NO_SUCH_STOP = new DynamicCommandExceptionType(
			count -> Component.literal("Stop numbers go from 1 to " + count));
	private static final SimpleCommandExceptionType ROUTE_EMPTY = new SimpleCommandExceptionType(
			Component.literal("That route has no stops yet"));
	private static final SimpleCommandExceptionType NOT_ON_ROUTE = new SimpleCommandExceptionType(
			Component.literal("You aren't following a route"));
	private static final DynamicCommandExceptionType DIMENSION_MISSING = new DynamicCommandExceptionType(
			dimension -> Component.literal("Dimension " + dimension + " isn't loaded on this server"));

	private static final SimpleCommandExceptionType NO_PORTAL = new SimpleCommandExceptionType(Component.literal(
			"Look at a Nether portal (the purple part) within " + (int) PortalGuide.LOOK_RANGE + " blocks"));
	private static final DynamicCommandExceptionType NO_PORTAL_GUIDE = new DynamicCommandExceptionType(
			number -> Component.literal("You don't have a portal guide #" + number + " (see /cway portal list)"));

	private final ModContext mod;

	private WaypointCommand(ModContext mod) {
		this.mod = mod;
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, ModContext mod) {
		WaypointCommand command = new WaypointCommand(mod);
		// Categories come from config.json and can change on /cway reload, so suggest the current ones.
		SuggestionProvider<CommandSourceStack> categorySuggestions =
				(context, builder) -> SharedSuggestionProvider.suggest(mod.categories().ids(), builder);
		BiPredicate<CommandSourceStack, Waypoint> anyWaypoint = (source, waypoint) -> true;

		dispatcher.register(Commands.literal("cway")
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
								.suggests(categorySuggestions)
								.executes(context -> command.nearest(context.getSource(), command.category(context)))))

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

				// ---- routes
				.then(command.routeTree(anyWaypoint))

				// ---- admin
				.then(Commands.literal("reload")
						.requires(WaypointPermissions.requireReload())
						.executes(context -> command.reload(context.getSource())))

				// ---- xaero
				.then(Commands.literal("xaero")
						.then(command.nameArgument("name", anyWaypoint)
								.executes(context -> command.shareToXaero(context.getSource(), string(context, "name")))))
				.then(Commands.literal("sync")
						.executes(context -> command.syncHelp(context.getSource())))

				// ---- portals: look at one, and its matching spot on the other side is highlighted (several at once)
				.then(Commands.literal("portal")
						.executes(context -> command.portalGuide(context.getSource()))
						.then(Commands.literal("list")
								.executes(context -> command.listPortalGuides(context.getSource())))
						.then(Commands.literal("stop")
								.executes(context -> command.stopPortalGuides(context.getSource()))
								.then(Commands.argument("number", IntegerArgumentType.integer(1))
										.executes(context -> command.stopPortalGuide(context.getSource(),
												IntegerArgumentType.getInteger(context, "number"))))))

				// ---- editing
				.then(Commands.literal("add")
						.requires(WaypointPermissions.requireAdd())
						.then(Commands.argument("name", StringArgumentType.string())
								.then(Commands.argument("category", StringArgumentType.word())
										.suggests(categorySuggestions)
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

				// ---- projects
				.then(command.statusTree())
				.then(Commands.literal("projects")
						.executes(context -> command.listProjects(context.getSource(), null))
						.then(Commands.argument("status", StringArgumentType.word())
								.suggests((context, builder) -> SharedSuggestionProvider.suggest(
										java.util.Arrays.stream(ProjectStatus.State.values()).map(ProjectStatus.State::id), builder))
								.executes(context -> command.listProjects(context.getSource(), command.state(context)))))

				// Brigadier always prefers a matching literal (add, info, ...) over this argument,
				// so a category name can never shadow a subcommand.
				.then(Commands.argument("category", StringArgumentType.word())
						.suggests(categorySuggestions)
						.executes(context -> command.listCategory(context.getSource(), command.category(context), 1))
						.then(Commands.argument("page", IntegerArgumentType.integer(1))
								.executes(context -> command.listCategory(context.getSource(),
										command.category(context), page(context))))));
	}

	// ------------------------------------------------------------------ browsing

	private int listAll(CommandSourceStack source, int requestedPage) {
		List<Waypoint> all = mod.waypoints().all();
		if (all.isEmpty()) {
			reply(source, Component.literal("No shared waypoints yet. ")
					.withStyle(ChatFormatting.GRAY)
					.append(Component.literal("[Add one]").withStyle(style -> style
							.withColor(ChatFormatting.YELLOW)
							.withClickEvent(new ClickEvent.SuggestCommand("/cway add "))
							.withHoverEvent(new HoverEvent.ShowText(
									Component.literal("/cway add <name> <category> [x y z]"))))));
			return 0;
		}
		Page<Waypoint> page = Page.of(all, requestedPage, mod.config().pageSize);
		reply(source, WaypointText.header("Shared Waypoints (" + all.size() + ")"));
		sendGrouped(source, page.items());
		if (page.count() > 1) {
			reply(source, WaypointText.pageFooter(page, "/cway page"));
		}
		return page.items().size();
	}

	private int listCategory(CommandSourceStack source, Category category, int requestedPage) {
		List<Waypoint> waypoints = mod.waypoints().inCategory(category);
		Page<Waypoint> page = Page.of(waypoints, requestedPage, mod.config().pageSize);
		Viewer viewer = viewer(source);
		reply(source, WaypointText.categoryHeader(category, waypoints.size()));
		if (waypoints.isEmpty()) {
			reply(source, WaypointText.muted("No waypoints in this category yet."));
		}
		for (Waypoint waypoint : page.items()) {
			reply(source, WaypointText.line(waypoint, viewer));
		}
		if (page.count() > 1) {
			reply(source, WaypointText.pageFooter(page, "/cway " + category.id()));
		}
		return page.items().size();
	}

	private int listCategories(CommandSourceStack source) {
		Map<String, Integer> counts = mod.waypoints().countsByCategory();
		reply(source, WaypointText.header("Categories"));
		for (Category category : mod.categories().all()) {
			reply(source, WaypointText.categorySummary(category, counts.getOrDefault(category.id(), 0)));
		}
		// Waypoints whose category was removed from config.json keep it; show those too.
		counts.forEach((id, count) -> {
			if (mod.categories().byId(id).isEmpty()) {
				reply(source, WaypointText.categorySummary(mod.categories().resolve(id), count)
						.copy().append(WaypointText.muted(" (not in config.json)")));
			}
		});
		return mod.categories().all().size();
	}

	private int reload(CommandSourceStack source) {
		mod.reload(source.getServer());
		List<String> maps = mod.maps().activeMaps();
		reply(source, WaypointText.success("Reloaded Cytra Waypoints: " + mod.waypoints().size() + " waypoints, "
				+ mod.routes().size() + " routes, " + mod.categories().all().size() + " categories"
				+ (maps.isEmpty() ? "" : ", markers on " + String.join(" and ", maps)) + "."));
		return 1;
	}

	private int search(CommandSourceStack source, String query) {
		List<Waypoint> results = mod.waypoints().search(query);
		reply(source, WaypointText.header("Search: " + query.trim() + " (" + results.size() + ")"));
		if (results.isEmpty()) {
			reply(source, WaypointText.muted("Nothing matches. Searches names, descriptions and creators."));
			return 0;
		}
		Viewer viewer = viewer(source);
		results.stream().limit(MAX_SEARCH_RESULTS)
				.forEach(waypoint -> reply(source, WaypointText.line(waypoint, viewer)));
		if (results.size() > MAX_SEARCH_RESULTS) {
			reply(source, WaypointText.muted("…and " + (results.size() - MAX_SEARCH_RESULTS)
					+ " more. Try a longer search."));
		}
		return results.size();
	}

	private int info(CommandSourceStack source, String name) throws CommandSyntaxException {
		Waypoint waypoint = find(name);
		boolean canEdit = WaypointPermissions.canEdit(source, waypoint);
		boolean canSetStatus = WaypointPermissions.canSetStatus(source, waypoint);
		for (Component line : WaypointText.info(waypoint, viewer(source), canEdit, canSetStatus)) {
			reply(source, line);
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
		reply(source, WaypointText.header("Within " + radius + "m (" + nearby.size() + ")"));
		if (nearby.isEmpty()) {
			reply(source, WaypointText.muted("Nothing that close. Try /cway nearest."));
			return 0;
		}
		nearby.stream().limit(MAX_NEAR_RESULTS)
				.forEach(waypoint -> reply(source, WaypointText.distanceLine(waypoint, viewer)));
		return nearby.size();
	}

	private int nearest(CommandSourceStack source, Category category) throws CommandSyntaxException {
		source.getPlayerOrException();
		Viewer viewer = viewer(source);
		Waypoint nearest = byDistance(viewer).stream()
				.filter(waypoint -> category == null || waypoint.category() == category)
				.findFirst()
				.orElseThrow(NOTHING_REACHABLE::create);
		reply(source, WaypointText.header("Nearest" + (category == null ? "" : " " + category.id())));
		reply(source, WaypointText.distanceLine(nearest, viewer));
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
		reply(source, wasNavigating
				? WaypointText.success("Navigation stopped.")
				: WaypointText.muted("You weren't navigating anywhere."));
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
		reply(source, WaypointText.success("Teleported to " + waypoint.name()));
		return 1;
	}

	// ------------------------------------------------------------------- personal

	private int toggleFavorite(CommandSourceStack source, String name) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Waypoint waypoint = find(name);
		boolean nowFavorite = mod.favorites().toggle(player.getUUID(), waypoint.id());
		reply(source, nowFavorite
				? Component.literal("★ ").withStyle(ChatFormatting.GOLD)
						.append(WaypointText.success(waypoint.name() + " added to your favourites. "))
						.append(Component.literal("[View favourites]").withStyle(style -> style
								.withColor(ChatFormatting.YELLOW)
								.withClickEvent(new ClickEvent.RunCommand("/cway favorites"))))
				: WaypointText.muted("☆ " + waypoint.name() + " removed from your favourites."));
		return 1;
	}

	private int listFavorites(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Viewer viewer = viewer(source);
		List<Waypoint> favorites = mod.favorites().of(player.getUUID()).stream()
				.map(id -> mod.waypoints().get(id))
				.flatMap(Optional::stream)
				.sorted(WaypointStore.DISPLAY_ORDER)
				.toList();
		reply(source, WaypointText.header("★ Your favourites (" + favorites.size() + ")"));
		if (favorites.isEmpty()) {
			reply(source, WaypointText.muted("None yet. Open a waypoint with /cway info and click [☆ Favourite]."));
		}
		favorites.forEach(waypoint -> reply(source, WaypointText.line(waypoint, viewer)));
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

		reply(source, WaypointText.success("Added waypoint " + name));
		reply(source, WaypointText.line(waypoint, viewer(source)));
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
		reply(source, WaypointText.success("Removed waypoint " + waypoint.name()));
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
		reply(source, WaypointText.success("Renamed " + waypoint.name() + " to " + renamed.name()));
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
		reply(source, description.isEmpty()
				? WaypointText.success("Cleared the description of " + waypoint.name())
				: WaypointText.success("Updated the description of " + waypoint.name()));
		warnIfUnsaved(source);
		return 1;
	}

	// ------------------------------------------------------------------- projects

	/** {@code /cway status <name> [planned|wip|done|broken [note] | clear]}. */
	private LiteralArgumentBuilder<CommandSourceStack> statusTree() {
		RequiredArgumentBuilder<CommandSourceStack, String> name = nameArgument("name", WaypointPermissions::canSetStatus)
				.executes(context -> showStatus(context.getSource(), string(context, "name")));
		for (ProjectStatus.State state : ProjectStatus.State.values()) {
			name.then(Commands.literal(state.id())
					.executes(context -> setStatus(context.getSource(), string(context, "name"), state, ""))
					.then(Commands.argument("note", StringArgumentType.greedyString())
							.executes(context -> setStatus(context.getSource(), string(context, "name"), state,
									string(context, "note")))));
		}
		name.then(Commands.literal("clear")
				.executes(context -> setStatus(context.getSource(), string(context, "name"), null, "")));
		return Commands.literal("status").then(name);
	}

	/** {@code /cway status <name>}: the current status and buttons to change it. */
	private int showStatus(CommandSourceStack source, String name) throws CommandSyntaxException {
		Waypoint waypoint = find(name);
		reply(source, WaypointText.statusLine(waypoint));
		if (WaypointPermissions.canSetStatus(source, waypoint)) {
			reply(source, WaypointText.statusButtons(waypoint));
		}
		return 1;
	}

	private int setStatus(CommandSourceStack source, String name, ProjectStatus.State state, String rawNote)
			throws CommandSyntaxException {
		Waypoint waypoint = find(name);
		if (!WaypointPermissions.canSetStatus(source, waypoint)) {
			throw CANNOT_SET_STATUS.create();
		}
		if (state == null) {
			mod.waypoints().update(waypoint.withStatus(null));
			reply(source, WaypointText.success("Cleared the status of " + waypoint.name()));
			warnIfUnsaved(source);
			return 1;
		}
		String note = rawNote.trim();
		Optional<String> problem = ProjectStatus.validateNote(note);
		if (problem.isPresent()) {
			throw INVALID_TEXT.create(problem.get());
		}
		ServerPlayer player = source.getPlayer();
		ProjectStatus status = new ProjectStatus(state, note.isEmpty() ? null : note,
				player != null ? player.getUUID() : Waypoint.SERVER_UUID,
				player != null ? player.getGameProfile().name() : source.getTextName(),
				Instant.now().truncatedTo(ChronoUnit.SECONDS));
		Waypoint updated = mod.waypoints().update(waypoint.withStatus(status));
		reply(source, WaypointText.success("Marked " + waypoint.name() + " as " + state.displayName())
				.copy().append(" ").append(WaypointText.statusTag(updated)));
		warnIfUnsaved(source);
		tellCreator(source, updated);
		return 1;
	}

	/** Lets the creator know (if they're online and didn't do it themselves) that someone changed their build's status. */
	private void tellCreator(CommandSourceStack source, Waypoint waypoint) {
		ServerPlayer creator = source.getServer().getPlayerList().getPlayer(waypoint.creatorUuid());
		if (creator != null && creator != source.getPlayer() && WaypointPermissions.canView(creator)) {
			creator.sendSystemMessage(WaypointText.statusChanged(waypoint, Viewer.of(creator, mod.favorites())));
		}
	}

	/** {@code /cway projects [status]}: every waypoint with a status (or one status), most recently changed first. */
	private int listProjects(CommandSourceStack source, ProjectStatus.State state) {
		List<Waypoint> projects = mod.waypoints().withStatus(state);
		reply(source, WaypointText.header((state == null ? "Projects" : state.displayName() + " projects")
				+ " (" + projects.size() + ")"));
		if (projects.isEmpty()) {
			reply(source, WaypointText.muted(state == null
					? "No waypoint has a status yet. Set one with /cway status <name> <planned|wip|done|broken>."
					: "Nothing is marked " + state.displayName() + "."));
			return 0;
		}
		if (state == null) {
			reply(source, WaypointText.statusFilters(mod.waypoints().countsByStatus()));
		}
		Viewer viewer = viewer(source);
		projects.stream().limit(MAX_SEARCH_RESULTS).forEach(waypoint -> reply(source, WaypointText.projectLine(waypoint, viewer)));
		if (projects.size() > MAX_SEARCH_RESULTS) {
			reply(source, WaypointText.muted("…and " + (projects.size() - MAX_SEARCH_RESULTS)
					+ " more. Filter with /cway projects <status>."));
		}
		return projects.size();
	}

	private ProjectStatus.State state(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String id = string(context, "status");
		return ProjectStatus.State.byId(id).orElseThrow(() -> new SimpleCommandExceptionType(Component.literal(
				"Unknown status \"" + id + "\". Use one of: planned, wip, done, broken")).create());
	}

	// --------------------------------------------------------------------- routes

	private LiteralArgumentBuilder<CommandSourceStack> routeTree(BiPredicate<CommandSourceStack, Waypoint> anyWaypoint) {
		BiPredicate<CommandSourceStack, Route> anyRoute = (source, route) -> true;
		BiPredicate<CommandSourceStack, Route> editable = WaypointPermissions::canEdit;
		BiPredicate<CommandSourceStack, Route> removable = WaypointPermissions::canRemove;
		return Commands.literal("route")
				.executes(context -> listRoutes(context.getSource()))
				.then(Commands.literal("list")
						.executes(context -> listRoutes(context.getSource())))
				.then(Commands.literal("info")
						.then(routeArgument("route", anyRoute)
								.executes(context -> routeInfo(context.getSource(), string(context, "route")))))
				.then(Commands.literal("go")
						.then(routeArgument("route", anyRoute)
								.executes(context -> followRoute(context.getSource(), string(context, "route"), 1))
								.then(Commands.argument("stop", IntegerArgumentType.integer(1, Route.MAX_STOPS))
										.executes(context -> followRoute(context.getSource(), string(context, "route"),
												IntegerArgumentType.getInteger(context, "stop"))))))
				.then(Commands.literal("skip")
						.executes(context -> skipStop(context.getSource())))
				.then(Commands.literal("create")
						.requires(WaypointPermissions.requireRoute())
						.then(Commands.argument("name", StringArgumentType.string())
								.executes(context -> createRoute(context.getSource(), string(context, "name")))))
				.then(Commands.literal("add")
						.then(routeArgument("route", editable)
								.then(nameArgument("waypoint", anyWaypoint)
										.executes(context -> addStop(context.getSource(), string(context, "route"),
												string(context, "waypoint"))))))
				.then(Commands.literal("drop")
						.then(routeArgument("route", editable)
								.then(Commands.argument("stop", IntegerArgumentType.integer(1, Route.MAX_STOPS))
										.executes(context -> dropStop(context.getSource(), string(context, "route"),
												IntegerArgumentType.getInteger(context, "stop"))))))
				.then(Commands.literal("move")
						.then(routeArgument("route", editable)
								.then(Commands.argument("from", IntegerArgumentType.integer(1, Route.MAX_STOPS))
										.then(Commands.argument("to", IntegerArgumentType.integer(1, Route.MAX_STOPS))
												.executes(context -> moveStop(context.getSource(), string(context, "route"),
														IntegerArgumentType.getInteger(context, "from"),
														IntegerArgumentType.getInteger(context, "to")))))))
				.then(Commands.literal("rename")
						.then(routeArgument("old", editable)
								.then(Commands.argument("new", StringArgumentType.string())
										.executes(context -> renameRoute(context.getSource(), string(context, "old"),
												string(context, "new"))))))
				.then(Commands.literal("describe")
						.then(routeArgument("route", editable)
								.executes(context -> describeRoute(context.getSource(), string(context, "route"), ""))
								.then(Commands.argument("text", StringArgumentType.greedyString())
										.executes(context -> describeRoute(context.getSource(), string(context, "route"),
												string(context, "text"))))))
				.then(Commands.literal("delete")
						.then(routeArgument("route", removable)
								.executes(context -> deleteRoute(context.getSource(), string(context, "route")))));
	}

	private int listRoutes(CommandSourceStack source) {
		List<Route> routes = mod.routes().all();
		reply(source, WaypointText.header("Routes (" + routes.size() + ")"));
		if (routes.isEmpty()) {
			reply(source, WaypointText.muted("No routes yet. ")
					.copy().append(Component.literal("[Create one]").withStyle(style -> style
							.withColor(ChatFormatting.YELLOW)
							.withClickEvent(new ClickEvent.SuggestCommand("/cway route create "))
							.withHoverEvent(new HoverEvent.ShowText(Component.literal(
									"/cway route create <name>, then /cway route add <route> <waypoint>"))))));
			return 0;
		}
		boolean isPlayer = source.getPlayer() != null;
		routes.forEach(route -> reply(source, WaypointText.routeLine(route, stopsOf(route), isPlayer)));
		return routes.size();
	}

	private int routeInfo(CommandSourceStack source, String name) throws CommandSyntaxException {
		Route route = findRoute(name);
		for (Component line : WaypointText.routeInfo(route, stopsOf(route), viewer(source),
				WaypointPermissions.canEdit(source, route), WaypointPermissions.canRemove(source, route))) {
			reply(source, line);
		}
		return 1;
	}

	private int followRoute(CommandSourceStack source, String name, int stop) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Route route = findRoute(name);
		if (route.stops().isEmpty()) {
			throw ROUTE_EMPTY.create();
		}
		if (stop > route.stops().size()) {
			throw NO_SUCH_STOP.create(route.stops().size());
		}
		if (!mod.navigation().startRoute(player, route, stop - 1)) {
			throw ROUTE_EMPTY.create();
		}
		return 1;
	}

	private int skipStop(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!mod.navigation().skip(player)) {
			throw NOT_ON_ROUTE.create();
		}
		return 1;
	}

	private int createRoute(CommandSourceStack source, String rawName) throws CommandSyntaxException {
		String name = checkedName(rawName);
		if (mod.routes().contains(name)) {
			throw ROUTE_NAME_TAKEN.create(name);
		}
		ServerPlayer player = source.getPlayer();
		Route route = new Route(UUID.randomUUID(), name, List.of(), null,
				player != null ? player.getUUID() : Waypoint.SERVER_UUID,
				player != null ? player.getGameProfile().name() : source.getTextName(),
				Instant.now().truncatedTo(ChronoUnit.SECONDS));
		mod.routes().add(route);
		reply(source, WaypointText.success("Created route " + name + ". ")
				.copy().append(Component.literal("[+ Add a stop]").withStyle(style -> style
						.withColor(ChatFormatting.YELLOW)
						.withClickEvent(new ClickEvent.SuggestCommand(WaypointText.routeCommand("add", name) + " "))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Add a waypoint as the next stop"))))));
		warnIfRoutesUnsaved(source);
		return 1;
	}

	private int addStop(CommandSourceStack source, String routeName, String waypointName) throws CommandSyntaxException {
		Route route = editableRoute(source, routeName);
		Waypoint waypoint = find(waypointName);
		if (route.stops().size() >= Route.MAX_STOPS) {
			throw ROUTE_FULL.create();
		}
		Route updated = mod.routes().update(route.withStopAdded(waypoint.id()));
		reply(source, WaypointText.success("Added " + waypoint.name() + " to " + route.name()
				+ " as stop " + updated.stops().size()));
		warnIfRoutesUnsaved(source);
		return updated.stops().size();
	}

	private int dropStop(CommandSourceStack source, String routeName, int stop) throws CommandSyntaxException {
		Route route = editableRoute(source, routeName);
		checkStop(route, stop);
		String dropped = mod.waypoints().get(route.stops().get(stop - 1)).map(Waypoint::name).orElse("stop " + stop);
		mod.routes().update(route.withStopRemoved(stop - 1));
		reply(source, WaypointText.success("Removed " + dropped + " (stop " + stop + ") from " + route.name()));
		warnIfRoutesUnsaved(source);
		return 1;
	}

	private int moveStop(CommandSourceStack source, String routeName, int from, int to) throws CommandSyntaxException {
		Route route = editableRoute(source, routeName);
		checkStop(route, from);
		checkStop(route, to);
		String moved = mod.waypoints().get(route.stops().get(from - 1)).map(Waypoint::name).orElse("stop " + from);
		mod.routes().update(route.withStopMoved(from - 1, to - 1));
		reply(source, WaypointText.success("Moved " + moved + " to stop " + to + " of " + route.name()));
		warnIfRoutesUnsaved(source);
		return 1;
	}

	private int renameRoute(CommandSourceStack source, String oldName, String rawNewName) throws CommandSyntaxException {
		Route route = editableRoute(source, oldName);
		String newName = checkedName(rawNewName);
		if (mod.routes().contains(newName) && !newName.equalsIgnoreCase(route.name())) {
			throw ROUTE_NAME_TAKEN.create(newName);
		}
		mod.routes().update(route.withName(newName));
		reply(source, WaypointText.success("Renamed route " + route.name() + " to " + newName));
		warnIfRoutesUnsaved(source);
		return 1;
	}

	private int describeRoute(CommandSourceStack source, String name, String text) throws CommandSyntaxException {
		Route route = editableRoute(source, name);
		String description = text.trim();
		Optional<String> problem = Waypoint.validateDescription(description);
		if (problem.isPresent()) {
			throw INVALID_TEXT.create(problem.get());
		}
		mod.routes().update(route.withDescription(description));
		reply(source, description.isEmpty()
				? WaypointText.success("Cleared the description of route " + route.name())
				: WaypointText.success("Updated the description of route " + route.name()));
		warnIfRoutesUnsaved(source);
		return 1;
	}

	private int deleteRoute(CommandSourceStack source, String name) throws CommandSyntaxException {
		Route route = findRoute(name);
		if (!WaypointPermissions.canRemove(source, route)) {
			throw CANNOT_DELETE_ROUTE.create();
		}
		mod.routes().remove(route.id());
		reply(source, WaypointText.success("Deleted route " + route.name() + " (its waypoints are unchanged)"));
		warnIfRoutesUnsaved(source);
		return 1;
	}

	private Route findRoute(String name) throws CommandSyntaxException {
		return mod.routes().get(name).orElseThrow(() -> UNKNOWN_ROUTE.create(name));
	}

	private Route editableRoute(CommandSourceStack source, String name) throws CommandSyntaxException {
		Route route = findRoute(name);
		if (!WaypointPermissions.canEdit(source, route)) {
			throw CANNOT_EDIT_ROUTE.create();
		}
		return route;
	}

	private static void checkStop(Route route, int stop) throws CommandSyntaxException {
		if (stop < 1 || stop > route.stops().size()) {
			throw route.stops().isEmpty() ? ROUTE_EMPTY.create() : NO_SUCH_STOP.create(route.stops().size());
		}
	}

	/** The route's stops as waypoints (every stored stop exists: deleted waypoints are dropped from routes). */
	private List<Waypoint> stopsOf(Route route) {
		return route.stops().stream().map(id -> mod.waypoints().get(id)).flatMap(Optional::stream).toList();
	}

	private void warnIfRoutesUnsaved(CommandSourceStack source) {
		if (mod.routes().lastSaveFailed()) {
			reply(source, WaypointText.warning(
					"Warning: could not write routes.json, the change is only in memory. See the server log."));
		}
	}

	/** A route-name argument that suggests names (quoted when needed) passing {@code filter}. */
	private RequiredArgumentBuilder<CommandSourceStack, String> routeArgument(String argument,
			BiPredicate<CommandSourceStack, Route> filter) {
		return Commands.argument(argument, StringArgumentType.string())
				.suggests((context, builder) -> SharedSuggestionProvider.suggest(
						mod.routes().all().stream().filter(route -> filter.test(context.getSource(), route)).toList(),
						builder,
						route -> StringArgumentType.escapeIfRequired(route.name()),
						route -> Component.literal(route.stops().size() + (route.stops().size() == 1 ? " stop" : " stops"))));
	}

	/** {@code /cway portal}: highlight the matching spot for the portal you're looking at, next to any others. */
	private int portalGuide(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		BlockPos portal = PortalGuide.lookedAt(player).orElseThrow(() -> NO_PORTAL.create());
		Optional<String> problem = mod.portalGuide().start(player, portal, source.getServer().getTickCount());
		if (problem.isPresent()) {
			throw new SimpleCommandExceptionType(Component.literal(problem.get())).create();
		}
		return 1;
	}

	private int listPortalGuides(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		mod.portalGuide().list(player, source.getServer().getTickCount()).forEach(line -> reply(source, line));
		return 1;
	}

	/** {@code /cway portal stop}: all your portal guides. */
	private int stopPortalGuides(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		int stopped = mod.portalGuide().stopAll(player.getUUID());
		reply(source, Component.literal(switch (stopped) {
			case 0 -> "You don't have a portal guide running.";
			case 1 -> "Portal guide stopped.";
			default -> "Stopped all " + stopped + " portal guides.";
		}).withStyle(ChatFormatting.GRAY));
		return stopped;
	}

	/** {@code /cway portal stop <number>}: one of them (what the [Stop] buttons run). */
	private int stopPortalGuide(CommandSourceStack source, int number) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		String where = mod.portalGuide().stop(player, number).orElseThrow(() -> NO_PORTAL_GUIDE.create(number));
		reply(source, Component.literal("Portal guide #" + number + " stopped (" + where + ").")
				.withStyle(ChatFormatting.GRAY));
		return 1;
	}

	/** How to get the automatic Xaero's Minimap sync, and whether it's on for you. */
	private int syncHelp(CommandSourceStack source) {
		mod.clientModTip().steps(source.getPlayer()).forEach(line -> reply(source, line));
		return 1;
	}

	// -------------------------------------------------------------------- helpers

	/** Sends waypoint lines with a category header in front of each new category. */
	private void sendGrouped(CommandSourceStack source, List<Waypoint> waypoints) {
		Map<String, Integer> counts = mod.waypoints().countsByCategory();
		Viewer viewer = viewer(source);
		Category current = null;
		for (Waypoint waypoint : waypoints) {
			if (current == null || !waypoint.category().id().equals(current.id())) {
				current = waypoint.category();
				Category header = current;
				reply(source, WaypointText.categoryHeader(header, counts.getOrDefault(header.id(), 0)));
			}
			reply(source, WaypointText.line(waypoint, viewer));
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
			reply(source, WaypointText.warning(
					"Warning: could not write waypoints.json, the change is only in memory. See the server log."));
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

	private Category category(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		String id = string(context, "category");
		Optional<Category> category = mod.categories().byId(id);
		if (category.isEmpty()) {
			throw new SimpleCommandExceptionType(Component.literal("Unknown category \"" + id + "\". Use one of: "
					+ String.join(", ", mod.categories().ids()))).create();
		}
		return category.get();
	}

	/**
	 * Sends a reply to whoever ran the command. Vanilla drops command replies when the {@code sendCommandFeedback}
	 * gamerule is off, but for /cway the reply <em>is</em> the result, so players get it anyway.
	 */
	private static void reply(CommandSourceStack source, Component message) {
		ServerPlayer player = source.getPlayer();
		if (player != null && !source.isSilent() && !player.commandSource().acceptsSuccess()) {
			player.sendSystemMessage(message);
		} else {
			source.sendSuccess(() -> message, false);
		}
	}

	private static int page(CommandContext<CommandSourceStack> context) {
		return IntegerArgumentType.getInteger(context, "page");
	}

	private static String string(CommandContext<CommandSourceStack> context, String argument) {
		return StringArgumentType.getString(context, argument);
	}
}
