package io.github.steelaspect.sharedwaypoints.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.steelaspect.sharedwaypoints.permission.WaypointPermissions;
import io.github.steelaspect.sharedwaypoints.text.WaypointText;
import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import io.github.steelaspect.sharedwaypoints.waypoint.Category;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import io.github.steelaspect.sharedwaypoints.waypoint.WaypointStore;
import io.github.steelaspect.sharedwaypoints.xaero.XaeroShareFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * /waypoints                                          list everything, grouped by category
 * /waypoints &lt;category&gt;                               list one category
 * /waypoints categories                               list categories with counts
 * /waypoints info &lt;name&gt;                              details for one waypoint
 * /waypoints add &lt;name&gt; &lt;category&gt; [x y z] [dimension]  add (defaults to your position and dimension)
 * /waypoints remove &lt;name&gt;                            remove (op level 2, or your own)
 * /waypoints rename &lt;old&gt; &lt;new&gt;                       rename (op level 2, or your own)
 * /waypoints xaero &lt;name&gt;                             send the Xaero share line (used by [Add to Xaero])
 * </pre>
 *
 * <p>Only vanilla argument types are used (string, word, block_pos, dimension) so unmodded clients can parse
 * and tab-complete the tree.
 */
public final class WaypointCommand {
	private static final DynamicCommandExceptionType UNKNOWN_WAYPOINT = new DynamicCommandExceptionType(
			name -> Component.literal("No waypoint named \"" + name + "\""));
	private static final DynamicCommandExceptionType UNKNOWN_CATEGORY = new DynamicCommandExceptionType(
			name -> Component.literal("Unknown category \"" + name + "\". Use one of: "
					+ String.join(", ", Category.ids())));
	private static final DynamicCommandExceptionType NAME_TAKEN = new DynamicCommandExceptionType(
			name -> Component.literal("A waypoint named \"" + name + "\" already exists"));
	private static final DynamicCommandExceptionType INVALID_NAME = new DynamicCommandExceptionType(
			reason -> Component.literal(String.valueOf(reason)));
	private static final SimpleCommandExceptionType CANNOT_REMOVE = new SimpleCommandExceptionType(
			Component.literal("You can only remove waypoints you created"));
	private static final SimpleCommandExceptionType CANNOT_RENAME = new SimpleCommandExceptionType(
			Component.literal("You can only rename waypoints you created"));
	private static final SimpleCommandExceptionType NEEDS_POSITION = new SimpleCommandExceptionType(
			Component.literal("Give coordinates (x y z) when not running this as a player"));

	private static final SuggestionProvider<CommandSourceStack> CATEGORY_SUGGESTIONS =
			(context, builder) -> SharedSuggestionProvider.suggest(Category.ids(), builder);

	private final WaypointStore store;

