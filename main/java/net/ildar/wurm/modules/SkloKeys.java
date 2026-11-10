package net.ildar.wurm.modules;

import com.wurmonline.client.console.KeyBinding;
import com.wurmonline.client.console.WurmConsole;
import com.wurmonline.client.game.World;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.MethodCall;
import net.ildar.wurm.WurmHelper;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SkloGraphics draws the key of its "default action" and "repeat action" prompts as the literal letters
 * E and R, whatever those actions are bound to. This makes the prompts show the keys the player has
 * actually bound to default_action and repeat_action. It also moves them from above the cursor to just
 * below and left of it, clear of the hover tooltip that sits below and to the right.
 */
public final class SkloKeys {
    private static final Logger logger = Logger.getLogger("WurmHelper");
    private static final long REFRESH_MS = 500;

    private static Field keyBindsField;
    private static boolean unreadable;
    private static long checkedAt;
    private static String defaultKey = "E";
    private static String repeatKey = "R";

    private SkloKeys() {
    }

    /** Patches the prompt drawing in SkloGraphics' WorldOverlay. Must run before that class is loaded. */
    static void install() throws Exception {
        CtClass overlay = HookManager.getInstance().getClassPool().get("com.wurmonline.client.renderer.gui.WorldOverlay");
        if (overlay.isFrozen()) {
            logger.warning("[WurmHelper] SkloGraphics prompts keep their letters: WorldOverlay is already loaded");
            return;
        }
        CtMethod prompts = overlay.getDeclaredMethod("promptCmds");
        final int[] patched = {0};
        prompts.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall call) throws javassist.CannotCompileException {
                if (call.getMethodName().equals("promptCmd")) {
                    call.replace("$_ = $proceed($1, $2, net.ildar.wurm.modules.SkloKeys.label($3), $4, $5, $6);");
                    patched[0]++;
                }
            }
        });
        logger.info("[WurmHelper] SkloGraphics key prompts follow your keybinds (" + patched[0] + " call sites)");

        // below the cursor: drawPrompts puts the prompts under it when it is in the bottom 160 px of the
        // screen, above it otherwise; reading the screen height as 0 makes that always the case
        final int[] below = {0};
        overlay.getDeclaredMethod("drawPrompts").instrument(new ExprEditor() {
            @Override
            public void edit(FieldAccess field) throws javassist.CannotCompileException {
                if (field.isStatic() && field.isReader() && field.getFieldName().equals("promptScreenH")) {
                    field.replace("$_ = 0;");
                    below[0]++;
                }
            }
        });
        // left of the cursor: drawPrompt centres a prompt of width w on the cursor, as
        // max(4, min(screenW - w - 4, x - w / 2)); shift the centre so its right edge is 16 px left of the cursor
        final int[] left = {0};
        overlay.getDeclaredMethod("drawPrompt").instrument(new ExprEditor() {
            @Override
            public void edit(javassist.expr.MethodCall call) throws javassist.CannotCompileException {
                if (call.getClassName().equals("java.lang.Math") && call.getMethodName().equals("min")) {
                    call.replace("{ int w = promptScreenW - 4 - $1; $_ = $proceed($1, $2 - w / 2 - 16); }");
                    left[0]++;
                }
            }
        });
        logger.info("[WurmHelper] SkloGraphics key prompts moved below-left of the cursor (" + below[0] + " + " + left[0] + " sites)");
    }

    /** The key shown in a prompt: the letter SkloGraphics asked for, or the key bound to that prompt's action. */
    public static String label(String letter) {
        refresh();
        if ("E".equals(letter))
            return defaultKey;
        if ("R".equals(letter))
            return repeatKey;
        return letter;
    }

    @SuppressWarnings("unchecked")
    private static void refresh() {
        long now = System.currentTimeMillis();
        if (unreadable || now - checkedAt < REFRESH_MS)
            return;
        checkedAt = now;
        try {
            World world = WurmHelper.hud == null ? null : WurmHelper.hud.getWorld();
            if (world == null)
                return;
            WurmConsole console = world.getClient().getConsole();
            if (keyBindsField == null) {
                Field field = WurmConsole.class.getDeclaredField("keyBinds");
                field.setAccessible(true);
                keyBindsField = field;
            }
            String forDefault = "-", forRepeat = "-";
            for (KeyBinding binding : ((Map<Integer, KeyBinding>) keyBindsField.get(console)).values()) {
                String command = binding.getStrCommand();
                if (command == null)
                    continue;
                command = command.replace("\"", "").trim();
                if (command.equalsIgnoreCase("default_action"))
                    forDefault = shorten(binding.getStrName());
                else if (command.equalsIgnoreCase("repeat_action"))
                    forRepeat = shorten(binding.getStrName());
            }
            defaultKey = forDefault;
            repeatKey = forRepeat;
        } catch (Throwable t) {
            unreadable = true;
            logger.log(Level.WARNING, "[WurmHelper] Cannot read the keybinds, SkloGraphics prompts keep E and R", t);
        }
    }

    private static String shorten(String key) {
        return key == null || key.isEmpty() ? "-" : key.length() > 5 ? key.substring(0, 5) : key.toUpperCase();
    }
}
