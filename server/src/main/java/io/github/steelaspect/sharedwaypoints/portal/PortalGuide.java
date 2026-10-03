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
 * same size and facing) is shown to you in the other dimension, at your height, as see-through ghost blocks (the
 * opening and the obsidian frame, with a glowing outline through terrain; see {@link PortalGhosts}) and a compass
 * bar. Frame ghosts disappear as you place obsidian. It ends when a portal is built there, with
 * {@code /cway portal stop}, or after 30 minutes.
 *
 * <p>It doesn't check which portal vanilla would link to: chunk-loader portals are often built close together on
 * purpose. Vanilla clients see everything (the ghosts are vanilla block displays, and a boss bar).
 */
public final class PortalGuide {
	/** How far you can be from the portal you look at. */
	public static final double LOOK_RANGE = 16;
	static final int LIFETIME_TICKS = 30 * 60 * 20;
	/** How often the ghosts follow the player's height and drop frame blocks that are now obsidian. */
	private static final int GHOST_INTERVAL = 10;
	private static final int BUILT_CHECK_INTERVAL = 20;
	/** Ghosts are only shown when you're this close. */
	private static final double SHOW_RANGE = 128;

	private final Map<UUID, Guide> guides = new HashMap<>();

	private static final class Guide {
		final String fromDimension;
		final String toDimension;
		final PortalShape from;
		final PortalShape to;
		final ServerBossEvent bar;
		final int endsAt;
		final PortalGhosts ghosts = new PortalGhosts();
		/** The Y the ghosts' opening starts at (the player's height when they were last placed). */
		int ghostY;
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
		end(guide);
		return true;
	}

	/** Updates the player's ghost blocks now instead of on the next refresh tick, for the GameTest. */
	public void refreshGhosts(ServerPlayer player) {
		Guide guide = guides.get(player.getUUID());
		if (guide != null && Dimensions.id(player.level().dimension()).equals(guide.toDimension)) {
			updateGhosts(player, guide);
		}
	}

	/** How many ghost blocks the player's game shows right now, for the GameTest. */
	public int ghostCount(UUID player) {
		return Optional.ofNullable(guides.get(player)).map(guide -> guide.ghosts.count()).orElse(0);
	}

	/** Where the player's guide points (the other side, Y = 0), for the GameTest. */
	public Optional<PortalShape> targetOf(UUID player) {
		return Optional.ofNullable(guides.get(player)).map(guide -> guide.to);
	}

	public void forget(UUID player) {
		stop(player);
	}

	public void clear() {
		guides.values().forEach(PortalGuide::end);
		guides.clear();
	}

	private static void end(Guide guide) {
		guide.bar.removeAllPlayers();
		guide.ghosts.hide();
	}

	/** Every server tick: compass bar, ghost blocks, and whether the portal has been built. */
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
				end(guide);
				iterator.remove();
				player.sendSystemMessage(Component.literal("Portal guide ended after 30 minutes. Look at the portal and "
						+ "run /cway portal again to restart it.").withStyle(ChatFormatting.GRAY));
				continue;
			}
			if (!Dimensions.id(player.level().dimension()).equals(guide.toDimension)) {
				// Only shown on the side you're building on.
				end(guide);
				continue;
			}
			if (now % BUILT_CHECK_INTERVAL == 0 && isBuilt(player.level(), guide.to)) {
				end(guide);
				iterator.remove();
				player.sendSystemMessage(Component.literal("✦ Portal built at the matching spot. Guide finished.")
						.withStyle(ChatFormatting.LIGHT_PURPLE));
				continue;
			}
			updateBar(player, guide);
			if (now % GHOST_INTERVAL == 0 || guide.ghosts.count() == 0) {
				updateGhosts(player, guide);
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
			guide.bar.setName(Component.literal("✦ Build your " + size + " portal in the ghost blocks")
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
	 * Shows the ghosts when the player is close enough, at their height. They follow the player up or down only once
	 * they land, so jumping doesn't make them flicker.
	 */
	private static void updateGhosts(ServerPlayer player, Guide guide) {
		PortalShape spot = guide.to;
		if (Math.hypot(spot.centerX() - player.getX(), spot.centerZ() - player.getZ()) > SHOW_RANGE) {
			guide.ghosts.hide();
			return;
		}
		if (guide.ghosts.count() == 0 || player.onGround()) {
			guide.ghostY = player.getBlockY();
		}
		ServerLevel level = player.level();
		guide.ghosts.show(player, PortalGhosts.cells(spot, guide.ghostY, cell -> {
			BlockState state = level.getBlockState(cell);
			return state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN);
		}));
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
						+ " — go through and the spot is shown in ghost blocks. ").withStyle(ChatFormatting.GRAY)
						.append(copy).append(" ").append(stop));
	}
}
