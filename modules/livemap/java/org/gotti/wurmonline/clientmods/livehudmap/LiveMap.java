package org.gotti.wurmonline.clientmods.livehudmap;

import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.gotti.wurmonline.clientmods.livehudmap.renderer.RenderType;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import com.wurmonline.client.game.PlayerPosition;
import com.wurmonline.client.game.TerrainChangeListener;
import com.wurmonline.client.game.World;
import com.wurmonline.client.renderer.PickData;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.cave.CaveBufferChangeListener;
import com.wurmonline.client.renderer.gui.Renderer;
import com.wurmonline.client.resources.textures.ImageTexture;
import com.wurmonline.client.resources.textures.ImageTextureLoader;
import com.wurmonline.client.resources.textures.PreProcessedTextureData;
import com.wurmonline.client.resources.textures.TextureLoader;

public class LiveMap implements TerrainChangeListener, CaveBufferChangeListener {
	
	private int size;
	private final World world;
	
	private MapLayerView surface;
	private MapLayerView cave;

	private volatile boolean dirty = true;
	private BufferedImage image;
	private ImageTexture texture;

	private int x;
	private int y;
	
	private volatile int px = 0, py = 0;
	private final Method preprocessImage;

	/** Re-render at most this often; terrain packets and walking would otherwise redraw the map most frames. */
	private static final long RENDER_INTERVAL_NANOS = 250_000_000L;
	private long lastRender;
	private boolean rendered;
	
	public LiveMap(World world, int size) {
		this.size = size;
		
		this.world = world;
		
		this.world.getNearTerrainBuffer().addListener(this);
		this.world.getCaveBuffer().addCaveBufferListener(this);
		
		this.surface = new MapLayerView(world, RenderType.FLAT);
		this.cave = new MapLayerView(world, RenderType.CAVE);
		
		try {
			this.preprocessImage = ReflectionUtil.getMethod(TextureLoader.class, "preprocessImage", new Class[] { BufferedImage.class, boolean.class });
			this.preprocessImage.setAccessible(true);
		} catch (NoSuchMethodException e) {
			throw new RuntimeException(e);
		}
	}

	public void update(int windowX, int windowY) {
		x = windowX;
		y = windowY;

		PlayerPosition pos = world.getPlayer().getPos();
		if (dirty || px != pos.getTileX() || py != pos.getTileY()) {
			long now = System.nanoTime();
			if (rendered && now - lastRender < RENDER_INTERVAL_NANOS) {
				return;
			}
			rendered = true;
			lastRender = now;
			// cleared first so a change that arrives while rendering is drawn next time
			dirty = false;

			px = pos.getTileX();
			py = pos.getTileY();

			// the renderers reuse one image; it is copied into the texture data here before the next render
			image = getLayer().render(px, py);
			if (texture == null) {
				texture = ImageTextureLoader.loadNowrapNearestTexture(image, false);
			} else {
				try {
					PreProcessedTextureData data = (PreProcessedTextureData) preprocessImage.invoke(null, image, true);
					texture.deferInit(data, TextureLoader.Filter.NEAREST, false, false, false);
				} catch (InvocationTargetException | IllegalAccessException | IllegalArgumentException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}

	/** Lets a change the user asked for (zoom, map type) skip the render throttle. */
	private void forceRender() {
		rendered = false;
		dirty = true;
	}

	/**
	 * Whether the tiles x0..x1, y0..y1 can show on either map. The bounds are generous: the isometric map looks
	 * half a map above and below the square, and the renderers read neighbouring tiles.
	 */
	private boolean isNearShown(int x0, int y0, int x1, int y1) {
		if (surface == null || cave == null) {
			return true; // the listeners are added before the views exist
		}
		int reach = Math.max(surface.getSize(), cave.getSize()) + 2;
		int cx = px, cy = py;
		return x1 >= cx - reach && x0 <= cx + reach && y1 >= cy - reach && y0 <= cy + reach;
	}
	
	private MapLayerView getLayer() {
		if (isSurface()) {
			return surface;
		} else {
			return cave;
		}
	}
	
	private MapLayerView getLayer(MapLayer layer) {
		switch (layer) {
		case SURFACE:
			return surface;
		case CAVE:
			return cave;
		}
		throw new IllegalArgumentException(layer.name());
	}
	
	public void setRenderer(MapLayer layer, RenderType renderType) {
		getLayer(layer).setRenderer(renderType);
		forceRender();
	}

	public void zoomIn() {
		getLayer().zoomIn();
		forceRender();
	}

	public void zoomOut() {
		getLayer().zoomOut();
		forceRender();
	}

	private boolean isSurface() {
		return world.getPlayerLayer() >= 0;
	}
	
	public void render(Queue queue, float textureX, float textureY, float textureScale) {
		float resultX = textureX / this.size;
		float resultY = textureY / this.size;

		if (texture != null) {
			Renderer.texturedQuadAlphaBlend(queue, texture, 1.0f, 1.0f, 1.0f, 1.0f, (float) this.x,
					(float) this.y, (float) this.size, (float) this.size, resultX, resultY, textureScale,
					textureScale);
		}
	}

	@Override
	public void caveChanged(int x0, int y0, int x1, int y1, boolean paramBoolean) {
		// the client passes the changed tiles as x0, y0, x0 + width, y0 + height
		if (isNearShown(x0, y0, x1, y1)) {
			dirty = true;
		}
	}

	@Override
	public void terrainUpdated(int x0, int y0, int x1, int y1, boolean paramBoolean1, boolean paramBoolean2) {
		if (isNearShown(x0, y0, x1, y1)) {
			dirty = true;
		}
	}

	public void pick(final PickData pickData, final float xMouse, final float yMouse) {
		this.getLayer().pick(pickData, xMouse, yMouse);
	}
}
