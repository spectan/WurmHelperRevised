package net.encode.wurmesp.feature.hook;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import com.wurmonline.client.game.World;
import com.wurmonline.client.renderer.PickRenderer;
import com.wurmonline.client.renderer.backend.Queue;

import net.encode.wurmesp.Unit;
import net.encode.wurmesp.WurmEspMod;

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

    /** Runs one feature of the frame; a failure is logged once per feature and never reaches the renderer. */
    private static void guard(String feature, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            logOnce(feature, t);
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
                Class<?> cls = proxy.getClass();
                world = (World)ReflectionUtil.getPrivateField((Object)proxy, (Field)ReflectionUtil.getField(cls, (String)"world"));
                WurmEspMod._pickRenderer = (PickRenderer)ReflectionUtil.getPrivateField((Object)proxy, (Field)ReflectionUtil.getField(cls, (String)"pickRenderer"));
                queuePick = (Queue)ReflectionUtil.getPrivateField((Object)proxy, (Field)ReflectionUtil.getField(cls, (String)"queuePick"));
            } catch (Throwable t) {
                logOnce("setup", t);
                return null;
            }
            // each feature on its own: one that throws must not switch off the ones after it
            guard("units", () -> {
                for (Unit unit : WurmEspMod.trackedUnits()) {
                    if (!WurmEspMod.shouldRender(unit)) {
                        continue;
                    }
                    boolean showConditioned = unit.isConditioned() && WurmEspMod.conditioned
                            || unit.isConditioned() && WurmEspMod.conditionedcolorsallways
                            || unit.isChampion() && WurmEspMod.championmcoloralways;
                    unit.renderUnit(queuePick, showConditioned);
                }
            });
            guard("tiles highlight", () -> {
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
            });
            guard("tiles close by", () -> {
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
            });
            guard("tiles close by (not rideable)", () -> {
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
            });
            guard("flower tiles", () -> {
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
            });
            guard("xray", () -> {
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
            });
            return null;
        });
        WurmEspMod.logger.log(Level.INFO, "[WurmEspMod] WorldRender.renderPickedItem hooked");
    }
}

