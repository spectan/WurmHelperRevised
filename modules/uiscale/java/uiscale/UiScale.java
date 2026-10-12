package uiscale;

import java.awt.Font;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import com.wurmonline.client.WurmClientBase;
import com.wurmonline.client.WurmEventListener;
import com.wurmonline.client.renderer.Matrix;
import com.wurmonline.client.renderer.PickData;
import com.wurmonline.client.renderer.backend.Pipeline;
import com.wurmonline.client.renderer.backend.Primitive;
import com.wurmonline.client.renderer.backend.Queue;
import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import com.wurmonline.client.renderer.gui.HudQueue;
import com.wurmonline.client.renderer.gui.FightWindowComponent;
import com.wurmonline.client.renderer.gui.TargetWindow;
import com.wurmonline.client.renderer.gui.WurmComponent;
import com.wurmonline.client.renderer.gui.text.FontTexture;
import com.wurmonline.client.startup.splash.StartupRenderer;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

/**
 * Runtime half of the UI scale mod.
 *
 * The idea: the interface is told the window is smaller than it really is
 * (real size / scale), lays itself out in that "virtual" space, and is drawn
 * with a projection that stretches the virtual space over the whole window.
 * Everything it draws - windows, icons, text - comes out {@code scale} times
 * larger. Coordinates are converted only where they cross between the two
 * spaces:
 *
 *   real -> virtual : window size and mouse position going INTO the interface
 *   virtual -> real : the viewport and scissor rectangles going OUT to OpenGL
 *
 * Patcher.java rewrites the handful of call sites where that crossing happens so
 * they call the static methods below instead. Every method keeps the exact
 * stack shape of the call it replaces: the original receiver becomes the first
 * parameter.
 */
public final class UiScale {

    private UiScale() {}

    /**
     * Settings are published as system properties by UiScaleMod.configure(),
     * from mods/uiscale.properties, before any game class is patched. Reading
     * them from properties keeps them right however early this class is
     * initialised.
     */
    static final String SCALE_PROPERTY = "uiscale.scale";

    /** Requested scale. */
    private static volatile float scale = readScale();

    private static float readScale() {
        try {
            String s = System.getProperty(SCALE_PROPERTY);
            if (s != null) {
                float f = Float.parseFloat(s);
                if (f >= 1f && f <= 4f) {
                    return f;
                }
            }
        } catch (Throwable ignored) {
            // fall through to the default
        }
        return 1.5f;
    }

    /** Real window size, and the smaller size the interface believes it has. */
    private static volatile int realW, realH, virtW, virtH;

    static void configure(float s) {
        scale = s;
        realW = realH = virtW = virtH = 0;
    }

    private static void setReal(int w, int h) {
        if (w == realW && h == realH && virtW > 0) {
            return;
        }
        int vw = Math.max(1, Math.round(w / scale));
        int vh = Math.max(1, Math.round(h / scale));
        virtW = vw;
        virtH = vh;
        realW = w;
        realH = h;
        Patcher.log("window " + w + "x" + h + " -> interface " + vw + "x" + vh);
    }

    // The effective factor per axis is realW/virtW rather than the nominal
    // scale, because the virtual size is rounded to whole pixels and the
    // projection stretches exactly virtW over exactly realW.

    private static int toVirtX(int x) {
        return virtW > 0 ? (int) Math.floor((double) x * virtW / realW) : x;
    }

    private static int toVirtY(int y) {
        return virtH > 0 ? (int) Math.floor((double) y * virtH / realH) : y;
    }

    // ---- window size into the interface (WurmClientBase) --------------------

    public static void hudInit(HeadsUpDisplay hud, int w, int h) {
        setReal(w, h);
        hud.init(virtW, virtH);
    }

    public static void hudSetSize(HeadsUpDisplay hud, int w, int h) {
        setReal(w, h);
        hud.setSize(virtW, virtH);
    }

