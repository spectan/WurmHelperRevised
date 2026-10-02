package uiscale;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;

/**
 * The call sites UI scale rewrites, and the bytecode rewriter.
 *
 * {@link UiScaleMod} hands each targeted game class to {@link #patchLoaded}
 * during the mod loader's init phase, before the game starts, and the
 * matching call sites are redirected through {@link UiScale}. Nothing on disk
 * is modified.
 *
 * Uses the ASM library the Java 8 runtime already carries internally
 * (jdk.internal.org.objectweb.asm), so there is nothing to download.
 *
 * Every rewrite either replaces one invocation with a static call that
 * consumes exactly the same stack values, or inserts a value-for-value helper
 * call (Font -> Font, int -> int) in straight-line code. Stack depth never
 * grows and no branch is added, so no stack map frame or max-stack value
 * changes, and the class writer can copy them through.
 */
public final class Patcher {

    private Patcher() {}

    /** Release version; package.ps1 reads it from here to name the zip. */
    static final String VERSION = "1.0.0";

    static final String HELPER = "uiscale/UiScale";

    static final String HUD = "com/wurmonline/client/renderer/gui/HeadsUpDisplay";
    static final String LISTENER = "com/wurmonline/client/WurmEventListener";
    static final String CLIENT = "com/wurmonline/client/WurmClientBase";
    static final String PIPELINE = "com/wurmonline/client/renderer/backend/Pipeline";

    /** Replace the call with a static helper taking the same stack values. */
    static final int REDIRECT = 0;
    /** Keep the call, but first pass its java.awt.Font argument (under one
     *  more category-1 value) through a helper: SWAP, helper(Font)Font, SWAP. */
    static final int WRAP_FONT_ARG = 1;
    /** In one named method, pass every int it returns through a helper. */
    static final int CONVERT_RETURN = 2;
    /** Keep the call, but first convert its two int arguments (width, height):
     *  SWAP, splashW, SWAP, splashH. Used on a constructor, which cannot be
     *  redirected to a static method. */
    static final int WRAP_WH_ARGS = 3;
    /** Insert "load locals, call helper[, store result]" at the entry of a
     *  method, or before each of its void returns. */
    static final int INJECT = 4;

    /** One call site pattern to patch inside one class. */
    static final class Rule {
        final int mode, opcode;
        final String owner, name, desc, helper;
        // INJECT only
        boolean atEntry;
        int[] loads = new int[0];
        String injectDesc;
        int storeTo = -1;
        int hits;

        Rule(int mode, int opcode, String owner, String name, String desc, String helper) {
            this.mode = mode;
            this.opcode = opcode;
            this.owner = owner;
            this.name = name;
            this.desc = desc;
            this.helper = helper;
        }

        /**
         * @param method     method to patch
         * @param methodDesc its descriptor, or null for every overload
         * @param atEntry    true: at entry; false: before each RETURN
         * @param loads      local slots to ALOAD as the helper's arguments
         * @param storeTo    local slot to ASTORE the helper's result in, or -1
         */
        static Rule inject(String method, String methodDesc, boolean atEntry, int[] loads,
                           String helper, String helperDesc, int storeTo) {
            Rule r = new Rule(INJECT, Opcodes.RETURN, "", method, methodDesc, helper);
            r.atEntry = atEntry;
            r.loads = loads;
            r.injectDesc = helperDesc;
            r.storeTo = storeTo;
            return r;
        }

        boolean matchesInject(String inMethod, String inDesc) {
            return mode == INJECT && name.equals(inMethod) && (desc == null || desc.equals(inDesc));
        }

        boolean matchesCall(int op, String o, String n, String d) {
            return mode != CONVERT_RETURN && mode != INJECT
                   && op == opcode && o.equals(owner) && n.equals(name) && d.equals(desc);
        }

