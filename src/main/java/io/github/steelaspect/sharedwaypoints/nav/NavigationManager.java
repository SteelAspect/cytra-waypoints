package io.github.steelaspect.sharedwaypoints.nav;

import io.github.steelaspect.sharedwaypoints.ModContext;
import io.github.steelaspect.sharedwaypoints.text.WaypointText;
import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import io.github.steelaspect.sharedwaypoints.waypoint.Category;
import io.github.steelaspect.sharedwaypoints.waypoint.Route;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.phys.Vec3;

/**
 * Live navigation: a boss bar per navigating player that works as a compass (arrow relative to where they look,
 * distance, height difference, progress), a particle beacon at the destination, and an "Arrived!" title.
 *
 * <p>Everything is vanilla packets (boss bar, particles, titles, sound), so it works on unmodded clients.
 * Navigation between the Overworld and the Nether points at the matching portal spot (coordinates / 8 or * 8).
 *
 * <p>Following a {@link Route} is navigation to one stop at a time: arriving at a stop moves the compass on to the
 * next one, and stops whose waypoint was deleted are skipped.
 */
public final class NavigationManager {
	private static final int UPDATE_INTERVAL = 5; // ticks between compass updates
	private static final int PARTICLE_INTERVAL = 10; // ticks between beacon puffs
	private static final int PARTICLE_RANGE = 256; // blocks; clients drop forced particles past 512 anyway
	private static final int BEACON_HEIGHT = 20;

	private final ModContext context;
	private final Map<UUID, Session> sessions = new HashMap<>();
	private final List<Consumer<UUID>> changeListeners = new ArrayList<>();
	private long ticks;

	/**
	 * Where a player is on a route.
	 *
	 * @param routeId the route being followed
	 * @param index   0-based index of the stop being navigated to
	 */
	public record RouteProgress(UUID routeId, int index) {
	}

	/** One player's navigation. */
	private static final class Session {
		UUID waypointId;
		final ServerBossEvent bar;
		/** The route being followed, or null for a single waypoint. */
		UUID routeId;
		int stopIndex;
		/** Dimension the start distance was measured in; reset when the player changes dimension or stop. */
		String measuredIn;
		double startDistance;

		Session(UUID waypointId, ServerBossEvent bar) {
			this.waypointId = waypointId;
			this.bar = bar;
		}
	}

	public NavigationManager(ModContext context) {
		this.context = context;
	}

	/** Called with a player's id whenever their navigation starts or ends (the client menu shows it). */
	public void onChange(Consumer<UUID> listener) {
		changeListeners.add(listener);
	}

	private void changed(UUID playerId) {
		changeListeners.forEach(listener -> listener.accept(playerId));
	}

	/** Starts (or switches) navigation for a player. */
	public void start(ServerPlayer player, Waypoint waypoint) {
		stop(player.getUUID());
		ServerBossEvent bar = new ServerBossEvent(Component.literal(waypoint.name()),
				barColor(waypoint.category()), BossEvent.BossBarOverlay.NOTCHED_10);
		bar.addPlayer(player);
		Session session = new Session(waypoint.id(), bar);
		sessions.put(player.getUUID(), session);
		player.sendSystemMessage(WaypointText.navigationStarted(waypoint));
		if (!update(player, session, true)) {
			stop(player.getUUID());
		} else {
			changed(player.getUUID());
		}
	}

	/**
	 * Starts following a route at stop {@code fromIndex} (0-based); stops whose waypoint no longer exists are
	 * skipped. Returns false if there is no stop left to go to.
	 */
	public boolean startRoute(ServerPlayer player, Route route, int fromIndex) {
		Optional<Integer> first = nextExistingStop(route, fromIndex);
		if (first.isEmpty()) {
			return false;
		}
		stop(player.getUUID());
		Waypoint waypoint = context.waypoints().get(route.stops().get(first.get())).orElseThrow();
		ServerBossEvent bar = new ServerBossEvent(Component.literal(waypoint.name()),
				barColor(waypoint.category()), BossEvent.BossBarOverlay.NOTCHED_10);
		bar.addPlayer(player);
		Session session = new Session(waypoint.id(), bar);
		session.routeId = route.id();
		session.stopIndex = first.get();
		sessions.put(player.getUUID(), session);
		player.sendSystemMessage(WaypointText.routeStarted(route, session.stopIndex, waypoint));
		if (!update(player, session, true)) {
			stop(player.getUUID());
		} else {
			changed(player.getUUID());
		}
		return true;
	}