	private WaypointCommand(WaypointStore store) {
		this.store = store;
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, WaypointStore store) {
		WaypointCommand command = new WaypointCommand(store);

		dispatcher.register(Commands.literal("waypoints")
				.requires(WaypointPermissions.requireView())
				.executes(context -> command.listAll(context.getSource()))

				.then(Commands.literal("categories")
						.executes(context -> command.listCategories(context.getSource())))

				.then(Commands.literal("info")
						.then(Commands.argument("name", StringArgumentType.string())
								.suggests(command.nameSuggestions((source, waypoint) -> true))
								.executes(context -> command.info(context.getSource(), getString(context, "name")))))

				.then(Commands.literal("xaero")
						.then(Commands.argument("name", StringArgumentType.string())
								.suggests(command.nameSuggestions((source, waypoint) -> true))
								.executes(context -> command.shareToXaero(context.getSource(), getString(context, "name")))))

				.then(Commands.literal("add")
						.requires(WaypointPermissions.requireAdd())
						.then(Commands.argument("name", StringArgumentType.string())
								.then(Commands.argument("category", StringArgumentType.word())
										.suggests(CATEGORY_SUGGESTIONS)
										// No coordinates: the executing entity's block position and dimension.
										.executes(context -> command.addHere(context))
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
						.then(Commands.argument("name", StringArgumentType.string())
								.suggests(command.nameSuggestions(WaypointPermissions::canRemove))
								.executes(context -> command.remove(context.getSource(), getString(context, "name")))))

				.then(Commands.literal("rename")
						.then(Commands.argument("old", StringArgumentType.string())
								.suggests(command.nameSuggestions(WaypointPermissions::canRename))
								.then(Commands.argument("new", StringArgumentType.string())
										.executes(context -> command.rename(context.getSource(),
												getString(context, "old"), getString(context, "new"))))))

				// Brigadier always prefers a matching literal (add, info, ...) over this argument,
				// so a category name can never shadow a subcommand.
				.then(Commands.argument("category", StringArgumentType.word())
						.suggests(CATEGORY_SUGGESTIONS)
						.executes(context -> command.listCategory(context.getSource(), getString(context, "category")))));
	}

	// ------------------------------------------------------------------ listing

	private int listAll(CommandSourceStack source) {
		List<Waypoint> all = store.all();
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

		source.sendSuccess(() -> WaypointText.header("Shared Waypoints (" + all.size() + ")"), false);
		Map<Category, Integer> counts = store.countsByCategory();
		Category current = null;
		for (Waypoint waypoint : all) {
			// The list is sorted by category, so a header goes in front of each new group.
			if (waypoint.category() != current) {
				current = waypoint.category();
				Category header = current;
				source.sendSuccess(() -> WaypointText.categoryHeader(header, counts.get(header)), false);
			}
			source.sendSuccess(() -> WaypointText.line(waypoint), false);
		}
		return all.size();
	}

	private int listCategory(CommandSourceStack source, String categoryId) throws CommandSyntaxException {
		Category category = Category.byId(categoryId).orElseThrow(() -> UNKNOWN_CATEGORY.create(categoryId));
		List<Waypoint> waypoints = store.inCategory(category);
		source.sendSuccess(() -> WaypointText.categoryHeader(category, waypoints.size()), false);
		if (waypoints.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No waypoints in this category yet.")
					.withStyle(ChatFormatting.GRAY), false);
		}
		for (Waypoint waypoint : waypoints) {
			source.sendSuccess(() -> WaypointText.line(waypoint), false);
		}
		return waypoints.size();
	}

	private int listCategories(CommandSourceStack source) {
		Map<Category, Integer> counts = store.countsByCategory();
		source.sendSuccess(() -> WaypointText.header("Categories"), false);
		for (Category category : Category.values()) {
			source.sendSuccess(() -> WaypointText.categorySummary(category, counts.get(category)), false);
		}
		return Category.values().length;
	}

	private int info(CommandSourceStack source, String name) throws CommandSyntaxException {
		Waypoint waypoint = find(name);
		for (Component line : WaypointText.info(waypoint)) {
			source.sendSuccess(() -> line, false);
		}
		return 1;
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

	// ---------------------------------------------------------------- changes

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
		String name = checkedName(getString(context, "name"));
		String categoryId = getString(context, "category");
		Category category = Category.byId(categoryId).orElseThrow(() -> UNKNOWN_CATEGORY.create(categoryId));
		if (store.contains(name)) {
			throw NAME_TAKEN.create(name);
		}

		ServerPlayer player = source.getPlayer();
		UUID creatorUuid = player != null ? player.getUUID() : Waypoint.SERVER_UUID;
		String creatorName = player != null ? player.getGameProfile().name() : source.getTextName();

		Waypoint waypoint = new Waypoint(name, category, pos.getX(), pos.getY(), pos.getZ(),
				Dimensions.id(level.dimension()), creatorUuid, creatorName,
				Instant.now().truncatedTo(ChronoUnit.SECONDS));
		store.add(waypoint);

		source.sendSuccess(() -> WaypointText.success("Added waypoint " + name), false);
		source.sendSuccess(() -> WaypointText.line(waypoint), false);
		warnIfUnsaved(source);
		return 1;
	}

	private int remove(CommandSourceStack source, String name) throws CommandSyntaxException {
		Waypoint waypoint = find(name);
		if (!WaypointPermissions.canRemove(source, waypoint)) {
			throw CANNOT_REMOVE.create();
		}
		store.remove(waypoint.name());
		source.sendSuccess(() -> WaypointText.success("Removed waypoint " + waypoint.name()), false);
		warnIfUnsaved(source);
		return 1;
	}

	private int rename(CommandSourceStack source, String oldName, String rawNewName) throws CommandSyntaxException {
		Waypoint waypoint = find(oldName);
		if (!WaypointPermissions.canRename(source, waypoint)) {
			throw CANNOT_RENAME.create();
		}
		String newName = checkedName(rawNewName);
		// A different waypoint already uses the name? (Changing only the case of its own name is fine.)
		if (store.contains(newName) && !newName.equalsIgnoreCase(waypoint.name())) {
			throw NAME_TAKEN.create(newName);
		}
		Waypoint renamed = store.rename(waypoint, newName);
		source.sendSuccess(() -> WaypointText.success("Renamed " + waypoint.name() + " to " + renamed.name()), false);
		warnIfUnsaved(source);
		return 1;
	}

	// ---------------------------------------------------------------- helpers

	private Waypoint find(String name) throws CommandSyntaxException {
		return store.get(name).orElseThrow(() -> UNKNOWN_WAYPOINT.create(name));
	}

	private static String checkedName(String raw) throws CommandSyntaxException {
		String name = raw.trim();
		Optional<String> problem = Waypoint.validateName(name);
		if (problem.isPresent()) {
			throw INVALID_NAME.create(problem.get());
		}
		return name;
	}

	private void warnIfUnsaved(CommandSourceStack source) {
		if (store.lastSaveFailed()) {
			source.sendSuccess(() -> WaypointText.warning(
					"Warning: could not write waypoints.json, the change is only in memory. See the server log."), false);
		}
	}

	/**
	 * Suggests waypoint names (quoted when needed) that pass {@code filter}, with coordinates as the tooltip.
	 * Used to show only removable/renamable waypoints for remove and rename.
	 */
	private SuggestionProvider<CommandSourceStack> nameSuggestions(BiPredicate<CommandSourceStack, Waypoint> filter) {
		return (context, builder) -> SharedSuggestionProvider.suggest(
				store.all().stream().filter(waypoint -> filter.test(context.getSource(), waypoint)).toList(),
				builder,
				waypoint -> StringArgumentType.escapeIfRequired(waypoint.name()),
				waypoint -> Component.literal(waypoint.coordinates() + " (" + Dimensions.shortName(waypoint.dimension()) + ")"));
	}

	private static String getString(CommandContext<CommandSourceStack> context, String argument) {
		return StringArgumentType.getString(context, argument);
	}
}
