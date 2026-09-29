package io.github.steelaspect.sharedwaypoints.text;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import io.github.steelaspect.sharedwaypoints.waypoint.Category;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * Builds the chat components the commands send. Only vanilla text, click and hover events are used, so every
 * client can show them.
 */
public final class WaypointText {
	private static final DateTimeFormatter DATE_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

	private WaypointText() {
	}

	/** Command that re-runs a name through Brigadier's quoting rules, e.g. {@code /waypoints info "Main Base"}. */
	public static String command(String subcommand, String name) {
		return "/waypoints " + subcommand + " " + StringArgumentType.escapeIfRequired(name);
	}

	/**
	 * One listing line:
	 * {@code [Category] Name — X Y Z (dimension) [Add to Xaero] [Copy coords]}
	 */
	public static Component line(Waypoint waypoint) {
		return categoryTag(waypoint.category())
				.append(" ")
				.append(name(waypoint))
				.append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal(waypoint.coordinates()).withStyle(ChatFormatting.GRAY))
				.append(Component.literal(" (" + Dimensions.shortName(waypoint.dimension()) + ")")
						.withStyle(ChatFormatting.DARK_GRAY))
				.append(" ")
				.append(addToXaeroButton(waypoint))
				.append(" ")
				.append(copyCoordsButton(waypoint));
	}

	/** Multi-line details for {@code /waypoints info}. */
	public static List<Component> info(Waypoint waypoint) {
		List<Component> lines = new ArrayList<>();
		lines.add(header("Waypoint: " + waypoint.name()));
		lines.add(field("Category", categoryTag(waypoint.category())));
		lines.add(field("Coordinates", Component.literal(waypoint.coordinates()).withStyle(ChatFormatting.WHITE)));
		lines.add(field("Dimension", Component.literal(Dimensions.shortName(waypoint.dimension()))
				.withStyle(style -> style
						.withColor(ChatFormatting.WHITE)
						.withHoverEvent(new HoverEvent.ShowText(Component.literal(waypoint.dimension()))))));
		lines.add(field("Created by", Component.literal(waypoint.creatorName())
				.withStyle(style -> style
						.withColor(ChatFormatting.WHITE)
						.withHoverEvent(new HoverEvent.ShowText(Component.literal(waypoint.creatorUuid().toString())))
						.withClickEvent(new ClickEvent.CopyToClipboard(waypoint.creatorUuid().toString())))));
		lines.add(field("Created on", Component.literal(DATE_FORMAT.format(waypoint.created()))
				.withStyle(ChatFormatting.WHITE)));
		lines.add(Component.literal("  ").append(addToXaeroButton(waypoint)).append(" ").append(copyCoordsButton(waypoint)));
		return lines;
	}

	/** Category header used when grouping the full list, e.g. {@code — Storage (3) —}. */
	public static Component categoryHeader(Category category, int count) {
		return Component.literal("— " + category.displayName() + " (" + count + ") —")
				.withStyle(style -> style
						.withColor(category.color())
						.withBold(true)
						.withClickEvent(new ClickEvent.RunCommand("/waypoints " + category.id()))
						.withHoverEvent(new HoverEvent.ShowText(
								Component.literal("Show only " + category.displayName().toLowerCase()))));
	}

	/** One line of {@code /waypoints categories}. */
	public static Component categorySummary(Category category, int count) {
		return categoryTag(category)
				.append(Component.literal(" " + category.id()).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" — " + count + (count == 1 ? " waypoint" : " waypoints"))
						.withStyle(ChatFormatting.GRAY))
				.append(Component.literal(" · Xaero colour: " + category.xaeroColorName()
						+ " (" + category.xaeroColorIndex() + ")").withStyle(ChatFormatting.DARK_GRAY));
	}

	/** Coloured, clickable {@code [Category]} tag. Clicking lists that category. */
	public static MutableComponent categoryTag(Category category) {
		return Component.literal("[" + category.displayName() + "]")
				.withStyle(style -> style
						.withColor(category.color())
						.withClickEvent(new ClickEvent.RunCommand("/waypoints " + category.id()))
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

	/** Waypoint name; hover shows where it is, click opens {@code /waypoints info}. */
	private static MutableComponent name(Waypoint waypoint) {
		return Component.literal(waypoint.name())
				.withStyle(style -> style
						.withColor(ChatFormatting.WHITE)
						.withClickEvent(new ClickEvent.RunCommand(command("info", waypoint.name())))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click for details\n")
								.append(Component.literal("Added by " + waypoint.creatorName())
										.withStyle(ChatFormatting.GRAY)))));
	}

	/**
	 * {@code [Add to Xaero]}: runs {@code /waypoints xaero <name>}, which makes the server send the
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
	private static MutableComponent copyCoordsButton(Waypoint waypoint) {
		return Component.literal("[Copy coords]")
				.withStyle(style -> style
						.withColor(ChatFormatting.GRAY)
						.withClickEvent(new ClickEvent.CopyToClipboard(waypoint.coordinates()))
						.withHoverEvent(new HoverEvent.ShowText(
								Component.literal("Copy \"" + waypoint.coordinates() + "\" to clipboard"))));
	}

	private static Component field(String label, Component value) {
		return Component.literal("  " + label + ": ").withStyle(ChatFormatting.GRAY).append(value);
	}
}
