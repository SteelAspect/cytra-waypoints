package io.github.steelaspect.sharedwaypoints.client;

import dev.steelaspect.alloyui.AlloyScreen;
import dev.steelaspect.alloyui.Toast;
import dev.steelaspect.alloyui.widget.Button;
import dev.steelaspect.alloyui.widget.Widget;
import io.github.steelaspect.sharedwaypoints.network.ResultPayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Base of the waypoint screens (Nocturne look through Alloy UI): listens for server updates while open, shows the
 * server's answers to the player's actions as toasts, and lets the client test press buttons and menu items by
 * their label.
 */
public abstract class WaypointsScreen extends AlloyScreen {
	/** The last result already shown as a toast, so each answer is shown once. */
	private static @Nullable ResultPayload shownResult;

	private final List<Widget> openOverlays = new ArrayList<>();
	/** Actions that aren't buttons (menu items), by label, for {@link #pressForTest}. */
	private final Map<String, Runnable> testActions = new LinkedHashMap<>();

	protected WaypointsScreen(Component title, @Nullable Screen parent) {
		super(title, parent);
	}

	@Override
	protected void init() {
		super.init();
		ClientWaypoints.setListener(() -> {
			showNewResult();
			onServerUpdate();
		});
		// A result that arrived while another screen was open (e.g. the form that sent it) is shown here.
		showNewResult();
	}

	/** A new snapshot or action result arrived from the server. */
	protected void onServerUpdate() {
	}

	private void showNewResult() {
		ClientWaypoints.recentResult().ifPresent(result -> {
			if (result != shownResult) {
				shownResult = result;
				toast(result.success() ? Toast.Kind.SUCCESS : Toast.Kind.ERROR, Component.literal(result.message()));
			}
		});
	}

	/** Registers a menu item's action so the client test can run it by label. */
	protected Runnable testAction(Component label, Runnable action) {
		testActions.put(label.getString(), action);
		return action;
	}

	@Override
	public void openOverlay(Widget overlay, boolean modal) {
		super.openOverlay(overlay, modal);
		openOverlays.add(overlay);
	}

	@Override
	public void closeOverlay(Widget overlay) {
		super.closeOverlay(overlay);
		openOverlays.remove(overlay);
	}

	/** For tests: whether a dialog or menu is open. */
	public boolean overlayOpenForTest() {
		return hasOverlay();
	}

	/**
	 * For tests: presses the enabled button with this label (overlays first, then the screen), or runs the menu item
	 * with this label. Returns false if there's none.
	 */
	public boolean pressForTest(String label) {
		for (int i = openOverlays.size() - 1; i >= 0; i--) {
			Button b = find(openOverlays.get(i), label);
			if (b != null) {
				b.press();
				return true;
			}
		}
		Button b = find(root(), label);
		if (b != null) {
			b.press();
			return true;
		}
		Runnable action = testActions.get(label);
		if (action != null) {
			action.run();
			return true;
		}
		return false;
	}

	private static @Nullable Button find(@Nullable Widget w, String label) {
		if (w == null || !w.visible()) {
			return null;
		}
		if (w instanceof Button b && b.enabled() && b.searchLabel() != null && b.searchLabel().getString().equals(label)) {
			return b;
		}
		for (Widget child : w.children()) {
			Button found = find(child, label);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	@Override
	public void removed() {
		ClientWaypoints.setListener(null);
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
