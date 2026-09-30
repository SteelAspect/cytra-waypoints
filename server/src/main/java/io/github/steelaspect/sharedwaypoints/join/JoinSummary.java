package io.github.steelaspect.sharedwaypoints.join;

import io.github.steelaspect.sharedwaypoints.ModContext;
import io.github.steelaspect.sharedwaypoints.permission.WaypointPermissions;
import io.github.steelaspect.sharedwaypoints.text.Viewer;
import io.github.steelaspect.sharedwaypoints.text.WaypointText;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * "3 new waypoints since you last played", shown on join with the usual [Add to Xaero] / [Copy coords] / [Go]
 * buttons, for everyone (no client mod needed). Last-seen times are kept per player in {@code waypoints.json}.
 */
public final class JoinSummary {
	/** Longer summaries end with a button to the full list instead. */
	static final int MAX_LINES = 8;

	private final ModContext mod;

	public JoinSummary(ModContext mod) {
		this.mod = mod;
	}

	/** A player joined: show what's new, then remember that they're online. */
	public void onJoin(ServerPlayer player) {
		UUID id = player.getUUID();
		Optional<Instant> previous = mod.waypoints().lastSeen(id);
		mod.waypoints().markSeen(id, Instant.now());
		if (mod.config().joinSummary && WaypointPermissions.canView(player)) {
			lines(previous, Viewer.of(player, mod.favorites())).forEach(player::sendSystemMessage);
		}
	}

	/** A player left: live announcements covered everything until now. */
	public void onLeave(UUID player) {
		mod.waypoints().markSeen(player, Instant.now());
	}

	/**
	 * The summary for a player last seen at {@code previous} (empty: never). Nothing when nothing is new. Public so
	 * the GameTest can check it without a real join.
	 */
	public List<Component> lines(Optional<Instant> previous, Viewer viewer) {
		List<Component> lines = new ArrayList<>();
		if (previous.isEmpty()) {
			// First visit: no "new" list, just point at the waypoints if there are any.
			int total = mod.waypoints().size();
			if (total > 0) {
				lines.add(Component.literal("✦ This server shares " + total + (total == 1 ? " waypoint. " : " waypoints. "))
						.withStyle(ChatFormatting.GOLD)
						.append(showAllButton()));
			}
			return lines;
		}
		List<Waypoint> fresh = mod.waypoints().addedSince(previous.get());
		if (fresh.isEmpty()) {
			return lines;
		}
		lines.add(Component.literal("✦ " + fresh.size() + (fresh.size() == 1 ? " new waypoint" : " new waypoints")
				+ " since you last played:").withStyle(ChatFormatting.GOLD));
		fresh.stream().limit(MAX_LINES).forEach(waypoint -> lines.add(WaypointText.line(waypoint, viewer)));
		if (fresh.size() > MAX_LINES) {
			lines.add(WaypointText.muted("…and " + (fresh.size() - MAX_LINES) + " more. ").copy().append(showAllButton()));
		}
		return lines;
	}

	private static Component showAllButton() {
		return Component.literal("[Show all]").withStyle(style -> style
				.withColor(ChatFormatting.YELLOW)
				.withClickEvent(new ClickEvent.RunCommand("/waypoints"))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("List every shared waypoint"))));
	}
}
