package io.github.steelaspect.sharedwaypoints.portal;

import io.github.steelaspect.sharedwaypoints.nav.NavMath;
import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * A Nether portal's inside: the purple blocks, {@code width} along its axis by {@code height}, with
 * {@code (x, y, z)} the lowest corner. {@code axis} is the block state's: {@link Direction.Axis#X} for a portal you
 * walk through going north or south, {@link Direction.Axis#Z} for east or west.
 */
public record PortalShape(int x, int y, int z, Direction.Axis axis, int width, int height) {
	/** The biggest portal vanilla lights (21 × 21 inside). */
	static final int MAX_SIZE = 21;

	/**
	 * The whole portal containing {@code start}: every portal block connected to it in the portal's plane.
	 * Empty if {@code start} isn't a portal block or the shape is bigger than vanilla allows.
	 *
	 * @param isPortal whether a position holds a portal block with this {@code axis}
	 */
	public static Optional<PortalShape> find(BlockPos start, Direction.Axis axis, Predicate<BlockPos> isPortal) {
		if (!isPortal.test(start) || axis == Direction.Axis.Y) {
			return Optional.empty();
		}
		Direction along = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		seen.add(start);
		queue.add(start);
		int minAlong = Integer.MAX_VALUE, maxAlong = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			int a = axis == Direction.Axis.X ? pos.getX() : pos.getZ();
			minAlong = Math.min(minAlong, a);
			maxAlong = Math.max(maxAlong, a);
			minY = Math.min(minY, pos.getY());
			maxY = Math.max(maxY, pos.getY());
			if (seen.size() > MAX_SIZE * MAX_SIZE) {
				return Optional.empty();
			}
			for (BlockPos next : new BlockPos[] {pos.relative(along), pos.relative(along.getOpposite()), pos.above(), pos.below()}) {
				if (!seen.contains(next) && isPortal.test(next)) {
					seen.add(next);
					queue.add(next);
				}
			}
		}
		int width = maxAlong - minAlong + 1;
		int height = maxY - minY + 1;
		if (width > MAX_SIZE || height > MAX_SIZE) {
			return Optional.empty();
		}
		return Optional.of(axis == Direction.Axis.X
				? new PortalShape(minAlong, minY, start.getZ(), axis, width, height)
				: new PortalShape(start.getX(), minY, minAlong, axis, width, height));
	}

	/**
	 * The matching spot in the other one of Overworld / Nether: the same size and facing, the corner's X and Z
	 * divided (or multiplied) by 8, like vanilla's portal maths. Y is left at 0: the guide uses the player's height.
	 * Empty for other dimensions.
	 */
	public Optional<PortalShape> otherSide(String dimension) {
		if (dimension.equals(Dimensions.OVERWORLD)) {
			return Optional.of(new PortalShape(Math.floorDiv(x, NavMath.NETHER_SCALE), 0, Math.floorDiv(z, NavMath.NETHER_SCALE),
					axis, width, height));
		}
		if (dimension.equals(Dimensions.NETHER)) {
			return Optional.of(new PortalShape(x * NavMath.NETHER_SCALE, 0, z * NavMath.NETHER_SCALE, axis, width, height));
		}
		return Optional.empty();
	}

	/** The block {@code along} steps along the portal and {@code up} steps up from the corner, with the corner at {@code baseY}. */
	public BlockPos cell(int along, int up, int baseY) {
		return axis == Direction.Axis.X ? new BlockPos(x + along, baseY + up, z) : new BlockPos(x, baseY + up, z + along);
	}

	/** "north–south" or "east–west": the way you walk through it. */
	public String facing() {
		return axis == Direction.Axis.X ? "north–south" : "east–west";
	}

	/** Horizontal centre of the portal (block X and Z plus a half), for distances and the compass. */
	public double centerX() {
		return x + (axis == Direction.Axis.X ? width / 2.0 : 0.5);
	}

	public double centerZ() {
		return z + (axis == Direction.Axis.Z ? width / 2.0 : 0.5);
	}
}
