package io.github.steelaspect.sharedwaypoints.client;

import dev.steelaspect.alloyui.AlloyScreen;
import dev.steelaspect.alloyui.UiContext;
import dev.steelaspect.alloyui.render.Draw;
import dev.steelaspect.alloyui.theme.Theme;
import dev.steelaspect.alloyui.widget.Button;
import dev.steelaspect.alloyui.widget.Container;
import dev.steelaspect.alloyui.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The panel every waypoint screen sits in: a header (title on the left, controls on the right), the body, and a
 * footer (a status line on the left, buttons on the right). Used as the screen's root.
 */
final class Frame extends Container {
	static final float HEADER = 30, FOOTER = 28, PAD = 10, GAP = 4;

	private record Slot(Widget widget, float width) {}

	private final Supplier<Component> title;
	private final List<Slot> headerSlots = new ArrayList<>();
	/** A header widget that takes the room the others leave (the search field), or null. */
	private @Nullable Widget headerFill;
	private float headerFillMax = 200;
	private final List<Button> footerButtons = new ArrayList<>();
	private @Nullable Supplier<Component> footerText;
	private @Nullable Widget body;
	private float maxWidth = 640, maxHeight = 340;
	private float px, py, pw, ph;

	Frame(Supplier<Component> title) {
		this.title = title;
	}

	Frame(Component title) {
		this(() -> title);
	}

	Frame maxSize(float width, float height) {
		this.maxWidth = width;
		this.maxHeight = height;
		return this;
	}

	<T extends Widget> T body(T widget) {
		body = add(widget);
		return widget;
	}

	/** A header control at a fixed width ({@code -1}: a button's preferred width), placed left to right. */
	<T extends Widget> T header(T widget, float width) {
		headerSlots.add(new Slot(add(widget), width));
		return widget;
	}

	/** The header control that grows into the free room, up to {@code max} wide (the search field). */
	<T extends Widget> T headerFill(T widget, float max) {
		headerFill = add(widget);
		headerFillMax = max;
		return widget;
	}

	Button footer(Button button) {
		footerButtons.add(add(button));
		return button;
	}

	Frame footerText(Supplier<Component> text) {
		this.footerText = text;
		return this;
	}

	/** Inner width of the panel (for narrow layouts). */
	float panelWidth() {
		return pw;
	}

	@Override
	public float measure(float width) {
		return 0;
	}

	@Override
	public boolean contains(double mx, double my) {
		return mx >= px && mx < px + pw && my >= py && my < py + ph;
	}

	private static float width(Slot s) {
		return s.width() >= 0 ? s.width() : s.widget() instanceof Button b ? b.preferredWidth() : 60;
	}

	@Override
	protected void layout() {
		float[] f = AlloyScreen.frameBounds(w, h);
		pw = Math.min(f[2], maxWidth);
		ph = Math.min(f[3], maxHeight);
		px = x + (w - pw) / 2;
		py = y + (h - ph) / 2;

		// Header: controls right-aligned, the fill control (search) takes what's left after the title.
		float cy = py + (HEADER - 16) / 2;
		float right = px + pw - PAD;
		for (int i = headerSlots.size() - 1; i >= 0; i--) {
			Slot s = headerSlots.get(i);
			if (!s.widget().visible()) {
				continue;
			}
			float sw = width(s);
			right -= sw;
			s.widget().setBounds(right, cy, sw, 16);
			right -= GAP;
		}
		if (headerFill != null) {
			float titleRight = px + PAD + Draw.textWidth(title.get(), Draw.Role.HEADING) + 3 * GAP;
			float fw = Math.max(40, Math.min(headerFillMax, right - titleRight));
			headerFill.setBounds(right - fw, cy, fw, 16);
		}

		float bx = px + pw - PAD;
		for (int i = footerButtons.size() - 1; i >= 0; i--) {
			Button b = footerButtons.get(i);
			if (!b.visible()) {
				continue;
			}
			float bw = Math.max(64, b.preferredWidth());
			bx -= bw;
			b.setBounds(bx, py + ph - FOOTER + (FOOTER - 16) / 2, bw, 16);
			bx -= GAP + 2;
		}
		if (body != null) {
			body.setBounds(px + PAD, py + HEADER, pw - 2 * PAD, ph - HEADER - FOOTER);
		}
	}

	/** Right edge of the free footer area (left of the buttons), for the status line. */
	private float footerTextRight() {
		float left = px + pw - PAD;
		for (Button b : footerButtons) {
			if (b.visible()) {
				left = Math.min(left, b.x());
			}
		}
		return left - 2 * GAP;
	}

	@Override
	protected void renderWidget(UiContext ctx) {
		Theme t = ctx.theme;
		Draw.elevation(ctx.g, px, py, pw, ph, t.metricF("radiusLarge"), 3);
		Draw.panel(ctx.g, px, py, pw, ph, t.metricF("radiusLarge"), t.color("panel"), t.color("border"), t.metricF("borderWidth"));
		Draw.heading(ctx.g, title.get(), px + PAD, py + (HEADER - 8) / 2, t.color("text"));
		Draw.fadeRule(ctx.g, px + PAD, py + HEADER - 2, pw - 2 * PAD, t.color("border"));
		Draw.fadeRule(ctx.g, px + PAD, py + ph - FOOTER, pw - 2 * PAD, t.color("border"));
		if (footerText != null) {
			float tx = px + PAD;
			Draw.textClipped(ctx.g, footerText.get(), tx, py + ph - FOOTER + (FOOTER - 8) / 2, footerTextRight() - tx,
					t.color("textMuted"));
		}
		super.renderWidget(ctx);
	}
}