    public static void hudBeginRender(HeadsUpDisplay hud, float fraction, boolean mouseAvailable, int x, int y) {
        int vx = toVirtX(x);
        int vy = toVirtY(y);
        // For the fade's hover test, and a per-frame reset of its scope so an
        // exception mid-render can never leave a window stuck faded.
        mouseVX = vx;
        mouseVY = vy;
        mouseOk = mouseAvailable;
        fadingWindow = null;
        fadeFactor = 1f;
        hud.beginRender(fraction, mouseAvailable, vx, vy);
    }

    // ---- mouse events (WurmEventHandler) ------------------------------------
    // The handler offers every event to a list of listeners in turn. Only the
    // interface's own listener gets converted coordinates; the 3D world, which
    // picks what you click on, keeps real ones.

    public static boolean listenerPressed(WurmEventListener l, int x, int y, int button, int clicks) {
        if (l instanceof HeadsUpDisplay) {
            x = toVirtX(x);
            y = toVirtY(y);
        }
        return l.mousePressed(x, y, button, clicks);
    }

    public static void listenerReleased(WurmEventListener l, int x, int y, int button) {
        if (l instanceof HeadsUpDisplay) {
            x = toVirtX(x);
            y = toVirtY(y);
        }
        l.mouseReleased(x, y, button);
    }

    public static void listenerDragged(WurmEventListener l, int x, int y) {
        if (l instanceof HeadsUpDisplay) {
            x = toVirtX(x);
            y = toVirtY(y);
        }
        l.mouseDragged(x, y);
    }

    public static void listenerMoved(WurmEventListener l, int x, int y) {
        if (l instanceof HeadsUpDisplay) {
            x = toVirtX(x);
            y = toVirtY(y);
        }
        l.mouseMoved(x, y);
    }

    public static void listenerWheeled(WurmEventListener l, int x, int y, int delta) {
        if (l instanceof HeadsUpDisplay) {
            x = toVirtX(x);
            y = toVirtY(y);
        }
        l.mouseWheeled(x, y, delta);
    }

    /** Right-click menu on something in the world, opened by the handler with raw coordinates. */
    public static void hudPopup(HeadsUpDisplay hud, int x, int y, String name, long id) {
        hud.popupRequested(toVirtX(x), toVirtY(y), name, id);
    }

    // ---- world code asking the interface about the mouse position ------------

    /** WorldRender: "is the cursor over a window?" before picking the 3D world. */
    public static boolean hudPick(HeadsUpDisplay hud, int x, int y, PickData data) {
        return hud.pick(toVirtX(x), toVirtY(y), data);
    }

    /** World: toolbelt slot under the cursor. */
    public static void hudSetActiveToolAt(HeadsUpDisplay hud, int x, int y) {
        hud.setActiveToolAt(toVirtX(x), toVirtY(y));
    }

    /** World: items selected under the cursor as targets of an action. */
    public static long[] hudCommandTargets(HeadsUpDisplay hud, int x, int y) {
        return hud.getCommandTargetsFrom(toVirtX(x), toVirtY(y));
    }

    // ---- the interface reading the real mouse position itself ---------------

    public static int guiMouseX(WurmClientBase client) {
        return toVirtX(client.getXMouse());
    }

    public static int guiMouseY(WurmClientBase client) {
        return toVirtY(client.getYMouse());
    }

    // ---- interface drawing going back out to OpenGL in real pixels ----------

    /**
     * gui.Renderer sets the viewport from the HUD's size, which is now the
     * virtual size. The viewport must cover the real window; only the
     * projection should be virtual. Anything else is passed through.
     */
    public static void guiViewport(Pipeline pipeline, int x, int y, int w, int h) {
        if (virtW > 0 && x == 0 && y == 0 && w == virtW && h == virtH) {
            pipeline.setViewport(0, 0, realW, realH);
        } else {
            pipeline.setViewport(x, y, w, h);
        }
    }

