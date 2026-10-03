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

    // UI Scale comes first: its init rewrites game classes and skips any class that is already loaded,
    // so it must run before the other mods (and WurmHelper) make the game load them
    private final List<Module> modules = Arrays.asList(
            new Module("uiscale", "UI Scale", "Scales the whole interface for high-resolution screens (console: uifade)",
                    "uiscale.UiScaleMod", "uiscale"),
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
                    "net.bdew.wurm.tooltip.TooltipMod", "tooltips")
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
            try {
                module.instance = Class.forName(module.className).getDeclaredConstructor().newInstance();
                if (module.instance instanceof Configurable)
                    ((Configurable) module.instance).configure(loadModuleConfig(module));
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
            if (early != module.id.equals("uiscale"))
                continue;
            try {
                ((Initable) module.instance).init();
            } catch (Throwable t) {
                fail(module, "init", t);
            }
        }
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
                if (!jar.isEmpty() && !new File(MODS_DIR, jar).isFile()) {
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
        if (file.isFile()) {
            try (InputStream in = new FileInputStream(file)) {
                properties.load(in);
            }
        }
        return properties;
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
