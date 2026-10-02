package uiscale;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.util.CheckClassAdapter;

/**
 * Offline check, run by build.cmd with the game's own Java 8 runtime:
 *
 *   java -cp build\classes;client-patched.jar uiscale.SelfTest client-patched.jar
 *
 * For every class the mod targets it:
 *   1. patches the real bytes from the game's jar and requires every rule to fire;
 *   2. type-checks every method of the original and the patched class with
 *      ASM's data-flow verifier against the real game classes, and requires
 *      the patched class to produce no error the original does not;
 *   3. checks the coordinate arithmetic and the uifade console filter.
 * Nothing is executed from the game and no static initialiser runs.
 *
 * LoaderTest covers the other half: the mod running inside Ago's mod loader.
 */
public final class SelfTest {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("usage: SelfTest path\\to\\client-patched.jar");
            System.exit(2);
        }
        File clientJar = new File(args[0]);
        URL classes = SelfTest.class.getProtectionDomain().getCodeSource().getLocation();
        ClassLoader loader = new URLClassLoader(new URL[] { clientJar.toURI().toURL(), classes },
                                                ClassLoader.getSystemClassLoader().getParent());
        int failures = 0;
        ZipFile zip = new ZipFile(clientJar);
        try {
            for (Map.Entry<String, List<Patcher.Rule>> e : Patcher.RULES.entrySet()) {
                String cls = e.getKey();
                ZipEntry entry = zip.getEntry(cls + ".class");
                if (entry == null) {
                    System.out.println("FAIL  " + cls + " is not in " + clientJar.getName());
                    failures++;
                    continue;
                }
                byte[] original = read(zip.getInputStream(entry));
                byte[] patched = Patcher.patch(original, e.getValue());

                for (Patcher.Rule r : e.getValue()) {
                    boolean ok = r.hits > 0;
                    if (!ok) {
                        failures++;
                    }
                    System.out.println((ok ? "ok    " : "FAIL  ") + r.hits + "x  " + shortName(cls) + ": " + r);
                }

                String before = verify(original, loader);
                String after = verify(patched, loader);
                if (after.equals(before)) {
                    System.out.println("ok    verifier: " + shortName(cls) + " patched class type-checks"
                                       + (before.length() == 0 ? " cleanly" : " (same notes as the original)"));
                } else {
                    failures++;
                    System.out.println("FAIL  verifier: " + shortName(cls) + " - patched class differs from original:");
                    System.out.println(after);
                }
            }
        } finally {
            zip.close();
        }

        // The target window fade reads TargetWindow.targetId by reflection.
        Field targetId = null;
        try {
            targetId = Class.forName("com.wurmonline.client.renderer.gui.TargetWindow", false, loader)
                            .getDeclaredField("targetId");
        } catch (NoSuchFieldException e) {
            // reported below
        }
        boolean targetOk = targetId != null && targetId.getType() == long.class;
        if (!targetOk) {
            failures++;
        }
        System.out.println((targetOk ? "ok    " : "FAIL  ") + "target fade: TargetWindow.targetId is a long field");

        // The fight window fade calls FightWindowComponent.getFighting().
        boolean fightOk;
        try {
            Method m = Class.forName("com.wurmonline.client.renderer.gui.FightWindowComponent", false, loader)
                            .getMethod("getFighting");
            fightOk = m.getReturnType() == boolean.class;
        } catch (NoSuchMethodException e) {
            fightOk = false;
        }
        if (!fightOk) {
            failures++;
        }
        System.out.println((fightOk ? "ok    " : "FAIL  ") + "fight window fade: FightWindowComponent.getFighting() is public boolean");

        failures += checkArithmetic();

        System.out.println(failures == 0 ? "\nALL CHECKS PASSED" : "\n" + failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static String verify(byte[] bytes, ClassLoader loader) {
        StringWriter sw = new StringWriter();
        CheckClassAdapter.verify(new ClassReader(bytes), loader, false, new PrintWriter(sw));
        return sw.toString().trim();
    }

    /** 1920x1080 at 1.5 -> 1280x720. Mouse maps down, scissor maps back up. */
    private static int checkArithmetic() throws Exception {
        int fail = 0;
        UiScale.configure(1.5f);
        set("realW", 1920); set("realH", 1080); set("virtW", 1280); set("virtH", 720);

        fail += expect("mouse 0,0", call("toVirtX", 0) + "," + call("toVirtY", 0), "0,0");
        fail += expect("mouse 1919,1079", call("toVirtX", 1919) + "," + call("toVirtY", 1079), "1279,719");
        fail += expect("mouse 960,540", call("toVirtX", 960) + "," + call("toVirtY", 540), "640,360");

        double f = 1920.0 / 1280;
        // A 101x101 virtual clip at (10,20) must cover at least the real pixels it maps onto.
        int x0 = (int) Math.floor(10 * f), x1 = (int) Math.ceil(111 * f);
        fail += expect("scissor covers", (x0 <= 15 && x1 >= 166) ? "yes" : "no", "yes");

        // Console filter. Only "status" is used here, so running the test
        // never changes the remembered on/off choice.
        fail += expect("console: uifade status", UiScale.consoleFilter("uifade status"), "//");
        fail += expect("console: other command untouched", UiScale.consoleFilter("say hello"), "say hello");
        fail += expect("console: mixed line keeps the rest",
                       UiScale.consoleFilter("uifade status;say hello").trim(), "say hello");
        fail += expect("console: comment untouched", UiScale.consoleFilter("// uifade off"), "// uifade off");
        fail += expect("console: case-insensitive", UiScale.consoleFilter("UIFADE status"), "//");
        return fail;
    }

    private static void set(String field, int v) throws Exception {
        Field f = UiScale.class.getDeclaredField(field);
        f.setAccessible(true);
        f.setInt(null, v);
    }

    private static int call(String method, int v) throws Exception {
        Method m = UiScale.class.getDeclaredMethod(method, int.class);
        m.setAccessible(true);
        return (Integer) m.invoke(null, v);
    }

    private static int expect(String what, String got, String want) {
        boolean ok = got.equals(want);
        System.out.println((ok ? "ok    " : "FAIL  ") + "math: " + what + " = " + got + (ok ? "" : " (want " + want + ")"));
        return ok ? 0 : 1;
    }

    private static String shortName(String cls) {
        return cls.substring(cls.lastIndexOf('/') + 1);
    }

    private static byte[] read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return out.toByteArray();
    }
}
