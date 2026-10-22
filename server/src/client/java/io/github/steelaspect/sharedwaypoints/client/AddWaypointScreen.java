package io.github.steelaspect.sharedwaypoints.client;

import dev.steelaspect.alloyui.widget.Button;
import dev.steelaspect.alloyui.widget.Dropdown;
import dev.steelaspect.alloyui.widget.TextField;
import io.github.steelaspect.sharedwaypoints.network.ActionPayload.Action;
import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import io.github.steelaspect.sharedwaypoints.waypoint.Category;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Form for a new waypoint: name, category, coordinates (your position by default) and dimension. */
public final class AddWaypointScreen extends FormScreen {
	private String name = "";
	private Category category;
	private String x = "";
	private String y = "";
	private String z = "";
	private String dimension;

	AddWaypointScreen(Screen parent) {
		this(parent, null);
	}

	/** With {@code at} = {x, y, z} and {@code atDimension} set, starts at that spot instead of the player's. */
	AddWaypointScreen(Screen parent, int @org.jspecify.annotations.Nullable [] at, String atDimension) {
		this(parent, at);
		if (at != null) {
			x = Integer.toString(at[0]);
			y = Integer.toString(at[1]);
			z = Integer.toString(at[2]);
			dimension = atDimension;
		}
	}

	private AddWaypointScreen(Screen parent, int @org.jspecify.annotations.Nullable [] at) {
		super(Ui.tr("add.title"), parent, 300, 220);
		List<Category> categories = categories();
		category = categories.get(0);
		dimension = currentDimension();

		label(Ui.tr("form.name"));
		field(new TextField(() -> name, value -> name = value).live().maxLength(Waypoint.MAX_NAME_LENGTH)
				.placeholder(Ui.tr("add.name.hint")).onEnter(this::save));
		label(Ui.tr("form.category"));
		field(new Dropdown<>(AddWaypointScreen::categories, value -> Component.literal(value.displayName()),
				() -> category, value -> category = value));
		label(Ui.tr("add.coordinates"));
		Row coordinates = field(new Row());
		coordinates.add(coordinate("X", () -> x, value -> x = value), 1);
		coordinates.add(coordinate("Y", () -> y, value -> y = value), 1);
		coordinates.add(coordinate("Z", () -> z, value -> z = value), 1);
		Row where = field(new Row());
		where.add(new Dropdown<>(AddWaypointScreen::dimensions, id -> Component.literal(Dimensions.shortName(id)),
				() -> dimension, value -> dimension = value), 1);
		where.add(new Button(Ui.tr("add.here"), this::useMyPosition), -1).tooltip(Ui.tr("add.here.tooltip"));
		mainButton(Ui.tr("add.save"), this::save);
		if (at == null) {
			useMyPosition();
		}
	}

	private TextField coordinate(String axis, java.util.function.Supplier<String> get, java.util.function.Consumer<String> set) {
		return new TextField(get, value -> {
			if (value.matches("-?\\d{0,8}")) {
				set.accept(value);
			}
		}).live().maxLength(9).placeholder(Component.literal(axis)).onEnter(this::save);
	}

	private static List<Category> categories() {
		return ClientWaypoints.categories().isEmpty() ? List.of(Category.unknown("other")) : ClientWaypoints.categories();
	}

	private static List<String> dimensions() {
		List<String> ids = new ArrayList<>();
		String here = currentDimension();
		ids.add(here);
		var connection = Minecraft.getInstance().getConnection();
		if (connection != null) {
			connection.levels().stream().map(Dimensions::id).sorted().filter(id -> !id.equals(here)).forEach(ids::add);
		}
		return ids;
	}

	private void useMyPosition() {
		var player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		BlockPos pos = player.blockPosition();
		x = Integer.toString(pos.getX());
		y = Integer.toString(pos.getY());
		z = Integer.toString(pos.getZ());
		dimension = currentDimension();
	}

	private static String currentDimension() {
		var player = Minecraft.getInstance().player;
		return player != null ? Dimensions.id(player.level().dimension()) : Dimensions.OVERWORLD;
	}

	private void save() {
		String trimmed = name.trim();
		var problem = Waypoint.validateName(trimmed);
		if (problem.isPresent()) {
			error(Component.literal(problem.get()));
			return;
		}
		if (!isNumber(x) || !isNumber(y) || !isNumber(z)) {
			error(Ui.tr("add.need_coordinates"));
			return;
		}
		SharedWaypointsClient.send(Action.ADD, trimmed, category.id(), x, y, z, dimension);
		WaypointMenuScreen.selectWhenListed(trimmed);
		minecraft.setScreen(parent);
	}

	private static boolean isNumber(String text) {
		return text.matches("-?\\d{1,8}");
	}
}
