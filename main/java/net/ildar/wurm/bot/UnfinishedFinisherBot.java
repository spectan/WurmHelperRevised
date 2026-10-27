package net.ildar.wurm.bot;

import com.wurmonline.client.comm.ServerConnectionListenerClass;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.GroundItemData;
import com.wurmonline.client.renderer.cell.GroundItemCellRenderable;
import com.wurmonline.client.renderer.gui.CreationFrame;
import com.wurmonline.client.renderer.gui.CreationItemTreeLisItem;
import com.wurmonline.client.renderer.gui.CreationWindow;
import com.wurmonline.client.renderer.gui.InventoryListComponent;
import com.wurmonline.client.renderer.gui.WurmTreeList;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.Utils;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.annotations.BotInfo;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;

@BotInfo(name = "Unfinished Finisher", description =
        "Finds unfinished items on the ground near the player, adds each to the crafting window and pulls the materials " +
        "it still needs out of bulk storage bins until it is finished",
        abbreviation = "uf")
public class UnfinishedFinisherBot extends Bot {
    private static final String[] UNFINISHED_PREFIXES = {"unfinished", "incomplete"};
    /** How long the same missing ingredient may stay unchanged before the item is given up on. */
    private static final long STUCK_MS = 60000;

    private volatile float distance = 4;
    private volatile int pullCount = 3;
    /** The bins to pull from; added from the console thread while the bot thread reads them. */
    private final List<InventoryListComponent> bins = new CopyOnWriteArrayList<>();
    /** Items that could not be finished, left alone for the rest of the run. */
    private final Set<Long> skipped = new HashSet<>();

    private CreationWindow creationWindow;
    private CreationFrame source;
    private CreationFrame target;
    private Method requestCreationList;
    private Method sendCreateAction;
    private ServerConnectionListenerClass connectionListener;

    public UnfinishedFinisherBot() {
        registerStaminaThresholdHandler(Inputs.s);
        registerInputHandler(Inputs.d, this::setDistance);
        registerInputHandler(Inputs.c, this::setPullCount);
        registerInputHandler(Inputs.sb, input -> addBin());
        registerInputHandler(Inputs.csb, input -> clearBins());

        staminaThreshold = 0.96f;
        timeout = 1000;
    }

    @Override
    void describeSettings(List<String> lines) {
        lines.add("Search distance: " + distance + " meters");
        lines.add("Pull at most " + pullCount + " of an ingredient at a time");
        lines.add("Bins: " + (bins.isEmpty() ? "none set" : bins.size() + " window(s)"));
    }

    @Override
    public void work() throws Exception {
        if (bins.isEmpty()) {
            Utils.consolePrint("No bin set! Point at the bulk storage bin window and use \"bot uf sb\", then \"bot uf on\"");
            deactivate();
            return;
        }
        creationWindow = WurmHelper.hud.getCreationWindow();
        source = Utils.getField(creationWindow, "source");
        target = Utils.getField(creationWindow, "target");
        requestCreationList = ReflectionUtil.getMethod(creationWindow.getClass(), "requestCreationList");
        requestCreationList.setAccessible(true);
        sendCreateAction = ReflectionUtil.getMethod(CreationWindow.class, "sendCreateAction");
        sendCreateAction.setAccessible(true);
        connectionListener = WurmHelper.hud.getWorld().getServerConnection().getServerConnectionListener();
        skipped.clear();

        while (isActive()) {
            waitOnPause();
            if (!hasStamina(staminaThreshold) || !isProgressZero()) {
                sleep(timeout);
                continue;
            }

            GroundItemData next = findNextUnfinished();
            if (next == null) {
                Utils.consolePrint("No more unfinished items within " + distance + " meters, done");
                deactivate();
                return;
            }
            Utils.consolePrint("Working on \"" + next.getName() + "\"");
            if (!addToCraftingWindow(next) || !finish(next))
                skipped.add(next.getId());
        }
    }

    /**
     * @return the nearest unfinished item on the ground that was not given up on, or null if there is none
     */
    private GroundItemData findNextUnfinished() throws Exception {
        Map<Long, GroundItemCellRenderable> groundItems = Utils.getField(connectionListener, "groundItems");
        if (groundItems == null)
            return null;
        float x = WurmHelper.hud.getWorld().getPlayerPosX();
        float y = WurmHelper.hud.getWorld().getPlayerPosY();
        for (int tries = 0; tries < 5; tries++) {
            try {
                GroundItemData best = null;
                float bestDistance = distance * distance;
                for (GroundItemCellRenderable cell : groundItems.values()) {
                    GroundItemData data = Utils.getField(cell, "item");
                    if (data == null || !isUnfinishedName(data.getName()) || skipped.contains(data.getId()))
                        continue;
                    float dx = data.getX() - x;
                    float dy = data.getY() - y;
                    float squared = dx * dx + dy * dy;
                    if (squared <= bestDistance) {
                        bestDistance = squared;
                        best = data;
                    }
                }
                return best;
            } catch (ConcurrentModificationException ignored) {
                // the ground items changed under us; look again
            }
        }
        return null;
    }

