package io.github.steelaspect.sharedwaypoints.map;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/**
 * Round map-marker icons in a category colour: a coloured disc with a dark outline and a white centre dot.
 * Drawn in code (headless AWT works on servers), so no image files need to ship with the mod.
 */
public final class MarkerIcons {
	/** Icon width and height in pixels. */
	public static final int SIZE = 20;

	private static final Map<Integer, BufferedImage> IMAGES = new ConcurrentHashMap<>();
	private static final Map<Integer, String> DATA_URIS = new ConcurrentHashMap<>();

	private MarkerIcons() {
	}

	/** The icon for a colour (0xRRGGBB). Images are cached; don't modify them. */
	public static BufferedImage image(int rgb) {
		return IMAGES.computeIfAbsent(rgb & 0xFFFFFF, MarkerIcons::draw);
	}

	/** The icon as a {@code data:image/png;base64,...} URI, for maps that load icons by address. */
	public static String dataUri(int rgb) {
		return DATA_URIS.computeIfAbsent(rgb & 0xFFFFFF, color -> {
			ByteArrayOutputStream png = new ByteArrayOutputStream();
			try {
				ImageIO.write(image(color), "png", png);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			return "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray());
		});
	}

	private static BufferedImage draw(int rgb) {
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(new Color(rgb));
			g.fillOval(2, 2, SIZE - 4, SIZE - 4);
			g.setColor(new Color(0x1B1F24));
			g.setStroke(new BasicStroke(2f));
			g.drawOval(2, 2, SIZE - 4, SIZE - 4);
			g.setColor(Color.WHITE);
			int dot = SIZE / 4;
			g.fillOval((SIZE - dot) / 2, (SIZE - dot) / 2, dot, dot);
		} finally {
			g.dispose();
		}
		return image;
	}
}
