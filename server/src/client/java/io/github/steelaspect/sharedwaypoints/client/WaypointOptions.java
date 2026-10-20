package io.github.steelaspect.sharedwaypoints.client;

import dev.steelaspect.alloyui.widget.Options;
import java.util.List;

/**
 * The client settings as one option list. The settings screen builds its rows from it, and Alloy Hub can build the
 * same rows on the mod's page ({@link #create()} is public for that), so the two never disagree.
 */
public final class WaypointOptions {
	private WaypointOptions() {
	}

	/** Labels come from {@code sharedwaypoints.option.<id>}, hints from {@code sharedwaypoints.option.<id>.hint}. */
	public static Options create() {
		return new Options("sharedwaypoints.option.")
				.section("general")
				.toggle("enabled", ClientSettings::enabled, ClientSettings::setEnabled, true)
				.toggle("xaero_sync", ClientSettings::xaeroSync, ClientSettings::setXaeroSync, true)
				.disabledWhen(() -> !Ui.xaeroInstalled(), Ui.tr("action.xaero.missing"))
				.toggle("pause_button", ClientSettings::pauseButton, ClientSettings::setPauseButton, true)
				.section("menu")
				.choice("default_sort", () -> List.of(ClientSettings.Sort.values()), sort -> Ui.tr("sort." + sort.id()),
						ClientSettings::defaultSort, ClientSettings::setDefaultSort, ClientSettings.Sort.CATEGORY)
				.toggle("remember_view", ClientSettings::rememberView, ClientSettings::setRememberView, true);
	}
}
