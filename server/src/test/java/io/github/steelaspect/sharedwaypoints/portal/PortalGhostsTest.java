package io.github.steelaspect.sharedwaypoints.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class PortalGhostsTest {
	private static final PortalShape SPOT = new PortalShape(130, 0, -39, Direction.Axis.X, 2, 3);

	@Test
	void everyOpeningAndFrameBlockGetsAGhostAtThePlayersHeight() {
		Map<BlockPos, PortalGhosts.Kind> cells = PortalGhosts.cells(SPOT, 70, pos -> false);
		// 2 × 3 opening, 4 × 5 with the frame: 6 opening + 14 frame blocks.
		assertEquals(20, cells.size());
		assertEquals(6, cells.values().stream().filter(kind -> kind == PortalGhosts.Kind.OPENING).count());
		assertEquals(PortalGhosts.Kind.FRAME, cells.get(new BlockPos(130, 70, -39)), "bottom frame row at the player's Y");
		assertFalse(cells.containsKey(new BlockPos(130, 69, -39)), "nothing in the ground under the player");
		assertEquals(PortalGhosts.Kind.OPENING, cells.get(new BlockPos(130, 71, -39)), "opening starts one above");
		assertEquals(PortalGhosts.Kind.OPENING, cells.get(new BlockPos(131, 73, -39)));
		assertEquals(PortalGhosts.Kind.FRAME, cells.get(new BlockPos(129, 70, -39)), "bottom-left corner");
		assertEquals(PortalGhosts.Kind.FRAME, cells.get(new BlockPos(132, 74, -39)), "top-right corner");
		assertFalse(cells.containsKey(new BlockPos(130, 71, -38)), "nothing beside the portal's plane");
	}

	@Test
	void eastWestPortalsRunAlongZ() {
		PortalShape eastWest = new PortalShape(-3, 0, 10, Direction.Axis.Z, 3, 4);
		Map<BlockPos, PortalGhosts.Kind> cells = PortalGhosts.cells(eastWest, 64, pos -> false);
		assertEquals(3 * 4 + 2 * 5 + 2 * 4, cells.size());
		assertEquals(PortalGhosts.Kind.OPENING, cells.get(new BlockPos(-3, 65, 12)));
		assertEquals(PortalGhosts.Kind.FRAME, cells.get(new BlockPos(-3, 64, 9)));
		assertTrue(cells.keySet().stream().allMatch(pos -> pos.getX() == -3), "all in the portal's plane");
	}

	@Test
	void frameBlocksAlreadyPlacedLoseTheirGhost() {
		Set<BlockPos> obsidian = Set.of(new BlockPos(129, 70, -39), new BlockPos(130, 70, -39), new BlockPos(131, 70, -39));
		Map<BlockPos, PortalGhosts.Kind> cells = PortalGhosts.cells(SPOT, 70, obsidian::contains);
		assertEquals(17, cells.size());
		assertTrue(obsidian.stream().noneMatch(cells::containsKey));
		// Only frame blocks count as placed: blocks in the opening (here a whole row) keep their ghosts, since they
		// have to be cleared for the portal. The row's two frame blocks lose theirs.
		Map<BlockPos, PortalGhosts.Kind> row = PortalGhosts.cells(SPOT, 70, pos -> pos.getY() == 71);
		assertEquals(18, row.size());
		assertEquals(6, row.values().stream().filter(kind -> kind == PortalGhosts.Kind.OPENING).count());
	}
}
