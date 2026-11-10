package net.encode.wurmesp.feature.hook;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import com.wurmonline.client.game.World;
import com.wurmonline.client.renderer.PickRenderer;
import com.wurmonline.client.renderer.backend.Queue;

import net.encode.wurmesp.Unit;
import net.encode.wurmesp.WurmEspMod;
import net.encode.wurmesp.util.ReUtils;

public class RenderPickedItem
extends Hook {

    /** Set while a background xray refresh runs, so a short refresh rate can't pile up threads */
    private static final AtomicBoolean xrayRefreshing = new AtomicBoolean(false);
    private static final java.util.Set<String> failedFeatures = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void refreshXray() {
        try {
            WurmEspMod.xrayManager.refresh();
        } finally {
            xrayRefreshing.set(false);
        }
    }

    /** Refresh the xray tiles, on a background thread when that is configured (never more than one at a time). */
    private static void startXrayRefresh() {
        if (WurmEspMod.xrayrefreshthread) {
            if (xrayRefreshing.compareAndSet(false, true)) {
                Thread refreshThread = new Thread(RenderPickedItem::refreshXray);
                refreshThread.setPriority(10);
                refreshThread.start();
            }
        } else {
            WurmEspMod.xrayManager.refresh();
        }
    }

    private static Field hudVisibleField;
    private static boolean hudVisibleUnreadable;

    /** True while the player has hidden the interface (the hide UI key): ESP unit outlines go with it. */
    private static boolean hudHidden(World world) {
        if (hudVisibleUnreadable) {
            return false;
        }
        try {
            Object hud = world.getHud();
            if (hud == null) {
                return false;
            }
            if (hudVisibleField == null) {
                Field field = ReflectionUtil.getField(hud.getClass(), "visible");
                field.setAccessible(true);
                hudVisibleField = field;
            }
            return !hudVisibleField.getBoolean(hud);
        } catch (Throwable t) {
            hudVisibleUnreadable = true;
            logOnce("hide with the UI", t);
            return false;
        }
    }

    /** One feature of the frame. Passed as a static method reference, which captures nothing, so no
     *  object is made per frame. */
    private interface FrameFeature {
        void run(World world, Queue queuePick);
    }

    /** Runs one feature of the frame; a failure is logged once per feature and never reaches the renderer. */
    private static void guard(String feature, FrameFeature body, World world, Queue queuePick) {
        try {
            body.run(world, queuePick);
        } catch (Throwable t) {
            logOnce(feature, t);
        }
    }

    /** WorldRender's fields, resolved once (ReflectionUtil.getField walks all of WorldRender's
     *  fields on every call); resolved again should the hooked object ever be another class. */
    private static Class<?> worldRenderClass;
    private static Field worldField;
    private static Field pickRendererField;
    private static Field queuePickField;

    private static void resolveFields(Class<?> cls) throws NoSuchFieldException {
        if (cls != worldRenderClass) {
            worldField = ReUtils.field(cls, "world");
            pickRendererField = ReUtils.field(cls, "pickRenderer");
            queuePickField = ReUtils.field(cls, "queuePick");
            worldRenderClass = cls;
        }
    }

    private static void units(World world, Queue queuePick) {
        if (!WurmEspMod.anyUnitToggle() || hudHidden(world)) {
            return;
        }
        // only the units that can be outlined at all; each works out what it is once per frame
        List<Unit> units = WurmEspMod.relevantUnits();
        for (int i = 0, n = units.size(); i < n; i++) {
            units.get(i).renderIfShown(queuePick);
        }
    }

    private static void tilesHighlight(World world, Queue queuePick) {
        if (WurmEspMod.tileshighlight) {
            WurmEspMod.tilesHighlightManager.setWorldQueue(world, queuePick);
            if (WurmEspMod.tilesHighlightManager.first) {
                WurmEspMod.tilesHighlightManager.refresh();
                WurmEspMod.tilesHighlightManager.first = false;
            } else if (WurmEspMod.tilesHighlightCronoManager.hasEnded()) {
                WurmEspMod.tilesHighlightManager.refresh();
                WurmEspMod.tilesHighlightCronoManager.restart(5000L);
            }
            WurmEspMod.tilesHighlightManager.queue();
        } else {
            WurmEspMod.tilesHighlightManager.setWorld(world);
        }
    }

    private static void tilesCloseBy(World world, Queue queuePick) {
        if (WurmEspMod.tilescloseby && world.getPlayer().getPos().getLayer() >= 0) {
            WurmEspMod.tilesCloseByManager.setWorldQueue(world, queuePick);
            if (WurmEspMod.tilesCloseByManager.first) {
                WurmEspMod.tilesCloseByManager.refresh();
                WurmEspMod.tilesCloseByManager.first = false;
            } else if (WurmEspMod.tilesCloseByCronoManager.hasEnded()) {
                WurmEspMod.tilesCloseByManager.refresh();
                WurmEspMod.tilesCloseByCronoManager.restart(1000L);
            }
            WurmEspMod.tilesCloseByManager.queue();
        }
    }

    private static void tilesCloseByWalkable(World world, Queue queuePick) {
        if (WurmEspMod.tilesclosebynotrideable && world.getPlayer().getPos().getLayer() >= 0) {
            WurmEspMod.tilesCloseByWalkableManager.setWorldQueue(world, queuePick);
            if (WurmEspMod.tilesCloseByWalkableManager.first) {
                WurmEspMod.tilesCloseByWalkableManager.refresh();
                WurmEspMod.tilesCloseByWalkableManager.first = false;
            } else if (WurmEspMod.tilesCloseByWalkableCronoManager.hasEnded()) {
                WurmEspMod.tilesCloseByWalkableManager.refresh();
                WurmEspMod.tilesCloseByWalkableCronoManager.restart(1000L);
            }
            WurmEspMod.tilesCloseByWalkableManager.queue();
        }
    }

    private static void tilesFlower(World world, Queue queuePick) {
        if (WurmEspMod.tilesFlower && world.getPlayer().getPos().getLayer() >= 0) {
            WurmEspMod.tilesFlowerManager.setWorldQueue(world, queuePick);
            if (WurmEspMod.tilesFlowerManager.first) {
                WurmEspMod.tilesFlowerManager.refresh();
                WurmEspMod.tilesFlowerManager.first = false;
            } else if (WurmEspMod.tilesFlowerCronoManager.hasEnded()) {
                WurmEspMod.tilesFlowerManager.refresh();
                WurmEspMod.tilesFlowerCronoManager.restart(1000L);
            }
            WurmEspMod.tilesFlowerManager.queue();
        }
    }

    private static void xray(World world, Queue queuePick) {
        if (WurmEspMod.xray && world.getPlayer().getPos().getLayer() < 0) {
            WurmEspMod.xrayManager.setWorldQueue(world, queuePick);
            if (WurmEspMod.xrayManager.first) {
                startXrayRefresh();
                WurmEspMod.xrayManager.first = false;
            } else if (WurmEspMod.xrayCronoManager.hasEnded()) {
                startXrayRefresh();
                WurmEspMod.xrayCronoManager.restart(WurmEspMod.xrayrefreshrate * 1000);
            }
            WurmEspMod.xrayManager.queue();
        }
    }

    private static void logOnce(String feature, Throwable t) {
        if (failedFeatures.add(feature))
            java.util.logging.Logger.getLogger(RenderPickedItem.class.getName()).log(Level.WARNING, "ESP " + feature + " failed", t);
    }

    public RenderPickedItem() {
        this.prepareHook("com.wurmonline.client.renderer.WorldRender", "renderPickedItem", "(Lcom/wurmonline/client/renderer/backend/Queue;)V", () -> (proxy, method, args) -> {
            method.invoke(proxy, args);
            final World world;
            final Queue queuePick;
            try {
                resolveFields(proxy.getClass());
                world = (World)worldField.get(proxy);
                WurmEspMod._pickRenderer = (PickRenderer)pickRendererField.get(proxy);
                queuePick = (Queue)queuePickField.get(proxy);
            } catch (Throwable t) {
                logOnce("setup", t);
                return null;
            }
            // each feature on its own: one that throws must not switch off the ones after it
            guard("units", RenderPickedItem::units, world, queuePick);
            guard("tiles highlight", RenderPickedItem::tilesHighlight, world, queuePick);
            guard("tiles close by", RenderPickedItem::tilesCloseBy, world, queuePick);
            guard("tiles close by (not rideable)", RenderPickedItem::tilesCloseByWalkable, world, queuePick);
            guard("flower tiles", RenderPickedItem::tilesFlower, world, queuePick);
            guard("xray", RenderPickedItem::xray, world, queuePick);
            return null;
        });
        WurmEspMod.logger.log(Level.INFO, "[WurmEspMod] WorldRender.renderPickedItem hooked");
    }
}