    private static boolean isUnfinishedName(String name) {
        if (name == null)
            return false;
        String lower = name.toLowerCase();
        for (String prefix : UNFINISHED_PREFIXES)
            if (lower.startsWith(prefix))
                return true;
        return false;
    }

    /**
     * Empty both crafting slots, then add the ground item to the source slot the way "Add to crafting window" does
     * @return false if the server did not put the item in the slot
     */
    private boolean addToCraftingWindow(GroundItemData item) throws Exception {
        waitOnPause();
        source.clearGroundItem();
        target.clearGroundItem();
        source.clearItemList();
        target.clearItemList();
        source.clearTexture();
        target.clearTexture();

        WurmHelper.hud.sendAction(PlayerAction.ADD_TO_CRAFTING_WINDOW, item.getId());
        for (int i = 0; i < 40 && source.getGroundItemId() != item.getId(); i++)
            sleep(100);
        if (source.getGroundItemId() != item.getId()) {
            Utils.consolePrint("Couldn't add \"" + item.getName() + "\" to the crafting window (too far away?), skipping it");
            return false;
        }
        requestCreationList.invoke(creationWindow);
        sleep(500);
        return true;
    }

    /**
     * Feed the item the ingredients it asks for until it is finished
     * @return false if it had to be given up on
     */
    private boolean finish(GroundItemData item) throws Exception {
        long listRequestedAt = System.currentTimeMillis();
        long createSentAt = 0;
        long lastChangeAt = System.currentTimeMillis();
        String lastName = null;
        int lastQuantity = -1;
        int emptyChecks = 0;
        int emptyPulls = 0;
        long busySince = 0;

        while (isActive()) {
            waitOnPause();
            if (!isStillUnfinished(item.getId())) {
                Utils.feedback("\"" + item.getName() + "\" is finished");
                return true;
            }
            if (source.getGroundItemId() != item.getId()) {
                Utils.consolePrint("The crafting window was changed, leaving \"" + item.getName() + "\"");
                return false;
            }

            long now = System.currentTimeMillis();
            // an action the server never answered would leave the window "busy" forever (the Crafter does the same)
            if (creationWindow.getActionInUse() > 0 && isProgressZero()) {
                if (busySince == 0)
                    busySince = now;
                else if (now - busySince > 20000) {
                    while (creationWindow.getActionInUse() > 0)
                        creationWindow.decreaseActionInUse();
                    busySince = 0;
                }
            } else {
                busySince = 0;
            }
            boolean idle = creationWindow.getActionInUse() == 0 && isProgressZero();
            // let the window catch up with the last attached ingredient
            if (idle && now - listRequestedAt > 2000 && now - createSentAt > 1500) {
                requestCreationList.invoke(creationWindow);
                listRequestedAt = now;
                sleep(500);
            }

            Need need = firstMissingIngredient();
            if (need == null) {
                if (++emptyChecks >= 8) {
                    Utils.consolePrint("Nothing more to add to \"" + item.getName() + "\", finish it by hand");
                    return false;
                }
                sleep(timeout);
                continue;
            }
            emptyChecks = 0;

            if (!need.name.equals(lastName) || need.quantity != lastQuantity) {
                lastName = need.name;
                lastQuantity = need.quantity;
                lastChangeAt = now;
            } else if (!hasStamina(staminaThreshold)) {
                lastChangeAt = now;
            } else if (now - lastChangeAt > STUCK_MS) {
                Utils.consolePrint("\"" + item.getName() + "\" is stuck on " + need.name + ", skipping it");
                return false;
            }

            int want = Math.min(need.quantity, pullCount);
            List<InventoryMetaItem> have = inventoryMatches(need.name);
            if (have.size() < want) {
                bins.removeIf(bin -> Utils.getRootItem(bin) == null); // closed windows
                if (bins.isEmpty()) {
                    Utils.consolePrint("All the bin windows were closed. Open them and use \"bot uf sb\" again");
                    deactivate();
                    return false;
                }
                if (!pullFromBins(need.name, want - have.size())) {
                    Utils.consolePrint("No bin has " + need.name + ", skipping \"" + item.getName() + "\"");
                    return false;
                }
            }
            sleep(300);
            have = inventoryMatches(need.name);
            if (have.isEmpty()) {
                // pulled something that doesn't look like the ingredient: don't keep emptying the bin
                if (++emptyPulls >= 3) {
                    Utils.consolePrint("Pulled items from the bin but none match \"" + need.name + "\" in your inventory");
                    deactivate();
                    return false;
                }
                sleep(timeout);
                continue;
            }
            emptyPulls = 0;
            // only as many as are still needed go in the target slot
            List<InventoryMetaItem> toUse = new ArrayList<>(have.subList(0, Math.min(want, have.size())));
            Utils.setField(target, "itemList", toUse);
            target.setTexture(toUse.get(0));

            // wait for the needs list to catch up with the last create before sending another
            if (idle && hasStamina(staminaThreshold) && now - createSentAt > 2500) {
                sendCreateAction.invoke(creationWindow);
                createSentAt = System.currentTimeMillis();
            }
            sleep(timeout);
        }
        return false;
    }

