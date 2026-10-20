package io.github.steelaspect.sharedwaypoints.client;

import dev.steelaspect.alloyui.widget.Button;
import dev.steelaspect.alloyui.widget.Column;
import dev.steelaspect.alloyui.widget.Container;
import dev.steelaspect.alloyui.widget.Label;
import dev.steelaspect.alloyui.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * A small form in the Nocturne frame: labelled fields stacked in the body, an error line in the footer, and Cancel
 * plus the main button at the bottom right. Esc goes back to the parent.
 */
abstract class FormScreen extends WaypointsScreen {
	protected final Frame frame;
	protected final Column fields = new Column(4);
	private @Nullable Component error;
	private @Nullable Widget initialFocus;

	protected FormScreen(Component title, Screen parent, float width, float height) {
		super(title, parent);
		frame = new Frame(title).maxSize(width, height);
		frame.body(fields);
		frame.footerText(() -> error == null ? Component.empty() : error.copy().withColor(0xFFF0605A));
		frame.footer(new Button(Ui.tr("form.cancel"), this::onClose));
		setRoot(frame);
	}

	/** Adds the main button (filled, last on the right). */
	protected Button mainButton(Component label, Runnable action) {
		Button b = frame.footer(new Button(label, action));
		b.style(Button.Style.PRIMARY);
		return b;
	}

	/** A label line above a field. */
	protected void label(Component text) {
		fields.add(new Label(text).color("textMuted"));
	}

	protected <T extends Widget> T field(T widget) {
		fields.add(widget);
		if (initialFocus == null && widget.focusable()) {
			initialFocus = widget;
		}
		return widget;
	}

	protected void error(@Nullable Component message) {
		this.error = message;
	}

	@Override
	protected void init() {
		super.init();
		if (initialFocus != null) {
			setFocus(initialFocus);
		}
	}

	/** Widgets side by side; widths are shares of the row ({@code -1}: a button's preferred width). */
	static final class Row extends Container {
		private final List<Float> weights = new ArrayList<>();

		<T extends Widget> T add(T widget, float weight) {
			weights.add(weight);
			return add(widget);
		}

		@Override
		public float measure(float width) {
			return 16;
		}

		@Override
		protected void layout() {
			float fixed = 0;
			float shares = 0;
			for (int i = 0; i < children.size(); i++) {
				float wgt = weights.get(i);
				if (wgt < 0) {
					fixed += ((Button) children.get(i)).preferredWidth();
				} else {
					shares += wgt;
				}
			}
			float gap = 4;
			float free = w - fixed - gap * (children.size() - 1);
			float cx = x;
			for (int i = 0; i < children.size(); i++) {
				float wgt = weights.get(i);
				float cw = wgt < 0 ? ((Button) children.get(i)).preferredWidth() : free * wgt / Math.max(shares, 1e-3f);
				children.get(i).setBounds(cx, y, cw, 16);
				cx += cw + gap;
			}
		}
	}
}
