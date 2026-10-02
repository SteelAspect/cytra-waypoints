package io.github.steelaspect.sharedwaypoints.network;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.steelaspect.sharedwaypoints.ModContext;
import io.github.steelaspect.sharedwaypoints.SharedWaypoints;
import io.github.steelaspect.sharedwaypoints.permission.WaypointPermissions;
import io.github.steelaspect.sharedwaypoints.waypoint.Category;
import io.github.steelaspect.sharedwaypoints.waypoint.ProjectStatus;
import io.github.steelaspect.sharedwaypoints.waypoint.Route;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of the optional client menu.
 *
 * <p>Players with the mod on their client get a {@link SyncPayload} when they open the menu and after every change.
 * Buttons arrive as {@link ActionPayload}s, which are turned into the matching /cway command and run as the
 * player, so permissions and validation are exactly the same as typing the command. The command's reply goes back
 * as a {@link ResultPayload}. Players without the client mod never receive anything from here.
 */
public final class MenuNetworking {
	private static final Pattern CATEGORY_ID = Pattern.compile("[a-z0-9_-]{1,24}");
	private static final Pattern DIMENSION_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

	private final ModContext mod;
	private MinecraftServer server;

	public MenuNetworking(ModContext mod) {
		this.mod = mod;
	}

	/** Registers the payload types. Must run on both sides, from the common entrypoint. */
	public static void registerPayloads() {
		PayloadTypeRegistry.playS2C().registerLarge(SyncPayload.TYPE, SyncPayload.CODEC, SyncPayload.MAX_SIZE);
		PayloadTypeRegistry.playS2C().register(ResultPayload.TYPE, ResultPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ActionPayload.TYPE, ActionPayload.CODEC);
	}

