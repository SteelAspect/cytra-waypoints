package io.github.steelaspect.sharedwaypoints.text;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.steelaspect.sharedwaypoints.nav.NavMath;
import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import io.github.steelaspect.sharedwaypoints.util.Page;
import io.github.steelaspect.sharedwaypoints.waypoint.Category;
import io.github.steelaspect.sharedwaypoints.waypoint.Route;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * Builds the chat components the commands send. Only vanilla text, click and hover events are used, so every
 * client can show them. Most lines are personalised for a {@link Viewer} (distance, favourite star, buttons).
 */
public final class WaypointText {
	private static final DateTimeFormatter DATE_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);
	private static final String STAR = "★";

	private WaypointText() {
	}

	/** Command that re-runs a name through Brigadier's quoting rules, e.g. {@code /cway info "Main Base"}. */
	public static String command(String subcommand, String name) {
		return "/cway " + subcommand + " " + StringArgumentType.escapeIfRequired(name);
	}

	// ------------------------------------------------------------------ lines

	/**
	 * One listing line:
	 * {@code [Category] Name — X Y Z (dimension) [Add to Xaero] [Copy coords] [Go]}
	 * with a gold star after the name for the viewer's favourites and a hover card on the name.
	 */
	public static MutableComponent line(Waypoint waypoint, Viewer viewer) {
		MutableComponent line = categoryTag(waypoint.category())
				.append(" ")
				.append(name(waypoint, viewer));
		if (viewer.isFavorite(waypoint)) {
			line.append(Component.literal(" " + STAR).withStyle(ChatFormatting.GOLD));
		}
		line.append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal(waypoint.coordinates()).withStyle(ChatFormatting.GRAY))
				.append(Component.literal(" (" + Dimensions.shortName(waypoint.dimension()) + ")")
						.withStyle(ChatFormatting.DARK_GRAY))
				.append(" ")
				.append(addToXaeroButton(waypoint))
				.append(" ")
				.append(copyCoordsButton(waypoint.coordinates()));
		if (viewer.isPlayer()) {
			line.append(" ").append(goButton(waypoint));
		}
		return line;
	}

	/** A listing line prefixed with distance and compass direction, for /cway near and nearest. */
	public static MutableComponent distanceLine(Waypoint waypoint, Viewer viewer) {
		MutableComponent prefix = Component.empty();
		viewer.target(waypoint).ifPresent(target -> {
			double distance = target.horizontalDistance(viewer.position().x, viewer.position().z);
			String direction = NavMath.compass(viewer.position().x, viewer.position().z, target.x() + 0.5, target.z() + 0.5);
			prefix.append(Component.literal(NavMath.formatDistance(distance) + " " + direction + " ")
					.withStyle(ChatFormatting.AQUA));
			if (target.viaPortal()) {
				prefix.append(Component.literal("⟳ ").withStyle(style -> style
						.withColor(ChatFormatting.LIGHT_PURPLE)
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Other side of a Nether portal: "
								+ target.x() + " " + target.y() + " " + target.z())))));
			}
		});
		return prefix.append(line(waypoint, viewer));
	}

	/** Multi-line details for {@code /cway info}. */
	public static List<Component> info(Waypoint waypoint, Viewer viewer, boolean canEdit) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal("=== ").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal(waypoint.name()).withStyle(waypoint.category().color(), ChatFormatting.BOLD))
				.append(Component.literal(viewer.isFavorite(waypoint) ? " " + STAR : "").withStyle(ChatFormatting.GOLD))
				.append(Component.literal(" ===").withStyle(ChatFormatting.YELLOW)));
		waypoint.descriptionText().ifPresent(description ->
				lines.add(Component.literal("  “" + description + "”").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
		lines.add(field("Category", categoryTag(waypoint.category())));
		lines.add(field("Coordinates", Component.literal(waypoint.coordinates()).withStyle(ChatFormatting.WHITE)
				.append(" ").append(copyCoordsButton(waypoint.coordinates()))));
		lines.add(field("Dimension", Component.literal(Dimensions.shortName(waypoint.dimension()))
				.withStyle(style -> style
						.withColor(ChatFormatting.WHITE)
						.withHoverEvent(new HoverEvent.ShowText(Component.literal(waypoint.dimension()))))));
		// The other side at the reader's own height ("~" for the console: copied into /tp it means "stay level").
		Integer readerY = viewer.position() == null ? null : (int) Math.floor(viewer.position().y);
		NavMath.portalEquivalent(waypoint, readerY == null ? 0 : readerY).ifPresent(other -> {
			String label = waypoint.dimension().equals(Dimensions.OVERWORLD) ? "Nether side" : "Overworld side";
			String coordinates = other.x() + " " + (readerY == null ? "~" : String.valueOf(readerY)) + " " + other.z();
			lines.add(field(label, Component.literal(coordinates).withStyle(ChatFormatting.LIGHT_PURPLE)
					.append(" ").append(copyCoordsButton(coordinates))));
		});
		distanceText(waypoint, viewer).ifPresent(distance -> lines.add(field("Distance", distance)));
		lines.add(field("Created by", Component.literal(waypoint.creatorName())
				.withStyle(style -> style
						.withColor(ChatFormatting.WHITE)
						.withHoverEvent(new HoverEvent.ShowText(Component.literal(waypoint.creatorUuid().toString())))
						.withClickEvent(new ClickEvent.CopyToClipboard(waypoint.creatorUuid().toString())))));
		lines.add(field("Created on", Component.literal(DATE_FORMAT.format(waypoint.created()))
				.withStyle(ChatFormatting.WHITE)
				.append(Component.literal(" (" + Formats.relativeAge(waypoint.created(), Instant.now()) + ")")
						.withStyle(ChatFormatting.DARK_GRAY))));

		MutableComponent buttons = Component.literal("  ");
		if (viewer.isPlayer()) {
			buttons.append(goButton(waypoint)).append(" ");
		}
		buttons.append(addToXaeroButton(waypoint));
		if (viewer.isPlayer()) {
			buttons.append(" ").append(favoriteButton(waypoint, viewer.isFavorite(waypoint)));
		}
		if (viewer.canTeleport()) {
			buttons.append(" ").append(button("[Teleport]", ChatFormatting.LIGHT_PURPLE,
					new ClickEvent.RunCommand(command("tp", waypoint.name())), "Teleport there (op)"));
		}
		if (canEdit) {
			buttons.append(" ").append(button("[Describe]", ChatFormatting.GRAY,
					new ClickEvent.SuggestCommand(command("describe", waypoint.name()) + " "),
					"Write a short note for this waypoint"));
		}
		lines.add(buttons);
		return lines;
	}

	/** Pushed to everyone online when a waypoint is added. */
	public static Component announcement(String creator, Waypoint waypoint, Viewer viewer) {
		return Component.literal("✦ ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(creator).withStyle(ChatFormatting.YELLOW))
				.append(Component.literal(" shared a new waypoint:").withStyle(ChatFormatting.GRAY))
				.append("\n")
				.append(line(waypoint, viewer));
	}

	/** Chat confirmation when navigation starts. */
	public static Component navigationStarted(Waypoint waypoint) {
		return Component.literal("➜ Navigating to ").withStyle(ChatFormatting.GREEN)
				.append(Component.literal(waypoint.name()).withStyle(waypoint.category().color()))
				.append(Component.literal(" — follow the compass at the top of your screen. ").withStyle(ChatFormatting.GRAY))
				.append(button("[Stop]", ChatFormatting.RED, new ClickEvent.RunCommand("/cway stop"), "Stop navigating"));
	}

	// ----------------------------------------------------------------- routes

	/** Command for a route subcommand, e.g. {@code /cway route go "Nether Tour"}. */
	public static String routeCommand(String subcommand, String routeName) {
		return "/cway route " + subcommand + " " + StringArgumentType.escapeIfRequired(routeName);
	}

	/** One line of the route list: {@code [Route] Nether Tour — 5 stops · 1.2km [Go] [Info]}. */
	public static MutableComponent routeLine(Route route, List<Waypoint> stops, boolean isPlayer) {
		MutableComponent hover = Component.literal(route.name()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		route.descriptionText().ifPresent(description -> hover.append(
				Component.literal("\n“" + description + "”").withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC)));
		for (int i = 0; i < stops.size(); i++) {
			Waypoint stop = stops.get(i);
			hover.append(Component.literal("\n" + (i + 1) + ". ").withStyle(ChatFormatting.GRAY))
					.append(Component.literal(stop.name()).withStyle(stop.category().color()));
		}
		hover.append(Component.literal("\nCreated by " + route.creatorName()).withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal("\nClick for details").withStyle(ChatFormatting.YELLOW));

		MutableComponent line = Component.literal("[Route] ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(route.name()).withStyle(style -> style
						.withColor(ChatFormatting.WHITE)
						.withClickEvent(new ClickEvent.RunCommand(routeCommand("info", route.name())))
						.withHoverEvent(new HoverEvent.ShowText(hover))))
				.append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal(routeSummary(stops)).withStyle(ChatFormatting.GRAY));
		if (isPlayer && !stops.isEmpty()) {
			line.append(" ").append(button("[Go]", ChatFormatting.GREEN,
					new ClickEvent.RunCommand(routeCommand("go", route.name())), "Follow this route stop by stop"));
		}
		return line.append(" ").append(button("[Info]", ChatFormatting.AQUA,
				new ClickEvent.RunCommand(routeCommand("info", route.name())), "Show every stop"));
	}

	/** "5 stops · 1.2km", "1 stop", "no stops yet". */
	public static String routeSummary(List<Waypoint> stops) {
		if (stops.isEmpty()) {
			return "no stops yet";
		}
		String count = stops.size() + (stops.size() == 1 ? " stop" : " stops");
		return stops.size() < 2 ? count : count + " · " + NavMath.formatDistance(NavMath.routeLength(stops));
	}

	/**
	 * Multi-line details for {@code /cway route info}: the numbered stops, with editing buttons for players
	 * who may change the route.
	 */
	public static List<Component> routeInfo(Route route, List<Waypoint> stops, Viewer viewer, boolean canEdit,
			boolean canRemove) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal("=== ").withStyle(ChatFormatting.YELLOW)
				.append(Component.literal("Route: " + route.name()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
				.append(Component.literal(" ===").withStyle(ChatFormatting.YELLOW)));
		route.descriptionText().ifPresent(description ->
				lines.add(Component.literal("  “" + description + "”").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
		lines.add(Component.literal("  " + routeSummary(stops) + " · created by " + route.creatorName() + ", "
				+ Formats.relativeAge(route.created(), Instant.now())).withStyle(ChatFormatting.GRAY));
		if (stops.isEmpty()) {
			lines.add(muted("  Add stops with /cway route add " + StringArgumentType.escapeIfRequired(route.name())
					+ " <waypoint>"));
		}
		for (int i = 0; i < stops.size(); i++) {
			Waypoint stop = stops.get(i);
			int number = i + 1;
			MutableComponent line = Component.literal("  " + number + ". ").withStyle(ChatFormatting.GRAY)
					.append(categoryTag(stop.category()))
					.append(" ")
					.append(name(stop, viewer))
					.append(Component.literal(" — " + stop.coordinates() + " (" + Dimensions.shortName(stop.dimension()) + ")")
							.withStyle(ChatFormatting.DARK_GRAY));
			if (viewer.isPlayer()) {
				line.append(" ").append(button("[Go from here]", ChatFormatting.GREEN,
						new ClickEvent.RunCommand(routeCommand("go", route.name()) + " " + number),
						"Follow the route starting at stop " + number));
			}
			if (canEdit) {
				if (i > 0) {
					line.append(" ").append(button("[↑]", ChatFormatting.GRAY,
							new ClickEvent.RunCommand(routeCommand("move", route.name()) + " " + number + " " + (number - 1)),
							"Move this stop up"));
				}
				line.append(" ").append(button("[✕]", ChatFormatting.RED,
						new ClickEvent.RunCommand(routeCommand("drop", route.name()) + " " + number),
						"Remove this stop from the route (the waypoint stays)"));
			}
			lines.add(line);
		}

		MutableComponent buttons = Component.literal("  ");
		if (viewer.isPlayer() && !stops.isEmpty()) {
			buttons.append(button("[Go]", ChatFormatting.GREEN,
					new ClickEvent.RunCommand(routeCommand("go", route.name())), "Follow this route stop by stop")).append(" ");
		}
		if (canEdit) {
			buttons.append(button("[+ Stop]", ChatFormatting.YELLOW,
					new ClickEvent.SuggestCommand(routeCommand("add", route.name()) + " "),
					"Add a waypoint as the last stop")).append(" ");
			buttons.append(button("[Describe]", ChatFormatting.GRAY,
					new ClickEvent.SuggestCommand(routeCommand("describe", route.name()) + " "),
					"Write a short note for this route")).append(" ");
		}
		if (canRemove) {
			buttons.append(button("[Delete]", ChatFormatting.RED,
					new ClickEvent.SuggestCommand(routeCommand("delete", route.name())),
					"Delete this route (its waypoints stay). Press Enter to confirm."));
		}
		lines.add(buttons);
		return lines;
	}

	/** Chat confirmation when a route is started. */
	public static Component routeStarted(Route route, int stopIndex, Waypoint firstStop) {
		return Component.literal("➜ Following route ").withStyle(ChatFormatting.GREEN)
				.append(Component.literal(route.name()).withStyle(ChatFormatting.GOLD))
				.append(Component.literal(" — stop " + (stopIndex + 1) + "/" + route.stops().size() + ": ")
						.withStyle(ChatFormatting.GRAY))
				.append(Component.literal(firstStop.name()).withStyle(firstStop.category().color()))
				.append(" ")
				.append(button("[Skip stop]", ChatFormatting.YELLOW, new ClickEvent.RunCommand("/cway route skip"),
						"Go straight to the next stop"))
				.append(" ")
				.append(button("[Stop]", ChatFormatting.RED, new ClickEvent.RunCommand("/cway stop"), "Stop navigating"));
	}

	// ---------------------------------------------------------------- headers

	/** Category header used when grouping lists, e.g. {@code — Storage (3) —}. */
	public static Component categoryHeader(Category category, int count) {
		return Component.literal("— " + category.displayName() + " (" + count + ") —")
				.withStyle(style -> style
						.withColor(category.color())
						.withBold(true)
						.withClickEvent(new ClickEvent.RunCommand("/cway " + category.id()))
						.withHoverEvent(new HoverEvent.ShowText(
								Component.literal("Show only " + category.displayName().toLowerCase()))));
	}

	/** One line of {@code /cway categories}. */
	public static Component categorySummary(Category category, int count) {
		return categoryTag(category)
				.append(Component.literal(" " + category.id()).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" — " + count + (count == 1 ? " waypoint" : " waypoints"))
						.withStyle(ChatFormatting.GRAY))
				.append(Component.literal(" · Xaero colour: " + category.xaeroColorName()
						+ " (" + category.xaeroColorIndex() + ")").withStyle(ChatFormatting.DARK_GRAY));
	}

	/**
	 * {@code « Prev   Page 2/5   Next »}. {@code commandPrefix} is the listing command without the page number,
	 * e.g. {@code /cway page} or {@code /cway storage}.
	 */
	public static Component pageFooter(Page<?> page, String commandPrefix) {
		MutableComponent footer = Component.empty();
		footer.append(page.hasPrevious()
				? button("« Prev", ChatFormatting.YELLOW, new ClickEvent.RunCommand(commandPrefix + " " + (page.number() - 1)), "Previous page")
				: Component.literal("« Prev").withStyle(ChatFormatting.DARK_GRAY));
		footer.append(Component.literal("   Page " + page.number() + "/" + page.count() + "   ").withStyle(ChatFormatting.GRAY));
		footer.append(page.hasNext()
				? button("Next »", ChatFormatting.YELLOW, new ClickEvent.RunCommand(commandPrefix + " " + (page.number() + 1)), "Next page")
				: Component.literal("Next »").withStyle(ChatFormatting.DARK_GRAY));
		return footer;
	}

	/** Coloured, clickable {@code [Category]} tag. Clicking lists that category. */
	public static MutableComponent categoryTag(Category category) {
		return Component.literal("[" + category.displayName() + "]")
				.withStyle(style -> style
						.withColor(category.color())
						.withClickEvent(new ClickEvent.RunCommand("/cway " + category.id()))
						.withHoverEvent(new HoverEvent.ShowText(
								Component.literal("Click to list " + category.displayName().toLowerCase()))));
	}

	public static Component header(String title) {
		return Component.literal("=== " + title + " ===").withStyle(ChatFormatting.YELLOW);
	}

	public static Component success(String message) {
		return Component.literal(message).withStyle(ChatFormatting.GREEN);
	}

	public static Component warning(String message) {
		return Component.literal(message).withStyle(ChatFormatting.GOLD);
	}

	public static Component muted(String message) {
		return Component.literal(message).withStyle(ChatFormatting.GRAY);
	}

	// ---------------------------------------------------------------- pieces

	/** Waypoint name with a hover card; click opens {@code /cway info}. */
	private static MutableComponent name(Waypoint waypoint, Viewer viewer) {
		return Component.literal(waypoint.name())
				.withStyle(style -> style
						.withColor(ChatFormatting.WHITE)
						.withClickEvent(new ClickEvent.RunCommand(command("info", waypoint.name())))
						.withHoverEvent(new HoverEvent.ShowText(hoverCard(waypoint, viewer))));
	}

	/** The card shown when hovering a waypoint name. */
	static Component hoverCard(Waypoint waypoint, Viewer viewer) {
		MutableComponent card = Component.literal(waypoint.name()).withStyle(waypoint.category().color(), ChatFormatting.BOLD);
		if (viewer.isFavorite(waypoint)) {
			card.append(Component.literal(" " + STAR).withStyle(ChatFormatting.GOLD));
		}
		card.append(Component.literal("\n" + waypoint.category().displayName() + " · "
				+ Dimensions.shortName(waypoint.dimension()) + " · " + waypoint.coordinates()).withStyle(ChatFormatting.GRAY));
		distanceText(waypoint, viewer).ifPresent(distance -> card.append("\n").append(distance));
		waypoint.descriptionText().ifPresent(description -> card.append(
				Component.literal("\n“" + description + "”").withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC)));
		card.append(Component.literal("\nAdded by " + waypoint.creatorName() + " · "
				+ Formats.relativeAge(waypoint.created(), Instant.now())).withStyle(ChatFormatting.DARK_GRAY));
		card.append(Component.literal("\nClick for details").withStyle(ChatFormatting.YELLOW));
		return card;
	}

	/** "340m NE", "1.2km SW via Nether portal" or "in the_end", for hover cards and info. */
	private static Optional<Component> distanceText(Waypoint waypoint, Viewer viewer) {
		if (viewer.position() == null) {
			return Optional.empty();
		}
		return Optional.of(viewer.target(waypoint)
				.<Component>map(target -> {
					double distance = target.horizontalDistance(viewer.position().x, viewer.position().z);
					String direction = NavMath.compass(viewer.position().x, viewer.position().z, target.x() + 0.5, target.z() + 0.5);
					return Component.literal(NavMath.formatDistance(distance) + " " + direction
							+ (target.viaPortal() ? " via Nether portal" : " from you")).withStyle(ChatFormatting.AQUA);
				})
				.orElseGet(() -> Component.literal("In " + Dimensions.shortName(waypoint.dimension()))
						.withStyle(ChatFormatting.AQUA)));
	}

	/**
	 * {@code [Add to Xaero]}: runs {@code /cway xaero <name>}, which makes the server send the
	 * {@code xaero-waypoint:...} share line as a system message. Xaero's Minimap swaps that line for its own
	 * "shared a waypoint" message with an [Add] button.
	 */
	private static MutableComponent addToXaeroButton(Waypoint waypoint) {
		return Component.literal("[Add to Xaero]")
				.withStyle(style -> style
						.withColor(ChatFormatting.YELLOW)
						.withClickEvent(new ClickEvent.RunCommand(command("xaero", waypoint.name())))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Share to Xaero's Minimap\n")
								.append(Component.literal("Xaero then shows its own [Add] button.\n"
										+ "Needs Xaero's Minimap on your client.").withStyle(ChatFormatting.GRAY)))));
	}

	/** {@code [Copy coords]}: copies {@code "X Y Z"} to the clipboard. */
	private static MutableComponent copyCoordsButton(String coordinates) {
		return button("[Copy coords]", ChatFormatting.GRAY, new ClickEvent.CopyToClipboard(coordinates),
				"Copy \"" + coordinates + "\" to clipboard");
	}

	/** {@code [Go]}: starts boss-bar navigation. */
	private static MutableComponent goButton(Waypoint waypoint) {
		return button("[Go]", ChatFormatting.GREEN, new ClickEvent.RunCommand(command("go", waypoint.name())),
				"Navigate here: live compass + beacon");
	}

	private static MutableComponent favoriteButton(Waypoint waypoint, boolean favorite) {
		return button(favorite ? "[" + STAR + " Unfavourite]" : "[☆ Favourite]", ChatFormatting.GOLD,
				new ClickEvent.RunCommand(command("favorite", waypoint.name())),
				favorite ? "Remove from your favourites" : "Add to your favourites");
	}

	private static MutableComponent button(String label, ChatFormatting color, ClickEvent click, String hover) {
		return Component.literal(label).withStyle(style -> style
				.withColor(color)
				.withClickEvent(click)
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
	}

	private static Component field(String label, Component value) {
		return Component.literal("  " + label + ": ").withStyle(ChatFormatting.GRAY).append(value);
	}
}
