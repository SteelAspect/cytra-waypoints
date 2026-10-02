package io.github.steelaspect.sharedwaypoints.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class PortalShapeTest {
	/** Portal blocks of a width × height portal with its lowest corner at (x, y, z). */
	private static Set<BlockPos> portal(int x, int y, int z, Direction.Axis axis, int width, int height) {
		Set<BlockPos> blocks = new HashSet<>();
		for (int a = 0; a < width; a++) {
			for (int up = 0; up < height; up++) {
				blocks.add(axis == Direction.Axis.X ? new BlockPos(x + a, y + up, z) : new BlockPos(x, y + up, z + a));
			}
		}
		return blocks;
	}

	@Test
	void findsTheWholePortalFromAnyBlockOfIt() {
		Set<BlockPos> blocks = portal(1040, 72, -312, Direction.Axis.X, 2, 3);
		PortalShape expected = new PortalShape(1040, 72, -312, Direction.Axis.X, 2, 3);
		for (BlockPos start : blocks) {
			assertEquals(Optional.of(expected), PortalShape.find(start, Direction.Axis.X, blocks::contains), start.toString());
		}
	}

	@Test
	void eastWestPortalsSpanZ() {
		Set<BlockPos> blocks = portal(-50, 64, 200, Direction.Axis.Z, 4, 5);
		assertEquals(Optional.of(new PortalShape(-50, 64, 200, Direction.Axis.Z, 4, 5)),
				PortalShape.find(new BlockPos(-50, 66, 202), Direction.Axis.Z, blocks::contains));
	}

	@Test
	void aNeighbouringPortalInAnotherPlaneIsNotPartOfIt() {
		Set<BlockPos> blocks = portal(0, 64, 0, Direction.Axis.X, 2, 3);
		blocks.addAll(portal(0, 64, 1, Direction.Axis.X, 2, 3)); // right behind it: a separate portal
		assertEquals(Optional.of(new PortalShape(0, 64, 0, Direction.Axis.X, 2, 3)),
				PortalShape.find(new BlockPos(0, 64, 0), Direction.Axis.X, blocks::contains));
	}

	@Test
	void notAPortalOrTooBigGivesNothing() {
		assertTrue(PortalShape.find(BlockPos.ZERO, Direction.Axis.X, pos -> false).isEmpty());
		Set<BlockPos> huge = portal(0, 0, 0, Direction.Axis.X, 22, 3);
		assertTrue(PortalShape.find(BlockPos.ZERO, Direction.Axis.X, huge::contains).isEmpty());
	}

	@Test
	void otherSideDividesOrMultipliesByEightAndKeepsTheShape() {
		PortalShape overworld = new PortalShape(1040, 72, -312, Direction.Axis.X, 2, 3);
		assertEquals(Optional.of(new PortalShape(130, 0, -39, Direction.Axis.X, 2, 3)), overworld.otherSide("minecraft:overworld"));
		// floorDiv, so -20 / 8 -> -3, like vanilla.
		PortalShape negative = new PortalShape(-20, 70, -1, Direction.Axis.Z, 3, 4);
		assertEquals(Optional.of(new PortalShape(-3, 0, -1, Direction.Axis.Z, 3, 4)), negative.otherSide("minecraft:overworld"));
		PortalShape nether = new PortalShape(130, 40, -39, Direction.Axis.Z, 2, 3);
		assertEquals(Optional.of(new PortalShape(1040, 0, -312, Direction.Axis.Z, 2, 3)), nether.otherSide("minecraft:the_nether"));
		assertTrue(nether.otherSide("minecraft:the_end").isEmpty());
	}

	@Test
	void cellsRunAlongThePortalAtTheGivenHeight() {
		PortalShape northSouth = new PortalShape(130, 0, -39, Direction.Axis.X, 2, 3);
		assertEquals(new BlockPos(131, 72, -39), northSouth.cell(1, 2, 70));
		PortalShape eastWest = new PortalShape(130, 0, -39, Direction.Axis.Z, 2, 3);
		assertEquals(new BlockPos(130, 70, -38), eastWest.cell(1, 0, 70));
		assertEquals("north–south", northSouth.facing());
		assertEquals("east–west", eastWest.facing());
	}
}
