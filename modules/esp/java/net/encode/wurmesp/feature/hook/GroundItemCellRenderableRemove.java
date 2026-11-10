package net.encode.wurmesp.feature.hook;

import java.util.logging.Level;

import com.wurmonline.client.renderer.GroundItemData;

import net.encode.wurmesp.WurmEspMod;
import net.encode.wurmesp.util.ReUtils;

public class GroundItemCellRenderableRemove
extends Hook {
    public GroundItemCellRenderableRemove() {
        this.prepareHook("com.wurmonline.client.renderer.cell.GroundItemCellRenderable", "removed", "(Z)V", () -> (proxy, method, args) -> {
            method.invoke(proxy, args);
            try {
                GroundItemData item = (GroundItemData)ReUtils.field(proxy.getClass(), "item").get(proxy);
                WurmEspMod.untrack(item.getId());
            } catch (Throwable t) {
                WurmEspMod.logger.log(Level.WARNING, "[WurmEspMod] could not untrack a ground item", t);
            }
            return null;
        });
        WurmEspMod.logger.log(Level.INFO, "[WurmEspMod] GroundItemCellRenderable.removed hooked");
    }
}

