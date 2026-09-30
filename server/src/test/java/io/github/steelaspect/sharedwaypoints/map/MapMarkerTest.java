package io.github.steelaspect.sharedwaypoints.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.steelaspect.sharedwaypoints.waypoint.TestCategories;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MapMarkerTest {
	private static final UUID ID = UUID.fromString("3f1c2b8e-6a0d-4e53-9d7c-2b1f6e4a9c10");

	private static Waypoint waypoint(String name, String description, String creator) {
		return new Waypoint(ID, name, TestCategories.STORAGE, 120, 64, -340, "minecraft:overworld", description,
				Waypoint.SERVER_UUID, creator, Instant.EPOCH);
	}

	@Test
	void markerCarriesPositionColourAndStableKey() {
		MapMarker marker = MapMarker.of(waypoint("Main Storage", "Sorted chests", "Steve"));
		assertEquals("wp-3f1c2b8e-6a0d-4e53-9d7c-2b1f6e4a9c10", marker.key());
		assertEquals("minecraft:overworld", marker.dimension());
		assertEquals(120.5, marker.x());
		assertEquals(64.0, marker.y());
		assertEquals(-339.5, marker.z());
		assertEquals(0x55FFFF, marker.rgb(), "storage is aqua");
		assertEquals("<div class=\"sharedwaypoints-marker\"><b>Main Storage</b><br>Storage · 120 64 -340 · overworld"
				+ "<br><i>Sorted chests</i><br><small>Added by Steve</small></div>", marker.detailHtml());
	}

	@Test
	void playerTextCannotInjectHtml() {
		MapMarker marker = MapMarker.of(waypoint("x\"onmouseover='alert(1)'",
				"<script>alert('hi')</script> & more", "<img src=x onerror=alert(1)>"));
		assertFalse(marker.detailHtml().contains("<script"), marker.detailHtml());
		assertFalse(marker.detailHtml().contains("<img"), marker.detailHtml());
		assertFalse(marker.labelHtml().contains("'") || marker.labelHtml().contains("\""), marker.labelHtml());
		assertTrue(marker.detailHtml().contains("&lt;script&gt;alert(&#39;hi&#39;)&lt;/script&gt; &amp; more"));
		assertEquals("x\"onmouseover='alert(1)'", marker.label(), "plain label is untouched (BlueMap escapes it)");
	}

	@Test
	void iconsAreRoundAndInTheCategoryColour() {
		BufferedImage icon = MarkerIcons.image(0x55FFFF);
		assertEquals(MarkerIcons.SIZE, icon.getWidth());
		assertEquals(MarkerIcons.SIZE, icon.getHeight());
		assertEquals(0, icon.getRGB(0, 0) >>> 24, "corners are transparent");
		int ring = icon.getRGB(MarkerIcons.SIZE / 2, 5);
		assertEquals(0x55FFFF, ring & 0xFFFFFF, "disc uses the category colour");
		assertTrue(MarkerIcons.dataUri(0x55FFFF).startsWith("data:image/png;base64,iVBOR"), "PNG data URI");
	}
}
