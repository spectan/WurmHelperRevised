package net.encode.wurmesp.feature.hook;

import java.lang.reflect.Field;
import java.util.logging.Level;

import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import com.wurmonline.client.renderer.GroundItemData;

import net.encode.wurmesp.WurmEspMod;

public class GroundItemCellRenderableRemove
extends Hook {
    public GroundItemCellRenderableRemove() {
        this.prepareHook("com.wurmonline.client.renderer.cell.GroundItemCellRenderable", "removed", "(Z)V", () -> (proxy, method, args) -> {
            method.invoke(proxy, args);
            try {
                Class<?> cls = proxy.getClass();
                GroundItemData item = (GroundItemData)ReflectionUtil.getPrivateField((Object)proxy, (Field)ReflectionUtil.getField(cls, (String)"item"));
                WurmEspMod.untrack(item.getId());
            } catch (Throwable t) {
                WurmEspMod.logger.log(Level.WARNING, "[WurmEspMod] could not untrack a ground item", t);
            }
            return null;
        });
        WurmEspMod.logger.log(Level.INFO, "[WurmEspMod] GroundItemCellRenderable.removed hooked");
    }
}

