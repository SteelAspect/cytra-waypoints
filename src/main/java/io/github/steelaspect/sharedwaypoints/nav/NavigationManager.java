package io.github.steelaspect.sharedwaypoints.nav;

import io.github.steelaspect.sharedwaypoints.ModContext;
import io.github.steelaspect.sharedwaypoints.text.WaypointText;
import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import io.github.steelaspect.sharedwaypoints.waypoint.Category;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
 */
public final class NavigationManager {
	private static final int UPDATE_INTERVAL = 5; // ticks between compass updates
	private static final int PARTICLE_INTERVAL = 10; // ticks between beacon puffs
	private static final int PARTICLE_RANGE = 256; // blocks; clients drop forced particles past 512 anyway
	private static final int BEACON_HEIGHT = 20;

	private final ModContext context;
	private final Map<UUID, Session> sessions = new HashMap<>();
	private long ticks;

	/** One player's navigation. */
	private static final class Session {
		final UUID waypointId;
		final ServerBossEvent bar;
		/** Dimension the start distance was measured in; reset when the player changes dimension. */
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
		}
	}

	/** Stops a player's navigation; returns whether there was one. */
	public boolean stop(UUID playerId) {
		Session session = sessions.remove(playerId);
		if (session == null) {
			return false;
		}
		session.bar.removeAllPlayers();
		return true;
	}

	/** Id of the waypoint a player is navigating to. */
	public Optional<UUID> destinationOf(UUID playerId) {
		return Optional.ofNullable(sessions.get(playerId)).map(session -> session.waypointId);
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
			}
		}
	}

	/** Refreshes one compass; returns false when navigation should end. */
	private boolean update(ServerPlayer player, Session session, boolean particles) {
		Optional<Waypoint> found = context.waypoints().get(session.waypointId);
		if (found.isEmpty()) {
			player.sendSystemMessage(WaypointText.warning("Navigation stopped: that waypoint was removed."));
			return false;
		}
		Waypoint waypoint = found.get();

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
			session.bar.setName(Component.literal("✦ " + waypoint.name()).withStyle(waypoint.category().color())
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
			arrive(player, waypoint);
			return false;
		}

		session.bar.setProgress((float) Math.clamp(1.0 - distance / session.startDistance, 0.0, 1.0));
		session.bar.setName(compassText(player, waypoint, target, distance, heightDifference));

		if (particles && context.config().navigationParticles && !target.viaPortal() && distance <= PARTICLE_RANGE) {
			beacon(player, waypoint, target);
		}
		return true;
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
		player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
		player.connection.send(new ClientboundSetSubtitleTextPacket(
				Component.literal(waypoint.name()).withStyle(waypoint.category().color())));
		player.connection.send(new ClientboundSetTitleTextPacket(
				Component.literal("Arrived!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		player.connection.send(new ClientboundSoundPacket(
				BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.PLAYER_LEVELUP), SoundSource.PLAYERS,
				player.getX(), player.getY(), player.getZ(), 0.6f, 1.3f, player.getRandom().nextLong()));
		player.sendSystemMessage(Component.literal("✔ Arrived at ").withStyle(ChatFormatting.GREEN)
				.append(WaypointText.categoryTag(waypoint.category()))
				.append(Component.literal(" " + waypoint.name()).withStyle(ChatFormatting.WHITE)));
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
