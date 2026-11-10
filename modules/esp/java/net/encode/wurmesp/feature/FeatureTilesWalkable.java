package net.encode.wurmesp.feature;

import com.wurmonline.client.game.PlayerPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.encode.wurmesp.WurmEspMod;
import net.encode.wurmesp.util.RenderUtils;
import net.encode.wurmesp.util.TerrainUtils;

public class FeatureTilesWalkable
extends Feature {
    private static final float[] COLOR = new float[]{1.0f, 0.0f, 0.0f, 0.5f};
    private static final int[] INDEXDATA = new int[]{1, 0, 0, 2, 2, 3, 3, 1};

    // the not rideable tiles in one primitive, rebuilt when the tiles or the steepness setting change
    private RenderUtils.LineBatch batch;
    private List<float[]> batchTiles;
    private int batchThreshold;

    @Override
    public void refresh() {
        // built off to the side and swapped in whole
        List<float[]> built = new ArrayList<>();
        WurmEspMod._terrainBuffer2 = this.world.getNearTerrainBuffer();
        PlayerPosition pos = this.world.getPlayer().getPos();
        int px = pos.getTileX();
        int py = pos.getTileY();
        int size = 6;
        int sx = px - size / 2;
        int sy = py - size / 2;
        float ox = this.world.getRenderOriginX();
        float oy = this.world.getRenderOriginY();
        for (int x = 0; x < size + 1; ++x) {
            for (int y = 0; y < size + 1; ++y) {
                float tileX = x + sx;
                float tileY = y + sy;
                float curX = tileX * 4.0f - ox;
                float curY = tileY * 4.0f - oy;
                float nextX = (tileX + 1.0f) * 4.0f - ox;
                float nextY = (tileY + 1.0f) * 4.0f - oy;
                float x0 = curX + 0.2f;
                float y0 = curY + 0.2f;
                float x1 = nextX - 0.2f;
                float y1 = nextY - 0.2f;
                float z0 = WurmEspMod._terrainBuffer2.getHeight((int)tileX, (int)tileY);
                float z1 = WurmEspMod._terrainBuffer2.getHeight((int)tileX + 1, (int)tileY);
                float z2 = WurmEspMod._terrainBuffer2.getHeight((int)tileX, (int)tileY + 1);
                float z3 = WurmEspMod._terrainBuffer2.getHeight((int)tileX + 1, (int)tileY + 1);
                built.add(new float[]{x0, z0, y0, x1, z1, y0, x0, z2, y1, x1, z3, y1});
            }
        }
        WurmEspMod._closeByWalkableTerrain = new CopyOnWriteArrayList<>(built);
    }

    @Override
    public void queue() {
        List<float[]> tiles = WurmEspMod._closeByWalkableTerrain;
        if (tiles == null) {
            return;
        }
        if (tiles != this.batchTiles || WurmEspMod.tilenotrideable != this.batchThreshold) {
            this.batchTiles = tiles;
            this.batchThreshold = WurmEspMod.tilenotrideable;
            RenderUtils.LineBatchBuilder builder = new RenderUtils.LineBatchBuilder();
            for (float[] t : tiles) {
                if (TerrainUtils.isNotRideable(t)) {
                    builder.add(t, INDEXDATA);
                }
            }
            RenderUtils.LineBatch next = RenderUtils.keepIfSame(this.batch, builder.build());
            RenderUtils.release(this.batch, next);
            this.batch = next;
        }
        this.batch.render(this.queuePick, COLOR, null);
    }
}
