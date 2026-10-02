package io.github.steelaspect.sharedwaypoints.portal;

import io.github.steelaspect.sharedwaypoints.nav.NavMath;
import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * {@code /cway portal}: look at a Nether portal and the matching spot on the other side (X and Z ÷ 8 or × 8, the
 * same size and facing) is highlighted for you in the other dimension, at your height, with particles only you see
 * and a compass bar. It ends when a portal is built there, with {@code /cway portal stop}, or after 30 minutes.
 *
 * <p>It doesn't check which portal vanilla would link to: chunk-loader portals are often built close together on
 * purpose. Vanilla clients see everything (particles and a boss bar).
 */
public final class PortalGuide {
	/** How far you can be from the portal you look at. */
	public static final double LOOK_RANGE = 16;
	static final int LIFETIME_TICKS = 30 * 60 * 20;
	private static final int PARTICLE_INTERVAL = 10;
	private static final int BUILT_CHECK_INTERVAL = 20;
	/** Particles are only sent when you're this close (clients drop forced particles past 512 anyway). */
	private static final double SHOW_RANGE = 128;
	private static final int PORTAL_PURPLE = 0xB34DFF;
	private static final int FRAME_WHITE = 0xFFFFFF;

	private final Map<UUID, Guide> guides = new HashMap<>();

	private static final class Guide {
		final String fromDimension;
		final String toDimension;
		final PortalShape from;
		final PortalShape to;
		final ServerBossEvent bar;
		final int endsAt;
		double startDistance = -1;

		Guide(String fromDimension, String toDimension, PortalShape from, PortalShape to, ServerBossEvent bar, int endsAt) {
			this.fromDimension = fromDimension;
			this.toDimension = toDimension;
			this.from = from;
			this.to = to;
			this.bar = bar;
			this.endsAt = endsAt;
		}
	}