        /** For CONVERT_RETURN, owner/name/desc name the method whose returns are converted. */
        boolean matchesReturn(String inMethod, String inDesc, int op) {
            return mode == CONVERT_RETURN && op == opcode && name.equals(inMethod) && desc.equals(inDesc);
        }

        /** Instance and interface calls: the receiver becomes the first parameter. */
        String helperDesc() {
            if (mode == WRAP_FONT_ARG) {
                return "(Ljava/awt/Font;)Ljava/awt/Font;";
            }
            if (mode == CONVERT_RETURN || mode == WRAP_WH_ARGS) {
                return "(I)I";
            }
            return opcode == Opcodes.INVOKESTATIC ? desc : "(L" + owner + ";" + desc.substring(1);
        }

        @Override
        public String toString() {
            if (mode == INJECT) {
                return (atEntry ? "entry of " : "returns of ") + name + (desc == null ? " (all overloads)" : desc)
                       + " -> " + helper;
            }
            String what = owner.substring(owner.lastIndexOf('/') + 1) + "." + name + desc;
            if (mode == WRAP_FONT_ARG) {
                return what + " font argument -> " + helper;
            }
            if (mode == CONVERT_RETURN) {
                return what + " return value -> " + helper;
            }
            if (mode == WRAP_WH_ARGS) {
                return what + " width/height arguments -> splashW, splashH";
            }
            return what + " -> " + helper;
        }
    }

    /** Target class (internal name) -> rules applied inside it. */
    static final Map<String, List<Rule>> RULES = new HashMap<String, List<Rule>>();

    private static void rule(String inClass, int opcode, String owner, String name, String desc, String helper) {
        add(inClass, new Rule(REDIRECT, opcode, owner, name, desc, helper));
    }

    private static void add(String inClass, Rule r) {
        List<Rule> list = RULES.get(inClass);
        if (list == null) {
            list = new ArrayList<Rule>();
            RULES.put(inClass, list);
        }
        list.add(r);
    }

