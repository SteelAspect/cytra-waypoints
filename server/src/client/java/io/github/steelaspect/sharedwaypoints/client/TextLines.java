package io.github.steelaspect.sharedwaypoints.client;

import dev.steelaspect.alloyui.UiContext;
import dev.steelaspect.alloyui.render.Draw;
import dev.steelaspect.alloyui.widget.Widget;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/** A block of text lines for a details panel: a heading, plain lines and wrapped paragraphs, each in its colour. */
final class TextLines extends Widget {
	static final float LINE = 11;

	/**
	 * One line. {@code color} is a theme key, or null with {@code argb} set. {@code maxLines} &gt; 1 wraps the text;
	 * {@code dot} draws a small coloured square in front (category colour), 0 for none.
	 */
	record Line(Component text, @Nullable String color, int argb, boolean heading, int maxLines, int dot, @Nullable Component right,
			@Nullable String rightColor) {
		static Line heading(Component text, int dot) {
			return new Line(text, "text", 0, true, 1, dot, null, null);
		}

		static Line of(Component text, String color) {
			return new Line(text, color, 0, false, 1, 0, null, null);
		}

		static Line argb(Component text, int argb) {
			return new Line(text, null, argb, false, 1, 0, null, null);
		}

		static Line wrapped(Component text, String color, int maxLines) {
			return new Line(text, color, 0, false, maxLines, 0, null, null);
		}

		Line withRight(Component text, String color) {
			return new Line(this.text, this.color, argb, heading, maxLines, dot, text, color);
		}

		/** A gap of half a line. */
		static Line space() {
			return new Line(Component.empty(), "text", 0, false, 0, 0, null, null);
		}
	}

	private final Supplier<List<Line>> lines;

	TextLines(Supplier<List<Line>> lines) {
		this.lines = lines;
	}

	private static float textLeft(Line line) {
		return line.dot() != 0 ? 10 : 0;
	}

	private static List<FormattedCharSequence> wrap(Line line, float width) {
		return Draw.font().split(Draw.styled(line.text(), false), Math.max(20, (int) (width - textLeft(line))));
	}

	private static float height(Line line, float width) {
		if (line.maxLines() == 0) {
			return LINE / 2;
		}
		if (line.maxLines() == 1) {
			return line.heading() ? LINE + 3 : LINE;
		}
		return Math.min(line.maxLines(), wrap(line, width).size()) * 10 + 1;
	}

	@Override
	public float measure(float width) {
		float total = 0;
		for (Line line : lines.get()) {
			total += height(line, width);
		}
		return total;
	}

	@Override
	protected void renderWidget(UiContext ctx) {
		float cy = y;
		for (Line line : lines.get()) {
			float lh = height(line, w);
			if (cy + Math.min(lh, LINE) > y + h + 1) {
				break;
			}
			int color = line.color() != null ? ctx.theme.color(line.color()) : line.argb();
			float tx = x + textLeft(line);
			if (line.dot() != 0) {
				Draw.roundRect(ctx.g, x, cy + (line.heading() ? 2 : 1), 6, 6, 1, line.dot());
			}
			if (line.maxLines() == 1) {
				float rightW = 0;
				if (line.right() != null) {
					rightW = Draw.textWidth(line.right()) + 6;
					Draw.text(ctx.g, line.right(), x + w - rightW + 6, cy, ctx.theme.color(line.rightColor()));
				}
				if (line.heading()) {
					Draw.textClipped(ctx.g, line.text(), tx, cy, w - (tx - x) - rightW, color, Draw.Role.STRONG);
				} else {
					Draw.textClipped(ctx.g, line.text(), tx, cy, w - (tx - x) - rightW, color);
				}
			} else if (line.maxLines() > 1) {
				List<FormattedCharSequence> wrapped = wrap(line, w);
				for (int i = 0; i < wrapped.size() && i < line.maxLines(); i++) {
					Draw.text(ctx.g, wrapped.get(i), tx, cy + i * 10, color);
				}
			}
			cy += lh;
		}
	}
}
