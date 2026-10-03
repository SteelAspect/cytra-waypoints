package io.github.steelaspect.sharedwaypoints.portal;

import io.github.steelaspect.sharedwaypoints.nav.NavMath;
import io.github.steelaspect.sharedwaypoints.text.WaypointText;
import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
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
 * bar. Frame ghosts disappear as you place obsidian.
 *
 * <p>A player can run several guides at once, one per portal looked at (up to {@link #MAX_GUIDES}): all their spots
 * in the dimension they're in are shown together, and the compass bar points to the nearest. Each guide has a number
 * ({@code #1}, {@code #2}, ...) for {@code /cway portal list} and {@code /cway portal stop <number>}, and ends by
 * itself when a portal is built there or after 30 minutes.
 *
 * <p>It doesn't check which portal vanilla would link to: chunk-loader portals are often built close together on
 * purpose. Vanilla clients see everything (the ghosts are vanilla block displays, and a boss bar).
 */
public final class PortalGuide {
	/** How far you can be from the portal you look at. */
	public static final double LOOK_RANGE = 16;
	/** How many guides one player can run at once. */
	public static final int MAX_GUIDES = 10;
	static final int LIFETIME_TICKS = 30 * 60 * 20;
	/** How often the ghosts follow the player's height and drop frame blocks that are now obsidian. */
	private static final int GHOST_INTERVAL = 10;
	private static final int BUILT_CHECK_INTERVAL = 20;
	/** Ghosts are only shown when you're this close. */
	private static final double SHOW_RANGE = 128;
	/**
	 * A spot's ghosts follow your height only while you're this close to it, so walking up or down to one spot
	 * doesn't move the others (which may be half built already).
	 */
	private static final double FOLLOW_RANGE = 16;

	private final Map<UUID, PlayerGuides> players = new HashMap<>();

	/** One player's guides, and what their game shows for them: one set of ghosts and one compass bar. */
	private static final class PlayerGuides {
		final List<Guide> guides = new ArrayList<>();
		final PortalGhosts ghosts = new PortalGhosts();
		final ServerBossEvent bar = new ServerBossEvent(Component.literal("Portal spot"), BossEvent.BossBarColor.PURPLE,
				BossEvent.BossBarOverlay.PROGRESS);
		/** Guide numbers are never reused while the player is online, so an old [Stop] button can't hit a new guide. */
		int nextNumber = 1;

		Optional<Guide> numbered(int number) {
			return guides.stream().filter(guide -> guide.number == number).findFirst();
		}

		void hide() {
			bar.removeAllPlayers();
			ghosts.hide();
		}
	}

	private static final class Guide {
		final int number;
		final String toDimension;
		final PortalShape from;
		final PortalShape to;
		int endsAt;
		/** The Y of this spot's bottom frame row; null until its ghosts are first shown. */
		Integer ghostY;
		double startDistance = -1;

		Guide(int number, String toDimension, PortalShape from, PortalShape to, int endsAt) {
			this.number = number;
			this.toDimension = toDimension;
			this.from = from;
			this.to = to;
			this.endsAt = endsAt;
		}

		double distance(ServerPlayer player) {
			return Math.hypot(to.centerX() - player.getX(), to.centerZ() - player.getZ());
		}

		/** "Nether side: 130 ~ -39", for chat. */
		String where() {
			return label(toDimension) + " side: " + coordinates(to);
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
	 * Adds a guide for the portal containing {@code pos}, next to the player's others. A portal whose spot is already
	 * guided gets its 30 minutes back instead of a second guide. Returns what's wrong if it can't. Public so the
	 * GameTest can call it without aiming a player.
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
		PlayerGuides mine = players.computeIfAbsent(player.getUUID(), id -> new PlayerGuides());
		Optional<Guide> same = mine.guides.stream()
				.filter(guide -> guide.toDimension.equals(toDimension) && guide.to.equals(otherSide.get()))
				.findFirst();
		if (same.isPresent()) {
			same.get().endsAt = serverTick + LIFETIME_TICKS;
			started(same.get(), shape.get(), mine.guides.size(), true).forEach(player::sendSystemMessage);
			return Optional.empty();
		}
		if (mine.guides.size() >= MAX_GUIDES) {
			return Optional.of("You already have " + MAX_GUIDES + " portal guides running. Stop one first: /cway portal list");
		}
		Guide guide = new Guide(mine.nextNumber++, toDimension, shape.get(), otherSide.get(), serverTick + LIFETIME_TICKS);
		mine.guides.add(guide);
		started(guide, shape.get(), mine.guides.size(), false).forEach(player::sendSystemMessage);
		return Optional.empty();
	}

	/** Ends all the player's guides. Returns how many there were. */
	public int stopAll(UUID player) {
		PlayerGuides mine = players.get(player);
		if (mine == null || mine.guides.isEmpty()) {
			return 0;
		}
		int count = mine.guides.size();
		mine.guides.clear();
		mine.hide();
		return count;
	}

	/** Ends the player's guide with this number, and shows what's left right away. Returns where it pointed. */
	public Optional<String> stop(ServerPlayer player, int number) {
		PlayerGuides mine = players.get(player.getUUID());
		Optional<Guide> guide = mine == null ? Optional.empty() : mine.numbered(number);
		guide.ifPresent(stopped -> {
			mine.guides.remove(stopped);
			update(player, mine, player.level().getServer().getTickCount(), true);
		});
		return guide.map(Guide::where);
	}

	/** {@code /cway portal list}: the player's guides, nearest first where they are, with [Copy coords] and [Stop]. */
	public List<Component> list(ServerPlayer player, int serverTick) {
		PlayerGuides mine = players.get(player.getUUID());
		if (mine == null || mine.guides.isEmpty()) {
			return List.of(Component.literal("You don't have a portal guide running. Look at a Nether portal and type "
					+ "/cway portal.").withStyle(ChatFormatting.GRAY));
		}
		String dimension = Dimensions.id(player.level().dimension());
		List<Guide> sorted = new ArrayList<>(mine.guides);
		sorted.sort(Comparator.<Guide, Boolean>comparing(guide -> !guide.toDimension.equals(dimension))
				.thenComparingDouble(guide -> guide.distance(player)));
		List<Component> lines = new ArrayList<>();
		lines.add(WaypointText.header("Portal guides (" + sorted.size() + ")"));
		for (Guide guide : sorted) {
			MutableComponent line = Component.literal("#" + guide.number + " ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(guide.where()).withStyle(ChatFormatting.LIGHT_PURPLE))
					.append(Component.literal(" (" + size(guide.to) + ", " + guide.to.facing() + ") ")
							.withStyle(ChatFormatting.GRAY));
			if (guide.toDimension.equals(dimension)) {
				line.append(Component.literal(NavMath.formatDistance(guide.distance(player)) + " " + NavMath.compass(
						player.getX(), player.getZ(), guide.to.centerX(), guide.to.centerZ())).withStyle(ChatFormatting.AQUA));
			} else {
				line.append(Component.literal("go to the " + label(guide.toDimension)).withStyle(ChatFormatting.AQUA));
			}
			int minutesLeft = Math.max(1, (guide.endsAt - serverTick + 20 * 60 - 1) / (20 * 60));
			line.append(Component.literal(" · " + minutesLeft + " min left ").withStyle(ChatFormatting.DARK_GRAY))
					.append(copyButton(guide.to)).append(" ").append(stopButton(guide.number));
			lines.add(line);
		}
		if (sorted.size() > 1) {
			lines.add(Component.literal("[Stop all]").withStyle(style -> style
					.withColor(ChatFormatting.RED)
					.withClickEvent(new ClickEvent.RunCommand("/cway portal stop"))
					.withHoverEvent(new HoverEvent.ShowText(Component.literal("Stop all your portal guides")))));
		}
		return lines;
	}

	/** Updates the player's ghost blocks now instead of on the next refresh tick, for the GameTest. */
	public void refreshGhosts(ServerPlayer player) {
		PlayerGuides mine = players.get(player.getUUID());
		if (mine != null) {
			update(player, mine, player.level().getServer().getTickCount(), true);
		}
	}

	/** How many ghost blocks the player's game shows right now, for the GameTest. */
	public int ghostCount(UUID player) {
		return Optional.ofNullable(players.get(player)).map(mine -> mine.ghosts.count()).orElse(0);
	}

	/** Where the player's guides point (the other side, Y = 0) in the order they were started, for the GameTest. */
	public List<PortalShape> targetsOf(UUID player) {
		return Optional.ofNullable(players.get(player))
				.map(mine -> mine.guides.stream().map(guide -> guide.to).toList())
				.orElse(List.of());
	}

	/** The player left: their guides end and their numbers start over next time. */
	public void forget(UUID player) {
		stopAll(player);
		players.remove(player);
	}

	public void clear() {
		players.values().forEach(PlayerGuides::hide);
		players.clear();
	}

	/** Every server tick: timeouts, built portals, compass bar and ghost blocks. */
	public void tick(MinecraftServer server) {
		if (players.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		for (Map.Entry<UUID, PlayerGuides> entry : players.entrySet()) {
			PlayerGuides mine = entry.getValue();
			if (mine.guides.isEmpty()) {
				continue;
			}
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				continue;
			}
			Iterator<Guide> iterator = mine.guides.iterator();
			while (iterator.hasNext()) {
				Guide guide = iterator.next();
				if (now >= guide.endsAt) {
					iterator.remove();
					player.sendSystemMessage(Component.literal("Portal guide #" + guide.number + " (" + guide.where()
							+ ") ended after 30 minutes. Look at the portal and run /cway portal again to restart it.")
							.withStyle(ChatFormatting.GRAY));
				} else if (now % BUILT_CHECK_INTERVAL == 0 && player.level() == levelOf(server, guide)
						&& isBuilt(player.level(), guide.to)) {
					iterator.remove();
					player.sendSystemMessage(Component.literal("✦ Portal built at " + coordinates(guide.to) + ". Guide #"
							+ guide.number + " finished." + (mine.guides.isEmpty() ? "" : " " + stillRunning(mine.guides.size())))
							.withStyle(ChatFormatting.LIGHT_PURPLE));
				}
			}
			update(player, mine, now, now % GHOST_INTERVAL == 0 || mine.ghosts.count() == 0);
		}
	}

	/** A portal block anywhere in the spot's footprint, at any height, counts as built. Unloaded chunks aren't read. */
	public static boolean isBuilt(ServerLevel level, PortalShape spot) {
		for (int along = 0; along < spot.width(); along++) {
			BlockPos column = spot.cell(along, 0, 0);
			if (!level.isLoaded(column)) {
				continue;
			}
			for (int y = level.getMinY(); y < level.getMaxY(); y++) {
				if (level.getBlockState(new BlockPos(column.getX(), y, column.getZ())).is(Blocks.NETHER_PORTAL)) {
					return true;
				}
			}
		}
		return false;
	}

	private static ServerLevel levelOf(MinecraftServer server, Guide guide) {
		return server.getLevel(guide.toDimension.equals(Dimensions.NETHER)
				? net.minecraft.world.level.Level.NETHER : net.minecraft.world.level.Level.OVERWORLD);
	}

	/**
	 * Shows the player's spots in the dimension they're in: the compass bar to the nearest, and (when
	 * {@code ghostsNow}) the ghosts of every spot within {@link #SHOW_RANGE}. Nothing when they have none here.
	 */
	private static void update(ServerPlayer player, PlayerGuides mine, int now, boolean ghostsNow) {
		String dimension = Dimensions.id(player.level().dimension());
		List<Guide> here = mine.guides.stream().filter(guide -> guide.toDimension.equals(dimension)).toList();
		if (here.isEmpty()) {
			// Only shown on the side you're building on.
			mine.hide();
			return;
		}
		updateBar(player, mine, here);
		if (ghostsNow) {
			updateGhosts(player, mine, here);
		}
	}

	private static void updateBar(ServerPlayer player, PlayerGuides mine, List<Guide> here) {
		ServerBossEvent bar = mine.bar;
		if (!bar.getPlayers().contains(player)) {
			bar.removeAllPlayers();
			bar.addPlayer(player);
		}
		Guide nearest = here.stream().min(Comparator.comparingDouble(guide -> guide.distance(player))).orElseThrow();
		double distance = nearest.distance(player);
		if (nearest.startDistance < 0) {
			nearest.startDistance = Math.max(distance, 1.0);
		}
		bar.setProgress((float) Math.clamp(1.0 - distance / nearest.startDistance, 0.0, 1.0));
		String size = size(nearest.to);
		Component more = Component.literal(here.size() > 1 ? "  +" + (here.size() - 1) + " more" : "")
				.withStyle(ChatFormatting.GRAY);
		if (distance <= nearest.to.width() / 2.0 + 3) {
			bar.setName(Component.literal("✦ Build your " + size + " portal in the ghost blocks")
					.withStyle(ChatFormatting.LIGHT_PURPLE).append(more));
			return;
		}
		String arrow = NavMath.arrow(player.getYRot(), player.getX(), player.getZ(), nearest.to.centerX(), nearest.to.centerZ());
		bar.setName(Component.literal(arrow + "  ").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
				.append(Component.literal("Portal spot").withStyle(ChatFormatting.LIGHT_PURPLE))
				.append(Component.literal("  " + NavMath.formatDistance(distance)).withStyle(ChatFormatting.WHITE))
				.append(Component.literal("  ⟳ " + size).withStyle(ChatFormatting.LIGHT_PURPLE))
				.append(more));
	}

	/**
	 * Shows the ghosts of every spot the player is close enough to, each with the bottom of its frame at their feet
	 * (not in the ground under them). A spot follows the player up or down only while they're near it, and only once
	 * they land, so jumping doesn't make it flicker. Where two spots overlap, the opening wins.
	 */
	private static void updateGhosts(ServerPlayer player, PlayerGuides mine, List<Guide> here) {
		ServerLevel level = player.level();
		Predicate<BlockPos> placed = cell -> {
			BlockState state = level.getBlockState(cell);
			return state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN);
		};
		Map<BlockPos, PortalGhosts.Kind> wanted = new LinkedHashMap<>();
		for (Guide guide : here) {
			double distance = guide.distance(player);
			if (distance > SHOW_RANGE) {
				continue;
			}
			if (guide.ghostY == null || (player.onGround() && distance <= FOLLOW_RANGE)) {
				guide.ghostY = player.getBlockY();
			}
			PortalGhosts.cells(guide.to, guide.ghostY, placed).forEach((cell, kind) -> wanted.merge(cell, kind,
					(shown, other) -> shown == PortalGhosts.Kind.OPENING ? shown : other));
		}
		if (wanted.isEmpty()) {
			mine.ghosts.hide();
		} else {
			mine.ghosts.show(player, wanted);
		}
	}

	private static String label(String dimension) {
		return dimension.equals(Dimensions.NETHER) ? "Nether" : "Overworld";
	}

	private static String coordinates(PortalShape spot) {
		return spot.x() + " ~ " + spot.z();
	}

	private static String size(PortalShape spot) {
		return spot.width() + "×" + spot.height();
	}

	private static String stillRunning(int count) {
		return count + (count == 1 ? " guide is" : " guides are") + " still running.";
	}

	private static MutableComponent copyButton(PortalShape spot) {
		String coordinates = coordinates(spot);
		return Component.literal("[Copy coords]").withStyle(style -> style
				.withColor(ChatFormatting.YELLOW)
				.withClickEvent(new ClickEvent.CopyToClipboard(coordinates))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Copy " + coordinates + " (~ = your height)"))));
	}

	private static MutableComponent stopButton(int number) {
		return Component.literal("[Stop]").withStyle(style -> style
				.withColor(ChatFormatting.RED)
				.withClickEvent(new ClickEvent.RunCommand("/cway portal stop " + number))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Stop portal guide #" + number))));
	}

	private static List<Component> started(Guide guide, PortalShape from, int running, boolean restarted) {
		String size = from.width() + " wide × " + from.height() + " tall";
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal("✦ Portal at " + from.x() + " " + from.y() + " " + from.z() + " (" + size + ", facing "
				+ from.facing() + ")").withStyle(ChatFormatting.LIGHT_PURPLE));
		lines.add(Component.literal("  " + guide.where() + " — go through and the spot is shown in ghost blocks. ")
				.withStyle(ChatFormatting.GRAY)
				.append(copyButton(guide.to)).append(" ").append(stopButton(guide.number)));
		if (restarted) {
			lines.add(Component.literal("  Already guiding that spot as #" + guide.number + ": its 30 minutes start over.")
					.withStyle(ChatFormatting.GRAY));
		} else if (running > 1) {
			lines.add(Component.literal("  Guide #" + guide.number + ", " + running + " running. ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal("[List]").withStyle(style -> style
							.withColor(ChatFormatting.YELLOW)
							.withClickEvent(new ClickEvent.RunCommand("/cway portal list"))
							.withHoverEvent(new HoverEvent.ShowText(Component.literal("Show all your portal guides"))))));
		}
		return lines;
	}
}
