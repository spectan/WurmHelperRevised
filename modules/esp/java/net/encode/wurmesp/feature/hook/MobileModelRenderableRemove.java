package net.encode.wurmesp.feature.hook;

import java.util.logging.Level;

import com.wurmonline.client.renderer.PickableUnit;

import net.encode.wurmesp.WurmEspMod;

public class MobileModelRenderableRemove
extends Hook {
    public MobileModelRenderableRemove() {
        this.prepareHook("com.wurmonline.client.renderer.cell.MobileModelRenderable", "removed", "(Z)V", () -> (proxy, method, args) -> {
            method.invoke(proxy, args);
            PickableUnit item = (PickableUnit)proxy;
            WurmEspMod.pickableUnits.removeIf(unit -> unit.getId() == item.getId());
            return null;
        });
        WurmEspMod.logger.log(Level.INFO, "[WurmEspMod] MobileModelRenderable.removed hooked");
    }
}

