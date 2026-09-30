package io.github.steelaspect.sharedwaypoints.client;

import io.github.steelaspect.sharedwaypoints.network.ActionPayload.Action;
import io.github.steelaspect.sharedwaypoints.util.Dimensions;
import io.github.steelaspect.sharedwaypoints.waypoint.Category;
import io.github.steelaspect.sharedwaypoints.waypoint.Waypoint;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Form for a new waypoint: name, category, coordinates (your position by default) and dimension. */
public final class AddWaypointScreen extends Screen {
	private static final int FIELD_WIDTH = 220;

	private final Screen parent;
	private EditBox name;
	private CycleButton<Category> category;
	private EditBox x;
	private EditBox y;
	private EditBox z;
	private CycleButton<String> dimension;
	private String error;

	AddWaypointScreen(Screen parent) {
		super(Component.literal("Add waypoint"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int left = (width - FIELD_WIDTH) / 2;
		int top = Math.max(30, height / 2 - 80);

		name = addRenderableWidget(new EditBox(font, left, top + 12, FIELD_WIDTH, 20, Component.literal("Name")));
		name.setMaxLength(Waypoint.MAX_NAME_LENGTH);
		name.setHint(Component.literal("e.g. Iron Farm"));

		List<Category> categories = ClientWaypoints.categories().isEmpty()
				? List.of(Category.unknown("other")) : ClientWaypoints.categories();
		category = addRenderableWidget(CycleButton.builder(
						(Category value) -> Component.literal(value.displayName()).withStyle(value.color()), categories.get(0))
				.withValues(categories)
				.create(left, top + 40, FIELD_WIDTH, 20, Component.literal("Category")));

		int coordinateWidth = (FIELD_WIDTH - 8) / 3;
		x = addRenderableWidget(coordinateBox(left, top + 80, coordinateWidth, "X"));
		y = addRenderableWidget(coordinateBox(left + coordinateWidth + 4, top + 80, coordinateWidth, "Y"));
		z = addRenderableWidget(coordinateBox(left + 2 * (coordinateWidth + 4), top + 80, coordinateWidth, "Z"));

		List<String> dimensions = new ArrayList<>();
		String here = currentDimension();
		dimensions.add(here);
		if (minecraft.getConnection() != null) {
			minecraft.getConnection().levels().stream().map(Dimensions::id).sorted()
					.filter(id -> !id.equals(here)).forEach(dimensions::add);
		}
		dimension = addRenderableWidget(CycleButton.builder((String id) -> Component.literal(Dimensions.shortName(id)), here)
				.withValues(dimensions)
				.create(left, top + 106, FIELD_WIDTH - 84, 20, Component.literal("Dimension")));
		addRenderableWidget(Button.builder(Component.literal("Here"), button -> useMyPosition())
				.bounds(left + FIELD_WIDTH - 80, top + 106, 80, 20).build());
		useMyPosition();

		addRenderableWidget(Button.builder(Component.literal("Add"), button -> save())
				.bounds(left, top + 140, FIELD_WIDTH / 2 - 2, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
				.bounds(left + FIELD_WIDTH / 2 + 2, top + 140, FIELD_WIDTH / 2 - 2, 20).build());
	}

	private EditBox coordinateBox(int left, int top, int boxWidth, String label) {
		EditBox box = new EditBox(font, left, top, boxWidth, 20, Component.literal(label));
		box.setMaxLength(9);
		box.setFilter(text -> text.matches("-?\\d{0,8}"));
		box.setHint(Component.literal(label));
		return box;
	}

	private void useMyPosition() {
		if (minecraft.player == null) {
			return;
		}
		BlockPos pos = minecraft.player.blockPosition();
		x.setValue(Integer.toString(pos.getX()));
		y.setValue(Integer.toString(pos.getY()));
		z.setValue(Integer.toString(pos.getZ()));
		dimension.setValue(currentDimension());
	}

	private String currentDimension() {
		return minecraft.player != null ? Dimensions.id(minecraft.player.level().dimension()) : Dimensions.OVERWORLD;
	}

	private void save() {
		String trimmed = name.getValue().trim();
		var problem = Waypoint.validateName(trimmed);
		if (problem.isPresent()) {
			error = problem.get();
			return;
		}
		if (x.getValue().isEmpty() || x.getValue().equals("-") || y.getValue().isEmpty() || y.getValue().equals("-")
				|| z.getValue().isEmpty() || z.getValue().equals("-")) {
			error = "Fill in X, Y and Z";
			return;
		}
		SharedWaypointsClient.send(Action.ADD, trimmed, category.getValue().id(), x.getValue(), y.getValue(), z.getValue(),
				dimension.getValue());
		minecraft.setScreen(parent);
	}

	/** Always start in the text field (Minecraft would otherwise tab past it after keyboard input). */
	@Override
	protected void setInitialFocus() {
		setInitialFocus(name);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		int left = (width - FIELD_WIDTH) / 2;
		int top = Math.max(30, height / 2 - 80);
		graphics.drawCenteredString(font, title, width / 2, top - 16, Colors.WHITE);
		graphics.drawString(font, "Name", left, top, Colors.GRAY);
		graphics.drawString(font, "Coordinates", left, top + 68, Colors.GRAY);
		if (error != null) {
			graphics.drawCenteredString(font, error, width / 2, top + 168, Colors.RED);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