    /** True while the ground item is still there and still called unfinished. */
    private boolean isStillUnfinished(long id) throws Exception {
        Map<Long, GroundItemCellRenderable> groundItems = Utils.getField(connectionListener, "groundItems");
        if (groundItems == null)
            return false;
        GroundItemCellRenderable cell = groundItems.get(id);
        if (cell == null)
            return false;
        GroundItemData data = Utils.getField(cell, "item");
        return data != null && isUnfinishedName(data.getName());
    }

    private static final class Need {
        final String name;
        final int quantity;

        Need(String name, int quantity) {
            this.name = name;
            this.quantity = quantity;
        }
    }

    /**
     * Read the crafting window's "Needed items" list
     * @return the first ingredient still missing, or null if the list is empty or not loaded
     */
    private Need firstMissingIngredient() throws Exception {
        WurmTreeList<CreationItemTreeLisItem> unfinishedItemList = Utils.getField(creationWindow, "unfinishedItemList");
        if (unfinishedItemList == null)
            return null;
        List<?> lines = Utils.getField(unfinishedItemList, "lines");
        if (lines == null)
            return null;
        List<?> snapshot = new ArrayList<>(lines);
        for (Object line : snapshot) {
            CreationItemTreeLisItem listItem = Utils.getField(line, "item");
            String chance = Utils.getField(listItem, "chance");
            // a percentage is an action the item can do, a plain number is the amount of an ingredient it needs
            if (chance == null || chance.trim().isEmpty() || chance.contains("%"))
                continue;
            try {
                int quantity = Integer.parseInt(chance.trim());
                if (quantity > 0)
                    return new Need(Utils.getField(listItem, "name"), quantity);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /** The items in the player's inventory that are the named ingredient (ignoring case and a plural s). */
    private static List<InventoryMetaItem> inventoryMatches(String name) {
        String wanted = name.toLowerCase();
        return Utils.getInventoryItems(item -> {
            String base = Utils.normalizeBaseName(item).toLowerCase();
            return base.equals(wanted) || base.endsWith("s") && base.substring(0, base.length() - 1).equals(wanted);
        });
    }

    private static boolean matchesIngredient(InventoryMetaItem item, String name) {
        String wanted = name.toLowerCase();
        String base = Utils.normalizeBaseName(item).toLowerCase();
        if (base.endsWith("s"))
            base = base.substring(0, base.length() - 1);
        // the same test as inventoryMatches, so what is pulled is what is then found in the inventory
        return base.equals(wanted) || ItemSpec.getCanonicalName(item).equalsIgnoreCase(wanted);
    }

    /**
     * Move some of an ingredient from the first bin that has it into the inventory
     * @return false if no bin has it
     */
    private boolean pullFromBins(String name, int amount) throws Exception {
        long inventoryId = playerInventoryId();
        if (inventoryId < 0) {
            Utils.consolePrint("Couldn't find your inventory window");
            return false;
        }
        for (InventoryListComponent bin : bins) {
            List<InventoryMetaItem> entries = Utils.getInventoryItems(bin, item -> matchesIngredient(item, name));
            for (InventoryMetaItem entry : entries) {
                Matcher matcher = BulkItemGetterBot.quantityRegex.matcher(entry.getDisplayName());
                if (matcher.find()) {
                    int available = Integer.parseInt(matcher.group(1));
                    if (available > 0 && sendBulk(inventoryId, entry.getId(), Math.min(amount, available)))
                        return true;
                } else {
                    // an ordinary container: move the items one by one
                    List<Long> ids = new ArrayList<>();
                    for (InventoryMetaItem item : entries) {
                        if (ids.size() >= amount)
                            break;
                        if (!BulkItemGetterBot.quantityRegex.matcher(item.getDisplayName()).find())
                            ids.add(item.getId());
                    }
                    if (ids.isEmpty())
                        continue;
                    long[] moved = new long[ids.size()];
                    for (int i = 0; i < moved.length; i++)
                        moved[i] = ids.get(i);
                    WurmHelper.hud.getWorld().getServerConnection().sendMoveSomeItems(inventoryId, moved);
                    sleep(500);
                    return true;
                }
            }
        }
        return false;
    }

    /** One bulk entry -> N into the inventory, through the quantity question; false when the question never came. */
    private boolean sendBulk(long dest, long sourceId, int quantity) throws InterruptedException {
        synchronized (BulkItemGetterBot.bulkLock) {
            BulkItemGetterBot.currentMoveQuantity = quantity;
            BulkItemGetterBot.bulkBusy = false;
            BulkItemGetterBot.closeBMLWindow = true;
            WurmHelper.hud.getWorld().getServerConnection().sendMoveSomeItems(dest, new long[]{sourceId});

            int sleeps = 0;
            while (BulkItemGetterBot.closeBMLWindow && sleeps++ < 50)
                sleep(100);
            if (BulkItemGetterBot.closeBMLWindow) {
                BulkItemGetterBot.closeBMLWindow = false;
                BulkItemGetterBot.currentMoveQuantity = -1;
                Utils.consolePrint("Timed out after 5 seconds waiting for the bulk transfer question");
                return false;
            }
            // the flag was also cleared by an "already busy" reply, not by the question being answered
            if (BulkItemGetterBot.bulkBusy) {
                BulkItemGetterBot.currentMoveQuantity = -1;
                return false;
            }
        }
        sleep(500);
        return true;
    }

    /** The id of the "inventory" item of the player, or -1 if it can't be found. */
    private static long playerInventoryId() {
        InventoryMetaItem root = Utils.getRootItem(WurmHelper.hud.getInventoryWindow().getInventoryListComponent());
        if (root == null || root.getChildren() == null)
            return -1;
        return root.getChildren().stream()
                .filter(item -> Utils.normalizeBaseName(item).equals("inventory"))
                .findFirst()
                .map(InventoryMetaItem::getId)
                .orElse(-1L);
    }

    private void setDistance(String[] input) {
        Float value = parseFloatArg(input, Inputs.d, 0.5f, 100);
        if (value == null)
            return;
        distance = value;
        Utils.feedback("New search distance is " + distance + " meters");
    }

    private void setPullCount(String[] input) {
        Integer value = parseIntArg(input, Inputs.c, 1, 1000);
        if (value == null)
            return;
        pullCount = value;
        Utils.feedback("The bot will pull at most " + pullCount + " of an ingredient at a time");
    }

    private void addBin() {
        InventoryListComponent bin = Utils.getTargetInventory();
        if (bin == null) {
            Utils.consolePrint("Point at the window of the bulk storage bin and try again");
            return;
        }
        if (bin == WurmHelper.hud.getInventoryWindow().getInventoryListComponent()) {
            Utils.consolePrint("That is your own inventory. Point at the bulk storage bin window");
            return;
        }
        if (!bins.contains(bin))
            bins.add(bin);
        InventoryMetaItem root = Utils.getRootItem(bin);
        Utils.feedback("Pulling from " + (root != null ? root.getDisplayName() : "that window") + " (" + bins.size() + " bin window(s))");
    }

    private void clearBins() {
        bins.clear();
        Utils.feedback("The list of bins was cleared");
    }

    private enum Inputs implements Bot.InputKey {
        s("Stamina", "Set the stamina threshold (0 to 1, or a percentage). Player will not do any actions if his stamina is lower than specified threshold",
                "<threshold>"),
        d("Distance", "Set the distance (in meters) the bot should look around the player in search for unfinished items", "<distance>"),
        c("Pull Count", "Set how many of an ingredient the bot pulls from the bin at a time (it never pulls more than the item still needs)", "<count>"),
        sb("Set Bin", "Add the bulk storage bin window under the cursor as a place to pull ingredients from", ""),
        csb("Clear Bins", "Forget all the bins", "");

        private final KeyInfo keyInfo;

        Inputs(String fullName, String description, String usage) {
            keyInfo = new KeyInfo(fullName, description, usage);
        }

        @Override
        public KeyInfo keyInfo() {
            return keyInfo;
        }
    }
}
