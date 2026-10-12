package net.encode.wurmesp.feature.hook;

import com.wurmonline.client.renderer.GroundItemData;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.cell.CellRenderable;

import java.lang.reflect.Field;
import java.util.logging.Level;
import net.encode.wurmesp.Unit;
import net.encode.wurmesp.WurmEspMod;
import net.encode.wurmesp.util.SoundUtils;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

public class GroundItemCellRenderableInit
extends Hook {
    public GroundItemCellRenderableInit() {
        this.prepareHook("com.wurmonline.client.renderer.cell.GroundItemCellRenderable", "initialize", "()V", () -> (proxy, method, args) -> {
            method.invoke(proxy, args);
            // ESP must never break the client's own initialize: anything it trips over stays here.
            try {
                Class<?> cls = proxy.getClass();
                PickableUnit pUnit = (PickableUnit)proxy;
                GroundItemData item = (GroundItemData)ReflectionUtil.getPrivateField((Object)proxy, (Field)ReflectionUtil.getField(cls, (String)"item"));
                Unit unit = new Unit(CellRenderable.world, item.getId(), pUnit, String.valueOf(item.getModelName()), pUnit.getHoverName());
                // Every item is tracked; what is outlined is decided each frame, so an esp search made
                // later still finds the items that were already on the ground.
                WurmEspMod.track(unit);
                if (unit.isSpecial()) {
                    if (WurmEspMod.specials && WurmEspMod.playsoundspecial) {
                        SoundUtils.playSound(WurmEspMod.soundspecial);
                    }
                } else if (unit.isSpotted()) {
                    if (WurmEspMod.items && WurmEspMod.playsounditem) {
                        SoundUtils.playSound(WurmEspMod.sounditem);
                    }
                }
            } catch (Throwable t) {
                WurmEspMod.logger.log(Level.WARNING, "[WurmEspMod] could not track a ground item", t);
            }
            return null;
        });
        WurmEspMod.logger.log(Level.INFO, "[WurmEspMod] GroundItemCellRenderable.initialize hooked");
    }
}
