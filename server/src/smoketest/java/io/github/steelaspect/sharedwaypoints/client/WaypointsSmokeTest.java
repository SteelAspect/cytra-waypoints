package io.github.steelaspect.sharedwaypoints.client;

import io.github.steelaspect.sharedwaypoints.network.ActionPayload;
import io.github.steelaspect.sharedwaypoints.network.SyncPayload;
import io.github.steelaspect.sharedwaypoints.protocol.HelloPayload;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import io.github.steelaspect.sharedwaypoints.xaerosync.SharedWaypointsSync;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 1.20.4 stand-in for the client game test (fabric-client-gametest-api-v1 starts at 1.21.4). Run with
 * {@code ./headless.sh ./smoketest.sh}: two dedicated 1.20.4 servers start, one with the built Alloy Waypoints jar and
 * one with only Fabric API. This client checks the settings screen on the title screen, joins the first server, waits
 * for the menu sync, adds a waypoint and a route through the menu protocol, checks WaypointsApi and opens every menu
 * screen and the Esc-menu button; then joins the server without the mod and checks the client stays quiet there.
 * Screenshots go to build/run/smoke/screenshots; logs {@code [WaypointsSmoke] PASS} or {@code FAIL: ...} and quits.
 */
public class WaypointsSmokeTest implements ClientModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("WaypointsSmoke");
	private static final int TIMEOUT_TICKS = 20 * 90;
	private static final String SPOT = "Smoke Spot";
	private static final String ROUTE = "Smoke Route";
	private static final boolean XAERO = FabricLoader.getInstance().isModLoaded("xaerominimap");

	private final List<String> failures = new ArrayList<>();
	private int step;
	private int wait = 40;
	private BooleanSupplier until;
	private int untilTicks;
	private String untilWhat;
	private boolean done;

	@Override
	public void onInitializeClient() {
		if (!Boolean.getBoolean("sharedwaypoints.smoke")) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
	}

	private void onTick(Minecraft mc) {
		if (done) {
			return;
		}
		if (until != null) {
			if (until.getAsBoolean()) {
				until = null;
			} else if (++untilTicks > TIMEOUT_TICKS) {
				fail("timed out waiting for " + untilWhat);
				finish(mc);
				return;
			} else {
				return;
			}
		}
		if (wait > 0) {
			wait--;
			return;
		}
		try {
			run(mc);
		} catch (Throwable t) {
			LOG.error("[WaypointsSmoke] step {} threw", step - 1, t);
			fail("step " + (step - 1) + " threw " + t);
			finish(mc);
		}
	}

	private void run(Minecraft mc) {
		switch (step++) {
			case 0 -> waitFor("title screen", () -> mc.screen instanceof TitleScreen);
			case 1 -> {
				mc.setScreen(new SettingsScreen(null));
				wait = 10;
			}
			case 2 -> {
				expect(mc, SettingsScreen.class);
				shot(mc, "settings");
				mc.setScreen(null);
				connect(mc, System.getProperty("sharedwaypoints.smoke.server", "localhost:25570"));
			}
			case 3 -> {
				LOG.info("[WaypointsSmoke] Xaero's Minimap installed: {}", XAERO);
				waitFor("the menu data from the server", ClientWaypoints::hasData);
			}
			case 4 -> {
				check(WaypointsApi.canAdd(), "WaypointsApi.canAdd() is false on a server with the mod");
				check(ClientPlayNetworking.canSend(HelloPayload.TYPE), "server did not register the Xaero sync channel");
				String category = ClientWaypoints.categories().get(0).id();
				SharedWaypointsClient.send(ActionPayload.Action.ADD, SPOT, category, "10", "64", "-20", "minecraft:overworld");
				waitFor("the added waypoint to sync back", () -> spot().isPresent() && (!XAERO || inXaero(SPOT)));
			}
			case 5 -> {
				check(ClientWaypoints.data(spot().get().id()).map(SyncPayload.WaypointData::canEdit).orElse(false),
						"creator cannot edit their own waypoint");
				check(WaypointsApi.openWaypoint(SPOT, null), "WaypointsApi.openWaypoint refused");
				wait = 10;
			}
			case 6 -> {
				expect(mc, WaypointMenuScreen.class);
				shot(mc, "menu");
				check(!WaypointsApi.openWaypoint("No Such Waypoint", null), "openWaypoint accepted an unknown name");
				check(WaypointsApi.openAdd(100, 70, 200, "minecraft:the_nether", mc.screen), "WaypointsApi.openAdd refused");
				wait = 10;
			}
			case 7 -> {
				expect(mc, AddWaypointScreen.class);
				shot(mc, "add");
				mc.setScreen(new EditWaypointScreen(null, spot().get()));
				wait = 10;
			}
			case 8 -> {
				expect(mc, EditWaypointScreen.class);
				shot(mc, "edit");
				mc.setScreen(new StatusScreen(null, spot().get()));
				wait = 10;
			}
			case 9 -> {
				expect(mc, StatusScreen.class);
				shot(mc, "status");
				SharedWaypointsClient.send(ActionPayload.Action.ROUTE_CREATE, ROUTE);
				waitFor("the new route to sync back", () -> route().isPresent());
			}
			case 10 -> {
				SharedWaypointsClient.send(ActionPayload.Action.ROUTE_ADD, route().get().id().toString(), spot().get().id().toString());
				waitFor("the route stop to sync back", () -> route().map(r -> r.stops().size() == 1).orElse(false));
			}
			case 11 -> {
				mc.setScreen(new RoutesScreen(null));
				wait = 10;
			}
			case 12 -> {
				expect(mc, RoutesScreen.class);
				shot(mc, "routes");
				mc.setScreen(new EditRouteScreen(mc.screen, route().get()));
				wait = 10;
			}
			case 13 -> {
				expect(mc, EditRouteScreen.class);
				shot(mc, "edit-route");
				mc.setScreen(new WaypointPickerScreen(null, route().get()));
				wait = 10;
			}
			case 14 -> {
				expect(mc, WaypointPickerScreen.class);
				shot(mc, "route-picker");
				mc.setScreen(null);
				SharedWaypointsClient.openMenu(mc);
				wait = 10;
			}
			case 15 -> {
				expect(mc, WaypointMenuScreen.class);
				mc.setScreen(new PauseScreen(true));
				wait = 10;
			}
			case 16 -> {
				check(hasPauseButton(mc.screen), "no Waypoints button on the Esc menu");
				shot(mc, "pause");
				SharedWaypointsClient.send(ActionPayload.Action.REMOVE, spot().get().id().toString());
				waitFor("the removed waypoint to sync back", () -> spot().isEmpty() && (!XAERO || !inXaero(SPOT)));
			}
			case 17 -> {
				mc.setScreen(null);
				LOG.info("[WaypointsSmoke] server with the mod: OK; joining the one without");
				disconnect(mc);
				connect(mc, System.getProperty("sharedwaypoints.smoke.plainServer", "localhost:25571"));
			}
			case 18 -> {
				check(!ClientWaypoints.hasData(), "menu data left over from the other server");
				check(!WaypointsApi.canAdd(), "WaypointsApi.canAdd() is true on a server without the mod");
				check(!WaypointsApi.openWaypoint(SPOT, null), "WaypointsApi.openWaypoint opened on a server without the mod");
				check(!ClientPlayNetworking.canSend(ActionPayload.TYPE), "a server without the mod accepts menu actions");
				SharedWaypointsClient.openMenu(mc);
				wait = 10;
			}
			case 19 -> {
				check(mc.screen == null, "the menu opened on a server without the mod: " + mc.screen);
				mc.setScreen(new PauseScreen(true));
				wait = 10;
			}
			case 20 -> {
				check(!hasPauseButton(mc.screen), "Waypoints button on the Esc menu of a server without the mod");
				shot(mc, "pause-plain-server");
				mc.setScreen(null);
				wait = 20;
			}
			default -> finish(mc);
		}
	}

	/** In Xaero's "Shared" set for the overworld (only when Xaero's Minimap is installed: smoketest.sh WITH_XAERO=1). */
	private static boolean inXaero(String name) {
		return SharedWaypointsSync.isActive() && SharedWaypointsSync.sharedWaypointNames("minecraft:overworld")
				.map(names -> names.contains(name)).orElse(false);
	}

	private static Optional<Waypoint> spot() {
		return ClientWaypoints.waypoints().stream().filter(waypoint -> waypoint.name().equals(SPOT)).findFirst();
	}

	private static Optional<SyncPayload.RouteData> route() {
		return ClientWaypoints.routes().stream().filter(route -> route.name().equals(ROUTE)).findFirst();
	}

	private static boolean hasPauseButton(Screen screen) {
		String label = Component.translatable("sharedwaypoints.pause.button").getString();
		return screen instanceof PauseScreen && Screens.getButtons(screen).stream()
				.map(AbstractWidget::getMessage).anyMatch(message -> message.getString().equals(label));
	}

	private void connect(Minecraft mc, String address) {
		LOG.info("[WaypointsSmoke] joining {}", address);
		ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address),
				new ServerData("smoke", address, ServerData.Type.OTHER), false);
		waitFor("joining " + address, () -> mc.player != null && mc.level != null && mc.screen == null);
		wait = 40;
	}

	private static void disconnect(Minecraft mc) {
		if (mc.level != null) {
			mc.level.disconnect();
		}
		mc.disconnect();
	}

	private void waitFor(String what, BooleanSupplier condition) {
		until = condition;
		untilTicks = 0;
		untilWhat = what;
	}

	private void expect(Minecraft mc, Class<? extends Screen> type) {
		check(type.isInstance(mc.screen), type.getSimpleName() + " did not open: " + mc.screen);
	}

	private void check(boolean ok, String message) {
		if (!ok) {
			fail(message);
		}
	}

	private void fail(String message) {
		LOG.error("[WaypointsSmoke] check failed: {}", message);
		failures.add(message);
	}

	private static void shot(Minecraft mc, String name) {
		Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
				text -> LOG.info("[WaypointsSmoke] {}", text.getString()));
	}

	private void finish(Minecraft mc) {
		done = true;
		if (failures.isEmpty()) {
			LOG.info("[WaypointsSmoke] PASS");
		} else {
			LOG.error("[WaypointsSmoke] FAIL: {}", failures);
		}
		disconnect(mc);
		mc.stop();
	}
}