	/**
	 * Skips the stop a player is heading to and moves on to the next one of their route. Returns false if they
	 * aren't following a route; finishing the route by skipping the last stop counts as a skip.
	 */
	public boolean skip(ServerPlayer player) {
		Session session = sessions.get(player.getUUID());
		if (session == null || session.routeId == null) {
			return false;
		}
		Optional<Route> route = context.routes().get(session.routeId);
		if (route.isEmpty() || !advance(session, route.get())) {
			routeFinished(player, route.map(Route::name).orElse("the route"));
			stop(player.getUUID());
		} else {
			update(player, session, false);
			changed(player.getUUID());
		}
		return true;
	}

	/** Stops a player's navigation; returns whether there was one. */
	public boolean stop(UUID playerId) {
		Session session = sessions.remove(playerId);
		if (session == null) {
			return false;
		}
		session.bar.removeAllPlayers();
		changed(playerId);
		return true;
	}

	/** Id of the waypoint a player is navigating to. */
	public Optional<UUID> destinationOf(UUID playerId) {
		return Optional.ofNullable(sessions.get(playerId)).map(session -> session.waypointId);
	}

	/** The route a player is following and which stop they're heading to. */
	public Optional<RouteProgress> routeOf(UUID playerId) {
		Session session = sessions.get(playerId);
		return session == null || session.routeId == null
				? Optional.empty()
				: Optional.of(new RouteProgress(session.routeId, session.stopIndex));
	}

	/** Stops everything (server stopping). */
	public void clear() {
		sessions.values().forEach(session -> session.bar.removeAllPlayers());
		sessions.clear();
	}

