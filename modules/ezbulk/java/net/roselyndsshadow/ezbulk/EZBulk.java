package net.roselyndsshadow.ezbulk;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.gui.HeadsUpDisplay;
import com.wurmonline.client.renderer.gui.InventoryListComponent;
import com.wurmonline.client.renderer.gui.InventoryWindow;
import com.wurmonline.client.renderer.gui.WurmComponent;
import com.wurmonline.shared.constants.PlayerAction;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.Properties;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class EZBulk implements WurmClientMod, Initable, PreInitable {

    private static final Logger logger = Logger.getLogger(EZBulk.class.getName());
    public static final String VERSION = "1.3";

    public static HeadsUpDisplay hud;

    private static boolean debug = false;
    /** When true, logs / wood scrap / sprouts match by material like ore. Off = all woods together. */
    private static boolean selectiveWood = false;
    private static boolean selectiveWoodScrap = false;
    private static boolean selectiveSprout = false;
    /** When true (default), all meat types move together. Off = only that animal's meat. */
    private static boolean allMeat = true;
    private static final java.util.Set<Integer> containerTypes = new java.util.HashSet<Integer>();

    private static boolean isMoving = false;
    private static Object lastDragNode = null;
    private static final java.util.IdentityHashMap<Object, Boolean> answeredBml =
            new java.util.IdentityHashMap<Object, Boolean>();
    private static long[] lastDropSourceIds = new long[0];
    private static volatile boolean pendingAutoAll = false;
    private static long pendingDestId = -1L;
    private static int pendingAmount = 0;
    private static InventoryMetaItem pendingSource = null;
    private static final List<Long> moveQueue =
            java.util.Collections.synchronizedList(new ArrayList<Long>());
    private static final Map<Long, Integer> retryCount =
            new java.util.concurrent.ConcurrentHashMap<Long, Integer>();
    private static final AtomicInteger moveQueueIndex = new AtomicInteger(0);
    private static volatile long lastSentId = 0L;
    private static final int MAX_RETRIES = 5;
    private static final long STEP_GAP_MS = 50L;
    private static volatile boolean waitingForDone = false;
    private static boolean moveStarted = false;
    private static final AtomicInteger queueGen = new AtomicInteger(0);
    private static final AtomicInteger stepToken = new AtomicInteger(0);
    private static final Timer watchdog = new Timer("EZBulk-watch", true);
    private static long rescanParentId = -1L;
    private static boolean shiftRescan = false;
    private static volatile boolean destRefused = false;
    private static boolean dumpEntire = false;
    private static int rescanPass = 0;
    private static final int MAX_RESCAN_PASSES = 20;
    private static long tSend = 0L;
    private static long tBml = 0L;
    private static int lastHeaveAmount = 0;
    private static int lastHeaveRate = 0;
    private static final AtomicInteger inFlight = new AtomicInteger(0);
    private static final int MAX_IN_FLIGHT = 5;
    private static boolean stepClosed = false;
    private static long lastPlayerTarget = -1L;
    private static InventoryMetaItem lastShiftSource = null;
    private static long lastShiftDestId = -1L;
    private static boolean sawAmountMenu = false;

    // ==================== LOGGING ====================
    private static void clearAndLog(String message) {
        if (!debug) return;
        writeLog(message, false);
    }

    private static void log(String message) {
        if (!debug) return;
        writeLog(message, true);
    }

    private static void debugLog(String message) {
        log(message);
    }

    private static void writeLog(String message, boolean append) {
        try {
            FileWriter writer = new FileWriter("EZBulk_Log.txt", append);
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            writer.write("[" + time + "] " + message + System.lineSeparator());
            writer.close();
        } catch (Exception ignored) {}
    }

    // ==================== MODIFIERS ====================
    private static boolean keyDown(String field) {
        try {
            Class<?> kb = Class.forName("org.lwjgl.input.Keyboard");
            Method isKeyDown = kb.getMethod("isKeyDown", int.class);
            int code = kb.getField(field).getInt(null);
            return (Boolean) isKeyDown.invoke(null, code);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isShiftDown() {
        return keyDown("KEY_LSHIFT") || keyDown("KEY_RSHIFT");
    }

    private static boolean isCtrlDown() {
        return keyDown("KEY_LCONTROL") || keyDown("KEY_RCONTROL")
                || keyDown("KEY_LCTRL") || keyDown("KEY_RCTRL");
    }

    private static boolean isVanillaStopCommand(String cmd) {
        if (cmd == null) return false;
        String c = cmd.trim();
        if (c.startsWith("/")) c = c.substring(1);
        int space = c.indexOf(' ');
        if (space > 0) c = c.substring(0, space);
        return c.equalsIgnoreCase("STOP_OR_MAIN_MENU")
                || c.equalsIgnoreCase("MAIN_MENU")
                || c.equalsIgnoreCase("STOP");
    }

    private static Object lastAmountWindow;

    private static boolean isMainMenuShowing() {
        if (hud == null) return false;
        try {
            Object menu = getFieldValue(hud, "mainMenu");
            if (menu == null) return false;
            Object vis = getFieldValue(menu, "visible");
            if (vis instanceof Boolean && ((Boolean) vis).booleanValue()) return true;
            try {
                Method m = menu.getClass().getMethod("isVisible");
                Object r = m.invoke(menu);
                if (r instanceof Boolean && ((Boolean) r).booleanValue()) return true;
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
        return false;
    }

    private static void dismissAmountWindow() {
        Object wc = lastAmountWindow;
        lastAmountWindow = null;
        if (wc == null || hud == null) return;
        try {
            Method click = null;
            Class<?> c = wc.getClass();
            while (c != null && click == null) {
                Method[] ms = c.getDeclaredMethods();
                for (int i = 0; i < ms.length; i++) {
                    if (ms[i].getName().equals("processButtonPressed")) {
                        click = ms[i];
                        break;
                    }
                }
                c = c.getSuperclass();
            }
            if (click != null) {
                click.setAccessible(true);
                try { click.invoke(wc, "none"); } catch (Exception ignored) {}
                try { click.invoke(wc, "close"); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        try {
            Method rem = hud.getClass().getMethod("removeComponent",
                    Class.forName("com.wurmonline.client.renderer.gui.WurmComponent"));
            rem.invoke(hud, wc);
        } catch (Exception ignored) {}
        try {
            Method close = wc.getClass().getMethod("close");
            close.invoke(wc);
        } catch (Exception ignored) {}
    }

    private static void abortIfEscape() {
        if (!transferActive() && moveQueue.isEmpty()) return;
        boolean esc = keyDown("KEY_ESCAPE");
        boolean menu = isMainMenuShowing();
        if (!esc && !menu) return;
        abortQueue(menu ? "main menu opened" : "KEY_ESCAPE / STOP_OR_MAIN_MENU");
        dismissAmountWindow();
    }

    // ==================== REFLECTION HELPERS ====================
    private static Object getFieldValue(Object obj, String name) {
        if (obj == null) return null;
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            try {
                Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch (Exception ignored) {}
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static Object invokeNoArg(Object obj, String name) {
        return invokeMethod(obj, name, new Class<?>[0], new Object[0]);
    }

    private static Object invokeMethod(Object obj, String name, Class<?>[] types, Object[] args) {
        if (obj == null) return null;
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            try {
                Method m = cls.getDeclaredMethod(name, types);
                m.setAccessible(true);
                return m.invoke(obj, args);
            } catch (Exception ignored) {}
            cls = cls.getSuperclass();
        }
        try {
            Method m = obj.getClass().getMethod(name, types);
            return m.invoke(obj, args);
        } catch (Exception ignored) {}
        return null;
    }

    private static long invokeDroppedOnTargetId(InventoryListComponent list, int x, int y) {
        Object res = invokeMethod(list, "getDroppedOnTargetId",
                new Class<?>[] { int.class, int.class },
                new Object[] { x, y });
        if (res instanceof Long) return (Long) res;
        if (res instanceof Integer) return ((Integer) res).longValue();
        return -1L;
    }

    // ==================== NAME / TYPE HELPERS ====================
    private static String stripQty(String name) {
        if (name == null) return "";
        name = name.toLowerCase().trim();
        int paren = name.lastIndexOf('(');
        if (paren > 0) name = name.substring(0, paren).trim();
        return name;
    }

    private static String safeName(InventoryMetaItem item, String method) {
        Object v = invokeNoArg(item, method);
        return v instanceof String ? stripQty((String) v) : "";
    }

    private static int materialId(InventoryMetaItem item) {
        if (item == null) return 0;
        Object id = invokeNoArg(item, "getMaterialId");
        if (id == null) id = invokeNoArg(item, "getMaterial");
        if (id instanceof Byte) return (Byte) id;
        if (id instanceof Short) return (Short) id;
        if (id instanceof Integer) return (Integer) id;
        return 0;
    }

    private static String materialName(InventoryMetaItem item) {
        int mat = materialId(item);
        if (mat <= 0) return "";
        try {
            Class<?> util = Class.forName("com.wurmonline.shared.util.MaterialUtilities");
            Object name = util.getMethod("getMaterialString", byte.class).invoke(null, (byte) mat);
            if (name instanceof String && !((String) name).isEmpty()
                    && !((String) name).equalsIgnoreCase("unknown")) {
                return ((String) name).toLowerCase().trim();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static String typeName(InventoryMetaItem item) {
        if (item == null) return "";
        String base = stripQty(item.getBaseName());
        if (base.isEmpty()) base = stripQty(item.getDisplayName());
        int comma = base.indexOf(',');
        if (comma > 0) base = base.substring(0, comma).trim();
        return base;
    }

    /**
     * Commodity key = type + material id. Custom names cannot merge gold with iron.
     */
    private static String coreName(InventoryMetaItem item) {
        if (item == null) return "";
        String type = typeName(item);
        String mat = materialName(item);
        if (!type.isEmpty() && !mat.isEmpty()) return type + ", " + mat;
        String display = stripQty(item.getDisplayName());
        if (display.contains(",")) return display;
        return type.isEmpty() ? display : type;
    }

    private static boolean ignoreMaterial(String type) {
        if (type == null) return false;
        String t = type.toLowerCase();
        if (t.equals("ore") || t.equals("ores")
                || t.equals("lump") || t.equals("lumps")) {
            return false;
        }
        if (t.equals("log") || t.equals("logs") || t.equals("wood")
                || t.startsWith("log ")) {
            return !selectiveWood;
        }
        if (t.equals("wood scrap") || t.equals("woodscraps") || t.equals("scrap")
                || t.startsWith("wood scrap")) {
            return !selectiveWoodScrap;
        }
        if (t.equals("sprout") || t.equals("sprouts") || t.startsWith("sprout ")) {
            return !selectiveSprout;
        }
        if (t.equals("meat") || t.equals("meats") || t.equals("fillet") || t.equals("fillets")) {
            return allMeat;
        }
        return true;
    }

    private static boolean sameCommodity(InventoryMetaItem a, InventoryMetaItem b) {
        if (a == null || b == null) return false;
        String ta = typeName(a);
        String tb = typeName(b);
        if (ta.isEmpty() || tb.isEmpty() || !ta.equalsIgnoreCase(tb)) return false;
        if (ignoreMaterial(ta)) return true;
        int ma = materialId(a);
        int mb = materialId(b);
        if (ma > 0 || mb > 0) return ma == mb;
        return coreName(a).equals(coreName(b));
    }

    private static void applyDefaultContainerTypes() {
        containerTypes.clear();
        int[] defaults = {
                469, 661, 662,
                311, 312, 851, 852,
                670,
                1119, 1120,
                1277, 1278, 1279,
                1311, 1312,
                1315, 1316, 1317
        };
        for (int i = 0; i < defaults.length; i++) {
            containerTypes.add(Integer.valueOf(defaults[i]));
        }
    }

    private static boolean isBulkContainerType(int type) {
        if (containerTypes.isEmpty()) applyDefaultContainerTypes();
        return containerTypes.contains(Integer.valueOf(type));
    }

    private static String itemModel(InventoryMetaItem item) {
        if (item == null) return "";
        String[] names = { "getModelName", "getModel", "getTypeName", "getIconName" };
        for (int i = 0; i < names.length; i++) {
            Object v = invokeNoArg(item, names[i]);
            if (v instanceof String && !((String) v).isEmpty()) return ((String) v).toLowerCase();
        }
        Object field = getFieldValue(item, "modelName");
        if (field instanceof String) return ((String) field).toLowerCase();
        return "";
    }

    private static boolean modelIsBulkContainer(String model) {
        if (model == null || model.isEmpty()) return false;
        return model.contains("storagebin")
                || model.contains("storage.bin")
                || model.contains("foodbin")
                || model.contains("food.bin")
                || model.contains("bulkcontainer")
                || model.contains("bulk.container")
                || model.contains("crate")
                || model.contains("larder")
                || model.contains("bsb");
    }

    private static boolean nameIsBulkContainer(String name) {
        if (name == null) return false;
        String n = name.toLowerCase();
        return n.contains("bulk storage")
                || n.contains("food storage")
                || n.contains("storage bin")
                || n.contains("crate")
                || n.contains("larder")
                || n.contains("bulk container");
    }

    private static boolean isBulkContainer(InventoryMetaItem item) {
        if (item == null) return false;
        try {
            if (isBulkContainerType(item.getType())) return true;
        } catch (Exception ignored) {}
        if (modelIsBulkContainer(itemModel(item))) return true;
        if (nameIsBulkContainer(coreName(item))) return true;
        try {
            if (nameIsBulkContainer(item.getDisplayName())) return true;
        } catch (Exception ignored) {}
        try {
            if (item.getChildren() != null) {
                for (InventoryMetaItem child : item.getChildren()) {
                    if (child != null && isLikelyBulkItem(child)) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean isLikelyBulkItem(InventoryMetaItem item) {
        if (item == null) return false;
        try {
            if (item.getType() == 669) return true;
        } catch (Exception ignored) {}
        try {
            String display = item.getDisplayName();
            if (display != null && display.toLowerCase().matches(".*\\(\\d+x\\).*")) return true;
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean isBulkCommodity(InventoryMetaItem item) {
        // Anything that lives in a BSB/FSB/crate is bulk. Type 669 is only
        // one possible client tag and is missing on this version.
        if (item == null) return false;
        if (isLikelyBulkItem(item)) return true;
        return findBulkAncestor(item) != null;
    }

    private static boolean looksLikeContainer(InventoryMetaItem item) {
        if (item == null) return false;
        if (isBulkContainer(item)) return true;
        String n = coreName(item);
        try {
            if (item.getType() == 20) return true;
        } catch (Exception ignored) {}
        if (n.contains("inventory") || n.contains("backpack")) return true;
        if (n.contains("wagon") || n.contains("cart")
                || n.contains("chest") || n.contains("crate") || n.contains("barrel")
                || n.contains("bin") || n.contains("larder") || n.contains("saddle")
                || n.contains("bulk")) return true;
        try {
            return item.getChildren() != null && !item.getChildren().isEmpty();
        } catch (Exception ignored) {}
        return false;
    }

    private static InventoryMetaItem getParent(InventoryMetaItem item) {
        if (item == null) return null;
        Object via = invokeNoArg(item, "getParent");
        if (via instanceof InventoryMetaItem) return (InventoryMetaItem) via;
        Object viaId = invokeNoArg(item, "getParentId");
        if (viaId instanceof Long) {
            InventoryMetaItem byId = findItemById((Long) viaId);
            if (byId != null) return byId;
        }
        return findParentByWalk(item);
    }

    private static InventoryMetaItem findParentByWalk(InventoryMetaItem child) {
        if (child == null) return null;
        long id = child.getId();
        for (InventoryMetaItem item : allKnownItems()) {
            if (item == null || item.getId() == id) continue;
            try {
                if (item.getChildren() == null) continue;
                for (InventoryMetaItem c : item.getChildren()) {
                    if (c != null && c.getId() == id) return item;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static InventoryMetaItem findBulkAncestor(InventoryMetaItem item) {
        InventoryMetaItem cur = item;
        for (int i = 0; i < 8 && cur != null; i++) {
            InventoryMetaItem parent = getParent(cur);
            if (parent == null) break;
            if (isBulkContainer(parent)) return parent;
            cur = parent;
        }
        return null;
    }

    // ==================== INVENTORY WALK ====================
    private static InventoryMetaItem getRootFromList(InventoryListComponent ilc) {
        if (ilc == null) return null;
        Object listRoot = getFieldValue(ilc, "rootItem");
        if (listRoot instanceof InventoryMetaItem) return (InventoryMetaItem) listRoot;
        Object nested = getFieldValue(listRoot, "item");
        if (nested instanceof InventoryMetaItem) return (InventoryMetaItem) nested;
        return extractItem(listRoot);
    }

    private static InventoryListComponent findListComponent(Object obj) {
        if (obj == null) return null;
        if (obj instanceof InventoryListComponent) return (InventoryListComponent) obj;
        Object viaMethod = invokeNoArg(obj, "getInventoryListComponent");
        if (viaMethod instanceof InventoryListComponent) return (InventoryListComponent) viaMethod;
        Object viaField = getFieldValue(obj, "component");
        if (viaField instanceof InventoryListComponent) return (InventoryListComponent) viaField;
        return null;
    }

    private static void collectItems(InventoryMetaItem item, List<InventoryMetaItem> out) {
        if (item == null) return;
        out.add(item);
        try {
            if (item.getChildren() != null) {
                for (InventoryMetaItem child : item.getChildren()) {
                    collectItems(child, out);
                }
            }
        } catch (Exception ignored) {}
    }

    private static List<InventoryMetaItem> allKnownItems() {
        List<InventoryMetaItem> out = new ArrayList<InventoryMetaItem>();
        if (hud == null || hud.getWorld() == null) return out;

        try {
            InventoryMetaItem root = hud.getWorld().getInventoryManager().getPlayerInventory().getRootItem();
            collectItems(root, out);
        } catch (Exception e) {
            debugLog("player inventory walk error: " + e.getMessage());
        }

        try {
            InventoryWindow invWin = hud.getInventoryWindow();
            if (invWin != null) {
                collectItems(getRootFromList(invWin.getInventoryListComponent()), out);
            }
        } catch (Exception ignored) {}

        try {
            Field compsField = HeadsUpDisplay.class.getDeclaredField("components");
            compsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<WurmComponent> comps = (List<WurmComponent>) compsField.get(hud);
            if (comps != null) {
                for (WurmComponent c : comps) {
                    InventoryListComponent ilc = findListComponent(c);
                    if (ilc != null) collectItems(getRootFromList(ilc), out);
                }
            }
        } catch (Exception e) {
            debugLog("HUD component walk error: " + e.getMessage());
        }

        return out;
    }

    private static InventoryMetaItem findItemById(long id) {
        if (id <= 0) return null;
        for (InventoryMetaItem item : allKnownItems()) {
            try {
                if (item.getId() == id) return item;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static InventoryMetaItem extractItem(Object obj) {
        if (obj == null) return null;
        if (obj instanceof InventoryMetaItem) return (InventoryMetaItem) obj;
        if (obj instanceof Long) return findItemById((Long) obj);

        Object nested = getFieldValue(obj, "item");
        if (nested instanceof InventoryMetaItem) return (InventoryMetaItem) nested;
        if (nested != null && nested != obj) {
            InventoryMetaItem deeper = extractItem(nested);
            if (deeper != null) return deeper;
        }

        Object viaGet = invokeNoArg(obj, "getItem");
        if (viaGet instanceof InventoryMetaItem) return (InventoryMetaItem) viaGet;

        Object id = invokeNoArg(obj, "getId");
        if (id instanceof Long) return findItemById((Long) id);
        if (id instanceof Integer) return findItemById(((Integer) id).longValue());

        return null;
    }

    private static long[] getItemIds(List<InventoryMetaItem> items) {
        long[] ids = new long[items.size()];
        for (int i = 0; i < items.size(); i++) {
            ids[i] = items.get(i).getId();
        }
        return ids;
    }

    /**
     * Collapse inner weight units into their QL pile.
     * Do not walk into a type folder (handles / tenons) — that made matches=0.
     */
    private static InventoryMetaItem pileRow(InventoryMetaItem item) {
        InventoryMetaItem cur = item;
        for (int i = 0; i < 8 && cur != null; i++) {
            InventoryMetaItem parent = getParent(cur);
            if (parent == null || isBulkContainer(parent)) return cur;
            String ct = typeName(cur);
            String pt = typeName(parent);
            if (ct.isEmpty() || pt.isEmpty() || !ct.equalsIgnoreCase(pt)) return cur;
            int cm = materialId(cur);
            int pm = materialId(parent);
            if (cm > 0 && pm > 0 && cm != pm) return cur;
            if (pm == 0 && cm > 0) return cur;
            cur = parent;
        }
        return item;
    }

    /** @deprecated use pileRow — kept so older call sites compile if any remain */
    private static InventoryMetaItem commodityRow(InventoryMetaItem item) {
        return pileRow(item);
    }

    private static List<InventoryMetaItem> findAllQlGroups(InventoryMetaItem sample) {
        List<InventoryMetaItem> matches = new ArrayList<InventoryMetaItem>();
        if (sample == null) return matches;

        InventoryMetaItem row = pileRow(sample);
        InventoryMetaItem parent = findBulkAncestor(sample);
        if (parent == null) parent = getParent(row);

        List<InventoryMetaItem> pool = new ArrayList<InventoryMetaItem>();
        collectItems(parent, pool);
        if (lastDragNode != null) collectTreeItems(lastDragNode, pool);
        if (lastDropSourceIds != null) {
            for (int i = 0; i < lastDropSourceIds.length; i++) {
                InventoryMetaItem it = findItemById(lastDropSourceIds[i]);
                if (it != null) pool.add(it);
            }
        }
        for (InventoryMetaItem item : allKnownItems()) {
            if (item == null || parent == null) continue;
            InventoryMetaItem p = getParent(item);
            if (p != null && p.getId() == parent.getId()) collectItems(item, pool);
        }

        java.util.LinkedHashMap<Long, InventoryMetaItem> unique =
                new java.util.LinkedHashMap<Long, InventoryMetaItem>();
        for (InventoryMetaItem item : pool) {
            if (item == null) continue;
            if (parent != null && item.getId() == parent.getId()) continue;
            if (isBulkContainer(item)) continue;
            if (!sameCommodity(sample, item) && !sameCommodity(row, item)) continue;
            InventoryMetaItem top = pileRow(item);
            if (top == null) top = item;
            if (parent != null && top.getId() == parent.getId()) continue;
            if (isBulkContainer(top)) continue;
            unique.put(Long.valueOf(top.getId()), top);
        }

        matches.addAll(unique.values());
        StringBuffer poolNames = new StringBuffer();
        int shown = 0;
        for (InventoryMetaItem item : pool) {
            if (item == null) continue;
            if (parent != null && item.getId() == parent.getId()) continue;
            if (shown > 0) poolNames.append(" | ");
            poolNames.append(coreName(item));
            if (++shown >= 12) { poolNames.append(" | …"); break; }
        }
        log("QL scan parent=" + (parent != null ? coreName(parent) + "#" + parent.getId() : "null")
                + " drag=" + (lastDragNode != null ? lastDragNode.getClass().getSimpleName() : "null")
                + " pool=" + pool.size() + " matches=" + matches.size()
                + " sample=" + coreName(row) + " mat=" + materialId(row)
                + " poolItems=[" + poolNames + "]");
        if (matches.isEmpty()) matches.add(row);
        return matches;
    }

    /** Direct children of the dragged item's parent — the open list, not nested bags. */
    private static List<InventoryMetaItem> findAllInSameParent(InventoryMetaItem sample) {
        List<InventoryMetaItem> matches = new ArrayList<InventoryMetaItem>();
        if (sample == null) return matches;
        InventoryMetaItem row = commodityRow(sample);
        InventoryMetaItem parent = getParent(row);
        if (parent == null) parent = findBulkAncestor(row);
        List<InventoryMetaItem> pool = new ArrayList<InventoryMetaItem>();
        collectItems(parent, pool);
        if (lastDragNode != null) collectTreeItems(lastDragNode, pool);
        if (lastDropSourceIds != null) {
            for (int i = 0; i < lastDropSourceIds.length; i++) {
                InventoryMetaItem it = findItemById(lastDropSourceIds[i]);
                if (it != null) pool.add(it);
            }
        }
        for (InventoryMetaItem item : allKnownItems()) {
            if (item == null || parent == null) continue;
            InventoryMetaItem p = getParent(item);
            if (p != null && p.getId() == parent.getId()) pool.add(item);
        }
        java.util.LinkedHashMap<Long, InventoryMetaItem> unique =
                new java.util.LinkedHashMap<Long, InventoryMetaItem>();
        for (InventoryMetaItem item : pool) {
            if (item == null) continue;
            if (parent != null && item.getId() == parent.getId()) continue;
            InventoryMetaItem itemParent = getParent(item);
            if (parent != null && itemParent != null && itemParent.getId() != parent.getId()) continue;
            if (itemParent != null && itemParent.getId() == item.getId()) continue;
            unique.put(Long.valueOf(item.getId()), item);
        }
        matches.addAll(unique.values());
        log("dump scan parent=" + (parent != null ? coreName(parent) + "#" + parent.getId() : "null")
                + " items=" + matches.size());
        if (matches.isEmpty() && row != null) matches.add(row);
        return matches;
    }

    // ==================== MOVE ====================
    private static void cancelVanillaDrag() {
        if (hud == null) return;
        try {
            invokeNoArg(hud, "resetDrag");
        } catch (Exception ignored) {}
    }

    private static int pileCount(InventoryMetaItem item) {
        if (item == null) return 0;
        try {
            if (item.getChildren() != null && !item.getChildren().isEmpty()) {
                return item.getChildren().size();
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static InventoryListComponent listOwningNode(Object node) {
        Object cur = node;
        for (int i = 0; i < 12 && cur != null; i++) {
            if (cur instanceof InventoryListComponent) return (InventoryListComponent) cur;
            Object list = getFieldValue(cur, "list");
            if (list instanceof InventoryListComponent) return (InventoryListComponent) list;
            Object comp = getFieldValue(cur, "component");
            if (comp instanceof InventoryListComponent) return (InventoryListComponent) comp;
            cur = getFieldValue(cur, "parent");
        }
        return null;
    }

    private static boolean invokeDroppedAmount(InventoryListComponent list, Object treeItem, long destId, int amount) {
        if (list == null || treeItem == null) return false;
        Class<?> cls = list.getClass();
        while (cls != null && cls != Object.class) {
            for (Method m : cls.getDeclaredMethods()) {
                if (!m.getName().equals("itemDroppedAmount")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 3) continue;
                try {
                    m.setAccessible(true);
                    m.invoke(list, treeItem, destId, amount);
                    log("itemDroppedAmount invoked amount=" + amount + " dest=" + destId
                            + " node=" + treeItem.getClass().getSimpleName());
                    return true;
                } catch (Exception e) {
                    log("itemDroppedAmount invoke: " + e.getMessage());
                }
            }
            cls = cls.getSuperclass();
        }
        return false;
    }

    private static void deferCurrentForLater() {
        if (lastSentId == 0L) return;
        Long key = Long.valueOf(lastSentId);
        Integer n = retryCount.get(key);
        int c = n == null ? 0 : n.intValue();
        if (c >= MAX_RETRIES) {
            log("give up id=" + lastSentId + " after " + c + " deferrals");
            return;
        }
        retryCount.put(key, Integer.valueOf(c + 1));
        moveQueue.add(key);
        log("defer id=" + lastSentId + " to end of queue (try " + (c + 1) + "/" + MAX_RETRIES
                + ") queue=" + moveQueueIndex.get() + "/" + moveQueue.size());
    }

    private static void scheduleWatchdog(final int gen, final int step, final long delayMs) {
        watchdog.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    if (gen != queueGen.get() || step != stepToken.get()) return;
                    if (keyDown("KEY_ESCAPE")) {
                        abortQueue("KEY_ESCAPE");
                        return;
                    }
                    if (moveQueueIndex.get() >= moveQueue.size() && lastSentId == 0L) return;
                    if (pendingAutoAll) {
                        log("watchdog: no BML – wait for server, do not queue another");
                        pendingAutoAll = false;
                        waitingForDone = true;
                        scheduleWatchdog(gen, stepToken.get(), 8000L);
                        return;
                    }
                    if (waitingForDone) {
                        log("watchdog: still no Done! – skip this id and continue");
                        waitingForDone = false;
                        moveStarted = false;
                        stepToken.incrementAndGet();
                        sendNextQueuedMoveSoon();
                        return;
                    }
                    if (inFlight.get() >= MAX_IN_FLIGHT) {
                        inFlight.set(MAX_IN_FLIGHT - 1);
                        log("watchdog: action bar full, release one slot inFlight=" + inFlight.get());
                        sendNextQueuedMoveSoon();
                        return;
                    }
                    log("watchdog defer step " + moveQueueIndex.get() + "/" + moveQueue.size()
                            + " pendingBml=" + pendingAutoAll + " waitingDone=" + waitingForDone);
                    pendingAutoAll = false;
                    waitingForDone = false;
                    moveStarted = false;
                    stepToken.incrementAndGet();
                    deferCurrentForLater();
                    sendNextQueuedMoveSoon();
                } catch (Exception e) {
                    log("watchdog: " + e.getMessage());
                }
            }
        }, delayMs);
    }

    private static void sendNextQueuedMove() {
        if (transferActive() && keyDown("KEY_ESCAPE")) {
            abortQueue("KEY_ESCAPE");
            if (hud != null) hud.consoleOutput(">>> EZBulk transfer stopped");
            return;
        }
        if (hud == null || hud.getWorld() == null) return;
        if (moveQueueIndex.get() >= moveQueue.size()) {
            pendingAutoAll = false;
            waitingForDone = false;
            moveStarted = false;
            lastSentId = 0L;
            if (maybeRescanSource()) return;
            moveQueue.clear();
            moveQueueIndex.set(0);
            retryCount.clear();
            inFlight.set(0);
            log("queue finished");
            announceTransferComplete();
            return;
        }
        if (inFlight.get() >= MAX_IN_FLIGHT) {
            log("action bar full (" + inFlight.get() + ") – wait for a slot");
            scheduleWatchdog(queueGen.get(), stepToken.get(), 8000L);
            return;
        }
        lastSentId = moveQueue.get(moveQueueIndex.getAndIncrement()).longValue();
        pendingAutoAll = true;
        waitingForDone = false;
        moveStarted = false;
        tSend = System.currentTimeMillis();
        tBml = 0L;
        lastHeaveAmount = 0;
        lastHeaveRate = 0;
        try {
            isMoving = true;
            hud.getWorld().getServerConnection().sendMoveSomeItems(pendingDestId, new long[] { lastSentId });
            Integer tries = retryCount.get(Long.valueOf(lastSentId));
            inFlight.incrementAndGet();
            stepClosed = false;
            log("sendMoveSomeItems dest=" + pendingDestId
                    + " id=" + lastSentId
                    + " " + moveQueueIndex.get() + "/" + moveQueue.size()
                    + " inFlight=" + inFlight.get()
                    + (tries != null ? " retry=" + tries : ""));
            stepToken.incrementAndGet();
            scheduleWatchdog(queueGen.get(), stepToken.get(), 3000L);
        } catch (Exception e) {
            log("send error: " + e.getMessage());
            deferCurrentForLater();
            sendNextQueuedMoveSoon();
        } finally {
            isMoving = false;
        }
    }

    private static void sendNextQueuedMoveSoon() {
        final int gen = queueGen.get();
        watchdog.schedule(new TimerTask() {
            @Override
            public void run() {
                if (gen != queueGen.get()) return;
                sendNextQueuedMove();
            }
        }, STEP_GAP_MS);
    }

    private static String describeItem(InventoryMetaItem item) {
        if (item == null) return "null";
        short type = -1;
        try { type = item.getType(); } catch (Exception ignored) {}
        String model = itemModel(item);
        String base = "";
        try { base = stripQty(item.getBaseName()); } catch (Exception ignored) {}
        return coreName(item) + "#" + item.getId() + " type=" + type
                + " base=" + base
                + " mat=" + materialId(item)
                + (model.isEmpty() ? "" : " model=" + model);
    }

    private static boolean nodeIsSelected(Object node) {
        Object v = getFieldValue(node, "isSelected");
        return v instanceof Boolean && (Boolean) v;
    }

    /**
     * Prefer the specific row (lump, gold) over the group header (lump).
     */
    private static InventoryMetaItem pickSourceFromDrag(Object drag) {
        if (drag == null) return null;

        List<InventoryMetaItem> selected = new ArrayList<InventoryMetaItem>();
        List<InventoryMetaItem> withMaterial = new ArrayList<InventoryMetaItem>();
        List<InventoryMetaItem> all = new ArrayList<InventoryMetaItem>();
        collectTreeItems(drag, all);

        collectSelectedTreeItems(drag, selected);

        for (InventoryMetaItem it : all) {
            if (it == null) continue;
            if (coreName(it).contains(",")) withMaterial.add(it);
        }

        if (selected.size() == 1) return selected.get(0);
        if (selected.size() > 1) {
            for (InventoryMetaItem it : selected) {
                if (coreName(it).contains(",")) return it;
            }
            return selected.get(0);
        }

        InventoryMetaItem direct = extractItem(drag);
        if (direct != null && coreName(direct).contains(",")) return direct;

        if (withMaterial.size() == 1) return withMaterial.get(0);
        if (direct != null) return direct;
        if (!withMaterial.isEmpty()) return withMaterial.get(0);
        if (!all.isEmpty()) return all.get(0);
        return direct;
    }

    private static void collectSelectedTreeItems(Object node, List<InventoryMetaItem> out) {
        if (node == null) return;
        if (nodeIsSelected(node)) {
            InventoryMetaItem item = extractItem(node);
            if (item != null) out.add(item);
        }
        Object children = getFieldValue(node, "children");
        if (children instanceof List) {
            for (Object child : (List<?>) children) collectSelectedTreeItems(child, out);
        }
    }

    private static void collectTreeItems(Object node, List<InventoryMetaItem> out) {
        if (node == null) return;
        InventoryMetaItem item = extractItem(node);
        if (item != null) out.add(item);
        Object children = getFieldValue(node, "children");
        if (children instanceof List) {
            for (Object child : (List<?>) children) {
                collectTreeItems(child, out);
            }
        }
        Object[] arr = null;
        if (children != null && children.getClass().isArray()) {
            Object[] tmp = (Object[]) children;
            arr = tmp;
        }
        if (arr != null) {
            for (Object child : arr) collectTreeItems(child, out);
        }
    }

    private static long worldHoverId() {
        try {
            if (hud == null || hud.getWorld() == null) return -1L;
            Object hover = hud.getWorld().getCurrentHoveredObject();
            if (hover == null) return -1L;
            Object id = invokeNoArg(hover, "getId");
            if (id instanceof Long) return (Long) id;
            if (id instanceof Integer) return ((Integer) id).longValue();
        } catch (Exception ignored) {}
        return -1L;
    }

    private static String worldHoverName() {
        try {
            if (hud == null || hud.getWorld() == null) return "";
            Object hover = hud.getWorld().getCurrentHoveredObject();
            if (hover == null) return "";
            Object n = invokeNoArg(hover, "getHoverName");
            if (n instanceof String) return (String) n;
            n = invokeNoArg(hover, "getName");
            if (n instanceof String) return (String) n;
        } catch (Exception ignored) {}
        return "";
    }

    private static boolean transferActive() {
        if (pendingAutoAll || waitingForDone || moveStarted) return true;
        return moveQueueIndex.get() > 0 && moveQueueIndex.get() < moveQueue.size();
    }

    private static boolean tryExpandMove(long destId, InventoryMetaItem source) {
        if (source == null) return false;
        boolean shift = isShiftDown();
        boolean ctrl = isCtrlDown();
        if (!shift && !ctrl) return false;

        if (isMoving) return true;
        if (transferActive() && !moveQueue.isEmpty()
                && moveQueueIndex.get() > 0 && moveQueueIndex.get() < moveQueue.size()) {
            log("SHIFT ignored – already transferring "
                    + moveQueueIndex.get() + "/" + moveQueue.size()
                    + " pending=" + pendingAutoAll + " waiting=" + waitingForDone);
            return true;
        }

        boolean dumpAll = shift && ctrl;
        if (!dumpAll && !sawAmountMenu && !isBulkCommodity(source)) {
            log("SHIFT drop ignored – " + describeItem(source) + " is not a bulk commodity"
                    + " parent=" + describeItem(getParent(source))
                    + " ancestor=" + describeItem(findBulkAncestor(source)));
            return false;
        }

        InventoryMetaItem dest = destId > 0 ? findItemById(destId) : null;
        if (destId <= 0) {
            long hoverId = worldHoverId();
            if (hoverId > 0) destId = hoverId;
            else if (lastPlayerTarget > 0) destId = lastPlayerTarget;
            dest = destId > 0 ? findItemById(destId) : null;
        }

        InventoryMetaItem srcParent = getParent(source);
        log("SHIFT drop " + describeItem(source) + " -> " + describeItem(dest)
                + " srcParent=" + describeItem(srcParent));

        if (dest == null && destId <= 0) {
            log("SHIFT drop ignored – no destination item for id " + destId);
            return false;
        }
        if (dest == null) {
            log("SHIFT drop world dest id=" + destId + " (not in inventory lists)");
        }
        if (dest != null && srcParent != null && dest.getId() == srcParent.getId()) {
            log("SHIFT drop ignored – same container");
            return false;
        }
        if (dest == null && srcParent != null && srcParent.getId() == destId) {
            log("SHIFT drop ignored – same container");
            return false;
        }

        List<InventoryMetaItem> all = new ArrayList<InventoryMetaItem>();
        if (dumpAll) {
            all.addAll(findAllInSameParent(source));
        } else if (shift) {
            all.addAll(findAllQlGroups(source));
        } else {
            InventoryMetaItem row = commodityRow(source);
            all.add(row != null ? row : source);
        }
        long[] ids = getItemIds(all);
        String mode = dumpAll ? "CTRL+SHIFT entire container"
                : (shift ? "SHIFT all QL groups" : "CTRL one stack");
        String label = dumpAll
                ? (srcParent != null ? coreName(srcParent) : "container")
                : (ignoreMaterial(typeName(source)) ? typeName(source) : coreName(source));
        String msg = mode + " \"" + label + "\" – " + ids.length + " id(s)";
        log(msg);
        if (debug && hud != null) hud.consoleOutput(">>> [EZBulk] " + msg);

        pendingDestId = destId;
        pendingSource = source;
        pendingAmount = ids.length;
        rescanParentId = srcParent != null ? srcParent.getId() : -1L;
        shiftRescan = shift || dumpAll;
        dumpEntire = dumpAll;
        destRefused = false;
        rescanPass = 0;
        inFlight.set(0);
        stepClosed = false;
        moveQueue.clear();
        retryCount.clear();
        lastSentId = 0L;
        for (int i = 0; i < ids.length; i++) moveQueue.add(Long.valueOf(ids[i]));
        moveQueueIndex.set(0);
        queueGen.incrementAndGet();
        waitingForDone = false;
        moveStarted = false;
        sendNextQueuedMove();
        return true;
    }

    private static InventoryListComponent findListForItem(InventoryMetaItem item) {
        if (hud == null || item == null) return null;
        try {
            InventoryWindow invWin = hud.getInventoryWindow();
            if (invWin != null) {
                InventoryListComponent ilc = invWin.getInventoryListComponent();
                if (listContains(ilc, item.getId())) return ilc;
            }
        } catch (Exception ignored) {}
        try {
            Field compsField = HeadsUpDisplay.class.getDeclaredField("components");
            compsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<WurmComponent> comps = (List<WurmComponent>) compsField.get(hud);
            if (comps != null) {
                for (WurmComponent c : comps) {
                    InventoryListComponent ilc = findListComponent(c);
                    if (listContains(ilc, item.getId())) return ilc;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean listContains(InventoryListComponent ilc, long id) {
        InventoryMetaItem root = getRootFromList(ilc);
        if (root == null) return false;
        if (root.getId() == id) return true;
        try {
            if (root.getChildren() != null) {
                for (InventoryMetaItem c : root.getChildren()) {
                    if (c != null && c.getId() == id) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Item under the cursor in a list (the wagon row you dropped onto, the BSB
     * line, or a commodity already in the destination bin).
     */
    private static InventoryMetaItem itemAtPoint(InventoryListComponent list, int x, int y) {
        if (list == null) return null;
        Object node = invokeMethod(list, "getDraggableComponentAt",
                new Class<?>[] { int.class, int.class },
                new Object[] { x, y });
        return extractItem(node);
    }

    /**
     * Destination container for a drop. The open window may be a wagon that
     * only *holds* the BSB — we want the BSB under the cursor, not the wagon
     * and not the player inventory the server uses as a transit path.
     */
    private static InventoryMetaItem resolveDestContainer(InventoryListComponent list, int x, int y) {
        InventoryMetaItem hovered = itemAtPoint(list, x, y);
        if (hovered != null) {
            if (isBulkContainer(hovered)) return hovered;
            InventoryMetaItem ancestor = findBulkAncestor(hovered);
            if (ancestor != null) return ancestor;
            InventoryMetaItem parent = getParent(hovered);
            if (parent != null && isBulkContainer(parent)) return parent;
        }

        long id = invokeDroppedOnTargetId(list, x, y);
        InventoryMetaItem byId = findItemById(id);
        if (byId != null) {
            if (isBulkContainer(byId)) return byId;
            InventoryMetaItem parent = getParent(byId);
            if (parent != null && isBulkContainer(parent)) return parent;
        }

        InventoryMetaItem root = getRootFromList(list);
        if (root != null && isBulkContainer(root)) return root;
        return null;
    }

    private static long destIdFromList(InventoryListComponent list, int x, int y) {
        InventoryMetaItem dest = resolveDestContainer(list, x, y);
        if (dest != null) return dest.getId();
        if (list == null) return -1L;
        long id = invokeDroppedOnTargetId(list, x, y);
        if (id > 0) return id;
        InventoryMetaItem root = getRootFromList(list);
        return root != null ? root.getId() : -1L;
    }

    /**
     * itemDropped(int x, int y, DraggableComponent drag) on the TARGET list.
     * Returning true skips vanilla, which is what closes the amount popup.
     */
    private static boolean onItemDropped(Object proxy, Object[] args) {
        if (isMoving || args == null || args.length < 3) return false;
        if (!isShiftDown() && !isCtrlDown()) return false;

        int x = args[0] instanceof Integer ? (Integer) args[0] : 0;
        int y = args[1] instanceof Integer ? (Integer) args[1] : 0;
        lastDragNode = args[2];
        InventoryMetaItem source = pickSourceFromDrag(args[2]);
        InventoryListComponent destList = findListComponent(proxy);
        InventoryMetaItem hovered = itemAtPoint(destList, x, y);
        InventoryMetaItem destContainer = resolveDestContainer(destList, x, y);
        long destId = destContainer != null ? destContainer.getId() : destIdFromList(destList, x, y);

        log("itemDropped source=" + (source != null ? coreName(source) + "#" + source.getId() : "null")
                + " hovered=" + (hovered != null ? coreName(hovered) : "null")
                + " dest=" + (destContainer != null ? coreName(destContainer) + "#" + destContainer.getId() : ("id=" + destId))
                + " drag=" + (args[2] != null ? args[2].getClass().getSimpleName() : "null"));

        rememberShiftDrop(destId, source);
        return false;
    }

    private static void rememberShiftDrop(long destId, InventoryMetaItem source) {
        if (source == null && destId <= 0) return;
        if (destId > 0) lastShiftDestId = destId;
        if (source != null) lastShiftSource = source;
        destRefused = false;
        log("remember drop dest=" + destId
                + " source=" + describeItem(source)
                + " – wait for Removing items menu");
    }

    private static void startFromAmountMenu() {
        if (transferActive() && !moveQueue.isEmpty()) return;
        if (lastShiftSource == null || lastShiftDestId <= 0) {
            log("amount menu – no remembered drop");
            return;
        }
        if (!isShiftDown() && !isCtrlDown()) {
            log("amount menu – no SHIFT/CTRL, leave vanilla window");
            return;
        }
        sawAmountMenu = true;
        log("amount menu – start EZBulk dest=" + lastShiftDestId
                + " source=" + describeItem(lastShiftSource));
        tryExpandMove(lastShiftDestId, lastShiftSource);
    }

    /**
     * handleDrop(long destId, long[] sourceIds) – vanilla path after the amount box.
     * Do not start our queue here. Wait for the amount menu.
     */
    private static boolean onHandleDrop(Object[] args) {
        if (args != null && args.length >= 2 && args[1] instanceof long[]) {
            lastDropSourceIds = (long[]) args[1];
            log("handleDrop destId=" + args[0] + " sources=" + lastDropSourceIds.length);
        }
        if (transferActive()) return true;
        if (args == null || args.length < 2) return false;
        if (!(args[0] instanceof Long) || !(args[1] instanceof long[])) return false;

        long destId = (Long) args[0];
        long[] sourceIds = (long[]) args[1];
        if (sourceIds.length == 0) return false;

        InventoryMetaItem source = null;
        for (int i = 0; i < sourceIds.length; i++) {
            InventoryMetaItem it = findItemById(sourceIds[i]);
            if (it != null) { source = it; break; }
        }
        rememberShiftDrop(destId, source);
        return false;
    }

    private static boolean onItemDroppedAmount(Object[] args) {
        if (isMoving || args == null || args.length < 2) return false;
        InventoryMetaItem source = extractItem(args[0]);
        long destId = -1L;
        if (args[1] instanceof Long) destId = (Long) args[1];
        rememberShiftDrop(destId, source);
        return false;
    }

    private static boolean onTreeItemDropped(Object proxy, Object[] args) {
        if (isMoving || args == null || args.length < 3) return false;
        int x = args[0] instanceof Integer ? (Integer) args[0] : 0;
        int y = args[1] instanceof Integer ? (Integer) args[1] : 0;
        lastDragNode = args[2];
        InventoryMetaItem source = pickSourceFromDrag(args[2]);
        InventoryListComponent destList = findListComponent(proxy);
        long destId = destIdFromList(destList, x, y);
        rememberShiftDrop(destId, source);
        return false;
    }

    private static void parseHeaveHoMs(String t) {
        int amount = -1;
        int rate = -1;
        int paren = t.indexOf('(');
        if (paren >= 0) {
            int end = t.indexOf(')', paren);
            if (end > paren) {
                try {
                    amount = Integer.parseInt(t.substring(paren + 1, end).trim());
                } catch (Exception ignored) {}
            }
        }
        int rateAt = t.indexOf("rate of ");
        if (rateAt >= 0) {
            int from = rateAt + 8;
            int to = from;
            while (to < t.length() && t.charAt(to) >= '0' && t.charAt(to) <= '9') to++;
            if (to > from) {
                try {
                    rate = Integer.parseInt(t.substring(from, to));
                } catch (Exception ignored) {}
            }
        }
        lastHeaveAmount = amount;
        lastHeaveRate = rate;
    }

    private static boolean maybeRescanSource() {
        if (!shiftRescan || destRefused) return false;
        if (rescanParentId <= 0 || pendingSource == null) return false;
        if (rescanPass >= MAX_RESCAN_PASSES) {
            log("rescan stop – max passes");
            return false;
        }
        List<InventoryMetaItem> left = new ArrayList<InventoryMetaItem>();
        InventoryMetaItem parent = findItemById(rescanParentId);
        List<InventoryMetaItem> pool = new ArrayList<InventoryMetaItem>();
        if (parent != null) collectItems(parent, pool);
        for (InventoryMetaItem item : allKnownItems()) {
            if (item == null) continue;
            InventoryMetaItem p = getParent(item);
            if (p != null && p.getId() == rescanParentId) pool.add(item);
        }
        java.util.LinkedHashMap<Long, InventoryMetaItem> unique =
                new java.util.LinkedHashMap<Long, InventoryMetaItem>();
        for (InventoryMetaItem item : pool) {
            if (item == null) continue;
            if (item.getId() == rescanParentId) continue;
            if (isBulkContainer(item)) continue;
            if (!dumpEntire && pendingSource != null && !sameCommodity(pendingSource, item)) continue;
            InventoryMetaItem top = dumpEntire ? item : pileRow(item);
            if (top == null) top = item;
            if (top.getId() == rescanParentId) continue;
            if (isBulkContainer(top)) continue;
            InventoryMetaItem itemParent = getParent(top);
            if (dumpEntire && itemParent != null && itemParent.getId() != rescanParentId) continue;
            unique.put(Long.valueOf(top.getId()), top);
        }
        left.addAll(unique.values());
        if (left.isEmpty()) {
            log("rescan: source empty of \""
                    + (ignoreMaterial(typeName(pendingSource)) ? typeName(pendingSource) : coreName(pendingSource))
                    + "\"");
            return false;
        }
        rescanPass++;
        moveQueue.clear();
        retryCount.clear();
        for (int i = 0; i < left.size(); i++) moveQueue.add(Long.valueOf(left.get(i).getId()));
        moveQueueIndex.set(0);
        lastSentId = 0L;
        queueGen.incrementAndGet();
        log("rescan pass " + rescanPass + " – " + left.size() + " still in source, running again");
        sendNextQueuedMove();
        return true;
    }

    private static void announceOnscreen(String msg) {
        if (msg == null || msg.isEmpty()) return;
        log(msg);
        if (hud == null) return;
        try {
            hud.getClass()
                    .getMethod("addOnscreenMessage", String.class, float.class, float.class, float.class, byte.class)
                    .invoke(hud, msg, Float.valueOf(1f), Float.valueOf(1f), Float.valueOf(1f), Byte.valueOf((byte) 1));
        } catch (Exception e) {
            try {
                hud.consoleOutput(">>> " + msg);
            } catch (Exception ignored) {}
        }
    }

    private static void announceTransferComplete() {
        announceOnscreen("Transfer Complete!");
    }

    private static boolean isPlayerAbort(String reason) {
        if (reason == null) return true;
        String r = reason.toLowerCase();
        return r.contains("escape")
                || r.contains("main menu")
                || r.contains("mainmenu")
                || r.contains("ezbulk_stop")
                || r.contains("ezbulk_cancel")
                || r.contains("stop_or_main")
                || r.contains("player action stop")
                || r.contains("hud ");
    }

    private static boolean isGameCap(String reason) {
        if (reason == null) return false;
        String r = reason.toLowerCase();
        return r.contains("fit")
                || r.contains("space")
                || r.contains("carry")
                || r.contains("strong enough")
                || r.contains("only ore")
                || r.contains("will not accept")
                || r.contains("can not be put")
                || r.contains("cannot be put")
                || r.contains("can't be put")
                || r.contains("any more items");
    }

    private static void abortQueue(String reason) {
        log("queue abort: " + reason);
        String r = reason != null ? reason.toLowerCase() : "";
        destRefused = true;
        lastShiftSource = null;
        lastShiftDestId = -1L;
        moveQueue.clear();
        moveQueueIndex.set(0);
        lastSentId = 0L;
        waitingForDone = false;
        pendingAutoAll = false;
        moveStarted = false;
        inFlight.set(0);
        stepClosed = true;
        shiftRescan = false;
        dumpEntire = false;
        queueGen.incrementAndGet();
        dismissAmountWindow();
        if (isGameCap(reason)) announceOnscreen("Transfer Capped!");
        else if (isPlayerAbort(reason)) announceOnscreen("Transfer Stopped!");
        else announceOnscreen("Transfer Stopped!");
    }

    private static void onEventText(String text) {
        abortIfEscape();
        if (text == null || text.isEmpty()) return;
        if (moveQueueIndex.get() > moveQueue.size()) return;
        if (!pendingAutoAll && !waitingForDone
                && (moveQueueIndex.get() == 0 || moveQueue.isEmpty())) return;

        String t = text.toLowerCase();
        if (t.contains("[") && t.contains("]")) {
            int close = t.indexOf(']');
            if (close > 0 && close + 1 < t.length()) t = t.substring(close + 1).trim();
        }

        boolean active = pendingAutoAll || waitingForDone
                || (!moveQueue.isEmpty() && moveQueueIndex.get() > 0
                        && moveQueueIndex.get() <= moveQueue.size());
        if (active) log("event: " + text);

        if (t.contains("can not even carry") || t.contains("cannot even carry")
                || t.contains("can't carry") || t.contains("you are not strong enough")
                || t.contains("not enough space") || t.contains("will not fit")
                || t.contains("can not even fit") || t.contains("cannot even fit")
                || t.contains("can't fit") || t.contains("any more items")
                || t.contains("only ore can be put")
                || t.contains("can not be put") || t.contains("cannot be put")
                || t.contains("can't be put") || t.contains("will not accept")) {
            abortQueue(text);
            return;
        }

        if (t.contains("you get a ")) {
            abortQueue(text);
            return;
        }

        if (t.contains("you stop") || t.contains("you interrupt")) {
            abortQueue(text);
            return;
        }

        if (t.contains("already busy")) {
            log("busy – defer current and continue");
            pendingAutoAll = false;
            waitingForDone = false;
            moveStarted = false;
            deferCurrentForLater();
            sendNextQueuedMoveSoon();
            return;
        }

        if (t.contains("you may now queue")) {
            inFlight.set(0);
            log("action bar empty – inFlight=0");
            pendingAutoAll = false;
            waitingForDone = false;
            moveStarted = false;
            sendNextQueuedMoveSoon();
            return;
        }

        if (t.contains("heave-ho") || t.contains("moving a whole")) {
            parseHeaveHoMs(t);
            log("heave-ho amount=" + lastHeaveAmount + " rate=" + lastHeaveRate
                    + " (flavor, not used as a timer)");
            return;
        }

        if (t.contains("you selected max")) {
            if (stepClosed) return;
            stepClosed = true;
            pendingAutoAll = false;
            waitingForDone = false;
            moveStarted = false;
            log("timing ACK id=" + lastSentId
                    + " " + moveQueueIndex.get() + "/" + moveQueue.size()
                    + " inFlight=" + inFlight.get()
                    + " send->ack=" + (tSend > 0 ? (System.currentTimeMillis() - tSend) : -1) + "ms");
            sendNextQueuedMoveSoon();
        }
    }

    /**
     * WurmBot BulkItemGetter: when sendMoveSomeItems hits a BSB, the client
     * adds a BMLWindowComponent titled "Removing items". Click submit.
     */
    private static boolean maybeSubmitRemovingItems(Object wc) {
        abortIfEscape();
        if (destRefused && !isShiftDown()) return false;
        if (wc == null) return false;
        String cls = wc.getClass().getSimpleName();
        if (!cls.toLowerCase().contains("bmlwindow")) return false;

        Object titleObj = getFieldValue(wc, "title");
        String title = titleObj instanceof String ? (String) titleObj : "";
        log("BML window title=\"" + title + "\" class=" + cls + " pending=" + pendingAutoAll
                + " active=" + transferActive() + " shift=" + isShiftDown()
                + " queue=" + moveQueueIndex.get() + "/" + moveQueue.size());
        if (!"Removing items".equals(title) && !looksLikeAmountPrompt(title)) return false;
        boolean ourJob = pendingAutoAll
                || (waitingForDone && !moveQueue.isEmpty() && moveQueueIndex.get() > 0);
        boolean modifier = isShiftDown() || isCtrlDown();
        if (!ourJob && modifier) startFromAmountMenu();
        ourJob = pendingAutoAll
                || transferActive()
                || (waitingForDone && !moveQueue.isEmpty());
        if (!ourJob) {
            log("BML \"" + title + "\" – vanilla (no EZBulk queue)");
            return false;
        }
        lastAmountWindow = wc;

        try {
            Method click = null;
            Class<?> c = wc.getClass();
            while (c != null && click == null) {
                Method[] ms = c.getDeclaredMethods();
                for (int i = 0; i < ms.length; i++) {
                    if (ms[i].getName().equals("processButtonPressed")) {
                        click = ms[i];
                        break;
                    }
                }
                c = c.getSuperclass();
            }
            if (click == null) {
                log("BML processButtonPressed not found");
                return false;
            }
            click.setAccessible(true);
            click.invoke(wc, "submit");
            pendingAutoAll = false;
            waitingForDone = true;
            tBml = System.currentTimeMillis();
            log("BML submit on \"" + title + "\" send->bml="
                    + (tSend > 0 ? (tBml - tSend) : -1) + "ms");
            return true;
        } catch (Exception e) {
            log("BML submit error: " + e.getMessage());
            return false;
        }
    }

    private static boolean looksLikeAmountPrompt(String text) {
        if (text == null) return false;
        String t = text.toLowerCase();
        return t.contains("how many")
                || t.contains("removing items")
                || t.contains("wish to remove")
                || t.contains("wish to transfer")
                || t.contains("all items");
    }

    private static String describeObj(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + obj + "\"";
        if (obj instanceof List) {
            StringBuffer sb = new StringBuffer("[");
            int n = 0;
            for (Object o : (List<?>) obj) {
                if (n++ > 0) sb.append(", ");
                sb.append(String.valueOf(o));
                if (n > 12) { sb.append(", ..."); break; }
            }
            return sb.append("]").toString();
        }
        return obj.getClass().getSimpleName();
    }

    private static boolean answerAmountPopup(Object popup) {
        if (popup == null) return false;
        log("amount popup class=" + popup.getClass().getName());

        String[] texts = { "getTitle", "getText", "getMessage", "getLabel", "toString" };
        for (int i = 0; i < texts.length; i++) {
            Object v = invokeNoArg(popup, texts[i]);
            if (v instanceof String && looksLikeAmountPrompt((String) v)) {
                log("popup text via " + texts[i] + ": " + v);
            }
        }

        String[] confirm = { "confirm", "ok", "send", "accept", "submit", "doSend", "closeAndSend" };
        String[] select = { "setSelected", "select", "setValue", "setText", "setInput" };

        Object options = getFieldValue(popup, "options");
        if (options == null) options = invokeNoArg(popup, "getOptions");
        if (options == null) options = invokeNoArg(popup, "getItems");
        log("popup options=" + describeObj(options));

        boolean selected = false;
        if (options instanceof List) {
            int idx = 0;
            int allIdx = -1;
            for (Object o : (List<?>) options) {
                String s = String.valueOf(o).toLowerCase();
                if (s.contains("all")) { allIdx = idx; break; }
                idx++;
            }
            if (allIdx >= 0) {
                invokeMethod(popup, "setSelectedIndex",
                        new Class<?>[] { int.class }, new Object[] { allIdx });
                invokeMethod(popup, "select",
                        new Class<?>[] { int.class }, new Object[] { allIdx });
                selected = true;
                log("selected All items index=" + allIdx);
            }
        }

        invokeMethod(popup, "setText", new Class<?>[] { String.class }, new Object[] { "All items" });
        invokeMethod(popup, "setValue", new Class<?>[] { String.class }, new Object[] { "All items" });
        invokeMethod(popup, "setInput", new Class<?>[] { String.class }, new Object[] { String.valueOf(pendingAmount) });

        Object input = getFieldValue(popup, "input");
        if (input == null) input = getFieldValue(popup, "textField");
        if (input == null) input = getFieldValue(popup, "edit");
        if (input != null) {
            invokeMethod(input, "setText", new Class<?>[] { String.class }, new Object[] { "All items" });
            log("set input field to All items");
        }

        boolean confirmed = false;
        for (int i = 0; i < confirm.length; i++) {
            Object r = invokeNoArg(popup, confirm[i]);
            if (r != null || methodExists(popup, confirm[i])) {
                log("invoked popup." + confirm[i]);
                confirmed = true;
            }
        }

        if (lastDragNode != null && pendingDestId > 0) {
            InventoryListComponent owner = listOwningNode(lastDragNode);
            if (invokeDroppedAmount(owner, lastDragNode, pendingDestId, pendingAmount > 0 ? pendingAmount : 99999)) {
                confirmed = true;
            }
        }

        if (selected || confirmed) {
            pendingAutoAll = false;
            return true;
        }
        return false;
    }

    private static boolean methodExists(Object obj, String name) {
        Class<?> cls = obj.getClass();
        while (cls != null && cls != Object.class) {
            Method[] ms = cls.getDeclaredMethods();
            for (int i = 0; i < ms.length; i++) {
                if (ms[i].getName().equals(name) && ms[i].getParameterTypes().length == 0)
                    return true;
            }
            cls = cls.getSuperclass();
        }
        return false;
    }

    private static boolean shouldAutoAnswer(Object[] args) {
        if (!pendingAutoAll || args == null) return false;
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof String && looksLikeAmountPrompt((String) args[i])) return true;
            if (args[i] instanceof List) {
                for (Object o : (List<?>) args[i]) {
                    if (looksLikeAmountPrompt(String.valueOf(o))) return true;
                }
            }
        }
        return pendingAutoAll;
    }

    private static void hookPopup(String className, String method, String desc) {
        try {
            HookManager.getInstance().registerHook(
                    className, method, desc,
                    () -> (proxy, m, args) -> {
                        Object result = m.invoke(proxy, args);
                        try {
                            if (!pendingAutoAll) return result;
                            StringBuffer sb = new StringBuffer("popup " + method + "(");
                            if (args != null) {
                                for (int i = 0; i < args.length; i++) {
                                    if (i > 0) sb.append(", ");
                                    sb.append(describeObj(args[i]));
                                }
                            }
                            log(sb.append(")").toString());
                            Object target = (args != null && args.length > 0) ? args[args.length - 1] : proxy;
                            if (args != null) {
                                for (int i = 0; i < args.length; i++) {
                                    if (args[i] != null && !(args[i] instanceof String)
                                            && !(args[i] instanceof Number)
                                            && !(args[i] instanceof List)
                                            && !(args[i] instanceof Byte)) {
                                        target = args[i];
                                        break;
                                    }
                                }
                            }
                            boolean named = method.toLowerCase().contains("popup")
                                    || method.toLowerCase().contains("dropdown");
                            if (named || shouldAutoAnswer(args)) {
                                answerAmountPopup(target instanceof String ? proxy : target);
                            }
                        } catch (Exception e) {
                            log("popup hook " + method + ": " + e.getMessage());
                        }
                        return result;
                    }
            );
            log(">>> Hooked popup " + className + "." + method);
        } catch (Exception e) {
            log(">>> Could not hook popup " + className + "." + method + ": " + e.getMessage());
        }
    }

    private static boolean interceptSendMove(Object[] args) {
        if (isMoving) return false;
        if (transferActive()) return true;
        if (args == null || args.length < 2) return false;
        if (!(args[0] instanceof Long) || !(args[1] instanceof long[])) return false;
        long destId = (Long) args[0];
        long[] ids = (long[]) args[1];
        if (destId <= 0 || ids == null || ids.length == 0) return false;
        lastDropSourceIds = ids;
        InventoryMetaItem source = findItemById(ids[0]);
        log("vanilla sendMoveSomeItems dest=" + destId + " n=" + ids.length
                + " first=" + describeItem(source) + " – wait for menu");
        rememberShiftDrop(destId, source);
        return false;
    }

    private static boolean isStopAction(PlayerAction act) {
        if (act == null) return false;
        short id = act.getId();
        if (id == 0) return true;
        String s = String.valueOf(act).toLowerCase();
        return s.contains("stop") || s.contains("main_menu") || s.contains("main menu");
    }

    private static void rememberPlayerAction(PlayerAction act, long a1, long a2) {
        if (act == null) return;
        lastPlayerTarget = a2 > 0 ? a2 : a1;
        log("player action id=" + act.getId() + " target=" + lastPlayerTarget
                + " arg1=" + a1 + " arg2=" + a2);
        if (transferActive() && isStopAction(act)) {
            abortQueue("player action STOP id=" + act.getId());
            if (hud != null) hud.consoleOutput(">>> EZBulk transfer stopped");
        }
    }

    private static void registerActionHook(String className, String methodName, String descriptor) {
        try {
            HookManager.getInstance().registerHook(
                    className, methodName, descriptor,
                    () -> (proxy, m, args) -> {
                        try {
                            PlayerAction act = null;
                            long first = -1L;
                            long second = -1L;
                            int longs = 0;
                            if (args != null) {
                                for (int i = 0; i < args.length; i++) {
                                    if (args[i] instanceof PlayerAction) act = (PlayerAction) args[i];
                                    else if (args[i] instanceof Long) {
                                        longs++;
                                        if (longs == 1) first = (Long) args[i];
                                        else if (longs == 2) second = (Long) args[i];
                                    } else if (args[i] instanceof long[]) {
                                        lastDropSourceIds = (long[]) args[i];
                                    }
                                }
                            }
                            if (act != null) rememberPlayerAction(act, first, second);
                        } catch (Exception ignored) {}
                        return m.invoke(proxy, args);
                    }
            );
            log(">>> Hooked " + className + "." + methodName);
        } catch (Exception e) {
            log(">>> Could not hook " + className + "." + methodName + ": " + e.getMessage());
        }
    }

    private static void hookSendMove(String className) {
        try {
            HookManager.getInstance().registerHook(
                    className,
                    "sendMoveSomeItems",
                    "(J[J)V",
                    () -> (proxy, m, args) -> {
                        try {
                            if (interceptSendMove(args)) return null;
                        } catch (Exception e) {
                            log("intercept sendMove: " + e.getMessage());
                        }
                        return m.invoke(proxy, args);
                    }
            );
            log(">>> Hooked " + className + ".sendMoveSomeItems(J[J)V");
        } catch (Exception e) {
            log(">>> Could not hook " + className + ".sendMoveSomeItems: " + e.getMessage());
        }
    }

    private static void hook(String className, String method, String desc, final int kind) {
        try {
            HookManager.getInstance().registerHook(
                    className, method, desc,
                    () -> (proxy, m, args) -> {
                        try {
                            boolean handled = false;
                            if (kind == 1) handled = onItemDropped(proxy, args);
                            else if (kind == 2) handled = onHandleDrop(args);
                            else if (kind == 3) handled = onItemDroppedAmount(args);
                            else if (kind == 4) handled = onTreeItemDropped(proxy, args);
                            if (handled) return null;
                        } catch (Exception e) {
                            log("hook error " + method + ": " + e.getMessage());
                        }
                        return m.invoke(proxy, args);
                    }
            );
            log(">>> Hooked " + className + "." + method + desc);
        } catch (Exception e) {
            log(">>> Could not hook " + className + "." + method + ": " + e.getMessage());
        }
    }

    private static void hookConsoleInput(String method, String desc) {
        try {
            HookManager.getInstance().registerHook(
                    "com.wurmonline.client.console.WurmConsole",
                    method, desc,
                    () -> (proxy, m, args) -> {
                        try {
                            if (args != null && args.length > 0 && args[0] instanceof String) {
                                String cmd = (String) args[0];
                                String[] data = (args.length > 1 && args[1] instanceof String[])
                                        ? (String[]) args[1] : new String[0];
                                if (debug && transferActive()) log("console: " + cmd);
                                handleInput(cmd, data);
                            }
                        } catch (Exception ignored) {}
                        return m.invoke(proxy, args);
                    }
            );
            log(">>> Hooked WurmConsole." + method + desc);
        } catch (Exception e) {
            log(">>> Could not hook WurmConsole." + method + ": " + e.getMessage());
        }
    }

    private static void hookBmlWindow(String method, String desc) {
        try {
            HookManager.getInstance().registerHook(
                    "com.wurmonline.client.renderer.gui.BmlWindowComponent",
                    method, desc,
                    () -> (proxy, m, args) -> {
                        Object result = m.invoke(proxy, args);
                        try {
                            onBmlWindowSeen(proxy);
                        } catch (Exception e) {
                            log("BmlWindow." + method + ": " + e.getMessage());
                        }
                        return result;
                    }
            );
            log(">>> Hooked BmlWindowComponent." + method + desc);
        } catch (Exception e) {
            log(">>> Could not hook BmlWindowComponent." + method + ": " + e.getMessage());
        }
    }

    private static void onBmlWindowSeen(Object wc) {
        if (wc == null) return;
        Object titleObj = getFieldValue(wc, "title");
        String title = titleObj instanceof String ? (String) titleObj : "";
        if (!"Removing items".equals(title) && !looksLikeAmountPrompt(title)) return;
        if (answeredBml.containsKey(wc)) return;
        answeredBml.put(wc, Boolean.TRUE);
        log("BmlWindow seen title=\"" + title + "\"");
        maybeSubmitRemovingItems(wc);
    }

    // ==================== COMMANDS ====================
    public static boolean handleInput(final String cmd, final String[] data) {

        String cleanCmd = cmd.startsWith("/") ? cmd.substring(1) : cmd;

        if (isVanillaStopCommand(cleanCmd)) {
            if (transferActive()) {
                abortQueue(cleanCmd);
                if (hud != null) hud.consoleOutput(">>> EZBulk transfer stopped");
            }
            return false;
        }

        if (cleanCmd.equalsIgnoreCase("ezbulk")) {
            String msg = "EZBulk v" + VERSION
                    + " | debug=" + debug
                    + " | selective_wood=" + onOff(selectiveWood)
                    + " selective_wood_scrap=" + onOff(selectiveWoodScrap)
                    + " selective_sprout=" + onOff(selectiveSprout)
                    + " all_meat=" + onOff(allMeat)
                    + " | CTRL+drag = one stack. SHIFT+drag = every QL group."
                    + " CTRL+SHIFT+drag = every item in that container.";
            log(msg);
            if (hud != null) hud.consoleOutput(">>> " + msg);
            return true;
        }

        if (cleanCmd.equalsIgnoreCase("ezbulk_debug")) {
            debug = !debug;
            String msg = debug ? "Debug mode ENABLED" : "Debug mode DISABLED";
            if (debug) clearAndLog(">>> EZBulk v" + VERSION + " – " + msg);
            if (hud != null) hud.consoleOutput(">>> " + msg);
            return true;
        }

        if (cleanCmd.equalsIgnoreCase("ezbulk_stop")
                || cleanCmd.equalsIgnoreCase("ezbulk_cancel")) {
            abortQueue("player /ezbulk_stop");
            if (hud != null) hud.consoleOutput(">>> EZBulk transfer stopped");
            return true;
        }

        if (cleanCmd.equalsIgnoreCase("ezbulk_reload")) {
            loadProperties();
            String msg = "Reloaded mods/WurmHelper/ezbulk.properties"
                    + " | selective_wood=" + onOff(selectiveWood)
                    + " selective_wood_scrap=" + onOff(selectiveWoodScrap)
                    + " selective_sprout=" + onOff(selectiveSprout)
                    + " all_meat=" + onOff(allMeat)
                    + " container_types=" + formatTypeIds(containerTypes);
            if (hud != null) hud.consoleOutput(">>> " + msg);
            log(msg);
            return true;
        }

        return false;
    }

    private static String onOff(boolean v) {
        return v ? "on" : "off";
    }

    private static String firstProp(Properties props, String... keys) {
        if (props == null || keys == null) return null;
        for (int i = 0; i < keys.length; i++) {
            String v = props.getProperty(keys[i]);
            if (v != null && !v.trim().isEmpty()) return v;
        }
        return null;
    }

    private static boolean parseOnOff(String raw, boolean fallback) {
        if (raw == null) return fallback;
        String v = raw.trim().toLowerCase();
        if (v.equals("on") || v.equals("true") || v.equals("yes") || v.equals("1")) return true;
        if (v.equals("off") || v.equals("false") || v.equals("no") || v.equals("0")) return false;
        return fallback;
    }

    private static void parseTypeIds(String raw, java.util.Set<Integer> dest) {
        dest.clear();
        if (raw == null || raw.trim().isEmpty()) return;
        String[] parts = raw.split("[,;\\s]+");
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (p.isEmpty()) continue;
            try {
                dest.add(Integer.valueOf(Integer.parseInt(p)));
            } catch (NumberFormatException ignored) {}
        }
    }

    private static String formatTypeIds(java.util.Set<Integer> ids) {
        if (ids == null || ids.isEmpty()) return "(none)";
        StringBuffer sb = new StringBuffer();
        java.util.List<Integer> sorted = new ArrayList<Integer>(ids);
        java.util.Collections.sort(sorted);
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(sorted.get(i));
        }
        return sb.toString();
    }

    private static void loadProperties() {
        selectiveWood = false;
        selectiveWoodScrap = false;
        selectiveSprout = false;
        allMeat = true;
        applyDefaultContainerTypes();
        File configFile = new File("mods/WurmHelper/ezbulk.properties");
        if (!configFile.exists()) {
            log("mods/WurmHelper/ezbulk.properties not found – using built-in container_types");
            return;
        }
        try {
            FileInputStream fis = new FileInputStream(configFile);
            Properties props = new Properties();
            props.load(fis);
            fis.close();
            selectiveWood = parseOnOff(firstProp(props,
                    "selective_wood", "selective_logs", "selective_log"), false);
            selectiveWoodScrap = parseOnOff(firstProp(props,
                    "selective_wood_scrap", "selective_scrap"), false);
            selectiveSprout = parseOnOff(firstProp(props,
                    "selective_sprout", "selective_sprouts"), false);
            allMeat = parseOnOff(props.getProperty("all_meat"), true);
            String rawTypes = firstProp(props, "container_types", "extra_container_types");
            if (rawTypes != null && !rawTypes.trim().isEmpty()) {
                parseTypeIds(rawTypes, containerTypes);
            }
            log("properties selective_wood=" + onOff(selectiveWood)
                    + " selective_wood_scrap=" + onOff(selectiveWoodScrap)
                    + " selective_sprout=" + onOff(selectiveSprout)
                    + " all_meat=" + onOff(allMeat)
                    + " container_types=" + formatTypeIds(containerTypes));
        } catch (Exception e) {
            log("WARNING: Could not load properties: " + e.getMessage());
        }
    }

    // ==================== PROPERTIES ====================
    @Override
    public void init() {
        debug = false;
        loadProperties();

        try {
            HookManager.getInstance().registerHook(
                    "com.wurmonline.client.renderer.gui.HeadsUpDisplay",
                    "init", "(II)V",
                    () -> (proxy, method, args) -> {
                        method.invoke(proxy, args);
                        hud = (HeadsUpDisplay) proxy;
                        return null;
                    }
            );

            hook("com.wurmonline.client.renderer.gui.InventoryListComponent",
                    "itemDropped",
                    "(IILcom/wurmonline/client/renderer/gui/DraggableComponent;)V", 1);
            hook("com.wurmonline.client.renderer.gui.InventoryListComponent",
                    "handleDrop", "(J[J)V", 2);
            hook("com.wurmonline.client.renderer.gui.InventoryListComponent",
                    "itemDroppedAmount",
                    "(Lcom/wurmonline/client/renderer/gui/InventoryListComponent$InventoryTreeListItem;JI)V", 3);

            hook("com.wurmonline.client.renderer.gui.InventoryContainerWindow",
                    "itemDropped",
                    "(IILcom/wurmonline/client/renderer/gui/DraggableComponent;)V", 1);
            hook("com.wurmonline.client.renderer.gui.InventoryContainerWindow",
                    "handleInventoryTreeListItemDropped",
                    "(IILcom/wurmonline/client/renderer/gui/InventoryListComponent$InventoryTreeListItem;)V", 4);

            HookManager.getInstance().registerHook(
                    "com.wurmonline.client.console.WurmConsole",
                    "handleDevInput", "(Ljava/lang/String;[Ljava/lang/String;)Z",
                    () -> (proxy, method, args) -> {
                        if (handleInput((String) args[0], (String[]) args[1])) return true;
                        return method.invoke(proxy, args);
                    }
            );

            hookConsoleInput("handleInput", "(Ljava/lang/String;)Z");
            hookConsoleInput("handleInput", "(Ljava/lang/String;[Ljava/lang/String;)Z");
            hookConsoleInput("handleInput", "(Ljava/lang/String;)V");
            hookConsoleInput("execute", "(Ljava/lang/String;)V");
            hookConsoleInput("execute", "(Ljava/lang/String;)Z");
            hookConsoleInput("runCommand", "(Ljava/lang/String;)V");

            try {
                HookManager.getInstance().registerHook(
                        "com.wurmonline.client.renderer.gui.HeadsUpDisplay",
                        "addComponent",
                        "(Lcom/wurmonline/client/renderer/gui/WurmComponent;)Z",
                        () -> (proxy, method, args) -> {
                            Object added = method.invoke(proxy, args);
                            try {
                                if (args != null && args.length > 0 && args[0] != null) {
                                    String cn = args[0].getClass().getSimpleName();
                                    if (cn.toLowerCase().contains("mainmenu")
                                            || cn.toLowerCase().contains("optionswindow")) {
                                        if (transferActive() || !moveQueue.isEmpty()) {
                                            abortQueue("HUD " + cn);
                                        }
                                    } else {
                                        maybeSubmitRemovingItems(args[0]);
                                    }
                                }
                            } catch (Exception e) {
                                log("addComponent: " + e.getMessage());
                            }
                            return added;
                        }
                );
                log(">>> Hooked HeadsUpDisplay.addComponent(WurmComponent)Z");
            } catch (Exception e) {
                log(">>> Could not hook addComponent: " + e.getMessage());
            }

            try {
                HookManager.getInstance().registerHook(
                        "com.wurmonline.client.game.World",
                        "tick", "()V",
                        () -> (proxy, method, args) -> {
                            try { abortIfEscape(); } catch (Throwable ignored) {}
                            return method.invoke(proxy, args);
                        }
                );
                log(">>> Hooked World.tick for ESCAPE stop");
            } catch (Exception e) {
                log(">>> Could not hook World.tick: " + e.getMessage());
            }

            registerActionHook(
                    "com.wurmonline.client.renderer.gui.HeadsUpDisplay",
                    "sendAction",
                    "(Lcom/wurmonline/shared/constants/PlayerAction;J)V");
            registerActionHook(
                    "com.wurmonline.client.renderer.gui.HeadsUpDisplay",
                    "sendPopupAction",
                    "(Lcom/wurmonline/shared/constants/PlayerAction;)V");
            registerActionHook(
                    "com.wurmonline.client.game.World",
                    "sendLocalAction",
                    "(Lcom/wurmonline/shared/constants/PlayerAction;)V");
            registerActionHook(
                    "com.wurmonline.client.game.World",
                    "sendHoveredAction",
                    "(Lcom/wurmonline/shared/constants/PlayerAction;)V");
            registerActionHook(
                    "com.wurmonline.client.comm.SimpleServerConnectionClass",
                    "sendSingleAction",
                    "(JJLcom/wurmonline/shared/constants/PlayerAction;)V");
            registerActionHook(
                    "com.wurmonline.client.comm.SimpleServerConnectionClass",
                    "sendAction",
                    "(J[JLcom/wurmonline/shared/constants/PlayerAction;)V");

            hookSendMove("com.wurmonline.client.comm.ServerConnection");
            hookSendMove("com.wurmonline.client.comm.ServerConnectionClass");
            hookSendMove("com.wurmonline.client.comm.SimpleServerConnectionClass");

            hookBmlWindow("setTitle", "(Ljava/lang/String;)V");
            hookBmlWindow("<init>", "(Ljava/lang/String;Ljava/lang/String;)V");

            try {
                HookManager.getInstance().registerHook(
                        "com.wurmonline.client.renderer.gui.ChatPanelComponent",
                        "addText",
                        "(Ljava/lang/String;Ljava/lang/String;FFFZ)V",
                        () -> (proxy, method, args) -> {
                            try {
                                String context = (String) args[0];
                                String message = (String) args[1];
                                if (context != null && context.equalsIgnoreCase(":Event") && message != null) {
                                    onEventText(message);
                                }
                            } catch (Throwable ignored) {}
                            return method.invoke(proxy, args);
                        }
                );
                log(">>> Hooked ChatPanelComponent.addText :Event");
            } catch (Exception e) {
                log(">>> Could not hook ChatPanelComponent.addText: " + e.getMessage());
            }

            log(">>> Initialization complete");
        } catch (Throwable e) {
            log("Init error: " + e.getMessage());
        }
    }

    @Override
    public void preInit() {}
}
