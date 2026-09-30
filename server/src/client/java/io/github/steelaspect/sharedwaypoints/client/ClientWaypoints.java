package io.github.steelaspect.sharedwaypoints.client;

import io.github.steelaspect.sharedwaypoints.network.ResultPayload;
import io.github.steelaspect.sharedwaypoints.network.SyncPayload;
import io.github.steelaspect.sharedwaypoints.waypoint.Category;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;

/**
 * What the client knows from the server: the latest {@link SyncPayload} and the last action result. Only touched
 * on the client thread (network handlers and screens both run there).
 */
public final class ClientWaypoints {
	private static SyncPayload snapshot;
	private static Map<String, Category> categories = Map.of();
	private static List<Waypoint> waypoints = List.of();
	private static ResultPayload lastResult;
	private static long lastResultAt;
	private static Runnable listener = () -> {
	};

	private ClientWaypoints() {
	}

	/** A fresh snapshot arrived. */
	static void update(SyncPayload payload) {
		snapshot = payload;
		Map<String, Category> byId = new LinkedHashMap<>();
		for (SyncPayload.CategoryData data : payload.categories()) {
			ChatFormatting color = ChatFormatting.getById(data.colorId());
			byId.put(data.id(), new Category(data.id(), data.name(),
					color != null && color.isColor() ? color : ChatFormatting.WHITE, byId.size()));
		}
		categories = byId;
		// Rebuilt as the common Waypoint record so the menu can reuse the same maths and Xaero formatting.
		waypoints = payload.waypoints().stream()
				.map(data -> new Waypoint(data.id(), data.name(), category(data.categoryId()), data.x(), data.y(),
						data.z(), data.dimension(), data.description(), Waypoint.SERVER_UUID, data.creatorName(),
						Instant.ofEpochSecond(data.createdEpochSecond())))
				.toList();
		listener.run();
	}

	static void result(ResultPayload payload) {
		lastResult = payload;
		lastResultAt = System.currentTimeMillis();
		listener.run();
	}

	/** Forget everything (left the server). */
	static void clear() {
		snapshot = null;
		categories = Map.of();
		waypoints = List.of();
		lastResult = null;
	}

	/** The open menu listens for updates; only one menu is open at a time. */
	static void setListener(Runnable newListener) {
		listener = newListener == null ? () -> {
		} : newListener;
	}

	public static boolean hasData() {
		return snapshot != null;
	}

	public static List<Waypoint> waypoints() {
		return waypoints;
	}

	public static List<Category> categories() {
		return List.copyOf(categories.values());
	}

	public static Category category(String id) {
		return categories.getOrDefault(id, Category.unknown(id));
	}

	public static Optional<SyncPayload.WaypointData> data(UUID id) {
		return snapshot == null ? Optional.empty()
				: snapshot.waypoints().stream().filter(data -> data.id().equals(id)).findFirst();
	}

	public static boolean canAdd() {
		return snapshot != null && snapshot.canAdd();
	}

	public static boolean canTeleport() {
		return snapshot != null && snapshot.canTeleport();
	}

	public static UUID navigatingTo() {
		return snapshot == null ? null : snapshot.navigatingTo();
	}

	/** Every route, sorted by name. */
	public static List<SyncPayload.RouteData> routes() {
		return snapshot == null ? List.of() : snapshot.routes();
	}

	public static Optional<SyncPayload.RouteData> route(UUID id) {
		return routes().stream().filter(route -> route.id().equals(id)).findFirst();
	}

	/** The route's stops as waypoints, in order. */
	public static List<Waypoint> stops(SyncPayload.RouteData route) {
		return route.stops().stream()
				.map(id -> waypoints.stream().filter(waypoint -> waypoint.id().equals(id)).findFirst())
				.flatMap(Optional::stream)
				.toList();
	}

	public static boolean canAddRoute() {
		return snapshot != null && snapshot.canAddRoute();
	}

	/** The route the player is following, or null. */
	public static SyncPayload.RouteProgressData onRoute() {
		return snapshot == null ? null : snapshot.onRoute();
	}

	/** The last action result, if it arrived in the last few seconds. */
	public static Optional<ResultPayload> recentResult() {
		return lastResult != null && System.currentTimeMillis() - lastResultAt < 6000 ? Optional.of(lastResult) : Optional.empty();
	}
}