	/** Called every server tick. */
	public void tick(MinecraftServer server) {
		ticks++;
		if (sessions.isEmpty() || ticks % UPDATE_INTERVAL != 0) {
			return;
		}
		boolean particles = ticks % PARTICLE_INTERVAL == 0;
		Iterator<Map.Entry<UUID, Session>> iterator = sessions.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Session> entry = iterator.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !update(player, entry.getValue(), particles)) {
				entry.getValue().bar.removeAllPlayers();
				iterator.remove();
				changed(entry.getKey());
			}
		}
	}

	/** Refreshes one compass; returns false when navigation should end. */
	private boolean update(ServerPlayer player, Session session, boolean particles) {
		Optional<Route> route = Optional.empty();
		if (session.routeId != null) {
			route = context.routes().get(session.routeId);
			if (route.isEmpty()) {
				// The route was deleted: finish the current stop as ordinary navigation.
				session.routeId = null;
				player.sendSystemMessage(WaypointText.warning("That route was deleted; still navigating to this stop."));
			}
		}
		Optional<Waypoint> found = context.waypoints().get(session.waypointId);
		if (found.isEmpty()) {
			if (route.isPresent() && advance(session, route.get())) {
				player.sendSystemMessage(WaypointText.warning("That stop was removed; heading to the next one."));
				found = context.waypoints().get(session.waypointId);
			} else {
				player.sendSystemMessage(WaypointText.warning("Navigation stopped: that waypoint was removed."));
				return false;
			}
		}
		Waypoint waypoint = found.get();
		if (route.isPresent()) {
			int at = currentIndex(route.get(), session);
			if (at >= 0) {
				session.stopIndex = at; // stops were moved or dropped around this one
			}
		}
		String stopLabel = route.map(r -> "[" + (session.stopIndex + 1) + "/" + r.stops().size() + "] ").orElse("");

		// Respawning creates a new ServerPlayer object; move the bar over to it.
		if (!session.bar.getPlayers().contains(player)) {
			session.bar.removeAllPlayers();
			session.bar.addPlayer(player);
		}
		session.bar.setColor(barColor(waypoint.category()));

		String here = Dimensions.id(player.level().dimension());
		Optional<NavMath.Target> projected = NavMath.project(waypoint, here);
		if (projected.isEmpty()) {
			session.measuredIn = null;
			session.bar.setProgress(0f);
			session.bar.setName(Component.literal(stopLabel + "✦ " + waypoint.name()).withStyle(waypoint.category().color())
					.append(Component.literal("  is in " + Dimensions.shortName(waypoint.dimension())
							+ " — travel there to continue").withStyle(ChatFormatting.GRAY)));
			return true;
		}

		NavMath.Target target = projected.get();
		Vec3 position = player.position();
		double distance = target.horizontalDistance(position.x, position.z);
		int heightDifference = target.y() - (int) Math.floor(position.y);
		if (!here.equals(session.measuredIn)) {
			session.measuredIn = here;
			session.startDistance = Math.max(distance, 1.0);
		}

		if (!target.viaPortal() && Math.hypot(distance, heightDifference) <= context.config().arrivalRadius) {
			if (route.isPresent()) {
				return arriveAtStop(player, session, route.get(), waypoint);
			}
			arrive(player, waypoint);
			return false;
		}

		session.bar.setProgress((float) Math.clamp(1.0 - distance / session.startDistance, 0.0, 1.0));
		session.bar.setName(Component.literal(stopLabel).withStyle(ChatFormatting.GRAY)
				.append(compassText(player, waypoint, target, distance, heightDifference)));

		if (particles && context.config().navigationParticles && !target.viaPortal() && distance <= PARTICLE_RANGE) {
			beacon(player, waypoint, target);
		}
		return true;
	}

	/** Reached a route stop: announce it and move on, or finish the route. Returns whether to keep navigating. */
	private boolean arriveAtStop(ServerPlayer player, Session session, Route route, Waypoint waypoint) {
		int number = session.stopIndex + 1;
		if (!advance(session, route)) {
			arrive(player, waypoint);
			routeFinished(player, route.name());
			return false;
		}
		Waypoint next = context.waypoints().get(session.waypointId).orElseThrow();
		title(player, Component.literal("Stop " + number + "/" + route.stops().size()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.literal(waypoint.name()).withStyle(waypoint.category().color()));
		ding(player);
		player.sendSystemMessage(Component.literal("✔ " + number + "/" + route.stops().size() + " ").withStyle(ChatFormatting.GREEN)
				.append(WaypointText.categoryTag(waypoint.category()))
				.append(Component.literal(" " + waypoint.name()).withStyle(ChatFormatting.WHITE))
				.append(Component.literal("  Next: ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal(next.name()).withStyle(next.category().color())));
		changed(player.getUUID());
		return true;
	}

	/**
	 * Points a session at the next stop of its route that still exists. Returns false (session unchanged) if
	 * there is none. Works out where the player really is first, because the route may have been edited (stops
	 * dropped, moved, or deleted with their waypoint) since the session last looked.
	 */
	private boolean advance(Session session, Route route) {
		int at = currentIndex(route, session);
		// If the current stop left the route, the stop that took its place is the next one.
		Optional<Integer> next = nextExistingStop(route, at >= 0 ? at + 1 : session.stopIndex);
		if (next.isEmpty()) {
			return false;
		}
		session.stopIndex = next.get();
		session.waypointId = route.stops().get(next.get());
		session.measuredIn = null;
		return true;
	}

	/**
	 * Index of the session's waypoint in the route: its remembered position if that still matches, otherwise the
	 * occurrence nearest to it (a waypoint may be on a route more than once), or -1 if it isn't on the route.
	 */
	private static int currentIndex(Route route, Session session) {
		List<UUID> stops = route.stops();
		if (session.stopIndex >= 0 && session.stopIndex < stops.size() && stops.get(session.stopIndex).equals(session.waypointId)) {
			return session.stopIndex;
		}
		int best = -1;
		for (int i = 0; i < stops.size(); i++) {
			if (stops.get(i).equals(session.waypointId)
					&& (best < 0 || Math.abs(i - session.stopIndex) < Math.abs(best - session.stopIndex))) {
				best = i;
			}
		}
		return best;
	}

	/** Index of the first stop at or after {@code from} whose waypoint still exists. */
	private Optional<Integer> nextExistingStop(Route route, int from) {
		for (int i = Math.max(from, 0); i < route.stops().size(); i++) {
			if (context.waypoints().get(route.stops().get(i)).isPresent()) {
				return Optional.of(i);
			}
		}
		return Optional.empty();
	}

	private static void routeFinished(ServerPlayer player, String routeName) {
		title(player, Component.literal("Route complete!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.literal(routeName).withStyle(ChatFormatting.YELLOW));
		player.sendSystemMessage(Component.literal("⚑ Finished the route ").withStyle(ChatFormatting.GREEN)
				.append(Component.literal(routeName).withStyle(ChatFormatting.WHITE)));
	}

	private static void title(ServerPlayer player, Component title, Component subtitle) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
		player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		player.connection.send(new ClientboundSetTitleTextPacket(title));
	}

	/** e.g. {@code ↗ Main Storage  340m  ▲12} or {@code ⟳ Portal spot for Hub — take a Nether portal here}. */
	private MutableComponent compassText(ServerPlayer player, Waypoint waypoint, NavMath.Target target,
			double distance, int heightDifference) {
		ChatFormatting color = waypoint.category().color();
		if (target.viaPortal() && distance <= context.config().arrivalRadius) {
			return Component.literal("⟳ Portal spot for ").withStyle(ChatFormatting.LIGHT_PURPLE)
					.append(Component.literal(waypoint.name()).withStyle(color))
					.append(Component.literal(" — take a Nether portal here").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		String arrow = NavMath.arrow(player.getYRot(), player.getX(), player.getZ(), target.x() + 0.5, target.z() + 0.5);
		MutableComponent text = Component.literal(arrow + "  ").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
				.append(Component.literal(waypoint.name()).withStyle(color))
				.append(Component.literal("  " + NavMath.formatDistance(distance)).withStyle(ChatFormatting.WHITE));
		if (heightDifference > 2) {
			text.append(Component.literal("  ▲" + heightDifference).withStyle(ChatFormatting.GRAY));
		} else if (heightDifference < -2) {
			text.append(Component.literal("  ▼" + -heightDifference).withStyle(ChatFormatting.GRAY));
		}
		if (target.viaPortal()) {
			text.append(Component.literal("  ⟳ via Nether portal").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		return text;
	}

	/** A column of end-rod sparks with a category-coloured base, visible only to the navigating player. */
	private static void beacon(ServerPlayer player, Waypoint waypoint, NavMath.Target target) {
		ServerLevel level = player.level();
		double x = target.x() + 0.5;
		double z = target.z() + 0.5;
		for (int i = 0; i < BEACON_HEIGHT; i++) {
			level.sendParticles(player, ParticleTypes.END_ROD, true, true, x, target.y() + 0.5 + i * 1.5, z,
					1, 0.05, 0.3, 0.05, 0.0);
		}
		Integer rgb = waypoint.category().color().getColor();
		level.sendParticles(player, new DustParticleOptions(rgb != null ? rgb : 0xFFFFFF, 2.0f), true, true,
				x, target.y() + 0.3, z, 10, 0.6, 0.2, 0.6, 0.0);
	}

	private static void arrive(ServerPlayer player, Waypoint waypoint) {
		title(player, Component.literal("Arrived!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
				Component.literal(waypoint.name()).withStyle(waypoint.category().color()));
		ding(player);
		player.sendSystemMessage(Component.literal("✔ Arrived at ").withStyle(ChatFormatting.GREEN)
				.append(WaypointText.categoryTag(waypoint.category()))
				.append(Component.literal(" " + waypoint.name()).withStyle(ChatFormatting.WHITE)));
	}

	private static void ding(ServerPlayer player) {
		player.connection.send(new ClientboundSoundPacket(
				BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.PLAYER_LEVELUP), SoundSource.PLAYERS,
				player.getX(), player.getY(), player.getZ(), 0.6f, 1.3f, player.getRandom().nextLong()));
	}


	/** Boss bars only have seven colours; this picks the closest one to the category's chat colour. */
	static BossEvent.BossBarColor barColor(Category category) {
		return switch (category.color()) {
			case DARK_BLUE, BLUE, DARK_AQUA, AQUA -> BossEvent.BossBarColor.BLUE;
			case DARK_GREEN, GREEN -> BossEvent.BossBarColor.GREEN;
			case DARK_RED, RED -> BossEvent.BossBarColor.RED;
			case DARK_PURPLE, LIGHT_PURPLE -> BossEvent.BossBarColor.PURPLE;
			case GOLD, YELLOW -> BossEvent.BossBarColor.YELLOW;
			default -> BossEvent.BossBarColor.WHITE;
		};
	}
}
