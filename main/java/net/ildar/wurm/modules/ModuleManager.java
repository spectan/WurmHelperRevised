package net.ildar.wurm.modules;

import net.ildar.wurm.Utils;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs the client mods that were merged into WurmHelper (see the modules/ folder of the repository).
 * Each module is the original mod's main class; WurmHelper creates it and forwards the mod loader's
 * configure/preInit/init calls, the same calls the loader would make if the mod was installed on its own.
 * <p>
 * A module can be switched off with "module.&lt;id&gt;=false" in WurmHelper.properties. Its own settings
 * are read from mods/WurmHelper/&lt;id&gt;.properties. A module that is still installed as a separate mod
 * (its mods/&lt;name&gt;.properties exists and the jar(s) named by its "classpath" entry are present in
 * mods/) is skipped, so its hooks are not applied twice. A leftover .properties whose jar was deleted
 * does not count as installed.
 */
public final class ModuleManager {
    private static final Logger logger = Logger.getLogger("WurmHelper");
    static final File MODS_DIR = new File("mods");
    static final File CONFIG_DIR = new File(MODS_DIR, "WurmHelper");

    private static final File SKLOGRAPHICS_JAR = new File(new File(MODS_DIR, "skloGraphics"), "SkloGraphics.jar");
    private static final List<String> EARLY_INIT = Arrays.asList("uiscale", "sklographics");

    public enum State { DISABLED, SEPARATELY_INSTALLED, FAILED, LOADED }

    public static final class Module {
        public final String id;
        public final String name;
        public final String description;
        final String className;
        // file names (without .properties) the mod used when installed on its own
        final List<String> standaloneNames;
        Object instance;
        State state;
        String problem;

        Module(String id, String name, String description, String className, String... standaloneNames) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.className = className;
            this.standaloneNames = Arrays.asList(standaloneNames);
        }

        public State getState() {
            return state;
        }