	/** Registers the server receiver for menu actions (handlers run on the server thread). */
	public void registerReceiver() {
		ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE, (payload, context) -> {
			ResultPayload result = handle(context.player(), payload);
			if (result != null && ServerPlayNetworking.canSend(context.player(), ResultPayload.TYPE)) {
				ServerPlayNetworking.send(context.player(), result);
			}
		});
	}

	public void setServer(MinecraftServer server) {
		this.server = server;
	}

	// ------------------------------------------------------------------ sync

	/** Sends a fresh snapshot to one player, if their client has the menu. */
	public void pushTo(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, SyncPayload.TYPE)) {
			ServerPlayNetworking.send(player, snapshot(player));
		}
	}

	public void pushTo(UUID playerId) {
		if (server != null) {
			Optional.ofNullable(server.getPlayerList().getPlayer(playerId)).ifPresent(this::pushTo);
		}
	}

	/** Sends fresh snapshots to everyone with the menu (after any waypoint change). */
	public void pushToAll() {
		if (server != null) {
			server.getPlayerList().getPlayers().forEach(this::pushTo);
		}
	}

	/** What this player's menu shows: only what they may see, with their own favourites and edit rights. */
	public SyncPayload snapshot(ServerPlayer player) {
		CommandSourceStack source = player.createCommandSourceStack();
		if (!WaypointPermissions.canView(player)) {
			return new SyncPayload(List.of(), List.of(), false, false, null, List.of(), false, null);
		}
		Set<UUID> favorites = mod.favorites().of(player.getUUID());
		Map<String, SyncPayload.CategoryData> categories = new LinkedHashMap<>();
		mod.categories().all().forEach(category -> categories.put(category.id(), categoryData(category)));

		List<SyncPayload.WaypointData> waypoints = new ArrayList<>();
		for (Waypoint waypoint : mod.waypoints().all()) {
			// Categories removed from config.json still need a name and colour in the menu.
			categories.putIfAbsent(waypoint.category().id(), categoryData(waypoint.category()));
			waypoints.add(new SyncPayload.WaypointData(waypoint.id(), waypoint.name(), waypoint.category().id(),
					waypoint.x(), waypoint.y(), waypoint.z(), waypoint.dimension(), waypoint.description(),
					waypoint.creatorName(), waypoint.created().getEpochSecond(),
					WaypointPermissions.canEdit(source, waypoint), WaypointPermissions.canRemove(source, waypoint),
					favorites.contains(waypoint.id()), statusData(waypoint.status()),
					WaypointPermissions.canSetStatus(source, waypoint)));
		}
		List<SyncPayload.RouteData> routes = new ArrayList<>();
		for (Route route : mod.routes().all()) {
			routes.add(new SyncPayload.RouteData(route.id(), route.name(), route.stops(), route.description(),
					route.creatorName(), WaypointPermissions.canEdit(source, route),
					WaypointPermissions.canRemove(source, route)));
		}
		SyncPayload.RouteProgressData onRoute = mod.navigation().routeOf(player.getUUID())
				.map(progress -> new SyncPayload.RouteProgressData(progress.routeId(), progress.index()))
				.orElse(null);
		return new SyncPayload(List.copyOf(categories.values()), waypoints, WaypointPermissions.canAdd(source),
				WaypointPermissions.canTeleport(source), mod.navigation().destinationOf(player.getUUID()).orElse(null),
				routes, WaypointPermissions.canCreateRoute(source), onRoute);
	}

	private static SyncPayload.StatusData statusData(ProjectStatus status) {
		return status == null ? null : new SyncPayload.StatusData(status.state().id(), status.note(), status.setByName(),
				status.setAt().getEpochSecond());
	}

	private static SyncPayload.CategoryData categoryData(Category category) {
		return new SyncPayload.CategoryData(category.id(), category.displayName(), category.color().getId());
	}

	// --------------------------------------------------------------- actions

	/**
	 * Runs a menu action as the player. Returns the reply to show in the menu, or null for {@code SYNC}.
	 * Public so the GameTest can drive it without a real client.
	 */
	public ResultPayload handle(ServerPlayer player, ActionPayload payload) {
		List<String> args = payload.args();
		String command;
		try {
			command = switch (payload.action()) {
				case SYNC -> null;
				case GO -> "cway go " + name(args, 0);
				case STOP -> "cway stop";
				case FAVORITE -> "cway favorite " + name(args, 0);
				case TELEPORT -> "cway tp " + name(args, 0);
				case REMOVE -> "cway remove " + name(args, 0);
				case RENAME -> "cway rename " + name(args, 0) + " " + quoted(arg(args, 1));
				case DESCRIBE -> {
					String text = oneLine(arg(args, 1));
					yield "cway describe " + name(args, 0) + (text.isEmpty() ? "" : " " + text);
				}
				case ADD -> "cway add " + quoted(arg(args, 0)) + " " + matching(arg(args, 1), CATEGORY_ID)
						+ " " + Integer.parseInt(arg(args, 2)) + " " + Integer.parseInt(arg(args, 3))
						+ " " + Integer.parseInt(arg(args, 4)) + " " + matching(arg(args, 5), DIMENSION_ID);
				case ROUTE_GO -> "cway route go " + routeName(args, 0) + " " + Integer.parseInt(arg(args, 1));
				case ROUTE_SKIP -> "cway route skip";
				case ROUTE_CREATE -> "cway route create " + quoted(arg(args, 0));
				case ROUTE_ADD -> "cway route add " + routeName(args, 0) + " " + name(args, 1);
				case ROUTE_DROP -> "cway route drop " + routeName(args, 0) + " " + Integer.parseInt(arg(args, 1));
				case ROUTE_MOVE -> "cway route move " + routeName(args, 0) + " " + Integer.parseInt(arg(args, 1))
						+ " " + Integer.parseInt(arg(args, 2));
				case ROUTE_RENAME -> "cway route rename " + routeName(args, 0) + " " + quoted(arg(args, 1));
				case ROUTE_DESCRIBE -> {
					String text = oneLine(arg(args, 1));
					yield "cway route describe " + routeName(args, 0) + (text.isEmpty() ? "" : " " + text);
				}
				case ROUTE_DELETE -> "cway route delete " + routeName(args, 0);
				case STATUS -> {
					String state = arg(args, 1);
					if (!state.equals("clear") && ProjectStatus.State.byId(state).isEmpty()) {
						throw new BadRequest("Invalid value: " + state);
					}
					String note = args.size() > 2 ? oneLine(args.get(2)) : "";
					yield "cway status " + name(args, 0) + " " + state.toLowerCase(java.util.Locale.ROOT)
							+ (note.isEmpty() || state.equals("clear") ? "" : " " + note);
				}
			};
		} catch (BadRequest e) {
			return new ResultPayload(false, e.getMessage());
		} catch (NumberFormatException e) {
			return new ResultPayload(false, "Numbers must be whole numbers");
		}
		if (command == null) {
			pushTo(player);
			return null;
		}
		ResultPayload result = run(player, command);
		// Favourites and navigation aren't waypoint changes, so refresh this player's menu explicitly.
		pushTo(player);
		return result;
	}

	/** Runs a command as the player and collects what it replies. */
	private ResultPayload run(ServerPlayer player, String command) {
		Collector replies = new Collector();
		CommandSourceStack source = player.createCommandSourceStack().withSource(replies);
		try {
			int result = player.level().getServer().getCommands().getDispatcher().execute(command, source);
			String message = replies.first != null ? replies.first : "Done";
			return new ResultPayload(result > 0, message);
		} catch (CommandSyntaxException e) {
			return new ResultPayload(false, e.getRawMessage().getString());
		} catch (RuntimeException e) {
			SharedWaypoints.LOGGER.error("Menu action failed: /{}", command, e);
			return new ResultPayload(false, "Something went wrong, see the server log");
		}
	}

	/** Remembers the first reply a command sends (the headline, e.g. "Added waypoint Farm"). */
	private static final class Collector implements CommandSource {
		String first;

		@Override
		public void sendSystemMessage(Component message) {
			if (first == null) {
				// Chat buttons such as "[View favourites]" mean nothing in the menu's status line.
				first = message.getString().replaceAll("\\s*\\[[^\\]]*\\]\\s*$", "").trim();
			}
		}

		@Override
		public boolean acceptsSuccess() {
			return true;
		}

		@Override
		public boolean acceptsFailure() {
			return true;
		}

		@Override
		public boolean shouldInformAdmins() {
			return false;
		}
	}

	// ---------------------------------------------------------------- helpers

	private static final class BadRequest extends Exception {
		BadRequest(String message) {
			super(message);
		}
	}

	/** Argument {@code index}, or a bad request if it's missing. */
	private static String arg(List<String> args, int index) throws BadRequest {
		if (index >= args.size()) {
			throw new BadRequest("Incomplete request from the menu");
		}
		return args.get(index);
	}

	/** The (quoted) current name of the waypoint whose id is argument {@code index}. */
	private String name(List<String> args, int index) throws BadRequest {
		try {
			UUID id = UUID.fromString(arg(args, index));
			return quoted(mod.waypoints().get(id).orElseThrow(() -> new BadRequest("That waypoint no longer exists")).name());
		} catch (IllegalArgumentException e) {
			throw new BadRequest("Incomplete request from the menu");
		}
	}

	/** The (quoted) current name of the route whose id is argument {@code index}. */
	private String routeName(List<String> args, int index) throws BadRequest {
		try {
			UUID id = UUID.fromString(arg(args, index));
			return quoted(mod.routes().get(id).orElseThrow(() -> new BadRequest("That route no longer exists")).name());
		} catch (IllegalArgumentException e) {
			throw new BadRequest("Incomplete request from the menu");
		}
	}

	private static String quoted(String text) {
		return StringArgumentType.escapeIfRequired(oneLine(text));
	}

	private static String matching(String text, Pattern pattern) throws BadRequest {
		if (!pattern.matcher(text).matches()) {
			throw new BadRequest("Invalid value: " + text);
		}
		return text;
	}

	/** Descriptions and names are single-line; newlines would break the command. */
	private static String oneLine(String text) {
		return text.replaceAll("[\\r\\n\\t]+", " ").trim();
	}
}
