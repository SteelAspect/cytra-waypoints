package io.github.steelaspect.sharedwaypoints.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.steelaspect.sharedwaypoints.network.ActionPayload;
import io.github.steelaspect.sharedwaypoints.network.ResultPayload;
import io.github.steelaspect.sharedwaypoints.network.SyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Optional client side: a keybind (J by default) opens the waypoint menu. Everything still works without it
 * (chat commands and clickable text); with it, players get a proper screen. The server decides everything; the
 * client only shows what the server sends and asks the server to act.
 */
public final class SharedWaypointsClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath("sharedwaypoints", "main"));
	private static final Identifier OLD_ACTION_CHANNEL = Identifier.fromNamespaceAndPath("sharedwaypoints", "action");
	private static KeyMapping openMenu;

	@Override
	public void onInitializeClient() {
		openMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.sharedwaypoints.open_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY));

		ClientPlayNetworking.registerGlobalReceiver(SyncPayload.TYPE, (payload, context) -> ClientWaypoints.update(payload));
		ClientPlayNetworking.registerGlobalReceiver(ResultPayload.TYPE, (payload, context) -> ClientWaypoints.result(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientWaypoints.clear());

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openMenu.consumeClick()) {
				openMenu(client);
			}
		});
	}

	/** The "open waypoint menu" key (J by default). */
	public static KeyMapping openMenuKey() {
		return openMenu;
	}

	/** Opens the menu, or explains why not (server without the mod). */
	public static void openMenu(Minecraft client) {
		if (client.player == null) {
			return;
		}
		if (!ClientPlayNetworking.canSend(ActionPayload.TYPE)) {
			// 1.4.x servers speak the first version of the menu protocol ("action" instead of "action2").
			boolean olderServer = ClientPlayNetworking.getSendable().contains(OLD_ACTION_CHANNEL);
			client.player.displayClientMessage(Component.literal(olderServer
					? "This server runs an older SharedWaypoints; the menu needs the same version on both sides"
					: "SharedWaypoints isn't installed on this server").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		client.setScreen(new WaypointMenuScreen());
	}

	/** Sends a menu action to the server. */
	static void send(ActionPayload.Action action, String... args) {
		ClientPlayNetworking.send(ActionPayload.of(action, args));
	}
}
