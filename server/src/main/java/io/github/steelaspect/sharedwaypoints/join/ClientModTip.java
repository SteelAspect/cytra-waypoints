package io.github.steelaspect.sharedwaypoints.join;

import io.github.steelaspect.sharedwaypoints.ModContext;
import io.github.steelaspect.sharedwaypoints.protocol.WelcomePayload;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tells players how to get the automatic Xaero's Minimap sync: a short tip on their first join (only for players
 * who don't have sharedwaypoints-client yet) and the full steps in {@code /waypoints sync}.
 */
public final class ClientModTip {
	private final ModContext mod;

	public ClientModTip(ModContext mod) {
		this.mod = mod;
	}

	/**
	 * Whether this player's game runs sharedwaypoints-client with Xaero's Minimap: only then does it listen on the
	 * sync channel, which Fabric tells the server about before the player joins.
	 */
	public static boolean hasClientMod(ServerPlayer player) {
		return player.connection != null && ServerPlayNetworking.canSend(player, WelcomePayload.TYPE);
	}

	/** The join tip is for first visits of players without the client mod, on servers that sync. */
	public boolean shouldTip(ServerPlayer player, boolean firstVisit) {
		return firstVisit && mod.config().clientModTip && mod.config().syncToClientMod && !hasClientMod(player);
	}

	/** The join tip: one line with [Download] and [How it works]. */
	public Component tip() {
		MutableComponent line = Component.literal("✦ Want these waypoints in Xaero's Minimap automatically? ")
				.withStyle(ChatFormatting.GOLD)
				.append(Component.literal("Install the sharedwaypoints-client mod. ").withStyle(ChatFormatting.GRAY));
		downloadButton().ifPresent(button -> line.append(button).append(" "));
		return line.append(Component.literal("[How it works]").withStyle(style -> style
				.withColor(ChatFormatting.YELLOW)
				.withClickEvent(new ClickEvent.RunCommand("/waypoints sync"))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Show the steps (/waypoints sync)")))));
	}

	/** {@code /waypoints sync}: whether it's on for you, and how to set it up. */
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
		if (player != null && hasClientMod(player)) {
			lines.add(Component.literal("Your sharedwaypoints-client doesn't match this server. Update it to "
					+ jarName() + ".").withStyle(ChatFormatting.YELLOW));
		}
		lines.add(Component.literal("The server's shared waypoints can appear in Xaero's Minimap by themselves, in their "
				+ "own \"Shared\" waypoint set, and stay up to date. Your own waypoints are never touched.")
				.withStyle(ChatFormatting.GRAY));
		lines.add(step(1, "Put " + jarName() + " in your .minecraft/mods folder."));
		lines.add(step(2, "Also install Xaero's Minimap and Fabric API (Fabric, Minecraft 1.21.11)."));
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

	/** The client jar that matches this server, e.g. sharedwaypoints-client-2.0.0.jar. */
	static String jarName() {
		String version = FabricLoader.getInstance().getModContainer("sharedwaypoints")
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("");
		int plus = version.indexOf('+');
		if (plus >= 0) {
			version = version.substring(0, plus);
		}
		return version.isEmpty() ? "sharedwaypoints-client.jar" : "sharedwaypoints-client-" + version + ".jar";
	}
}
