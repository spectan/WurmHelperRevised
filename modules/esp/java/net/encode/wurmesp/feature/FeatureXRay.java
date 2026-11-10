package net.encode.wurmesp.feature;

import com.wurmonline.client.game.PlayerPosition;
import com.wurmonline.client.renderer.Matrix;
import com.wurmonline.mesh.Tiles;
import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.encode.wurmesp.WurmEspMod;
import net.encode.wurmesp.util.RenderUtils;
import net.encode.wurmesp.util.XrayColors;

public class FeatureXRay
extends Feature {
    private static final int[] INDEXDATA = new int[] { 0, 1, 1, 2, 2, 3, 3, 0, 4, 5, 5, 6, 6, 7, 7, 4, 0, 4, 1, 5, 2, 6, 3, 7 };

    /** All ore tiles of one colour, drawn as one primitive. */
    private static final class OreBatch {
        final int rgb;
        final float[] color;
        final RenderUtils.LineBatch lines;

        OreBatch(int rgb, float[] color, RenderUtils.LineBatch lines) {
            this.rgb = rgb;
            this.color = color;
            this.lines = lines;
        }
    }

    // refresh may run on its own thread: it publishes a new list, and only the render thread makes or frees
    // GL buffers, for the batches of the list it drew last
    private volatile List<OreBatch> batches;
    private List<OreBatch> drawn;

    @Override
    public void refresh() {
        if (!this.world.isOwnBodyAdded()) {
            return;
        }
        // built off to the side and swapped in whole, so the renderer never sees a half-filled list
        java.util.List<float[]> built = new java.util.ArrayList<>();
        Map<Integer, RenderUtils.LineBatchBuilder> builders = new LinkedHashMap<>();
        Map<Integer, float[]> colors = new LinkedHashMap<>();
        WurmEspMod._caveBuffer = this.world.getCaveBuffer();
        PlayerPosition pos = this.world.getPlayer().getPos();
        int px = pos.getTileX();
        int py = pos.getTileY();
        int size = WurmEspMod.xraydiameter;
        int sx = px - size / 2;
        int sy = py - size / 2;
        float ox = this.world.getRenderOriginX();
        float oy = this.world.getRenderOriginY();
        for (int x = 0; x < size; ++x) {
            for (int y = size - 1; y >= 0; --y) {
                try {
                    int tileX = x + sx;
                    int tileY = y + sy;
                    Tiles.Tile tile = WurmEspMod._caveBuffer.getTileType(tileX, tileY);
                    if (tile == null || !tile.isOreCave()) continue;
                    Color color = XrayColors.getColorFor(tile);
                    float[] colorF = new float[]{(float)color.getRed() / 255.0f, (float)color.getGreen() / 255.0f, (float)color.getBlue() / 255.0f};
                    float curX = (float)(tileX * 4) - ox;
                    float curY = (float)(tileY * 4) - oy;
                    float nextX = (float)((tileX + 1) * 4) - ox;
                    float nextY = (float)((tileY + 1) * 4) - oy;
                    float x0 = curX + 0.2f;
                    float y0 = curY + 0.2f;
                    float x1 = nextX - 0.2f;
                    float y1 = nextY - 0.2f;
                    built.add(new float[]{x0, y0, x1, y1, colorF[0], colorF[1], colorF[2]});
                    // the box goes from 0 to 3 high; queue() lifts it to the player's height with the model matrix
                    float z0 = 0.0f;
                    float z1 = 3.0f;
                    float[] vertexdata = new float[] {
                            x1, z0, y0,
                            x1, z1, y0,
                            x0, z1, y0,
                            x0, z0, y0,
                            x1, z0, y1,
                            x1, z1, y1,
                            x0, z1, y1,
                            x0, z0, y1 };
                    Integer rgb = color.getRGB();
                    RenderUtils.LineBatchBuilder builder = builders.get(rgb);
                    if (builder == null) {
                        builder = new RenderUtils.LineBatchBuilder();
                        builders.put(rgb, builder);
                        colors.put(rgb, new float[]{colorF[0], colorF[1], colorF[2], 1.0F});
                    }
                    builder.add(vertexdata, INDEXDATA);
                    continue;
                }
                catch (IllegalArgumentException | SecurityException ex) {
                    Logger.getLogger(FeatureXRay.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        // a colour whose tiles did not change keeps its batch, and with it its GL buffers
        List<OreBatch> previous = this.batches;
        List<OreBatch> next = new ArrayList<>();
        for (Map.Entry<Integer, RenderUtils.LineBatchBuilder> entry : builders.entrySet()) {
            int rgb = entry.getKey();
            RenderUtils.LineBatch lines = entry.getValue().build();
            if (previous != null) {
                for (OreBatch old : previous) {
                    if (old.rgb == rgb) {
                        lines = RenderUtils.keepIfSame(old.lines, lines);
                        break;
                    }
                }
            }
            next.add(new OreBatch(rgb, colors.get(rgb), lines));
        }
        WurmEspMod._terrain = new java.util.concurrent.CopyOnWriteArrayList<>(built);
        this.batches = next;
    }

    @Override
    public void queue() {
        List<OreBatch> current = this.batches;
        if (current != this.drawn) {
            if (this.drawn != null) {
                for (OreBatch old : this.drawn) {
                    boolean kept = false;
                    if (current != null) {
                        for (OreBatch batch : current) {
                            if (batch.lines == old.lines) {
                                kept = true;
                                break;
                            }
                        }
                    }
                    if (!kept) {
                        old.lines.delete();
                    }
                }
            }
            this.drawn = current;
        }
        if (current == null || current.isEmpty()) {
            return;
        }
        PlayerPosition pos = this.world.getPlayer().getPos();
        // a new matrix each frame, as the queued primitives keep a reference to it until they are drawn
        Matrix model = new Matrix();
        model.setTranslation(0.0f, pos.getH(), 0.0f);
        for (OreBatch batch : current) {
            batch.lines.render(this.queuePick, batch.color, model);
        }
    }
}
