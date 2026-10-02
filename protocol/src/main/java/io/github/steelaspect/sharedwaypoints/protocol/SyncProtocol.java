package io.github.steelaspect.sharedwaypoints.protocol;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * The sync protocol between the SharedWaypoints server and players' games running SharedWaypoints (or the old
 * sharedwaypoints-client jar, 2.2 and earlier).
 *
 * <ol>
 *   <li>On join a game with the mod sends {@link HelloPayload} with its {@link #VERSION}.</li>
 *   <li>The server answers {@link WelcomePayload} with its version. If they match and sync is enabled, it sends
 *       the full list ({@link FullSyncPayload}) and from then on every add or edit ({@link UpsertPayload}) and
 *       delete ({@link DeletePayload}).</li>
 * </ol>
 *
 * <p>Players without the mod never send a hello, so the server never sends them any of this. Every payload
 * carries the protocol version, and a client ignores payloads from another version.
 */
public final class SyncProtocol {
	/** Bump whenever a payload's layout or meaning changes. */
	public static final int VERSION = 1;
	/** Namespace of the channel ids. */
	public static final String NAMESPACE = "sharedwaypoints";

	private static boolean registered;

	private SyncProtocol() {
	}

	/**
	 * Registers the payload types. Both mods call this before registering their receivers; only the first call does
	 * anything, because a payload type may only be registered once (a player can have both mods installed).
	 */
	public static synchronized void register() {
		if (registered) {
			return;
		}
		registered = true;
		PayloadTypeRegistry.playC2S().register(HelloPayload.TYPE, HelloPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(WelcomePayload.TYPE, WelcomePayload.CODEC);
		PayloadTypeRegistry.playS2C().registerLarge(FullSyncPayload.TYPE, FullSyncPayload.CODEC, FullSyncPayload.MAX_SIZE);
		PayloadTypeRegistry.playS2C().register(UpsertPayload.TYPE, UpsertPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(DeletePayload.TYPE, DeletePayload.CODEC);
	}
}
