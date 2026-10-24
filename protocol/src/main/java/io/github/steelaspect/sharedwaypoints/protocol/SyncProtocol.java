package io.github.steelaspect.sharedwaypoints.protocol;

/**
 * The sync protocol between the Cytra Waypoints server and players' games running Cytra Waypoints (or the old
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

	private SyncProtocol() {
	}
}
