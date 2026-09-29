package io.github.steelaspect.sharedwaypoints.client;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** A one-line text form (rename, description). Enter saves, Esc cancels. */
final class TextInputScreen extends Screen {
	private final Screen parent;
	private final String initial;
	private final int maxLength;
	private final Consumer<String> onSave;
	private EditBox input;

	TextInputScreen(Screen parent, String title, String initial, int maxLength, Consumer<String> onSave) {
		super(Component.literal(title));
		this.parent = parent;
		this.initial = initial;
		this.maxLength = maxLength;
		this.onSave = onSave;
	}

	@Override
	protected void init() {
		int boxWidth = Math.min(300, width - 40);
		int x = (width - boxWidth) / 2;
		int y = height / 2 - 20;
		input = addRenderableWidget(new EditBox(font, x, y, boxWidth, 20, title));
		input.setMaxLength(maxLength);
		input.setValue(input.getValue().isEmpty() ? initial : input.getValue());
		setInitialFocus(input);
		addRenderableWidget(Button.builder(Component.literal("Save"), button -> save())
				.bounds(x, y + 28, boxWidth / 2 - 2, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
				.bounds(x + boxWidth / 2 + 2, y + 28, boxWidth / 2 - 2, 20).build());
	}

	private void save() {
		onSave.accept(input.getValue().trim());
		minecraft.setScreen(parent);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
			save();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, height / 2 - 40, Colors.WHITE);
		graphics.drawCenteredString(font, input.getValue().length() + " / " + maxLength, width / 2, height / 2 + 34, Colors.DARK_GRAY);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
