package io.github.steelaspect.sharedwaypoints.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.steelaspect.sharedwaypoints.client.SettingsScreen;

/** Mod Menu's config button opens Alloy Waypoints' settings. Only loaded when Mod Menu is installed. */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SettingsScreen::new;
	}
}
