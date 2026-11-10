package org.gotti.wurmonline.clientmods.livehudmap.renderer;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import com.wurmonline.client.game.CaveDataBuffer;
import com.wurmonline.mesh.Tiles.Tile;

public abstract class AbstractCaveRenderer implements MapRenderer {

	protected static final float MAP_HEIGHT = 1000;

	public abstract BufferedImage createMapDump(int xo, int yo, int lWidth, int lHeight, int px, int py);

	protected final CaveDataBuffer buffer;

	public AbstractCaveRenderer(CaveDataBuffer buffer) {
		this.buffer = buffer;
	}

	protected short getHeight(int x, int y) {
		int sum = buffer.getRawFloor(x, y) +
				buffer.getRawFloor(x + 1, y) +
				buffer.getRawFloor(x, y + 1) +
				buffer.getRawFloor(x + 1, y + 1);
		return (short) (sum / 4);
	}

	protected Tile getTileType(int x, int y) {
		return buffer.getTileType(x, y);
	}

	private BufferedImage image;

	/**
	 * The image the map is drawn into, kept between renders while the size stays the same.
	 * LiveMap copies it into the texture data straight away, so it is free again before the next render.
	 */
	protected BufferedImage reuseImage(int width, int height) {
		if (image == null || image.getWidth() != width || image.getHeight() != height) {
			image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		}
		return image;
	}

	/** The packed RGB pixels of a TYPE_INT_RGB image, row by row. */
	protected static int[] pixels(BufferedImage image) {
		return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
	}

	/** Packs one pixel the way WritableRaster.setPixels stores the r, g, b samples. */
	protected static int rgb(int r, int g, int b) {
		return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
	}
}
