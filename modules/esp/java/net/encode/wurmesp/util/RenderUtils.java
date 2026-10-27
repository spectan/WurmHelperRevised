package net.encode.wurmesp.util;

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
    }

    // keyed by the identity of the array a feature keeps for the outline, so the entries go with the arrays
    private static final Map<float[], CachedLines> cache = Collections.synchronizedMap(new WeakHashMap<float[], CachedLines>());

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
            lines = new CachedLines(numvertex, vertexdata, indexdata);
            cache.put(key, lines);
        }
        VertexBuffer _vBuffer = lines.vertexBuffer;
        IndexBuffer _iBuffer = lines.indexBuffer;
        
        PickRenderer tmp1257_1254 = WurmEspMod._pickRenderer;
		CustomPickOutlineRender customPickOutline = tmp1257_1254.new CustomPickOutlineRender();

		RenderState renderStateOutline = new RenderState();
		renderStateOutline.alphaval = 0.5F;
		renderStateOutline.twosided = false;
		renderStateOutline.depthtest = Primitive.TestFunc.LESS;
		renderStateOutline.depthwrite = false;
		renderStateOutline.blendmode = Primitive.BlendMode.ALPHABLEND;
		renderStateOutline.customstate = customPickOutline;
        
        Primitive p = queue.reservePrimitive();
        p.copyStateFrom(renderStateOutline);
        p.vertex = _vBuffer;
        p.index = _iBuffer;
        p.num = _iBuffer.getNumIndex() / 2;
        p.type = Primitive.Type.LINES;
        p.nolight = true;
        p.nofog = true;
        p.texture[0] = null;
        p.setColor(color[0], color[1], color[2], color[3]);
        queue.queue(p, null);
    }
}

