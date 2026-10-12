package net.ildar.wurm.bot;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.gui.*;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.Utils;
import net.ildar.wurm.annotations.BotInfo;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@BotInfo(name = "Crafter", description =
        "Automatically does crafting operations using items from crafting window. " +
        "New crafting operations are not starting until an action queue becomes empty. This behaviour can be disabled. ",
        abbreviation = "c")
public class CrafterBot extends Bot {
    private volatile boolean repairInstrument = true;
    private volatile String targetName;
    private volatile String sourceName;
    private Comparator<InventoryMetaItem> weightComparator = Comparator.comparingDouble(InventoryMetaItem::getWeight);
    private volatile int targetX;
    private volatile int targetY;
    private volatile int sourceX;
    private volatile int sourceY;
    private volatile boolean noSort = true;
    private volatile boolean combineTargets;
    private volatile boolean combineSources;
    private volatile long combineTimeout;
    private volatile boolean craftUnfinishedItemMode;
    private volatile boolean withoutActionsInUse;
    private volatile long lastClick;
    private volatile boolean singleSourceItemMode;

    public CrafterBot() {
        registerInputHandler(CrafterBot.InputKey.r, input -> toggleRepairInstrument());
        registerInputHandler(CrafterBot.InputKey.st, this::setTargetName);
        registerInputHandler(CrafterBot.InputKey.stxy, input -> setTargetXY());
        registerInputHandler(CrafterBot.InputKey.ss, this::setSourceName);
        registerInputHandler(CrafterBot.InputKey.ssxy, input -> setSourceXY());
        registerInputHandler(CrafterBot.InputKey.nosort, input -> toggleSorting());
        registerInputHandler(CrafterBot.InputKey.ct, input -> toggleTargetsCombining());
        registerInputHandler(CrafterBot.InputKey.cs, input -> toggleSourcesCombining());
        registerInputHandler(CrafterBot.InputKey.ctimeout, this::setCombineTimeout);
        registerStaminaThresholdHandler(CrafterBot.InputKey.s);
        registerInputHandler(CrafterBot.InputKey.u, input -> toggleUnfinishedMode());
        registerInputHandler(CrafterBot.InputKey.ssid, this::addSourceByItemId);
        registerInputHandler(CrafterBot.InputKey.an, this::setActionNumber);
        registerInputHandler(CrafterBot.InputKey.noan, input -> toggleActionNumberChecks());
        registerInputHandler(CrafterBot.InputKey.s1s, input -> toggleSingleSourceItemMode());
        staminaThreshold = 0.96f;
    }

    @Override
    void describeSettings(List<String> lines) {
        String target;
        if (craftUnfinishedItemMode)
            target = "the top of the \"Needed items\" list (unfinished mode)";
        else if (targetX != 0 && targetY != 0)
            target = "items at screen point X " + targetX + " Y " + targetY;
        else
            target = targetName != null ? targetName : "not set (kept from the crafting window)";
        lines.add("Target: " + target);
        String source;
        if (sourceX != 0 && sourceY != 0)
            source = "items at screen point X " + sourceX + " Y " + sourceY;
        else
            source = sourceName != null ? sourceName : "not set (kept from the crafting window)";
        lines.add("Source: " + source);
        lines.add("Repair: " + onOff(repairInstrument));
        lines.add("Sort by weight: " + onOff(!noSort));
        lines.add("Combine targets: " + onOff(combineTargets) + ", combine sources: " + onOff(combineSources)
                + (combineTimeout > 0 ? " (every " + combineTimeout + " ms)" : ""));
        lines.add("Single source item: " + onOff(singleSourceItemMode));
        lines.add("Check action queue: " + onOff(!withoutActionsInUse));
    }

