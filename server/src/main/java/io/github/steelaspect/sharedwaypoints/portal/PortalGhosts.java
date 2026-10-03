package io.github.steelaspect.sharedwaypoints.portal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * See-through "ghost" blocks that show where a portal goes: purple glass for the opening and white glass for the
 * obsidian frame, with a glowing outline that shows through terrain. They are block-display entities that exist only
 * in one player's game: the server sends that player the packets for them and never adds them to the world, so
 * nobody else sees them, nothing can touch them, and vanilla clients show them without any mod.
 */
final class PortalGhosts {
	/** What a ghost stands for. */
	enum Kind {
		OPENING("minecraft:purple_stained_glass", 0xB34DFF),
		FRAME("minecraft:white_stained_glass", 0xE0D0FF);

		final String block;
		final int glow;

		Kind(String block, int glow) {
			this.block = block;
			this.glow = glow;
		}
	}

	/** Display entities render this many × 64 blocks away (the client's entity distance setting still applies). */
	private static final float VIEW_RANGE = 4f;

	/** The ghosts shown to the player right now, by block. Empty when none are shown. */
	private final Map<BlockPos, Entity> shown = new HashMap<>();
	/** The player and level they were sent to; they vanish by themselves when the player changes level. */
	private ServerPlayer shownTo;
	private ServerLevel shownIn;

	/**
	 * Where the ghosts go for a portal spot with its opening's bottom at {@code baseY}: every block of the opening, and
	 * every block of the frame around it (corners included) that isn't already obsidian.
	 *
	 * @param placed whether a frame block is already in place (obsidian or crying obsidian)
	 */
	static Map<BlockPos, Kind> cells(PortalShape spot, int baseY, Predicate<BlockPos> placed) {
		Map<BlockPos, Kind> cells = new LinkedHashMap<>();
		for (int along = -1; along <= spot.width(); along++) {
			for (int up = -1; up <= spot.height(); up++) {
				BlockPos cell = spot.cell(along, up, baseY);
				boolean frame = along < 0 || along == spot.width() || up < 0 || up == spot.height();
				if (!frame) {
					cells.put(cell, Kind.OPENING);
				} else if (!placed.test(cell)) {
					cells.put(cell, Kind.FRAME);
				}
			}
		}
		return cells;
	}

	/** Makes the player's game show exactly {@code wanted}: adds what's missing, removes what's no longer wanted. */
	void show(ServerPlayer player, Map<BlockPos, Kind> wanted) {
		ServerLevel level = player.level();
		if (shownTo != player || shownIn != level) {
			// A new level (or a respawned player) means a fresh client world: whatever was sent is gone already.
			shown.clear();
			shownTo = player;
			shownIn = level;
		}
		List<Integer> gone = new ArrayList<>();
		shown.entrySet().removeIf(entry -> {
			if (wanted.containsKey(entry.getKey())) {
				return false;
			}
			gone.add(entry.getValue().getId());
			return true;
		});
		List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
		if (!gone.isEmpty()) {
			packets.add(new ClientboundRemoveEntitiesPacket(gone.stream().mapToInt(Integer::intValue).toArray()));
		}
		wanted.forEach((cell, kind) -> {
			if (shown.containsKey(cell)) {
				return;
			}
			Entity ghost = ghost(level, cell, kind);
			shown.put(cell, ghost);
			packets.add(new ClientboundAddEntityPacket(ghost.getId(), ghost.getUUID(), cell.getX(), cell.getY(),
					cell.getZ(), 0f, 0f, EntityType.BLOCK_DISPLAY, 0, Vec3.ZERO, 0.0));
			var data = ghost.getEntityData().getNonDefaultValues();
			if (data != null) {
				packets.add(new ClientboundSetEntityDataPacket(ghost.getId(), data));
			}
		});
		if (!packets.isEmpty()) {
			player.connection.send(new ClientboundBundlePacket(packets));
		}
	}

	/** Removes every ghost from the player's game (if they're still in the level they were sent to). */
	void hide() {
		if (!shown.isEmpty() && shownTo != null && shownTo.level() == shownIn && !shownTo.hasDisconnected()) {
			shownTo.connection.send(new ClientboundRemoveEntitiesPacket(
					shown.values().stream().mapToInt(Entity::getId).toArray()));
		}
		shown.clear();
		shownTo = null;
		shownIn = null;
	}

	/** How many ghosts the player's game has right now (for the GameTest). */
	int count() {
		return shown.size();
	}

	/** A block display like {@code /summon block_display} would make, but never added to the world. */
	private static Entity ghost(ServerLevel level, BlockPos cell, Kind kind) {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", "minecraft:block_display");
		CompoundTag state = new CompoundTag();
		state.putString("Name", kind.block);
		tag.put("block_state", state);
		tag.putByte("Glowing", (byte) 1);
		tag.putInt("glow_color_override", kind.glow);
		tag.putFloat("view_range", VIEW_RANGE);
		CompoundTag brightness = new CompoundTag();
		brightness.putInt("sky", 15);
		brightness.putInt("block", 15);
		tag.put("brightness", brightness);
		Entity ghost = EntityType.loadEntityRecursive(tag, level, EntitySpawnReason.COMMAND, EntityProcessor.NOP);
		if (ghost == null) {
			throw new IllegalStateException("Couldn't create a block display");
		}
		ghost.setPos(cell.getX(), cell.getY(), cell.getZ());
		return ghost;
	}
}
