package uiscale;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URL;
import java.util.Properties;

import javassist.ClassPool;
import javassist.CtClass;

import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod;

/**
 * UI scale as a client mod for Ago's client mod loader.
 *
 * mods/uiscale.properties names this class. The loader calls configure() with
 * that file's settings, and later init(), before the game's first class is
 * loaded. The loader defines every game class from its javassist class pool,
 * so init() takes each class UI scale targets from the pool - with whatever
 * the loader and other mods changed in it so far - lets {@link Patcher}
 * rewrite the call sites in those bytes, and puts the result back in the pool.
 * Mods that change the class later build on the patched version.
 *
 * Initable is implemented on purpose: the loader (0.15) calls init() only
 * through that interface, and calls configure() before init() only for mods
 * that have it. WurmClientMod's own default init() is never used. Nothing is
 * done in a class loader hook: the loader replaces any translator a mod
 * installs as soon as the mod's preInit()/init() returns.
 */
public class UiScaleMod implements WurmClientMod, Configurable, Initable {

    private float scale = 1.5f;

    @Override
    public void configure(Properties p) {
        // Patcher uses the ASM copy inside the Java runtime; let the parent
        // loader supply it instead of the mod loader defining a second one.
        HookManager.getInstance().getLoader().delegateLoadingOf("jdk.internal.org.objectweb.asm.");

        File dir = modDir();
        System.setProperty(UiScale.DIR_PROPERTY, dir.getAbsolutePath());
        Patcher.openLog(dir);

        scale = number(p, "scale", 1.5f, 1f, 4f);
        float fade = number(p, "fade", 0.3f, 0.05f, 1f);
        float after = number(p, "fadeAfter", 15f, 1f, 3600f);
        float targetFade = number(p, "targetFade", 0f, 0f, 1f);
        float combatFade = number(p, "combatFade", 0.3f, 0f, 1f);
        System.setProperty(UiScale.SCALE_PROPERTY, Float.toString(scale));
        System.setProperty(UiScale.FADE_ALPHA_PROPERTY, Float.toString(fade));
        System.setProperty(UiScale.FADE_AFTER_PROPERTY, Float.toString(after));
        System.setProperty(UiScale.TARGET_FADE_PROPERTY, Float.toString(targetFade));
        System.setProperty(UiScale.COMBAT_FADE_PROPERTY, Float.toString(combatFade));

        Patcher.info("uiscale " + Patcher.VERSION + ": scale " + scale + ", chat fade " + fade
                     + " after " + after + " s, target fade " + targetFade + ", fight window fade " + combatFade
                     + " (java " + System.getProperty("java.version") + ")");
    }

    @Override
    public void init() {
        if (scale == 1f) {
            Patcher.info("uiscale: scale is 1.0, nothing to patch");
            return;
        }
        ClassPool pool = HookManager.getInstance().getClassPool();
        for (String internal : Patcher.RULES.keySet()) {
            String name = internal.replace('/', '.');
            try {
                CtClass cc = pool.get(name);
                if (cc.isFrozen()) {
                    // The loader freezes a class when it defines it: too late.
                    Patcher.rulesMissed += Patcher.RULES.get(internal).size();
                    Patcher.warn("NOT PATCHED  " + internal + ": already loaded before UI scale's init - "
                                 + "another mod loads it too early");
                    continue;
                }
                byte[] patched = Patcher.patchLoaded(internal, cc.toBytecode());
                if (patched == null) {
                    // toBytecode() froze it; the loader must still be able to
                    // write it out later, with any other mod's changes.
                    cc.defrost();
                    continue;
                }
                pool.makeClass(new ByteArrayInputStream(patched), false);
            } catch (Throwable t) {
                Patcher.warn("FAILED  " + internal + ", left unchanged: " + t);
            }
        }
    }

    @Override
    public String getVersion() {
        return Patcher.VERSION;
    }

    /** The folder the mod jar is in (mods/WurmHelper since it was merged into WurmHelper), for the log and the uifade setting. */
    private static File modDir() {
        try {
            // jar:file:/C:/.../mods/uiscale/uiscale.jar!/uiscale/UiScaleMod.class
            URL u = HookManager.getInstance().getClassPool().find(UiScaleMod.class.getName());
            if (u != null && "jar".equals(u.getProtocol())) {
                String s = u.getFile();
                File jar = new File(new URL(s.substring(0, s.indexOf("!/"))).toURI());
                return jar.getParentFile();
            }
        } catch (Throwable ignored) {
            // fall back below
        }
        return new File("mods", "WurmHelper").getAbsoluteFile(); // merged into WurmHelper
    }

    private static float number(Properties p, String key, float def, float min, float max) {
        String s = p.getProperty(key);
        if (s == null || s.trim().length() == 0) {
            return def;
        }
        try {
            float f = Float.parseFloat(s.trim().replace(',', '.'));
            if (f >= min && f <= max) {
                return f;
            }
            Patcher.warn("uiscale: " + key + "=" + s.trim() + " is outside " + min + " to " + max + ", using " + def);
        } catch (NumberFormatException e) {
            Patcher.warn("uiscale: " + key + "=" + s.trim() + " is not a number, using " + def);
        }
        return def;
    }
}