    /**
     * Clip rectangles are computed in interface space; glScissor works in
     * framebuffer pixels. Rounded outward so a clipped list never loses its
     * edge row.
     */
    public static void guiScissor(int x, int y, int w, int h) {
        if (virtW <= 0) {
            GL11.glScissor(x, y, w, h);
            return;
        }
        double fx = (double) realW / virtW;
        double fy = (double) realH / virtH;
        int x0 = (int) Math.floor(x * fx);
        int y0 = (int) Math.floor(y * fy);
        int x1 = (int) Math.ceil((x + w) * fx);
        int y1 = (int) Math.ceil((y + h) * fy);
        GL11.glScissor(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
    }

    /** renderPickData caps the hover box at a third of the screen: the interface's screen. */
    public static int guiDisplayWidth() {
        return virtW > 0 ? virtW : Display.getWidth();
    }

    // ---- loading screen (StartupRenderer) -----------------------------------
    // Drawn before the interface exists, in its own projection. It is given
    // the same scaled space as the interface so its text stays crisp.

    /** Constructor width argument. Axes are independent, so each converts alone. */
    public static int splashW(int w) {
        realW = w;
        virtW = Math.max(1, Math.round(w / scale));
        return virtW;
    }

    /** Constructor height argument. */
    public static int splashH(int h) {
        realH = h;
        virtH = Math.max(1, Math.round(h / scale));
        Patcher.log("loading screen " + realW + "x" + h + " -> " + virtW + "x" + virtH);
        return virtH;
    }

    public static void splashRender(StartupRenderer r, int w, int h) {
        setReal(w, h);
        r.render(virtW, virtH);
    }

    /** Retry/Quit buttons after a failed connection. */
    public static void splashClick(StartupRenderer r, int x, int y) {
        r.checkMouseClick(toVirtX(x), toVirtY(y));
    }

    // ---- crisp text (SimpleTextFont) ----------------------------------------
    //
    // Stretching the interface also stretches the glyph atlas, and an 11 px
    // font enlarged by 1.3 is soft. Instead the atlas is rasterised at the real
    // pixel size, each string is drawn through a 1/factor model transform, and
    // every metric is reported in interface units. After the projection
    // stretches the interface back up, one atlas texel covers one screen pixel.
    //
    // The per-axis factor is realW/virtW once the window size is known, and
    // the nominal scale before that (fonts are built at startup).

    private static float textFx() {
        return virtW > 0 ? (float) realW / virtW : scale;
    }

    private static float textFy() {
        return virtH > 0 ? (float) realH / virtH : scale;
    }

    /** The font handed to FontTexture, enlarged so the atlas holds real-size glyphs. */
    public static Font textAtlasFont(Font f) {
        return f.deriveFont(f.getSize2D() * scale);
    }

    /**
     * Replaces modelMatrix.setTranslation(x, y, 0) in drawString. The origin is
     * snapped to a whole screen pixel, otherwise every glyph would straddle
     * two pixels and be blurred by filtering after all.
     */
    public static Matrix textTransform(Matrix m, float x, float y, float z) {
        float fx = textFx();
        float fy = textFy();
        float tx = Math.round(x * fx) / fx;
        float ty = Math.round(y * fy) / fy;
        return m.fromTranslationAndNonUniformScale(tx, ty, z, 1f / fx, 1f / fy, 1f);
    }

    /** drawString returns how far it drew, in atlas pixels; layout wants interface units. */
    public static int textAdvance(int atlasPx) {
        return Math.round(atlasPx / textFx());
    }

    public static int ftMaxHeight(FontTexture ft) {
        return Math.round(ft.getMaxHeight() / textFy());
    }

    public static int ftAscent(FontTexture ft) {
        return Math.round(ft.getAscent() / textFy());
    }

    public static int ftDescent(FontTexture ft) {
        return Math.round(ft.getDescent() / textFy());
    }

    public static int ftLeading(FontTexture ft) {
        return Math.round(ft.getLeading() / textFy());
    }

    public static int ftWidth(FontTexture ft, String s) {
        return Math.round(ft.getWidth(s) / textFx());
    }

    public static int ftWidthChars(FontTexture ft, char[] chars, int offset, int length) {
        return Math.round(ft.getWidth(chars, offset, length) / textFx());
    }

    // ---- fading the chat, event and target windows ---------------------------
    //
    // A registered chat or event window counts as active while a line arrives
    // in any of its tabs, while you type in chat, or while the mouse is over
    // it. After fadeAfter seconds without any of those it fades over
    // FADE_OUT_SECONDS down to fadeAlpha, and snaps back in FADE_IN_SECONDS.
    //
    // The target window fades the same way down to targetFadeAlpha, but
    // whenever nothing is targeted and the mouse is not over it - no idle
    // delay. At 0 it is gone entirely: the mouse no longer brings it back
    // (there is nothing to see), and clicks pass through it to the world.
    //
    // The fight window (stances, attack directions) fades down to
    // combatFadeAlpha whenever you are not fighting and the mouse is not
    // over it.
    //
    // The fade is applied where every interface draw passes: Queue.queue.
    // While a registered window's render() is running, each primitive it
    // queues on the HUD queue is replaced by a copy with alpha multiplied
    // down. The original is never touched.

    static final String FADE_ALPHA_PROPERTY = "uiscale.fade";
    static final String FADE_AFTER_PROPERTY = "uiscale.fadeAfter";
    static final String TARGET_FADE_PROPERTY = "uiscale.targetFade";
    static final String COMBAT_FADE_PROPERTY = "uiscale.combatFade";

    static final String CHAT_TITLE = "Local chat window";
    static final String EVENT_TITLE = "Event chat window";
    static final String TARGET_TITLE = "Target window";
    static final String COMBAT_TITLE = "Fight window";

    private static final float fadeAlpha = readFloat(FADE_ALPHA_PROPERTY, 0.3f, 0.05f, 1f);
    private static final float fadeAfterSeconds = readFloat(FADE_AFTER_PROPERTY, 15f, 1f, 3600f);
    private static final float targetFadeAlpha = readFloat(TARGET_FADE_PROPERTY, 0f, 0f, 1f);
    private static final float combatFadeAlpha = readFloat(COMBAT_FADE_PROPERTY, 0.3f, 0f, 1f);
    private static final long fadeAfterNanos = (long) (fadeAfterSeconds * 1e9);
    private static final float FADE_OUT_SECONDS = 1.5f;
    private static final float FADE_IN_SECONDS = 0.2f;

    private static float readFloat(String property, float def, float min, float max) {
        try {
            String s = System.getProperty(property);
            if (s != null) {
                float f = Float.parseFloat(s);
                if (f >= min && f <= max) {
                    return f;
                }
            }
        } catch (Throwable ignored) {
            // fall through to the default
        }
        return def;
    }

    private static final class Faded {
        final Object window;
        final String title;
        /** The opacity it fades down to. */
        final float floor;
        volatile long lastActivity;
        float alpha = 1f;
        long lastUpdate;

        Faded(Object window, String title, float floor) {
            this.window = window;
            this.title = title;
            this.floor = floor;
            this.lastActivity = System.nanoTime();
            this.lastUpdate = this.lastActivity;
        }
    }

    private static final CopyOnWriteArrayList<Faded> faded = new CopyOnWriteArrayList<Faded>();

    /** The registered window whose render() is running, and its current factor. */
    private static volatile Object fadingWindow;
    private static volatile float fadeFactor = 1f;

    private static volatile int mouseVX = -1, mouseVY = -1;
    private static volatile boolean mouseOk;

    private static Faded find(Object window) {
        if (window == null) {
            return null;
        }
        for (Faded f : faded) {
            if (f.window == window) {
                return f;
            }
        }
        return null;
    }

    /** End of WurmTabbedWindow's constructor. */
    public static void fadeRegister(WurmComponent window, String title) {
        if (fadeAlpha >= 1f || !(CHAT_TITLE.equals(title) || EVENT_TITLE.equals(title))) {
            return;
        }
        faded.add(new Faded(window, title, fadeAlpha));
        Patcher.log("fade: '" + title + "' fades to " + fadeAlpha + " after " + fadeAfterSeconds + " s idle"
                  + (fadeEnabled ? "" : " - currently switched OFF by uifade"));
    }

    /** TargetWindow.targetId (protected), read every frame the window draws. */
    private static volatile Field targetIdField;
    /** TargetWindow.NO_TARGET. */
    private static final long NO_TARGET = -1L;
    /** Set once targetId turns out to be missing, so the lookup is not retried every frame. */
    private static volatile boolean targetUnavailable;

    /** Set once the fight window has been registered (or ruled out), so it is not looked for again. */
    private static volatile boolean combatSeen;

    /**
     * The target and fight windows are registered the first time they render
     * rather than through another call site: the HUD builds exactly one of
     * each, and they are only recognised by their class. Returns null for
     * every other component.
     */
    private static Faded registerByClass(WurmComponent c) {
        if (c instanceof TargetWindow) {
            return registerTarget(c);
        }
        if (!combatSeen && c instanceof FightWindowComponent) {
            combatSeen = true;
            if (combatFadeAlpha >= 1f) {
                return null;
            }
            Faded f = new Faded(c, COMBAT_TITLE, combatFadeAlpha);
            faded.add(f);
            Patcher.log("fade: '" + COMBAT_TITLE + "' fades to " + combatFadeAlpha + " out of combat"
                      + (fadeEnabled ? "" : " - currently switched OFF by uifade"));
            return f;
        }
        return null;
    }

    private static Faded registerTarget(WurmComponent c) {
        if (targetFadeAlpha >= 1f || targetUnavailable) {
            return null;
        }
        Field id = field(TargetWindow.class, "targetId");
        if (id == null || id.getType() != long.class) {
            // Without it there is no telling whether anything is targeted: never fade.
            targetUnavailable = true;
            Patcher.log("fade: TargetWindow.targetId not found, the target window will not fade");
            return null;
        }
        targetIdField = id;
        Faded f = new Faded(c, TARGET_TITLE, targetFadeAlpha);
        faded.add(f);
        targetFaded = f;
        Patcher.log("fade: '" + TARGET_TITLE + "' " + (targetFadeAlpha <= 0f ? "is hidden" : "fades to " + targetFadeAlpha)
                  + " when nothing is targeted" + (fadeEnabled ? "" : " - currently switched OFF by uifade"));
        return f;
    }

    /** The registered target window, once it has rendered. */
    private static volatile Faded targetFaded;

    /**
     * Replaces WurmComponent.contains in HeadsUpDisplay, which is how the HUD
     * finds the window under the mouse. A target window that is hidden
     * (targetFade 0, nothing targeted) is not there, so clicks reach the
     * world or the window beneath it.
     */
    public static boolean hudContains(WurmComponent c, int x, int y) {
        if (!c.contains(x, y)) {
            return false;
        }
        Faded f = targetFaded;
        return f == null || c != f.window || !fadeEnabled || f.floor > 0f || hasTarget(c);
    }

    private static boolean hasTarget(Object window) {
        try {
            return targetIdField.getLong(window) != NO_TARGET;
        } catch (Throwable t) {
            return true;
        }
    }

    // ---- console command: uifade [on|off|status] ----------------------------

    /** Set by UiScaleMod: the folder uiscale.jar is in, where the on/off choice is kept. */
    static final String DIR_PROPERTY = "uiscale.dir";

    /** The uifade on/off choice, remembered across restarts. */
    private static volatile boolean fadeEnabled = readFadeState();

    private static File stateFile() {
        String dir = System.getProperty(DIR_PROPERTY);
        return dir == null ? null : new File(dir, "uiscale-state.properties");
    }

    private static boolean readFadeState() {
        try {
            File f = stateFile();
            if (f == null || !f.isFile()) {
                return true;
            }
            Properties p = new Properties();
            InputStream in = new FileInputStream(f);
            try {
                p.load(in);
            } finally {
                in.close();
            }
            return !"off".equalsIgnoreCase(p.getProperty("fade", "on").trim());
        } catch (Throwable t) {
            return true;
        }
    }

    private static void saveFadeState(boolean on) {
        try {
            File f = stateFile();
            if (f == null) {
                return;
            }
            Properties p = new Properties();
            p.setProperty("fade", on ? "on" : "off");
            OutputStream out = new FileOutputStream(f);
            try {
                p.store(out, "uiscale: written by the uifade console command");
            } finally {
                out.close();
            }
        } catch (Throwable t) {
            Patcher.log("could not save the uifade setting: " + t);
        }
    }

    /**
     * Entry of WurmConsole.handleInput. Handles every "uifade" command on the
     * line (commands are ';'-separated) and returns the rest for the console,
     * or "//" - which the console ignores - when nothing is left.
     */
    public static String consoleFilter(String line) {
        if (line == null || line.toLowerCase().indexOf("uifade") < 0) {
            return line;
        }
        try {
            StringBuilder rest = new StringBuilder();
            for (String part : line.split(";")) {
                String[] words = part.trim().split("\\s+");
                if (words[0].equalsIgnoreCase("uifade")) {
                    fadeCommand(words.length > 1 ? words[1] : null);
                } else if (part.trim().length() > 0) {
                    if (rest.length() > 0) {
                        rest.append(';');
                    }
                    rest.append(part);
                }
            }
            return rest.length() == 0 ? "//" : rest.toString();
        } catch (Throwable t) {
            return line;
        }
    }

    private static void fadeCommand(String arg) {
        if (fadeAlpha >= 1f && targetFadeAlpha >= 1f && combatFadeAlpha >= 1f) {
            say("uifade: fading is switched off in mods\\uiscale.properties (fade=1, targetFade=1, combatFade=1)");
            return;
        }
        boolean on;
        if (arg == null || arg.equalsIgnoreCase("toggle")) {
            on = !fadeEnabled;
        } else if (arg.equalsIgnoreCase("on")) {
            on = true;
        } else if (arg.equalsIgnoreCase("off")) {
            on = false;
        } else if (arg.equalsIgnoreCase("status")) {
            say("uifade: fading is " + (fadeEnabled ? "ON" : "OFF") + " (chat and event "
                + (fadeAlpha >= 1f ? "never fade" : "fade to " + fadeAlpha + " after " + fadeAfterSeconds + " s")
                + ", target window " + (targetFadeAlpha >= 1f ? "never fades" : targetFadeAlpha <= 0f
                ? "hidden with no target" : "fades to " + targetFadeAlpha + " with no target")
                + ", fight window " + (combatFadeAlpha >= 1f ? "never fades" : "fades to " + combatFadeAlpha
                + " out of combat") + ")");
            return;
        } else {
            say("usage: uifade [on|off|status]   - no argument toggles");
            return;
        }
        long now = System.nanoTime();
        for (Faded f : faded) {
            f.lastActivity = now;
            if (!on) {
                f.alpha = 1f;
            }
        }
        fadeEnabled = on;
        saveFadeState(on);
        say("uifade: fading " + (on ? "ON" : "OFF") + " (remembered)");
    }

    /** The game routes System.out to its console. */
    private static void say(String msg) {
        System.out.println(msg);
        Patcher.log(msg);
    }

    /** Entry of every WurmComponent.render(). Cheap no-op unless it is a registered window. */
    public static void fadeEnter(WurmComponent c) {
        if (!fadeEnabled) {
            return;
        }
        Faded f = find(c);
        if (f == null && (f = registerByClass(c)) == null) {
            return;
        }
        long now = System.nanoTime();
        boolean hovered = mouseOk && c.contains(mouseVX, mouseVY);
        float target;
        if (TARGET_TITLE.equals(f.title)) {
            // Fully hidden, hovering its empty spot must not bring it back.
            target = (hasTarget(c) || (hovered && f.floor > 0f)) ? 1f : f.floor;
        } else if (COMBAT_TITLE.equals(f.title)) {
            target = (hovered || ((FightWindowComponent) c).getFighting()) ? 1f : f.floor;
        } else {
            if (hovered) {
                f.lastActivity = now;
            }
            target = (now - f.lastActivity < fadeAfterNanos) ? 1f : f.floor;
        }
        float dt = Math.min(0.25f, (now - f.lastUpdate) / 1e9f);
        f.lastUpdate = now;
        float span = 1f - f.floor;
        if (f.alpha < target) {
            f.alpha = Math.min(target, f.alpha + span * dt / FADE_IN_SECONDS);
        } else if (f.alpha > target) {
            f.alpha = Math.max(target, f.alpha - span * dt / FADE_OUT_SECONDS);
        }
        fadingWindow = c;
        fadeFactor = f.alpha;
    }

    /** Each return of WurmComponent.render(). */
    public static void fadeExit(WurmComponent c) {
        if (c == fadingWindow) {
            fadingWindow = null;
            fadeFactor = 1f;
        }
    }

    /**
     * Top of Queue.queue(). Only interface draws inside a registered window
     * are touched; the world's queues never are. The first test is the only
     * cost on the hot path the rest of the time.
     */
    public static Primitive fadePrimitive(Queue q, Primitive p) {
        float f = fadeFactor;
        if (f >= 0.999f || p == null || !(q instanceof HudQueue)) {
            return p;
        }
        Primitive copy = q.reservePrimitive();
        if (copy == null || copy == p) {
            return p;
        }
        copy.copyFrom(p);
        copy.a *= f;
        return copy;
    }

    /** Entry of DefaultTab.addLine(...): wake the window that holds the tab. */
    public static void fadeTabActivity(Object tab) {
        if (faded.isEmpty() || tab == null) {
            return;
        }
        try {
            // tab.owner is the ChatManager (a ChatPanelComponent for chat
            // tabs); its parent is the window. Both are non-public fields, so
            // this goes by reflection - once per incoming line, not per frame.
            Field ownerField = field(tab.getClass(), "owner");
            Object owner = ownerField == null ? null : ownerField.get(tab);
            if (owner == null) {
                return;
            }
            Field parentField = field(owner.getClass(), "parent");
            Faded f = find(parentField == null ? null : parentField.get(owner));
            if (f != null) {
                f.lastActivity = System.nanoTime();
            }
        } catch (Throwable ignored) {
            // never let the fade break chat
        }
    }

    /** Entry of HeadsUpDisplay.handleInputChanged: typing keeps chat awake. */
    public static void fadeTyping() {
        long now = System.nanoTime();
        for (Faded f : faded) {
            if (CHAT_TITLE.equals(f.title)) {
                f.lastActivity = now;
            }
        }
    }

    private static final Map<String, Field> fieldCache = new HashMap<String, Field>();

    /** Field by name on the class or a superclass, cached (including misses). */
    private static synchronized Field field(Class<?> cls, String name) {
        String key = cls.getName() + "#" + name;
        if (fieldCache.containsKey(key)) {
            return fieldCache.get(key);
        }
        Field found = null;
        for (Class<?> k = cls; k != null && found == null; k = k.getSuperclass()) {
            try {
                found = k.getDeclaredField(name);
                found.setAccessible(true);
            } catch (NoSuchFieldException e) {
                found = null;
            }
        }
        fieldCache.put(key, found);
        return found;
    }
}
