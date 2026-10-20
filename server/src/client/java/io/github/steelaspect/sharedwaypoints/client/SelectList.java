package io.github.steelaspect.sharedwaypoints.client;

import dev.steelaspect.alloyui.UiContext;
import dev.steelaspect.alloyui.render.Draw;
import dev.steelaspect.alloyui.theme.Theme;
import dev.steelaspect.alloyui.widget.Widget;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A scrolling list with one selected row: click selects, double-click (or Enter) activates, arrow keys move the
 * selection when the list has focus. Rows are drawn by a {@link RowRenderer}.
 */
final class SelectList<T> extends Widget {
	interface RowRenderer<T> {
		void render(UiContext ctx, T item, float x, float y, float w, float h, boolean selected);
	}

	private final float rowHeight;
	private final RowRenderer<T> renderer;
	private final Function<T, Object> key;
	private List<T> items = List.of();
	private @Nullable Object selectedKey;
	private Consumer<@Nullable T> onSelect = item -> {
	};
	private Consumer<T> onActivate = item -> {
	};
	private Supplier<Component> emptyText = Component::empty;
	private float scroll;
	private long lastClickAt;
	private @Nullable Object lastClickKey;

	SelectList(float rowHeight, Function<T, Object> key, RowRenderer<T> renderer) {
		this.rowHeight = rowHeight;
		this.key = key;
		this.renderer = renderer;
	}

	SelectList<T> onSelect(Consumer<@Nullable T> onSelect) {
		this.onSelect = onSelect;
		return this;
	}

	SelectList<T> onActivate(Consumer<T> onActivate) {
		this.onActivate = onActivate;
		return this;
	}

	SelectList<T> emptyText(Supplier<Component> text) {
		this.emptyText = text;
		return this;
	}

	/** Replaces the rows, keeping the selection if that item is still listed (without calling onSelect). */
	void show(List<T> newItems) {
		items = List.copyOf(newItems);
		if (selectedKey != null && indexOf(selectedKey) < 0) {
			selectedKey = null;
		}
		clampScroll();
	}

	List<T> items() {
		return items;
	}

	@Nullable T selected() {
		int i = selectedKey == null ? -1 : indexOf(selectedKey);
		return i < 0 ? null : items.get(i);
	}

	int selectedIndex() {
		return selectedKey == null ? -1 : indexOf(selectedKey);
	}

	/** Selects the item (or nothing), scrolls it into view and calls onSelect. */
	void select(@Nullable T item) {
		selectQuietly(item);
		onSelect.accept(item);
	}

	/** Selects without calling onSelect (restoring a remembered selection). */
	void selectQuietly(@Nullable T item) {
		selectedKey = item == null ? null : key.apply(item);
		int i = selectedIndex();
		if (i >= 0 && h > 0) {
			float top = i * rowHeight;
			if (top < scroll) {
				scroll = top;
			} else if (top + rowHeight > scroll + h) {
				scroll = top + rowHeight - h;
			}
			clampScroll();
		}
	}

	private int indexOf(Object k) {
		for (int i = 0; i < items.size(); i++) {
			if (Objects.equals(key.apply(items.get(i)), k)) {
				return i;
			}
		}
		return -1;
	}

	private void clampScroll() {
		float max = Math.max(0, items.size() * rowHeight - h);
		scroll = Math.max(0, Math.min(scroll, max));
	}

	@Override
	public float measure(float width) {
		return items.size() * rowHeight;
	}

	@Override
	public boolean focusable() {
		return !items.isEmpty();
	}

	@Override
	protected void renderWidget(UiContext ctx) {
		Theme t = ctx.theme;
		clampScroll();
		if (items.isEmpty()) {
			Component empty = emptyText.get();
			if (!empty.getString().isEmpty()) {
				Draw.textCentered(ctx.g, empty, x + w / 2, y + 16, t.color("textMuted"));
			}
			return;
		}
		ctx.pushClip(x, y, w, h);
		int first = (int) Math.floor(scroll / rowHeight);
		float rowW = w - (overflows() ? 6 : 0);
		for (int i = first; i < items.size(); i++) {
			float ry = y + i * rowHeight - scroll;
			if (ry > y + h) {
				break;
			}
			T item = items.get(i);
			boolean selected = Objects.equals(key.apply(item), selectedKey);
			if (selected) {
				Draw.roundRect(ctx.g, x, ry, rowW, rowHeight - 2, t.metricF("radius"), t.color("selection"));
			} else if (ctx.hovering(x, ry, rowW, rowHeight - 2)) {
				Draw.roundRect(ctx.g, x, ry, rowW, rowHeight - 2, t.metricF("radius"), t.color("panelHover"));
			}
			renderer.render(ctx, item, x, ry, rowW, rowHeight - 2, selected);
		}
		if (isFocused(ctx) && selectedIndex() >= 0) {
			float ry = y + selectedIndex() * rowHeight - scroll;
			Draw.roundOutline(ctx.g, x, ry, rowW, rowHeight - 2, t.metricF("radius"), 1, t.color("borderFocus"));
		}
		if (overflows()) {
			float total = items.size() * rowHeight;
			float barH = Math.max(12, h * h / total);
			float barY = y + (h - barH) * (scroll / (total - h));
			boolean barHover = ctx.hovering(x + w - 6, y, 6, h);
			Draw.roundRect(ctx.g, x + w - (barHover ? 4 : 3), barY, barHover ? 4 : 2, barH, 1,
					t.color(barHover ? "scrollbarHover" : "scrollbar"));
		}
		ctx.popClip();
	}

	private boolean overflows() {
		return items.size() * rowHeight > h;
	}

	private int rowAt(double my) {
		int i = (int) Math.floor((my - y + scroll) / rowHeight);
		return i >= 0 && i < items.size() ? i : -1;
	}

	@Override
	public boolean mouseClicked(UiContext ctx, double mx, double my, int button) {
		if (button != 0 || !contains(mx, my) || !ctx.inClip(mx, my)) {
			return false;
		}
		int i = rowAt(my);
		if (i < 0) {
			return true;
		}
		focus(ctx);
		T item = items.get(i);
		Object k = key.apply(item);
		long now = System.currentTimeMillis();
		boolean doubleClick = k.equals(lastClickKey) && now - lastClickAt < 300;
		lastClickAt = now;
		lastClickKey = k;
		select(item);
		if (doubleClick) {
			onActivate.accept(item);
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(UiContext ctx, double mx, double my, double amount) {
		if (!contains(mx, my) || !overflows()) {
			return false;
		}
		scroll -= (float) amount * rowHeight;
		clampScroll();
		return true;
	}

	@Override
	public boolean keyPressed(UiContext ctx, KeyEvent e) {
		int i = selectedIndex();
		switch (e.key()) {
			case GLFW.GLFW_KEY_DOWN -> {
				if (!items.isEmpty()) {
					select(items.get(Math.min(items.size() - 1, i + 1)));
				}
				return true;
			}
			case GLFW.GLFW_KEY_UP -> {
				if (!items.isEmpty()) {
					select(items.get(Math.max(0, i < 0 ? 0 : i - 1)));
				}
				return true;
			}
			case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
				T item = selected();
				if (item != null) {
					onActivate.accept(item);
				}
				return true;
			}
			default -> {
				return false;
			}
		}
	}
}
