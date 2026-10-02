package io.github.steelaspect.sharedwaypoints.join;

import io.github.steelaspect.sharedwaypoints.ModContext;
import io.github.steelaspect.sharedwaypoints.protocol.WelcomePayload;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells players how to get the automatic Xaero's Minimap sync, once, on their first join, and any time with
 * {@code /cway sync}:
 * <ul>
 * <li>without SharedWaypoints on their game: install it, the same jar as the server ({@link #tip()}, right away);</li>
 * <li>with it, but nothing synced a few seconds later: Xaero's Minimap is missing or a version the mod can't
 *     use ({@link #xaeroTip()}), or their SharedWaypoints is a different version ({@link #updateTip()}).</li>
 * </ul>
 * Players who still have the old (2.2 and earlier) sharedwaypoints-client jar count as having the mod: it speaks the
 * same sync protocol.
 */
public final class ClientModTip {
	/** How long the client gets to say hello before the follow-up tip (5 seconds). */
	static final int FOLLOW_UP_TICKS = 100;
	private static final URI XAERO_PAGE = URI.create("https://modrinth.com/mod/xaeros-minimap");

	/** The Xaero's Minimap version the sync was tested with (see the README). */
	static final String TESTED_XAERO = "26.5.0";

	private final ModContext mod;
	/** Players with the mod on their game, on their first visit, and the server tick at which to check on them. */
	private final Map<UUID, Integer> followUps = new HashMap<>();

	public ClientModTip(ModContext mod) {
		this.mod = mod;
	}

	/**
	 * Whether this player's game runs SharedWaypoints (2.3.0 or newer, or the old sharedwaypoints-client 2.0.1 and
	 * newer): it listens on the sync channel, which Fabric tells the server about before the player joins.
	 */
	public static boolean hasClientMod(ServerPlayer player) {
		return player.connection != null && ServerPlayNetworking.canSend(player, WelcomePayload.TYPE);
	}

	/** The install tip is for first visits of players without the mod on their game, on servers that sync. */
	public boolean shouldTip(ServerPlayer player, boolean firstVisit) {
		return firstVisit && enabled() && !hasClientMod(player);
	}

	/** A player's first visit: tip them now, or check on them in a few seconds if they have the mod. */
	public void onFirstVisit(ServerPlayer player, int serverTick) {
		if (shouldTip(player, true)) {
			player.sendSystemMessage(tip());
		} else if (enabled() && hasClientMod(player)) {
			followUps.put(player.getUUID(), serverTick + FOLLOW_UP_TICKS);
		}
	}

	/** Called every server tick: sends the follow-up tips that are due. */
	public void tick(MinecraftServer server) {
		if (followUps.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		var due = followUps.entrySet().iterator();
		while (due.hasNext()) {
			var entry = due.next();
			if (now >= entry.getValue()) {
				due.remove();
				ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
				if (player != null) {
					followUp(player).ifPresent(player::sendSystemMessage);
				}
			}
		}
	}

	/** The player left before their follow-up. */
	public void forget(UUID player) {
		followUps.remove(player);
	}

	/** For a player with the mod: nothing if they're synced, otherwise what's in the way. */
	public Optional<Component> followUp(ServerPlayer player) {
		if (!enabled() || mod.sync().isSubscribed(player.getUUID())) {
			return Optional.empty();
		}
		return Optional.of(mod.sync().hasSaidHello(player.getUUID()) ? updateTip() : xaeroTip());
	}

	private boolean enabled() {
		return mod.config().clientModTip && mod.config().syncToClientMod;
	}

	/** The join tip: one line with [Download] and [How it works]. */
	public Component tip() {
		MutableComponent line = Component.literal("✦ Want these waypoints in Xaero's Minimap automatically? ")
				.withStyle(ChatFormatting.GOLD)
				.append(Component.literal("Install SharedWaypoints on your game too, the same jar as the server. ")
						.withStyle(ChatFormatting.GRAY));
		downloadButton().ifPresent(button -> line.append(button).append(" "));
		return line.append(howItWorksButton());
	}

	private static Component howItWorksButton() {
		return Component.literal("[How it works]").withStyle(style -> style
				.withColor(ChatFormatting.YELLOW)
				.withClickEvent(new ClickEvent.RunCommand("/cway sync"))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Show the steps (/cway sync)"))));
	}

	private static Component xaeroButton() {
		return Component.literal("[Get Xaero's Minimap]").withStyle(style -> style
				.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent.OpenUrl(XAERO_PAGE))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(XAERO_PAGE.toString()))));
	}

	/** The mod is on their game but Xaero's Minimap isn't (or isn't a version it can use). */
	public Component xaeroTip() {
		return Component.literal("✦ Your SharedWaypoints can't reach Xaero's Minimap. ")
				.withStyle(ChatFormatting.GOLD)
				.append(Component.literal("Install Xaero's Minimap (tested with " + TESTED_XAERO + ") to get these waypoints "
						+ "in it automatically. ").withStyle(ChatFormatting.GRAY))
				.append(xaeroButton()).append(" ").append(howItWorksButton());
	}

	/** Their SharedWaypoints speaks a different sync protocol than this server. */
	public Component updateTip() {
		MutableComponent line = Component.literal("✦ Your SharedWaypoints doesn't match this server, ")
				.withStyle(ChatFormatting.GOLD)
				.append(Component.literal("so Xaero's Minimap isn't synced. Update it to " + jarName() + ". ")
						.withStyle(ChatFormatting.GRAY));
		downloadButton().ifPresent(button -> line.append(button).append(" "));
		return line.append(howItWorksButton());
	}

	/** {@code /cway sync}: whether it's on for you, and how to set it up. */
	public List<Component> steps(ServerPlayer player) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal("Automatic Xaero's Minimap sync").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		if (!mod.config().syncToClientMod) {
			lines.add(Component.literal("This server has the sync turned off. The [Add to Xaero] buttons in chat still work.")
					.withStyle(ChatFormatting.GRAY));
			return lines;
		}
		if (player != null && mod.sync().isSubscribed(player.getUUID())) {
			lines.add(Component.literal("✔ You have it: your Xaero's Minimap is in sync with this server. ")
					.withStyle(ChatFormatting.GREEN)
					.append(Component.literal("The waypoints are in Xaero's \"Shared\" waypoint set: pick that set in "
							+ "Xaero's waypoint menu, or turn on \"Render All WP Sets\" in Xaero's settings.")
							.withStyle(ChatFormatting.GRAY)));
			return lines;
		}
		if (player != null && mod.sync().hasSaidHello(player.getUUID())) {
			lines.add(Component.literal("Your SharedWaypoints doesn't match this server. Update it to "
					+ jarName() + ".").withStyle(ChatFormatting.YELLOW));
		} else if (player != null && hasClientMod(player)) {
			lines.add(Component.literal("You have SharedWaypoints, but it can't reach Xaero's Minimap: install "
					+ "Xaero's Minimap (tested with " + TESTED_XAERO + ").").withStyle(ChatFormatting.YELLOW));
		}
		lines.add(Component.literal("The server's shared waypoints can appear in Xaero's Minimap by themselves, in their "
				+ "own \"Shared\" waypoint set, and stay up to date. Your own waypoints are never touched.")
				.withStyle(ChatFormatting.GRAY));
		lines.add(step(1, "Put " + jarName() + " (the same jar as the server) in your .minecraft/mods folder. "
				+ "It replaces the old sharedwaypoints-client jar."));
		lines.add(step(2, "Also install Xaero's Minimap and Fabric API (Fabric, Minecraft "
				+ SharedConstants.getCurrentVersion().name() + ")."));
		lines.add(step(3, "Rejoin. The waypoints appear in Xaero's \"Shared\" set. You also get the waypoint menu (J)."));
		lines.add(downloadButton().<Component>map(button -> Component.empty().append(button))
				.orElse(Component.literal("Ask a server admin for the file.").withStyle(ChatFormatting.GRAY)));
		return lines;
	}

	private static Component step(int number, String text) {
		return Component.literal(" " + number + ". ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(text).withStyle(ChatFormatting.WHITE));
	}

	/** [Download], opening {@code clientModUrl}; nothing if it's empty or not a web link. */
	private Optional<Component> downloadButton() {
		return link(mod.config().clientModUrl).map(uri -> Component.literal("[Download]").withStyle(style -> style
				.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent.OpenUrl(uri))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Get " + jarName() + "\n" + uri)))));
	}

	static Optional<URI> link(String url) {
		if (url == null || url.isBlank()) {
			return Optional.empty();
		}
		try {
			URI uri = new URI(url.trim());
			String scheme = uri.getScheme();
			return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)
					? Optional.of(uri) : Optional.empty();
		} catch (java.net.URISyntaxException e) {
			return Optional.empty();
		}
	}

	/** The jar that matches this server, e.g. sharedwaypoints-2.3.0.jar (the same jar on the server and on players' games). */
	static String jarName() {
		String version = FabricLoader.getInstance().getModContainer("sharedwaypoints")
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("");
		int plus = version.indexOf('+');
		if (plus >= 0) {
			version = version.substring(0, plus);
		}
		return version.isEmpty() ? "sharedwaypoints.jar" : "sharedwaypoints-" + version + ".jar";
	}
}