    static {
        final int V = Opcodes.INVOKEVIRTUAL, I = Opcodes.INVOKEINTERFACE, S = Opcodes.INVOKESTATIC;

        // Window size and mouse position handed to the interface every frame.
        String c = "com/wurmonline/client/WurmClientBase";
        rule(c, V, HUD, "init", "(II)V", "hudInit");
        rule(c, V, HUD, "setSize", "(II)V", "hudSetSize");
        rule(c, V, HUD, "beginRender", "(FZII)V", "hudBeginRender");

        // Mouse events: offered to each listener; only the HUD's are converted.
        c = "com/wurmonline/client/WurmEventHandler";
        rule(c, I, LISTENER, "mousePressed", "(IIII)Z", "listenerPressed");
        rule(c, I, LISTENER, "mouseReleased", "(III)V", "listenerReleased");
        rule(c, I, LISTENER, "mouseDragged", "(II)V", "listenerDragged");
        rule(c, I, LISTENER, "mouseMoved", "(II)V", "listenerMoved");
        rule(c, I, LISTENER, "mouseWheeled", "(III)V", "listenerWheeled");
        // Right-click menu on a world object, opened with raw coordinates.
        rule(c, V, HUD, "popupRequested", "(IILjava/lang/String;J)V", "hudPopup");

        // World code asking the interface what is under the cursor.
        c = "com/wurmonline/client/game/World";
        rule(c, V, HUD, "setActiveToolAt", "(II)V", "hudSetActiveToolAt");
        rule(c, V, HUD, "getCommandTargetsFrom", "(II)[J", "hudCommandTargets");
        c = "com/wurmonline/client/renderer/WorldRender";
        rule(c, V, HUD, "pick", "(IILcom/wurmonline/client/renderer/PickData;)Z", "hudPick");

        // The interface reading the real mouse position itself.
        c = HUD;
        rule(c, V, CLIENT, "getXMouse", "()I", "guiMouseX");
        rule(c, V, CLIENT, "getYMouse", "()I", "guiMouseY");

        // Interface drawing that must go out to OpenGL in real pixels.
        c = "com/wurmonline/client/renderer/gui/Renderer";
        rule(c, V, PIPELINE, "setViewport", "(IIII)V", "guiViewport");
        rule(c, S, "org/lwjgl/opengl/Display", "getWidth", "()I", "guiDisplayWidth");
        c = "com/wurmonline/client/renderer/backend/ScissorControl$ClipRect";
        rule(c, S, "org/lwjgl/opengl/GL11", "glScissor", "(IIII)V", "guiScissor");

        // Crisp text. Every standard font is a SimpleTextFont drawing glyphs
        // from a FontTexture atlas. Rasterise the atlas at scale x the size,
        // draw each string through a 1/scale model transform snapped to the
        // pixel grid, and report every metric in interface units - so layout
        // is unchanged but each glyph texel lands on exactly one screen pixel.
        c = "com/wurmonline/client/renderer/gui/text/SimpleTextFont";
        String ft = "com/wurmonline/client/renderer/gui/text/FontTexture";
        add(c, new Rule(WRAP_FONT_ARG, Opcodes.INVOKESPECIAL, ft, "<init>", "(Ljava/awt/Font;Z)V", "textAtlasFont"));
        rule(c, V, "com/wurmonline/client/renderer/Matrix", "setTranslation",
             "(FFF)Lcom/wurmonline/client/renderer/Matrix;", "textTransform");
        rule(c, V, ft, "getMaxHeight", "()I", "ftMaxHeight");
        rule(c, V, ft, "getAscent", "()I", "ftAscent");
        rule(c, V, ft, "getDescent", "()I", "ftDescent");
        rule(c, V, ft, "getLeading", "()I", "ftLeading");
        rule(c, V, ft, "getWidth", "(Ljava/lang/String;)I", "ftWidth");
        rule(c, V, ft, "getWidth", "([CII)I", "ftWidthChars");
        add(c, new Rule(CONVERT_RETURN, Opcodes.IRETURN, c, "drawString",
                        "(Lcom/wurmonline/client/renderer/backend/Queue;Ljava/lang/String;IIFFFF)I", "textAdvance"));

        // Loading screen. It draws its own text in its own projection before
        // the interface exists, so without this the text patch above would
        // shrink its 1.3x atlas back down and soften it. Instead it gets the
        // same treatment as the interface: scaled size and mouse in, real
        // viewport out.
        String sr = "com/wurmonline/client/startup/splash/StartupRenderer";
        c = "com/wurmonline/client/WurmClientBase";
        add(c, new Rule(WRAP_WH_ARGS, Opcodes.INVOKESPECIAL, sr, "<init>", "(II)V", "splashW/splashH"));
        rule(c, V, sr, "render", "(II)V", "splashRender");
        rule("com/wurmonline/client/WurmClientBase$1", V, sr, "checkMouseClick", "(II)V", "splashClick");
        rule(sr, V, PIPELINE, "setViewport", "(IIII)V", "guiViewport");

        // Fading the chat and event windows when idle, the target window
        // when nothing is targeted, and the fight window out of combat.
        //
        // Register: the chat and event windows are identified by the title
        // they are built with ("Local chat window", "Event chat window").
        // Friends and Tickets windows are the same class and are left alone.
        // The target and fight windows need no rule here: fadeEnter
        // recognises them by class the first time they render.
        String gui = "com/wurmonline/client/renderer/gui/";
        add(gui + "WurmTabbedWindow", Rule.inject("<init>",
                "(Ljava/lang/String;L" + gui + "FlexComponent;)V", false, new int[] { 0, 1 },
                "fadeRegister", "(L" + gui + "WurmComponent;Ljava/lang/String;)V", -1));
        // Scope: every component renders its whole subtree inside render(),
        // so entering/leaving a registered window's render() brackets
        // everything it draws - frame, tabs, background, text.
        add(gui + "WurmComponent", Rule.inject("render",
                "(Lcom/wurmonline/client/renderer/backend/Queue;F)V", true, new int[] { 0 },
                "fadeEnter", "(L" + gui + "WurmComponent;)V", -1));
        add(gui + "WurmComponent", Rule.inject("render",
                "(Lcom/wurmonline/client/renderer/backend/Queue;F)V", false, new int[] { 0 },
                "fadeExit", "(L" + gui + "WurmComponent;)V", -1));
        // Apply: at the top of Queue.queue, swap in a faded copy of the
        // primitive. The original is never modified, so primitives a
        // component reuses every frame cannot compound.
        String bq = "com/wurmonline/client/renderer/backend/";
        add(bq + "Queue", Rule.inject("queue",
                "(L" + bq + "Primitive;Lcom/wurmonline/client/renderer/Matrix;)V", true, new int[] { 0, 1 },
                "fadePrimitive", "(L" + bq + "Queue;L" + bq + "Primitive;)L" + bq + "Primitive;", 1));
        // Wake: a new line in any tab of the window, or typing in chat.
        add(gui + "DefaultTab", Rule.inject("addLine", null, true, new int[] { 0 },
                "fadeTabActivity", "(Ljava/lang/Object;)V", -1));
        add(HUD, Rule.inject("handleInputChanged",
                "(L" + gui + "WurmInputField;Ljava/lang/String;)V", true, new int[0],
                "fadeTyping", "()V", -1));
        // Hidden: the HUD's one hit test for the window under the mouse, so
        // clicks pass through a target window that is hidden.
        rule(HUD, V, gui + "WurmComponent", "contains", "(II)Z", "hudContains");

        // Console command "uifade [on|off|status]". handleInput ignores any
        // line starting with "//", so the helper handles its own commands and
        // hands back the rest of the line - or "//" - with no branch added.
        add("com/wurmonline/client/console/WurmConsole", Rule.inject("handleInput",
                "(Ljava/lang/String;Z)V", true, new int[] { 1 },
                "consoleFilter", "(Ljava/lang/String;)Ljava/lang/String;", 1));
    }

