package io.github.steelaspect.sharedwaypoints.xaero;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.steelaspect.sharedwaypoints.waypoint.Category;
import io.github.steelaspect.sharedwaypoints.waypoint.CategoryRegistry;
import io.github.steelaspect.sharedwaypoints.waypoint.TestCategories;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class XaeroShareFormatTest {
	private static Waypoint waypoint(String name, Category category, String dimension) {
		return new Waypoint(UUID.randomUUID(), name, category, 120, 64, -340, dimension, null, UUID.randomUUID(), "Tester",
				Instant.EPOCH);
	}

	@Test
	void overworldLineMatchesXaeroFormat() {
		String line = XaeroShareFormat.shareMessage(waypoint("Main Storage", TestCategories.STORAGE, "minecraft:overworld"));
		assertEquals("xaero-waypoint:Main Storage:M:120:64:-340:11:false:0:Internal-overworld-waypoints", line);
	}

	@Test
	void netherIsEncodedLikeXaeroDoes() {
		String line = XaeroShareFormat.shareMessage(waypoint("Hub", TestCategories.PORTALS, "minecraft:the_nether"));
		assertEquals("xaero-waypoint:Hub:H:120:64:-340:13:false:0:Internal-the-nether-waypoints", line);
	}

	@ParameterizedTest
	@CsvSource({
			"minecraft:overworld, overworld",
			"minecraft:the_nether, the_nether",
			"minecraft:the_end, the_end",
			"mymod:deep_mine, dim%mymod$deep_mine",
			"mymod:caves/lower.level, 'dim%mymod$caves%lower,level'",
	})
	void dimensionRoundTripsThroughXaero(String dimension, String expectedXaeroNode) {
		String line = XaeroShareFormat.shareMessage(waypoint("Spot", TestCategories.OTHER, dimension));
		assertEquals(expectedXaeroNode, XaeroParserReplica.parse(line).dimensionNode());
	}

	@ParameterizedTest
	@ValueSource(strings = {"Main Storage", "Iron-Farm_2", "a*b:c", "x", "Café Ω", "--__**::", "12345678901234567890123456789012"})
	void namesRoundTripThroughXaero(String name) {
		Waypoint waypoint = waypoint(name, TestCategories.FARMS, "minecraft:overworld");
		XaeroParserReplica.Parsed parsed = XaeroParserReplica.parse(XaeroShareFormat.shareMessage(waypoint));

		assertEquals(name, parsed.name());
		assertEquals(120, parsed.x());
		assertEquals(64, parsed.y());
		assertEquals(-340, parsed.z());
		assertEquals(TestCategories.FARMS.xaeroColorIndex(), parsed.colorIndex());
		assertFalse(parsed.rotation());
		assertEquals(0, parsed.yaw());
	}

	@Test
	void literalUnderscoreDimensionFormFromTheSpecAlsoParses() {
		// "Internal-the_nether-waypoints" (underscore) is read the same way as Xaero's own "the-nether".
		String line = "xaero-waypoint:Hub:H:1:2:3:13:false:0:Internal-the_nether-waypoints";
		assertEquals("the_nether", XaeroParserReplica.parse(line).dimensionNode());
	}

	@Test
	void initialsSkipLeadingSymbols() {
		assertEquals("M", XaeroShareFormat.initials("main"));
		assertEquals("F", XaeroShareFormat.initials("#farm"));
		assertEquals("-", XaeroShareFormat.initials("--"));
	}

	@Test
	void everyCategoryUsesAValidXaeroColour() {
		// Indexes 0-15 are Xaero's colours that match the vanilla chat colours (§0-§f).
		for (Category category : CategoryRegistry.DEFAULT.all()) {
			int index = category.xaeroColorIndex();
			assertTrue(index >= 0 && index <= 15, category + " -> " + index);
			assertEquals(category.color().getChar(), "0123456789abcdef".charAt(index), category.toString());
		}
	}
}
