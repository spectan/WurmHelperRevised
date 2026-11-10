package net.encode.wurmesp.feature;

import com.wurmonline.client.game.PlayerPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.encode.wurmesp.WurmEspMod;
import net.encode.wurmesp.util.RenderUtils;
import net.encode.wurmesp.util.TerrainUtils;

public class FeatureTilesCloseBy
extends Feature {
    private static final float[] FLAT_COLOR = new float[]{0.0f, 1.0f, 0.0f, 0.5f};
    private static final float[] SLOPE_COLOR = new float[]{1.0f, 1.0f, 0.0f, 0.5f};
    private static final int[] INDEXDATA = new int[]{1, 0, 0, 2, 2, 3, 3, 1};

    // all flat tiles in one primitive and all sloped ones in another; kept while the tiles stay the same
    private RenderUtils.LineBatch flat;
    private RenderUtils.LineBatch slope;

    @Override
    public void refresh() {
        // built off to the side and swapped in whole
        List<float[]> built = new ArrayList<>();
        RenderUtils.LineBatchBuilder flatBuilder = new RenderUtils.LineBatchBuilder();
        RenderUtils.LineBatchBuilder slopeBuilder = new RenderUtils.LineBatchBuilder();
        WurmEspMod._terrainBuffer = this.world.getNearTerrainBuffer();
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
                (TerrainUtils.isFlat(t) ? flatBuilder : slopeBuilder).add(t, INDEXDATA);
            }
        }
        WurmEspMod._closeByTerrain = new CopyOnWriteArrayList<>(built);
        RenderUtils.LineBatch nextFlat = RenderUtils.keepIfSame(this.flat, flatBuilder.build());
        RenderUtils.LineBatch nextSlope = RenderUtils.keepIfSame(this.slope, slopeBuilder.build());
        RenderUtils.release(this.flat, nextFlat);
        RenderUtils.release(this.slope, nextSlope);
        this.flat = nextFlat;
        this.slope = nextSlope;
    }

    @Override
    public void queue() {
        if (this.flat != null) {
            this.flat.render(this.queuePick, FLAT_COLOR, null);
        }
        if (this.slope != null) {
            this.slope.render(this.queuePick, SLOPE_COLOR, null);
        }
    }
}