	/** The portal block the player is looking at, if any, within {@link #LOOK_RANGE} blocks. */
	public static Optional<BlockPos> lookedAt(ServerPlayer player) {
		HitResult hit = player.pick(LOOK_RANGE, 0f, false);
		if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult block
				&& player.level().getBlockState(block.getBlockPos()).is(Blocks.NETHER_PORTAL)) {
			return Optional.of(block.getBlockPos());
		}
		return Optional.empty();
	}

	/**
	 * Starts a guide for the portal containing {@code pos} (replacing the player's previous one). Returns what's
	 * wrong if it can't. Public so the GameTest can call it without aiming a player.
	 */
	public Optional<String> start(ServerPlayer player, BlockPos pos, int serverTick) {
		ServerLevel level = player.level();
		String dimension = Dimensions.id(level.dimension());
		BlockState state = level.getBlockState(pos);
		if (!state.is(Blocks.NETHER_PORTAL)) {
			return Optional.of("Look at a Nether portal (the purple part)");
		}
		Direction.Axis axis = state.getValue(NetherPortalBlock.AXIS);
		Optional<PortalShape> shape = PortalShape.find(pos, axis, at -> {
			BlockState there = level.getBlockState(at);
			return there.is(Blocks.NETHER_PORTAL) && there.getValue(NetherPortalBlock.AXIS) == axis;
		});
		if (shape.isEmpty()) {
			return Optional.of("That portal is bigger than vanilla allows");
		}
		Optional<PortalShape> otherSide = shape.get().otherSide(dimension);
		if (otherSide.isEmpty()) {
			return Optional.of("Portal guides only work between the Overworld and the Nether");
		}
		String toDimension = dimension.equals(Dimensions.OVERWORLD) ? Dimensions.NETHER : Dimensions.OVERWORLD;
		stop(player.getUUID());
		ServerBossEvent bar = new ServerBossEvent(Component.literal("Portal spot"), BossEvent.BossBarColor.PURPLE,
				BossEvent.BossBarOverlay.PROGRESS);
		guides.put(player.getUUID(), new Guide(dimension, toDimension, shape.get(), otherSide.get(), bar,
				serverTick + LIFETIME_TICKS));
		started(shape.get(), otherSide.get(), toDimension).forEach(player::sendSystemMessage);
		return Optional.empty();
	}

	/** Ends the player's guide. Returns whether they had one. */
	public boolean stop(UUID player) {
		Guide guide = guides.remove(player);
		if (guide == null) {
			return false;
		}
		guide.bar.removeAllPlayers();
		return true;
	}

	/** Where the player's guide points (the other side, Y = 0), for the GameTest. */
	public Optional<PortalShape> targetOf(UUID player) {
		return Optional.ofNullable(guides.get(player)).map(guide -> guide.to);
	}

	public void forget(UUID player) {
		stop(player);
	}

	public void clear() {
		guides.values().forEach(guide -> guide.bar.removeAllPlayers());
		guides.clear();
	}

	/** Every server tick: compass bar, particles, and whether the portal has been built. */
	public void tick(MinecraftServer server) {
		if (guides.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		Iterator<Map.Entry<UUID, Guide>> iterator = guides.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Guide> entry = iterator.next();
			Guide guide = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				continue;
			}
			if (now >= guide.endsAt) {
				guide.bar.removeAllPlayers();
				iterator.remove();
				player.sendSystemMessage(Component.literal("Portal guide ended after 30 minutes. Look at the portal and "
						+ "run /cway portal again to restart it.").withStyle(ChatFormatting.GRAY));
				continue;
			}
			if (!Dimensions.id(player.level().dimension()).equals(guide.toDimension)) {
				guide.bar.removeAllPlayers(); // only shown on the side you're building on
				continue;
			}
			if (now % BUILT_CHECK_INTERVAL == 0 && isBuilt(player.level(), guide.to)) {
				guide.bar.removeAllPlayers();
				iterator.remove();
				player.sendSystemMessage(Component.literal("✦ Portal built at the matching spot. Guide finished.")
						.withStyle(ChatFormatting.LIGHT_PURPLE));
				continue;
			}
			updateBar(player, guide);
			if (now % PARTICLE_INTERVAL == 0) {
				highlight(player, guide.to);
			}
		}
	}

	/** A portal block anywhere in the spot's footprint, at any height, counts as built. */
	public static boolean isBuilt(ServerLevel level, PortalShape spot) {
		for (int along = 0; along < spot.width(); along++) {
			BlockPos column = spot.cell(along, 0, 0);
			for (int y = level.getMinY(); y < level.getMaxY(); y++) {
				if (level.getBlockState(new BlockPos(column.getX(), y, column.getZ())).is(Blocks.NETHER_PORTAL)) {
					return true;
				}
			}
		}
		return false;
	}

	private static void updateBar(ServerPlayer player, Guide guide) {
		if (!guide.bar.getPlayers().contains(player)) {
			guide.bar.removeAllPlayers();
			guide.bar.addPlayer(player);
		}
		double distance = Math.hypot(guide.to.centerX() - player.getX(), guide.to.centerZ() - player.getZ());
		if (guide.startDistance < 0) {
			guide.startDistance = Math.max(distance, 1.0);
		}
		guide.bar.setProgress((float) Math.clamp(1.0 - distance / guide.startDistance, 0.0, 1.0));
		String size = guide.to.width() + "×" + guide.to.height();
		if (distance <= guide.to.width() / 2.0 + 3) {
			guide.bar.setName(Component.literal("✦ Build your " + size + " portal here — it's highlighted")
					.withStyle(ChatFormatting.LIGHT_PURPLE));
			return;
		}
		String arrow = NavMath.arrow(player.getYRot(), player.getX(), player.getZ(), guide.to.centerX(), guide.to.centerZ());
		guide.bar.setName(Component.literal(arrow + "  ").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
				.append(Component.literal("Portal spot").withStyle(ChatFormatting.LIGHT_PURPLE))
				.append(Component.literal("  " + NavMath.formatDistance(distance)).withStyle(ChatFormatting.WHITE))
				.append(Component.literal("  ⟳ " + size).withStyle(ChatFormatting.LIGHT_PURPLE)));
	}

	/**
	 * A white outline round the portal's opening (where the inside meets the obsidian frame) and purple specks in
	 * every portal block, at the player's Y, in the portal's own plane.
	 */
	private static void highlight(ServerPlayer player, PortalShape spot) {
		if (Math.hypot(spot.centerX() - player.getX(), spot.centerZ() - player.getZ()) > SHOW_RANGE) {
			return;
		}
		ServerLevel level = player.level();
		int baseY = player.getBlockY();
		DustParticleOptions inside = new DustParticleOptions(PORTAL_PURPLE, 1.5f);
		DustParticleOptions outline = new DustParticleOptions(FRAME_WHITE, 0.7f);
		for (int along = 0; along < spot.width(); along++) {
			for (int up = 0; up < spot.height(); up++) {
				BlockPos cell = spot.cell(along, up, baseY);
				level.sendParticles(player, inside, true, true,
						cell.getX() + 0.5, cell.getY() + 0.5, cell.getZ() + 0.5, 2, 0.25, 0.25, 0.25, 0.0);
			}
		}
		// The rectangle's edges, a dot every quarter block.
		double step = 0.25;
		for (double a = 0; a <= spot.width() + 1e-9; a += step) {
			dot(level, player, outline, spot, a, 0, baseY);
			dot(level, player, outline, spot, a, spot.height(), baseY);
		}
		for (double up = step; up < spot.height() - 1e-9; up += step) {
			dot(level, player, outline, spot, 0, up, baseY);
			dot(level, player, outline, spot, spot.width(), up, baseY);
		}
	}

	/** One outline dot, {@code along} and {@code up} blocks from the opening's corner, in the middle of its plane. */
	private static void dot(ServerLevel level, ServerPlayer player, DustParticleOptions options, PortalShape spot,
			double along, double up, int baseY) {
		boolean northSouth = spot.axis() == Direction.Axis.X;
		double x = northSouth ? spot.x() + along : spot.x() + 0.5;
		double z = northSouth ? spot.z() + 0.5 : spot.z() + along;
		level.sendParticles(player, options, true, true, x, baseY + up, z, 1, 0, 0, 0, 0.0);
	}

	private static String label(String dimension) {
		return dimension.equals(Dimensions.NETHER) ? "Nether" : "Overworld";
	}

	private static java.util.List<Component> started(PortalShape from, PortalShape to, String toDimension) {
		String size = from.width() + " wide × " + from.height() + " tall";
		String coordinates = to.x() + " ~ " + to.z();
		MutableComponent copy = Component.literal("[Copy coords]").withStyle(style -> style
				.withColor(ChatFormatting.YELLOW)
				.withClickEvent(new ClickEvent.CopyToClipboard(coordinates))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Copy " + coordinates
						+ " (~ = your height)"))));
		MutableComponent stop = Component.literal("[Stop]").withStyle(style -> style
				.withColor(ChatFormatting.RED)
				.withClickEvent(new ClickEvent.RunCommand("/cway portal stop"))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Stop the portal guide"))));
		return java.util.List.of(
				Component.literal("✦ Portal at " + from.x() + " " + from.y() + " " + from.z() + " (" + size + ", facing "
						+ from.facing() + ")").withStyle(ChatFormatting.LIGHT_PURPLE),
				Component.literal("  " + label(toDimension) + " side: " + coordinates
						+ " — go through and the spot is highlighted for you. ").withStyle(ChatFormatting.GRAY)
						.append(copy).append(" ").append(stop));
	}
}
