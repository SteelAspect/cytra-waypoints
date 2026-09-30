package io.github.steelaspect.sharedwaypoints.xaerosync;

import io.github.steelaspect.sharedwaypoints.protocol.SyncProtocol;
import net.fabricmc.api.ClientModInitializer;

/** Client entrypoint of the optional Xaero's Minimap sync mod. */
public final class SharedWaypointsSync implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SyncProtocol.register();
	}
}
