package org.gotti.wurmonline.clientmods.livehudmap.renderer;

import java.awt.Color;
import java.awt.image.BufferedImage;

import com.wurmonline.client.game.NearTerrainDataBuffer;
import com.wurmonline.mesh.Tiles.Tile;

public class MapRendererFlat extends AbstractSurfaceRenderer {
	public MapRendererFlat(NearTerrainDataBuffer buffer) {
		super(buffer);
	}

	@Override
	public BufferedImage createMapDump(int xo, int yo, int lWidth, int lHeight, int px, int py) {
		if (yo < 0)
			yo = 0;
		if (xo < 0)
			xo = 0;

		final BufferedImage bi2 = reuseImage(lWidth, lWidth);
		final int[] data = pixels(bi2);

		for (int x = 0; x < lWidth; x++) {
			for (int y = lWidth - 1; y >= 0; y--) {
				final short height = getSurfaceHeight(x + xo, y + yo);
				final Tile tile = getTileType(x + xo, y + yo);

				final Color color;
				if (tile != null) {
					color = tile.getColor();
				}
				else {
					color = Tile.TILE_DIRT.getColor();
				}
				int r = color.getRed();
				int g = color.getGreen();
				int b = color.getBlue();
				if (height < 0) {
					r = (int) (r * 0.2f + 0.4f * 0.4f * 256f);
					g = (int) (g * 0.2f + 0.5f * 0.4f * 256f);
					b = (int) (b * 0.2f + 1.0f * 0.4f * 256f);
				}
				
				if (px == x + xo && py == y + yo) {
					r = Color.RED.getRed();
					g = 0;
					b = 0;
				}

				data[x + y * lWidth] = rgb(r, g, b);
			}
		}

		return bi2;
	}
}