    /**
     * @return the names of the items at the given screen point of the inventory, for messages
     */
    private static String describeItemsAtPoint(int x, int y) {
        try {
            List<InventoryMetaItem> items = Utils.getInventoryItemsAtPoint(x, y);
            if (items == null || items.isEmpty())
                return "no items there yet";
            String name = items.get(0).getDisplayName();
            return items.size() == 1 ? name : name + " and " + (items.size() - 1) + " more";
        } catch (Exception e) {
            return "unknown items";
        }
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    public void work() throws Exception{
        long lastSourceCombineTime = 0;
        long lastTargetCombineTime = 0;

        CreationWindow creationWindow = WurmHelper.hud.getCreationWindow();
        Method sendCreateAction = ReflectionUtil.getMethod(CreationWindow.class, "sendCreateAction");
        sendCreateAction.setAccessible(true);
        Method requestCreationList = ReflectionUtil.getMethod(creationWindow.getClass(), "requestCreationList");
        requestCreationList.setAccessible(true);
        CreationFrame source = Utils.getField(creationWindow, "source");
        CreationFrame target = Utils.getField(creationWindow, "target");
        registerEventProcessors();
        while (isActive()) {
            waitOnPause();

            if (repairInstrument) {
                // snapshot: the client thread may change the live list
                List<InventoryMetaItem> liveItems = Utils.getField(source, "itemList");
                List<InventoryMetaItem> sourceItems = liveItems != null ? new ArrayList<>(liveItems) : null;
                if (sourceItems != null && sourceItems.size() > 0 && sourceItems.get(0).getDamage() > 10)
                    WurmHelper.hud.sendAction(PlayerAction.REPAIR, sourceItems.get(0).getId());
            }

            if (craftUnfinishedItemMode) {
                WurmTreeList<CreationItemTreeLisItem> unfinishedItemList = Utils.getField(creationWindow, "unfinishedItemList");
                if (unfinishedItemList != null) {
                    List lines = Utils.getField(unfinishedItemList, "lines");
                    if (lines != null && lines.size() > 0) {
                        targetName = null;
                        //noinspection ForLoopReplaceableByForEach
                        for (int i = 0; i < lines.size(); i++) {
                            CreationItemTreeLisItem listItem = Utils.getField(lines.get(i), "item");
                            String chance = Utils.getField(listItem, "chance");
                            if (chance != null && !chance.equals("") && !chance.contains("%")) {
                                targetName = Utils.getField(listItem, "name");
                                break;
                            }
                        }
                    } else {
                        requestCreationList.invoke(creationWindow);
                    }
                }
            }
            if (targetName != null && targetName.length() > 0) {
                List<InventoryMetaItem> targetItems = Utils.getInventoryItems(targetName).stream().filter(item -> Utils.normalizeBaseName(item).equals(targetName)).collect(Collectors.toList());
                if (!noSort)
                    targetItems.sort(weightComparator);
                Utils.setField(target, "itemList", targetItems);
                if (targetItems.size() > 0)
                    target.setTexture(targetItems.get(0));
            }
            if (sourceName != null && sourceName.length() > 0) {
                List<InventoryMetaItem> sourceItems = Utils.getInventoryItems(sourceName).stream().filter(item -> Utils.normalizeBaseName(item).equals(sourceName)).collect(Collectors.toList());
                if (!noSort)
                    sourceItems.sort(weightComparator);
                if (singleSourceItemMode && sourceItems.size() > 0) {
                    List<InventoryMetaItem> singleSourceItemList = new ArrayList<>();
                    singleSourceItemList.add(sourceItems.get(0));
                    Utils.setField(source, "itemList", singleSourceItemList);
                } else {
                    Utils.setField(source, "itemList", sourceItems);
                }
                if (sourceItems.size() > 0)
                    source.setTexture(sourceItems.get(0));
            }

            if (targetX != 0 && targetY != 0) {
                List<InventoryMetaItem> items = Utils.getInventoryItemsAtPoint(targetX, targetY);
                if (items != null && items.size() > 0)
                    Utils.setField(target, "itemList", items);
            }

            if (sourceX != 0 && sourceY != 0) {
                List<InventoryMetaItem> items = Utils.getInventoryItemsAtPoint(sourceX, sourceY);
                if (items != null && items.size() > 0)
                    Utils.setField(source, "itemList", items);
            }

            if (combineTargets && (Math.abs(lastTargetCombineTime - System.currentTimeMillis()) > combineTimeout)) {
                lastTargetCombineTime = System.currentTimeMillis();
                List<InventoryMetaItem> targetItems = Utils.getField(target, "itemList");
                if (targetItems != null && targetItems.size() > 1) {
                    long[] targets = Utils.getItemIds(targetItems);
                    creationWindow.sendCombineAction(targets[0], targets, target);
                    requestCreationList.invoke(creationWindow);
                }
            }

            if (combineSources && (Math.abs(lastSourceCombineTime - System.currentTimeMillis()) > combineTimeout)) {
                lastSourceCombineTime = System.currentTimeMillis();
                List<InventoryMetaItem> sourceItems = Utils.getField(source, "itemList");
                if (sourceItems != null && sourceItems.size() > 1) {
                    long[] sources = Utils.getItemIds(sourceItems);
                    creationWindow.sendCombineAction(sources[0], sources, source);
                    requestCreationList.invoke(creationWindow);
                }
            }

            if (source != null && target != null && (creationWindow.getActionInUse() == 0 || withoutActionsInUse) && canDoWork(staminaThreshold)) {
                sendCreateAction.invoke(creationWindow);
            }
            if (source != null && target != null
                    && hasStamina(staminaThreshold)
                    && Math.abs(lastClick - System.currentTimeMillis()) > 20000){
                requestCreationList.invoke(creationWindow);
                while (creationWindow.getActionInUse() > 0)
                    creationWindow.decreaseActionInUse();
                lastClick = System.currentTimeMillis();
            }
            sleep(timeout);
        }
    }

    private void registerEventProcessors() {
        registerEventProcessor(message -> message.contains("You create")
                || message.contains("you will start creating")
                || message.contains("You attach")
                || message.contains("you will start continuing"), () -> lastClick = System.currentTimeMillis());
    }

    private void toggleActionNumberChecks() {
        withoutActionsInUse = !withoutActionsInUse;
        if (withoutActionsInUse) {
            Utils.feedback(this.getClass().getSimpleName() + " will NOT check action queue");
        } else {
            Utils.feedback(this.getClass().getSimpleName() + " will check action queue");
        }
    }

    private void toggleSingleSourceItemMode() {
        singleSourceItemMode = !singleSourceItemMode;
        Utils.feedback("Single source item mode is " + onOff(singleSourceItemMode));
    }

    private void setActionNumber(String input[]) {
        Integer num = parseIntArg(input, CrafterBot.InputKey.an, 1, 100);
        if (num == null)
            return;
        try {
            CreationWindow creationWindow = WurmHelper.hud.getCreationWindow();
            Utils.setField(creationWindow, "selectedActions", num);
            Utils.feedback("The crafting window will do " + num + " action(s) per click");
        } catch (ReflectiveOperationException e) {
            Utils.consolePrint("Can't set an action number");
        }
    }

    private void addSourceByItemId(String input[]) {
        if(input == null || input.length != 1) {
            printInputKeyUsageString(CrafterBot.InputKey.ssid);
            return;
        }
        try {
            long id = Long.parseLong(input[0]);
            InventoryListComponent ilc = WurmHelper.hud.getInventoryWindow().getInventoryListComponent();
            List <InventoryMetaItem> allItems = Utils.getSelectedItems(ilc, true, true);
            InventoryMetaItem sourceItem = allItems.stream().filter(item->item.getId() == id).findAny().orElse(null);
            if (sourceItem == null) {
                Utils.consolePrint("Can't find item with id " + id);
                return;
            }
            CreationWindow creationWindow = WurmHelper.hud.getCreationWindow();
            CreationFrame source = Utils.getField(creationWindow, "source");
            List<InventoryMetaItem> newSourceList = new ArrayList<>();
            newSourceList.add(sourceItem);
            Utils.setField(source, "itemList", newSourceList);
            Method requestCreationList = ReflectionUtil.getMethod(creationWindow.getClass(), "requestCreationList");
            requestCreationList.setAccessible(true);
            requestCreationList.invoke(creationWindow);
            Utils.feedback("The source slot was set to \"" + sourceItem.getDisplayName() + "\" (id " + id + ")");
        } catch (NumberFormatException e) {
            Utils.consolePrint("Can't parse the item id \"" + input[0] + "\": not a number");
        } catch (Exception e) {
            Utils.consolePrint("Can't set new source item with provided id");
        }
    }

    private void toggleUnfinishedMode() {
        if (!craftUnfinishedItemMode) {
            targetName = null;
            targetX = targetY = 0;
            craftUnfinishedItemMode = true;
            Utils.feedback("The unfinished item crafting mode is on");
        } else {
            craftUnfinishedItemMode = false;
            Utils.feedback("The unfinished item crafting mode is off");
        }
    }

    private void setCombineTimeout(String input[]) {
        Integer value = parseIntArg(input, CrafterBot.InputKey.ctimeout, 0, 3600000);
        if (value != null)
            setCombineTimeout(value);
    }

    private void setCombineTimeout(long timeout) {
        this.combineTimeout = timeout;
        Utils.consolePrint("Timeout for item combining is " + combineTimeout);
    }

    private void toggleSourcesCombining() {
        combineSources = !combineSources;
        if (combineSources) {
            Utils.feedback("Source combining is on");
            if (combineTimeout == 0)
                setCombineTimeout(10000);
        } else
            Utils.feedback("Source combining is off");
    }

    private void toggleTargetsCombining() {
        combineTargets = !combineTargets;
        if (combineTargets) {
            Utils.feedback("Target combining is on");
            if (combineTimeout == 0)
                setCombineTimeout(10000);
        } else
            Utils.feedback("Target combining is off");
    }

    private void toggleSorting() {
        noSort = !noSort;
        if (noSort)
            Utils.feedback(this.getClass().getSimpleName() + " will NOT sort the targets and sources by weight");
        else
            Utils.feedback(this.getClass().getSimpleName() + " will sort the targets and sources by weight");
    }

    private void setTargetName(String input[]) {
        String target = joinArgs(input);
        if (target == null) {
            printInputKeyUsageString(CrafterBot.InputKey.st);
            return;
        }
        setTargetName(target);
    }

    private void setSourceName(String input[]) {
        String source = joinArgs(input);
        if (source == null) {
            printInputKeyUsageString(CrafterBot.InputKey.ss);
            return;
        }
        setSourceName(source);
    }

    private void toggleRepairInstrument(){
        repairInstrument = !repairInstrument;
        Utils.feedback("Instrument auto repairing is " + onOff(repairInstrument));
    }

    private void setTargetName(String t) {
        if (t != null && t.length() > 0) {
            targetName = t;
            // the fixed point would override the name
            targetX = targetY = 0;
            Utils.feedback("New target item name is - " + t);
        } else
            Utils.consolePrint("Can't set empty target item name");
    }

    private void setSourceName(String t) {
        if (t != null && t.length() > 0) {
            sourceName = t;
            sourceX = sourceY = 0;
            Utils.feedback("New source item name is - " + t);
        } else
            Utils.consolePrint("Can't set empty source item name");
    }

    private void setTargetXY() {
        targetX = WurmHelper.hud.getWorld().getClient().getXMouse();
        targetY = WurmHelper.hud.getWorld().getClient().getYMouse();
        targetName = null;
        Utils.feedback("The target was set to X - " + targetX + " Y - " + targetY + " (" + describeItemsAtPoint(targetX, targetY) + ")");
    }

    private void setSourceXY() {
        sourceX = WurmHelper.hud.getWorld().getClient().getXMouse();
        sourceY = WurmHelper.hud.getWorld().getClient().getYMouse();
        sourceName = null;
        Utils.feedback("The source was set to X - " + sourceX + " Y - " + sourceY + " (" + describeItemsAtPoint(sourceX, sourceY) + ")");
    }

    private enum InputKey implements Bot.InputKey {
        r("Repair", "Toggle the source item repairing (on the left side of crafting window). " +
                "Usually it is an instrument. When the source item gets 10% damage player will repair it automatically", ""),
        st("Set Target", "Set the target item name. " + CrafterBot.class.getSimpleName()+ " will place item with provided name from your inventory to the target slot(on the right side of crafting window). The name may contain spaces",
                "<item name>"),
        stxy("Target XY", "Set the target item fixed point. " + CrafterBot.class.getSimpleName()+ " will place item from that fixed point of screen to the target item slot(on the right side of crafting window)", ""),
        ss("Set Source", "Set the source item name. " + CrafterBot.class.getSimpleName()+ " will place item with provided name from your inventory to the source slot(on the left side of crafting window). The name may contain spaces",
                "<item name>"),
        ssxy("Source XY", "Set the source item fixed point. " + CrafterBot.class.getSimpleName()+ " will place item from that fixed point of screen to the source item slot(on the left side of crafting window)", ""),
        nosort("Toggle Sorting", "Sorting of source and target items by weight is disabled by default. This key toggles sorting on and off", ""),
        cs("Combine Sources", "Combine source items(on the left side of crafting window)", ""),
        ct("Combine Targets", "Combine target items(on the right side of crafting window)", ""),
        ctimeout("Combine Timeout", "Set the timeout for item combining in milliseconds", "<milliseconds>"),
        s("Stamina", "Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold",
                "<threshold>"),
        u("Unfinished Mode", "Toggle the special mode in which " + CrafterBot.class.getSimpleName() + " will place an item to the target item slot which is at the top of \"Needed items\" list", ""),
        ssid("Source By ID", "Set an item with provided id to the source slot(on the left side of crafting window)", "<item id>"),
        an("Clicks", "Set an action number. The number of crafting operations the player will do on each click on continue/create button", "<clicks>"),
        noan("Toggle Action Check", "Toggles the check for action queue state before the start of each crafting operation. " +
                "By default " + CrafterBot.class.getSimpleName() + " will check action queue and start crafting operations only when it is empty", ""),
        s1s("Single Source", "Toggles the setting of single item to source slot of crafting window", "");

        private final KeyInfo keyInfo;

        InputKey(String fullName, String description, String usage) {
            keyInfo = new KeyInfo(fullName, description, usage);
        }

        @Override
        public KeyInfo keyInfo() {
            return keyInfo;
        }
    }
}
