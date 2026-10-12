package net.encode.wurmesp.feature.hook;

import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.cell.CellRenderable;
import com.wurmonline.client.renderer.cell.CreatureCellRenderable;
import java.util.logging.Level;
import net.encode.wurmesp.Unit;
import net.encode.wurmesp.WurmEspMod;
import net.encode.wurmesp.util.SoundUtils;

public class MobileModelRenderableInit
extends Hook {
    public MobileModelRenderableInit() {
        this.prepareHook("com.wurmonline.client.renderer.cell.MobileModelRenderable", "initialize", "()V", () -> (proxy, method, args) -> {
            method.invoke(proxy, args);
            // ESP must never break the client's own initialize: anything it trips over stays here.
            try {
                // Only creatures are tracked (other mobile models, a boat, are not creatures).
                if (proxy instanceof CreatureCellRenderable) {
                    PickableUnit pUnit = (PickableUnit)proxy;
                    CreatureCellRenderable creature = (CreatureCellRenderable)proxy;
                    Unit unit = new Unit(CellRenderable.world, pUnit.getId(), pUnit, String.valueOf(creature.getModelName()), creature.getHoverName());
                    // Every creature is tracked; what is outlined is decided each frame, so a search or a
                    // toggle changed later still finds the ones that were already here.
                    WurmEspMod.track(unit);
                    if (unit.isPlayer() || unit.isMob()) {
                        if (unit.isUnique() && WurmEspMod.uniques && WurmEspMod.playsoundunique) {
                            SoundUtils.playSound(WurmEspMod.soundunique);
                        }
                    } else if (unit.isSpecial()) {
                        if (WurmEspMod.specials && WurmEspMod.playsoundspecial) {
                            SoundUtils.playSound(WurmEspMod.soundspecial);
                        }
                    } else if (unit.isSpotted()) {
                        if (WurmEspMod.items && WurmEspMod.playsounditem) {
                            SoundUtils.playSound(WurmEspMod.sounditem);
                        }
                    }
                }
            } catch (Throwable t) {
                WurmEspMod.logger.log(Level.WARNING, "[WurmEspMod] could not track a creature", t);
            }
            return null;
        });
        WurmEspMod.logger.log(Level.INFO, "[WurmEspMod] MobileModelRenderable.initialize hooked");
    }
}
