package net.encode.wurmesp.util;

import com.wurmonline.client.renderer.Matrix;
import com.wurmonline.client.renderer.PickRenderer;
import com.wurmonline.client.renderer.PickRenderer.CustomPickOutlineRender;
import com.wurmonline.client.renderer.backend.IndexBuffer;
import com.wurmonline.client.renderer.backend.Primitive;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.backend.RenderState;
import com.wurmonline.client.renderer.backend.VertexBuffer;

import net.encode.wurmesp.WurmEspMod;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class RenderUtils {
    /** The GL buffers built for one outline, kept while the same shape is drawn frame after frame. */
    private static final class CachedLines {
        final float[] vertexdata;
        final int[] indexdata;
        final VertexBuffer vertexBuffer;
        final IndexBuffer indexBuffer;

        CachedLines(int numvertex, float[] vertexdata, int[] indexdata) {
            this.vertexdata = vertexdata.clone();
            this.indexdata = indexdata.clone();
            vertexBuffer = VertexBuffer.create((VertexBuffer.Usage)VertexBuffer.Usage.PICK, (int)numvertex, (boolean)true, (boolean)false, (boolean)false, (boolean)false, (boolean)false, (int)0, (int)0, (boolean)false, (boolean)true);
            FloatBuffer vdata = vertexBuffer.lock();
            vdata.put(vertexdata);
            vertexBuffer.unlock();
            indexBuffer = IndexBuffer.create((int)indexdata.length, (boolean)false, (boolean)true);
            IntBuffer idata = indexBuffer.lock();
            idata.put(indexdata);
            indexBuffer.unlock();
        }

        boolean sameShape(float[] vertexdata, int[] indexdata) {
            return Arrays.equals(this.vertexdata, vertexdata) && Arrays.equals(this.indexdata, indexdata);
        }

        /** Frees the GL buffers now instead of leaving them to finalize(); render thread only. */
        void delete() {
            vertexBuffer.delete();
            indexBuffer.delete();
        }
    }

    // keyed by the identity of the array a feature keeps for the outline, so the entries go with the arrays
    private static final Map<float[], CachedLines> cache = Collections.synchronizedMap(new WeakHashMap<float[], CachedLines>());

    // the outline state is the same for every primitive; rebuilt only when the game hands us another pick renderer
    private static PickRenderer outlinePicker;
    private static RenderState outlineState;

    private static RenderState outlineState() {
        PickRenderer picker = WurmEspMod._pickRenderer;
        if (outlineState == null || outlinePicker != picker) {
            CustomPickOutlineRender customPickOutline = picker.new CustomPickOutlineRender();
            RenderState renderStateOutline = new RenderState();
            renderStateOutline.alphaval = 0.5F;
            renderStateOutline.twosided = false;
            renderStateOutline.depthtest = Primitive.TestFunc.LESS;
            renderStateOutline.depthwrite = false;
            renderStateOutline.blendmode = Primitive.BlendMode.ALPHABLEND;
            renderStateOutline.customstate = customPickOutline;
            outlineState = renderStateOutline;
            outlinePicker = picker;
        }
        return outlineState;
    }

    public static void renderPrimitiveLines(int numvertex, float[] vertexdata, int[] indexdata, Queue queue, float[] color) {
        renderPrimitiveLines(vertexdata, numvertex, vertexdata, indexdata, queue, color);
    }

    /**
     * @param key a long-lived array that stands for this outline; the buffers are rebuilt only when the
     *            vertex or index data differ from the last time it was drawn
     */
    public static void renderPrimitiveLines(float[] key, int numvertex, float[] vertexdata, int[] indexdata, Queue queue, float[] color) {
        CachedLines lines = cache.get(key);
        if (lines == null || !lines.sameShape(vertexdata, indexdata)) {
            CachedLines old = lines;
            lines = new CachedLines(numvertex, vertexdata, indexdata);
            cache.put(key, lines);
            if (old != null) {
                old.delete();
            }
        }
        queueLines(lines, queue, color[0], color[1], color[2], color[3], null);
    }

    private static void queueLines(CachedLines lines, Queue queue, float r, float g, float b, float a, Matrix model) {
        Primitive p = queue.reservePrimitive();
        p.copyStateFrom(outlineState());
        p.vertex = lines.vertexBuffer;
        p.index = lines.indexBuffer;
        p.num = lines.indexBuffer.getNumIndex() / 2;
        p.type = Primitive.Type.LINES;
        p.nolight = true;
        p.nofog = true;
        p.texture[0] = null;
        p.setColor(r, g, b, a);
        queue.queue(p, model);
    }

    /**
     * Many outlines drawn as one primitive. The data can be built on any thread; the GL buffers are made on
     * the render thread the first time it is drawn and freed by {@link #delete()} (render thread only).
     */
    public static final class LineBatch {
        private final float[] vertexdata;
        private final int[] indexdata;
        private CachedLines lines;

        private LineBatch(float[] vertexdata, int[] indexdata) {
            this.vertexdata = vertexdata;
            this.indexdata = indexdata;
        }

        public boolean isEmpty() {
            return indexdata.length == 0;
        }

        public boolean sameShape(LineBatch other) {
            return other != null && Arrays.equals(vertexdata, other.vertexdata) && Arrays.equals(indexdata, other.indexdata);
        }

        /** @param model moves the batch, e.g. to the player's height; null draws it where it was built */
        public void render(Queue queue, float[] color, Matrix model) {
            if (isEmpty()) {
                return;
            }
            if (lines == null) {
                lines = new CachedLines(vertexdata.length / 3, vertexdata, indexdata);
            }
            queueLines(lines, queue, color[0], color[1], color[2], color.length > 3 ? color[3] : 1.0f, model);
        }

        public void delete() {
            if (lines != null) {
                lines.delete();
                lines = null;
            }
        }
    }

    /** Collects outlines (x, height, y vertex triples plus a line index pattern) into a {@link LineBatch}. */
    public static final class LineBatchBuilder {
        private float[] vertexdata = new float[192];
        private int[] indexdata = new int[128];
        private int numFloats;
        private int numIndex;

        /** @param indexdata line pairs indexing into vertexdata, starting at 0 for this outline */
        public void add(float[] vertexdata, int[] indexdata) {
            if (numFloats + vertexdata.length > this.vertexdata.length) {
                this.vertexdata = Arrays.copyOf(this.vertexdata, Math.max(this.vertexdata.length * 2, numFloats + vertexdata.length));
            }
            if (numIndex + indexdata.length > this.indexdata.length) {
                this.indexdata = Arrays.copyOf(this.indexdata, Math.max(this.indexdata.length * 2, numIndex + indexdata.length));
            }
            int base = numFloats / 3;
            System.arraycopy(vertexdata, 0, this.vertexdata, numFloats, vertexdata.length);
            numFloats += vertexdata.length;
            for (int index : indexdata) {
                this.indexdata[numIndex++] = base + index;
            }
        }

        public LineBatch build() {
            return new LineBatch(Arrays.copyOf(vertexdata, numFloats), Arrays.copyOf(indexdata, numIndex));
        }
    }

    /** Returns next when it holds different lines, else current, so unchanged lines keep their GL buffers. */
    public static LineBatch keepIfSame(LineBatch current, LineBatch next) {
        return next.sameShape(current) ? current : next;
    }

    /** Frees old's GL buffers when it is being replaced by another batch; render thread only. */
    public static void release(LineBatch old, LineBatch replacement) {
        if (old != null && old != replacement) {
            old.delete();
        }
    }
}