    // ---------------------------------------------------------------------

    /** Rules that fired / did not fire so far, for the summary in client.log. */
    static int rulesPatched, rulesMissed;

    /**
     * Patches one targeted class, internal name (a/b/C), and logs each rule.
     * Returns the patched bytes, or null to leave the class unchanged.
     */
    static byte[] patchLoaded(String className, byte[] bytes) {
        List<Rule> rules = RULES.get(className);
        if (rules == null) {
            return null;
        }
        byte[] out;
        try {
            out = patch(bytes, rules);
            for (Rule r : rules) {
                if (r.hits > 0) {
                    rulesPatched++;
                    log("patched " + r.hits + "x  " + className + ": " + r);
                } else {
                    rulesMissed++;
                    warn("NOT FOUND  " + className + ": " + r);
                }
            }
        } catch (Throwable t) {
            rulesMissed += rules.size();
            warn("FAILED to patch " + className + ", left unchanged: " + t);
            out = null;
        }
        // Once every rule has been seen, say how it went in client.log too.
        if (rulesPatched + rulesMissed == ruleCount()) {
            if (rulesMissed == 0) {
                info("uiscale: all " + rulesPatched + " patches applied");
            } else {
                warn("uiscale: " + rulesMissed + " of " + ruleCount() + " patches missing, see mods\\uiscale\\uiscale.log");
            }
        }
        return out;
    }

    /** Number of rules in total, for the summary. */
    static int ruleCount() {
        int n = 0;
        for (List<Rule> l : RULES.values()) {
            n += l.size();
        }
        return n;
    }

