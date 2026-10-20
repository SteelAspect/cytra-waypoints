package io.github.steelaspect.sharedwaypoints.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.steelaspect.alloyui.widget.Button;
import dev.steelaspect.alloyui.widget.Column;
import dev.steelaspect.alloyui.widget.KeybindPicker;
import dev.steelaspect.alloyui.widget.ScrollPanel;
import dev.steelaspect.alloyui.widget.SettingRow;
import dev.steelaspect.alloyui.widget.Widget;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Alloy Waypoints' own settings (⚙ in the menu, Mod Menu): the rows from {@link WaypointOptions}, plus the menu
 * key. The server's settings stay in its config file.
 */
public final class SettingsScreen extends WaypointsScreen {
	private final Column rows;

	public SettingsScreen(@Nullable Screen parent) {
		super(Ui.tr("settings.title"), parent);
		rows = WaypointOptions.create().build();
		KeyMapping key = SharedWaypointsClient.openMenuKey();
		if (key != null) {
			rows.add(new SettingRow(Ui.tr("option.key"), Ui.tr("option.key.hint"),
					new KeybindPicker(() -> InputConstants.getKey(key.saveString()), value -> {
						key.setKey(value);
						KeyMapping.resetMapping();
						Minecraft.getInstance().options.save();
					})));
		}
		Frame frame = new Frame(Ui.tr("settings.title")).maxSize(420, 340);
		frame.body(new ScrollPanel(rows));
		frame.footerText(() -> Ui.tr("settings.server_note"));
		Button done = frame.footer(new Button(Component.translatable("gui.done"), this::onClose));
		done.style(Button.Style.PRIMARY);
		setRoot(frame);
	}

	/** For tests: the row whose label is this option's ({@code sharedwaypoints.option.<id>}), or null. */
	public @Nullable SettingRow rowForTest(String id) {
		return find(rows, Component.translatable("sharedwaypoints.option." + id).getString());
	}

	private static @Nullable SettingRow find(@Nullable Widget w, String label) {
		if (w == null) {
			return null;
		}
		if (w instanceof SettingRow r && r.label().getString().equals(label)) {
			return r;
		}
		for (Widget c : w.children()) {
			SettingRow r = find(c, label);
			if (r != null) {
				return r;
			}
		}
		return null;
	}
}
