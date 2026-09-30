package io.github.steelaspect.sharedwaypoints.xaerosync;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class XaeroBridgeTest {
	@Test
	void withoutXaerosMinimapSyncIsSimplyOff() {
		// The unit-test environment has no Xaero's Minimap: the bridge must say so (one log line) and not throw.
		assertTrue(XaeroBridge.create().isEmpty());
	}
}