    /** Rewrites matching call sites. Resets and fills in each rule's hit count. */
    static byte[] patch(byte[] bytes, final List<Rule> rules) {
        for (Rule r : rules) {
            r.hits = 0;
        }
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(cr, 0);
        cr.accept(new ClassVisitor(Opcodes.ASM5, cw) {
            @Override
            public MethodVisitor visitMethod(int access, final String methodName, final String methodDesc,
                                             String sig, String[] exc) {
                MethodVisitor mv = super.visitMethod(access, methodName, methodDesc, sig, exc);
                return new MethodVisitor(Opcodes.ASM5, mv) {
                    @Override
                    public void visitMethodInsn(int op, String owner, String n, String d, boolean itf) {
                        for (Rule r : rules) {
                            if (!r.matchesCall(op, owner, n, d)) {
                                continue;
                            }
                            r.hits++;
                            if (r.mode == WRAP_FONT_ARG) {
                                // stack: ..., Font, boolean  ->  ..., helper(Font), boolean
                                super.visitInsn(Opcodes.SWAP);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, r.helper, r.helperDesc(), false);
                                super.visitInsn(Opcodes.SWAP);
                                super.visitMethodInsn(op, owner, n, d, itf);
                            } else if (r.mode == WRAP_WH_ARGS) {
                                // stack: ..., w, h  ->  ..., splashW(w), splashH(h)
                                super.visitInsn(Opcodes.SWAP);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "splashW", "(I)I", false);
                                super.visitInsn(Opcodes.SWAP);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "splashH", "(I)I", false);
                                super.visitMethodInsn(op, owner, n, d, itf);
                            } else {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, r.helper, r.helperDesc(), false);
                            }
                            return;
                        }
                        super.visitMethodInsn(op, owner, n, d, itf);
                    }

                    @Override
                    public void visitCode() {
                        super.visitCode();
                        for (Rule r : rules) {
                            if (r.atEntry && r.matchesInject(methodName, methodDesc)) {
                                r.hits++;
                                emitInject(r);
                            }
                        }
                    }

                    @Override
                    public void visitInsn(int op) {
                        for (Rule r : rules) {
                            if (r.matchesReturn(methodName, methodDesc, op)) {
                                r.hits++;
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, r.helper, r.helperDesc(), false);
                                break;
                            }
                            if (op == Opcodes.RETURN && !r.atEntry && r.matchesInject(methodName, methodDesc)) {
                                r.hits++;
                                emitInject(r);
                            }
                        }
                        super.visitInsn(op);
                    }

                    private void emitInject(Rule r) {
                        for (int slot : r.loads) {
                            super.visitVarInsn(Opcodes.ALOAD, slot);
                        }
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, r.helper, r.injectDesc, false);
                        if (r.storeTo >= 0) {
                            super.visitVarInsn(Opcodes.ASTORE, r.storeTo);
                        }
                    }
                };
            }
        }, 0);
        return cw.toByteArray();
    }

    // ---- logging --------------------------------------------------------------
    //
    // Every detail goes to uiscale.log next to the jar. Problems and a one-line
    // summary also go to the mod loader's client.log, which is what players
    // usually attach to a bug report.

    private static final Logger LOGGER = Logger.getLogger("uiscale");

    private static PrintWriter logOut;

    /** Starts a fresh uiscale.log in dir. */
    static synchronized void openLog(File dir) {
        try {
            File f = new File(dir, "uiscale.log");
            // Empty it, then append: an appending writer always writes at the
            // end of the file, so no line can land on top of another.
            new FileWriter(f, false).close();
            logOut = new PrintWriter(new FileWriter(f, true), true);
        } catch (Throwable t) {
            logOut = null;
            LOGGER.warning("could not open uiscale.log in " + dir + ": " + t);
        }
    }

    static synchronized void log(String msg) {
        String line = new SimpleDateFormat("HH:mm:ss").format(new Date()) + "  " + msg;
        if (logOut != null) {
            logOut.println(line);
        } else {
            System.err.println("[uiscale] " + line);
        }
    }

    /** Both logs. */
    static void info(String msg) {
        log(msg);
        LOGGER.info(msg);
    }

    /** Both logs, as a warning. */
    static void warn(String msg) {
        log(msg);
        LOGGER.warning(msg);
    }
}
