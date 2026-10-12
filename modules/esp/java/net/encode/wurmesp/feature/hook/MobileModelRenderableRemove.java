package net.encode.wurmesp.feature.hook;

import java.util.logging.Level;

import com.wurmonline.client.renderer.PickableUnit;

import net.encode.wurmesp.WurmEspMod;

public class MobileModelRenderableRemove
extends Hook {
    public MobileModelRenderableRemove() {
        this.prepareHook("com.wurmonline.client.renderer.cell.MobileModelRenderable", "removed", "(Z)V", () -> (proxy, method, args) -> {
            method.invoke(proxy, args);
            try {
                if (proxy instanceof PickableUnit) {
                    WurmEspMod.untrack(((PickableUnit)proxy).getId());
                }
            } catch (Throwable t) {
                WurmEspMod.logger.log(Level.WARNING, "[WurmEspMod] could not untrack a creature", t);
            }
            return null;
        });
        WurmEspMod.logger.log(Level.INFO, "[WurmEspMod] MobileModelRenderable.removed hooked");
    }
}
