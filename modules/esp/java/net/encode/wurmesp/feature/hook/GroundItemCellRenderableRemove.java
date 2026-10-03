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
            Class<?> cls = proxy.getClass();
            GroundItemData item = (GroundItemData)ReflectionUtil.getPrivateField((Object)proxy, (Field)ReflectionUtil.getField(cls, (String)"item"));
            WurmEspMod.pickableUnits.removeIf(unit -> unit.getId() == item.getId());
            return null;
        });
        WurmEspMod.logger.log(Level.INFO, "[WurmEspMod] GroundItemCellRenderable.removed hooked");
    }
}

