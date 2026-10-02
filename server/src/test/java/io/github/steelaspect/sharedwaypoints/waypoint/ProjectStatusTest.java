package io.github.steelaspect.sharedwaypoints.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProjectStatusTest {
	@Test
	void statesHaveCommandIdsInListingOrder() {
		assertEquals(List.of("broken", "wip", "planned", "done"),
				java.util.Arrays.stream(ProjectStatus.State.values()).map(ProjectStatus.State::id).toList());
		assertEquals(Optional.of(ProjectStatus.State.WIP), ProjectStatus.State.byId("WIP"));
		assertTrue(ProjectStatus.State.byId("clear").isEmpty());
		assertEquals(0xFF5555, ProjectStatus.State.BROKEN.rgb());
	}

	@Test
	void summaryShowsSymbolNameAndNote() {
		ProjectStatus withNote = new ProjectStatus(ProjectStatus.State.BROKEN, "out of bonemeal", Waypoint.SERVER_UUID,
				"Steve", Instant.EPOCH);
		assertEquals("⚠ Broken: out of bonemeal", withNote.summary());
		assertEquals("✔ Done", new ProjectStatus(ProjectStatus.State.DONE, null, Waypoint.SERVER_UUID, "Steve",
				Instant.EPOCH).summary());
	}

	@Test
	void notesAreShortAndPlain() {
		assertTrue(ProjectStatus.validateNote("needs more hoppers").isEmpty());
		assertTrue(ProjectStatus.validateNote("x".repeat(ProjectStatus.MAX_NOTE_LENGTH + 1)).isPresent());
		assertTrue(ProjectStatus.validateNote("§cred").isPresent());
		assertTrue(ProjectStatus.validateNote("line\nbreak").isPresent());
	}
}
