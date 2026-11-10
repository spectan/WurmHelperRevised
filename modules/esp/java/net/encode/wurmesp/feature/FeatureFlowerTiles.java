package net.encode.wurmesp.feature;

import com.wurmonline.client.game.PlayerPosition;
import com.wurmonline.mesh.GrassData;
import com.wurmonline.mesh.Tiles;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.encode.wurmesp.WurmEspMod;
import net.encode.wurmesp.util.RenderUtils;

public class FeatureFlowerTiles
extends Feature {
	private static final float[] COLOR = new float[]{0.0f, 1.0f, 0.0f, 0.5f};
	private static final int[] INDEXDATA = new int[]{1, 0, 0, 2, 2, 3, 3, 1};

	// how many of the searched names each tile data byte's flower name contains (-1 = not looked up yet),
	// for the search list it was worked out for
	private final int[] matches = new int[256];
	private String[] matchesFor;

	private RenderUtils.LineBatch batch;

	private int matchCount(byte data) {
		if (this.matchesFor != WurmEspMod.tilesFlowerSearch) {
			Arrays.fill(this.matches, -1);
			this.matchesFor = WurmEspMod.tilesFlowerSearch;
		}
		int count = this.matches[data & 0xFF];
		if (count < 0) {
			String flower = GrassData.getFlowerTypeName(data);
			count = 0;
			for (String name : WurmEspMod.tilesFlowerSearch) {
				if (flower.contains(name)) {
					++count;
				}
			}
			this.matches[data & 0xFF] = count;
		}
		return count;
	}

	@Override
	public void refresh() {
		// built off to the side and swapped in whole
		List<float[]> built = new ArrayList<>();
		RenderUtils.LineBatchBuilder builder = new RenderUtils.LineBatchBuilder();
        WurmEspMod._terrainBuffer = this.world.getNearTerrainBuffer();
        PlayerPosition pos = this.world.getPlayer().getPos();
        int px = pos.getTileX();
        int py = pos.getTileY();
        int size = WurmEspMod.flowerdiameter;
        int sx = px - size / 2;
        int sy = py - size / 2;
        float ox = this.world.getRenderOriginX();
        float oy = this.world.getRenderOriginY();
        for (int x = 0; x < size + 1; ++x) {
            for (int y = 0; y < size + 1; ++y) {
                float tileX = x + sx;
                float tileY = y + sy;
                byte data = WurmEspMod._terrainBuffer.getData((int)tileX, (int)tileY);
                Tiles.Tile tileType = WurmEspMod._terrainBuffer.getTileType((int)tileX, (int)tileY);
                if (tileType.isGrass() && !tileType.isBush() && !tileType.isTree()) {
                	// one outline per matching name, as when each name was checked in turn
                	for (int m = this.matchCount(data); m > 0; --m)
                	{
                		float curX = tileX * 4.0f - ox;
                        float curY = tileY * 4.0f - oy;
                		float nextX = (tileX + 1.0f) * 4.0f - ox;
                        float nextY = (tileY + 1.0f) * 4.0f - oy;
                        float x0 = curX + 0.1f;
                        float y0 = curY + 0.1f;
                        float x1 = nextX - 0.1f;
                        float y1 = nextY - 0.1f;
                        float z0 = WurmEspMod._terrainBuffer.getHeight((int)tileX, (int)tileY);
                        float z1 = WurmEspMod._terrainBuffer.getHeight((int)tileX + 1, (int)tileY);
                        float z2 = WurmEspMod._terrainBuffer.getHeight((int)tileX, (int)tileY + 1);
                        float z3 = WurmEspMod._terrainBuffer.getHeight((int)tileX + 1, (int)tileY + 1);
                        float[] t = new float[]{x0, z0, y0, x1, z1, y0, x0, z2, y1, x1, z3, y1};
                        built.add(t);
                        builder.add(t, INDEXDATA);
                	}
                }
            }
        }
        WurmEspMod._flowerTerrain = new CopyOnWriteArrayList<>(built);
        RenderUtils.LineBatch next = RenderUtils.keepIfSame(this.batch, builder.build());
        RenderUtils.release(this.batch, next);
        this.batch = next;
	}

	@Override
	public void queue() {
		if (this.batch == null) {
            return;
        }
        this.batch.render(this.queuePick, COLOR, null);
	}
}