        public String getProblem() {
            return problem;
        }
    }

    // UI Scale comes first, then SkloGraphics: their inits rewrite game classes and skip any class that is
    // already loaded, so they must run before the other mods (and WurmHelper) make the game load them
    private final List<Module> modules = Arrays.asList(
            new Module("uiscale", "UI Scale", "Scales the whole interface for high-resolution screens (console: uifade)",
                    "uiscale.UiScaleMod", "uiscale"),
            new Module("sklographics", "SkloGraphics", "Graphics overhaul: shadows, water, sky, trees and post-processing (settings: quality preset)",
                    "com.sklotopolis.mod.client.graphics.SkloGraphics", "skloGraphics"),
            new Module("archeogroup", "Archeology Grouping", "Groups archaeology fragments in the inventory",
                    "net.bdew.wurm.archeogroup.ArcheoGroupMod", "archeogroup"),
            new Module("compass", "Improved Compass", "Improved compass on the HUD",
                    "net.bdew.wurm.compass.CompassMod", "compass"),
            new Module("esp", "ESP", "Highlights players, creatures, items and tiles (console: esp)",
                    "net.encode.wurmesp.WurmEspMod", "wurmesp", "WurmEsp", "esp"),
            new Module("ezbulk", "EZBulk", "Fast bulk container transfers without the quantity dialog (CTRL/SHIFT while dragging)",
                    "net.roselyndsshadow.ezbulk.EZBulk", "ezbulk"),
            new Module("fishbuddy", "Fish Buddy", "Fishing helper window",
                    "net.bdew.wurm.fishbuddy.FishBuddy", "fishbuddy"),
            new Module("freecam", "Freecam", "Free camera (console: togglefreecam)",
                    "freecammod.FreecamMod", "freecam", "freecammod", "FreecamMod"),
            new Module("improvedimprove", "Improved Improve", "Improve actions use the right tool from the toolbelt",
                    "org.gotti.wurmonline.clientmods.improvedImprove.ImprovedImprove", "improvedimprove", "ImprovedImprove"),
            new Module("livemap", "Live HUD Map", "Live map window on the HUD",
                    "org.gotti.wurmonline.clientmods.livehudmap.LiveHudMapMod", "livemap", "livehudmap"),
            new Module("skilltrack", "Skill Gain Tracker", "Skill gain tracker window",
                    "net.bdew.wurm.skilltrack.SkillTrackMod", "skilltrack"),
            new Module("timelock", "Time Lock", "Locks the time of day shown by the client (console: timelock)",
                    "net.bdew.wurm.timelock.TimeLockMod", "timelock"),
            new Module("toolbelt", "Max Toolbelt", "Unlocks all toolbelt slots",
                    "net.bdew.wurm.toolbelt.ToolbeltMod", "toolbelt"),
            new Module("tooltips", "Better Tooltips", "Extra information in tooltips",
                    "net.bdew.wurm.tooltip.TooltipMod", "tooltips"),
            new Module("rendertweaks", "Render Tweaks", "Hide grass and trees, change the weather (console: toggleGrass, toggleTrees, weather)",
                    "org.tmarchuk.wurmunlimited.client.rendertweaks.ClientRenderTweaksMod", "rendertweaks", "ClientRenderTweaks")
    );

    public List<Module> getModules() {
        return modules;
    }

    /**
     * Create the enabled modules and pass each its settings. Called from WurmHelper.configure
     */
    public void configure(Properties wurmHelperProperties) {
        for (Module module : modules) {
            if (!Boolean.parseBoolean(wurmHelperProperties.getProperty("module." + module.id, "true").trim())) {
                module.state = State.DISABLED;
                continue;
            }
            String standalone = findStandaloneInstall(module);
            if (standalone != null) {
                module.state = State.SEPARATELY_INSTALLED;
                module.problem = "mods/" + standalone + ".properties is installed. Remove the separate mod to use the built-in one";
                logger.warning("[WurmHelper] Module " + module.id + " skipped: " + module.problem);
                continue;
            }
            if (module.id.equals("sklographics") && !SKLOGRAPHICS_JAR.isFile()) {
                // it adds this jar to the game as a resource pack and crashes the client at startup without it
                fail(module, "configure", new IOException(SKLOGRAPHICS_JAR.getPath() + " is missing, install WurmHelper.zip again"));
                continue;
            }
            try {
                module.instance = Class.forName(module.className).getDeclaredConstructor().newInstance();
                if (module.instance instanceof Configurable)
                    ((Configurable) module.instance).configure(withOverrides(module, loadModuleConfig(module)));
                module.state = State.LOADED;
            } catch (Throwable t) {
                fail(module, "configure", t);
            }
        }
    }

    public void preInit() {
        for (Module module : modules) {
            if (module.state != State.LOADED || !(module.instance instanceof PreInitable))
                continue;
            try {
                ((PreInitable) module.instance).preInit();
            } catch (Throwable t) {
                fail(module, "preInit", t);
            }
        }
        for (Module module : modules) {
            if (module.id.equals("sklographics") && module.state == State.LOADED) {
                try {
                    SkloKeys.install();
                } catch (Throwable t) {
                    logger.log(Level.WARNING, "[WurmHelper] SkloGraphics prompts keep E and R", t);
                }
            }
        }
    }

    /**
     * Init the modules that must run before WurmHelper's own init (UI Scale). Called first in WurmHelper.init
     */
    public void initEarly() {
        init(true);
    }

    /**
     * Init the remaining modules. Called last in WurmHelper.init
     */
    public void init() {
        init(false);
    }

    private void init(boolean early) {
        for (Module module : modules) {
            if (module.state != State.LOADED || !(module.instance instanceof Initable))
                continue;
            if (early != EARLY_INIT.contains(module.id))
                continue;
            try {
                ((Initable) module.instance).init();
            } catch (Throwable t) {
                fail(module, "init", t);
            }
        }
    }

    private static Properties withOverrides(Module module, Properties properties) {
        // the built-in copy cannot replace its own jar, so SkloGraphics' updater stays off
        if (module.id.equals("sklographics"))
            properties.setProperty("autoUpdate", "false");
        return properties;
    }

    private void fail(Module module, String phase, Throwable t) {
        module.state = State.FAILED;
        module.problem = phase + " failed: " + t;
        logger.log(Level.SEVERE, "[WurmHelper] Module " + module.id + " " + phase + " failed", t);
        Utils.consolePrint("WurmHelper: module %s failed (%s), see the client log", module.name, module.problem);
    }

    private static String findStandaloneInstall(Module module) {
        for (String name : module.standaloneNames) {
            File propFile = new File(MODS_DIR, name + ".properties");
            if (!propFile.isFile())
                continue;
            // the mod loader loads a mod from its .properties, which names the jar(s) in "classpath";
            // a leftover .properties whose jar was deleted loads nothing
            Properties properties = new Properties();
            try (InputStream in = new FileInputStream(propFile)) {
                properties.load(in);
            } catch (IOException e) {
                logger.log(Level.WARNING, "[WurmHelper] Cannot read " + propFile + ", ignoring it", e);
                continue;
            }
            String classpath = properties.getProperty("classpath");
            if (classpath == null || classpath.trim().isEmpty()) {
                logger.fine("[WurmHelper] " + propFile + " names no classpath, treating the mod as not installed");
                continue;
            }
            boolean jarMissing = false;
            for (String jar : classpath.split(",")) {
                jar = jar.trim();
                // the loader resolves the classpath inside mods/<name>/ (mods/<name>.properties + mods/<name>/<jar>)
                if (!jar.isEmpty() && !new File(new File(MODS_DIR, name), jar).isFile() && !new File(MODS_DIR, jar).isFile()) {
                    logger.fine("[WurmHelper] " + propFile + " references mods/" + jar + " which is missing, treating the mod as not installed");
                    jarMissing = true;
                }
            }
            if (!jarMissing)
                return name;
        }
        return null;
    }

    private static Properties loadModuleConfig(Module module) throws IOException {
        Properties properties = new Properties();
        File file = new File(CONFIG_DIR, module.id + ".properties");
        String source;
        if (!file.isFile()) {
            // first run: write the bundled defaults so the settings can be edited (an update never overwrites them)
            byte[] bundled = readBundledDefaults(module);
            if (bundled != null) {
                try {
                    CONFIG_DIR.mkdirs();
                    java.nio.file.Files.write(file.toPath(), bundled);
                } catch (IOException e) {
                    logger.log(Level.WARNING, "[WurmHelper] Cannot write the default settings " + file, e);
                }
                // read-only install folder: use the bundled defaults directly
                try (InputStream in = new java.io.ByteArrayInputStream(bundled)) {
                    properties.load(in);
                }
                source = file.isFile() ? "bundled defaults, written to " + file : "bundled defaults (" + file + " not writable)";
            } else {
                source = "NOTHING: no " + file + " and no bundled defaults found";
                logger.warning("[WurmHelper] " + module.id + ": " + source);
            }
        } else {
            try (InputStream in = new FileInputStream(file)) {
                properties.load(in);
            }
            source = file.getPath();
        }
        logger.info("[WurmHelper] Module " + module.id + " settings from " + source + " (" + properties.size() + " entries)");
        return properties;
    }

    /** The module's default settings from WurmHelper.jar: the class loader first, then the jar itself. */
    private static byte[] readBundledDefaults(Module module) {
        String name = "default-config/" + module.id + ".properties";
        try (InputStream in = ModuleManager.class.getResourceAsStream("/" + name)) {
            if (in != null)
                return readAll(in);
        } catch (IOException e) {
            logger.log(Level.WARNING, "[WurmHelper] Cannot read bundled " + name, e);
        }
        // the mod loader's class loader does not serve resources and gives its classes no code source,
        // so open the jar the loader named in WurmHelper.properties ("classpath=WurmHelper.jar", in mods/WurmHelper/)
        List<File> jars = new ArrayList<>();
        try {
            java.security.CodeSource source = ModuleManager.class.getProtectionDomain().getCodeSource();
            if (source != null)
                jars.add(new File(source.getLocation().toURI()));
        } catch (Exception ignored) {
            // fall through to the known location
        }
        jars.add(new File(CONFIG_DIR, "WurmHelper.jar"));
        for (File jarFile : jars) {
            if (!jarFile.isFile())
                continue;
            try (java.util.zip.ZipFile jar = new java.util.zip.ZipFile(jarFile)) {
                java.util.zip.ZipEntry entry = jar.getEntry(name);
                if (entry != null) {
                    try (InputStream in = jar.getInputStream(entry)) {
                        return readAll(in);
                    }
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "[WurmHelper] Cannot read " + name + " from " + jarFile, e);
            }
        }
        return null;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        for (int n; (n = in.read(buffer)) > 0; )
            out.write(buffer, 0, n);
        return out.toByteArray();
    }

    /**
     * Lines for the "modules" console command
     */
    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        for (Module module : modules) {
            String state;
            if (module.state == null)
                state = "not loaded";
            else switch (module.state) {
                case LOADED: state = "on"; break;
                case DISABLED: state = "off (module." + module.id + "=false)"; break;
                default: state = module.state.name().toLowerCase().replace('_', ' ') + " - " + module.problem;
            }
            lines.add(String.format("  %s (%s) [%s] - %s", module.name, module.id, state, module.description));
        }
        return lines;
    }
}
