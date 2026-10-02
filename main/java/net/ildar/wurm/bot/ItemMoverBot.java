package net.ildar.wurm.bot;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.gui.*;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.Utils;
import net.ildar.wurm.annotations.BotInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@BotInfo(name = "Item Mover", description =
        "Moves items from your inventory to the target destination.",
        abbreviation = "im")
public class ItemMoverBot extends Bot {
    private final Set <String> itemNames = ConcurrentHashMap.newKeySet();
    private volatile TargetType targetType;
    private final Map <String, Float> itemMaximumWeights = new ConcurrentHashMap<>();
    private volatile long target;
    private volatile String targetName;
    private volatile InventoryListComponent targetComponent;
    private volatile String containerName;
    private volatile int containerVolume = 100;
    private volatile boolean notMoveRares = true;
    private volatile String lastItemName;
    private volatile boolean onlyFirstLevelItems = true;

    @Override
    public void work() throws Exception{
        while (isActive()) {
            waitOnPause();
            if (itemNames.size() > 0 && (target != 0 || targetComponent != null)) {
                List<InventoryMetaItem> invItems;
                if (onlyFirstLevelItems)
                    invItems = Utils.getFirstLevelItems();
                else
                    invItems = Utils.getSelectedItems(WurmHelper.hud.getInventoryWindow().getInventoryListComponent(), true, true);
                List<InventoryMetaItem> itemsToMove = new ArrayList<>();
                for (InventoryMetaItem invItem : invItems) {
                    boolean notRare = invItem.getRarity() == 0;
                    for (String itemName : itemNames) {
                        float maxWeight = itemMaximumWeights.getOrDefault(itemName, 0f);
                        if (invItem.getBaseName().contains(itemName)
                                && (maxWeight == 0 || invItem.getWeight() <= maxWeight)
                                && (!notMoveRares || notRare)) {
                            itemsToMove.add(invItem);
                            break;
                        }
                    }
                }
                if (itemsToMove.size() > 0) {
                    long [] sources = Utils.getItemIds(itemsToMove);
                    switch (targetType) {
                        case Item:
                            WurmHelper.hud.getWorld().getServerConnection().sendMoveSomeItems(target, sources);
                            break;
                        case ContainerRoot:
                            InventoryMetaItem rootItem = Utils.getRootItem(targetComponent);
                            if (rootItem != null)
                                WurmHelper.hud.getWorld().getServerConnection().sendMoveSomeItems(rootItem.getId(), sources);
                            else
                                Utils.consolePrint("Unable to move items to the target container");
                            break;
                        case Containers:
                            List<InventoryMetaItem> containers = Utils.getInventoryItems(targetComponent, containerName);
                            if (containers != null && containers.size() > 0) {
                                for (InventoryMetaItem container : containers)
                                    if (container.getChildren() != null && container.getChildren().size() < containerVolume) {
                                        int quantityToMove = Math.min(containerVolume - (container.getChildren() != null ? container.getChildren().size() : 0), sources.length);
                                        WurmHelper.hud.getWorld().getServerConnection().sendMoveSomeItems(
                                                container.getId(), Arrays.copyOfRange(sources, 0, quantityToMove));
                                        sources = Arrays.copyOfRange(sources, quantityToMove, sources.length);
                                        if (sources.length == 0)
                                            break;
                                    }
                                if (sources.length > 0)
                                    Utils.consolePrint("All containers are full!");
                            } else
                                Utils.consolePrint("Didn't find any \"" + containerName + "\" containers inside target container");
                            break;
                    }
                }
                sleep(timeout);
            } else
                sleep(1000);
        }
    }

    public ItemMoverBot() {
        timeout = 15000;
        registerInputHandler(ItemMoverBot.InputKey.clear, input -> clearItemList());
        registerInputHandler(ItemMoverBot.InputKey.st, input -> setTargetItem());
        registerInputHandler(ItemMoverBot.InputKey.stid, this::setTargetById);
        registerInputHandler(ItemMoverBot.InputKey.str, input -> setTargetContainerRoot());
        registerInputHandler(ItemMoverBot.InputKey.stc, this::setTargetAsContainer);
        registerInputHandler(ItemMoverBot.InputKey.stcn, this::setTargetContainerVolume);
        registerInputHandler(ItemMoverBot.InputKey.a, this::addNewItemName);
        registerInputHandler(ItemMoverBot.InputKey.sw, this::setMaximumItemWeight);
        registerInputHandler(ItemMoverBot.InputKey.r, input -> toggleRareItemsMoving());
        registerInputHandler(ItemMoverBot.InputKey.fl, input -> toggleFirstLevelItemMoving());
    }

    private void toggleFirstLevelItemMoving() {
        onlyFirstLevelItems = !onlyFirstLevelItems;
        if (onlyFirstLevelItems)
            Utils.feedback("Only first level items of your inventory will be moved");
        else
            Utils.feedback("All items from your inventory that match added names will be moved");
    }

    private void setTargetById(String []input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(ItemMoverBot.InputKey.stid);
            return;
        }
        long id;
        try {
            id = Long.parseLong(input[0]);
        } catch (NumberFormatException e) {
            Utils.consolePrint("`" + input[0] + "` is not a valid item id");
            printInputKeyUsageString(ItemMoverBot.InputKey.stid);
            return;
        }
        final long targetId = id;
        String name = null;
        try {
            List<InventoryMetaItem> found = Utils.getInventoryItems(item -> item.getId() == targetId);
            if (found != null && found.size() > 0)
                name = found.get(0).getDisplayName();
        } catch (Exception ignored) {
            // the name is only for the message
        }
        target = id;
        targetName = name != null ? "\"" + name + "\" (id " + id + ")" : "item with id " + id;
        targetType = TargetType.Item;
        Utils.feedback("New target is " + targetName);
    }

    private void toggleRareItemsMoving() {
        notMoveRares = !notMoveRares;
        if (notMoveRares)
            Utils.feedback("Rare+ items will not be moved");
        else
            Utils.feedback("Rare+ items will be moved too");
    }

    private void setMaximumItemWeight(String []input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(ItemMoverBot.InputKey.sw);
            return;
        }

        String itemName = lastItemName;
        if (itemName == null) {
            Utils.consolePrint("Add an item first!");
            return;
        }
        Float weight = parseFloatArg(input, ItemMoverBot.InputKey.sw, 0, 100000);
        if (weight == null)
            return;
        itemMaximumWeights.put(itemName, weight);
        if (weight == 0)
            Utils.consolePrint("Item - " + itemName + " will be moved with any weight");
        else
            Utils.consolePrint("Item - " + itemName + " will be moved only with weight up to " + weight);
    }

    private void addNewItemName(String []input) {
        List<String> names = parseNameList(input);
        if (names.isEmpty()) {
            printInputKeyUsageString(ItemMoverBot.InputKey.a);
            return;
        }
        for (String name : names) {
            itemNames.add(name);
            itemMaximumWeights.put(name, 0f);
            lastItemName = name;
        }
        Utils.consolePrint("Current item set - " + itemNames.toString());
    }

    private void setTargetContainerVolume(String []input) {
        Integer volume = parseIntArg(input, ItemMoverBot.InputKey.stcn, 1, 100000);
        if (volume == null)
            return;
        containerVolume = volume;
        Utils.consolePrint("Maximum number of items inside containers was set to " + containerVolume);
    }

    private void setTargetAsContainer(String []input) {
        String newContainer = joinArgs(input);
        if (newContainer == null) {
            printInputKeyUsageString(ItemMoverBot.InputKey.stc);
            return;
        }

        WurmComponent wurmComponent = Utils.getTargetComponent(c -> c instanceof ItemListWindow || c instanceof InventoryWindow);
        if (wurmComponent == null) {
            Utils.consolePrint("Didn't find an opened container");
            return;
        }
        try {
            InventoryListComponent ilc = Utils.getField(wurmComponent, "component");
            InventoryMetaItem rootItem = Utils.getRootItem(ilc);
            targetComponent = ilc;
            this.containerName = newContainer;
            targetType = TargetType.Containers;
            Utils.feedback("Items will go to \"" + containerName + "\" containers inside \""
                    + (rootItem != null ? rootItem.getBaseName() : "the target window") + "\"");
        } catch(Exception e) {
            Utils.consolePrint("Error on getting container information");
            e.printStackTrace();
        }
    }

    private void setTargetContainerRoot() {
        WurmComponent inventoryComponent = Utils.getTargetComponent(c -> c instanceof ItemListWindow || c instanceof InventoryWindow);
        if (inventoryComponent == null) {
            Utils.consolePrint("Didn't find an opened container");
            return;
        }
        InventoryListComponent ilc;
        try {
            ilc = Utils.getField(inventoryComponent, "component");
        } catch(Exception e) {
            Utils.consolePrint("Error on getting container information");
            e.printStackTrace();
            return;
        }
        InventoryMetaItem rootItem = Utils.getRootItem(ilc);
        if (rootItem!=null) {
            Utils.feedback("Items will go to the \"" + rootItem.getBaseName() + "\"");
            targetType = TargetType.ContainerRoot;
            targetComponent = ilc;
        } else {
            Utils.consolePrint("Failed on configuring the target container");
        }
    }
    private void setTargetItem() {
        try {
            int x = WurmHelper.hud.getWorld().getClient().getXMouse();
            int y = WurmHelper.hud.getWorld().getClient().getYMouse();
            long [] targets = WurmHelper.hud.getCommandTargetsFrom(x,y);
            if (targets != null && targets.length > 0) {
                target = targets[0];
                targetName = describeHoveredTarget(targets[0], x, y);
                targetType = TargetType.Item;
                Utils.feedback("New target is " + targetName);
            } else
                Utils.consolePrint("Can't find the target");
        }catch (Exception e) {
            Utils.consolePrint(this.getClass().getSimpleName() + " has encountered an error while setting target - " + e.getMessage());
            Utils.consolePrint( e.toString());
        }
    }

    /**
     * A readable name for the object under the mouse with the given id: the inventory item name, or the hovered
     * world object name. Falls back to the id
     */
    static String describeHoveredTarget(long id, int x, int y) {
        try {
            InventoryListComponent ilc = Utils.getInventoryAtPoint(x, y);
            if (ilc != null) {
                for (InventoryMetaItem item : Utils.getInventoryItemsAtPoint(ilc, x, y))
                    if (item.getId() == id)
                        return "\"" + item.getDisplayName() + "\"";
            } else {
                PickableUnit hovered = WurmHelper.hud.getWorld().getCurrentHoveredObject();
                if (hovered != null && hovered.getId() == id)
                    return "\"" + hovered.getHoverName() + "\"";
            }
        } catch (Exception ignored) {
            // the name is only for messages
        }
        return "item with id " + id;
    }

    @Override
    void describeSettings(List<String> lines) {
        StringBuilder items = new StringBuilder();
        for (String name : itemNames) {
            if (items.length() > 0)
                items.append(", ");
            items.append(name);
            Float weight = itemMaximumWeights.get(name);
            if (weight != null && weight > 0)
                items.append(" (max weight ").append(weight).append(")");
        }
        lines.add("Items: " + (items.length() > 0 ? items.toString() : "none"));
        TargetType type = targetType;
        String targetLine;
        if (type == null)
            targetLine = "not set";
        else {
            switch (type) {
                case Item:
                    targetLine = targetName != null ? targetName : "item with id " + target;
                    break;
                case ContainerRoot: {
                    InventoryListComponent ilc = targetComponent;
                    InventoryMetaItem root = ilc != null ? Utils.getRootItem(ilc) : null;
                    targetLine = root != null ? "\"" + root.getBaseName() + "\"" : "a closed container window";
                    break;
                }
                default: {
                    InventoryListComponent ilc = targetComponent;
                    InventoryMetaItem root = ilc != null ? Utils.getRootItem(ilc) : null;
                    targetLine = "\"" + containerName + "\" containers inside "
                            + (root != null ? "\"" + root.getBaseName() + "\"" : "a closed container window");
                    break;
                }
            }
        }
        lines.add("Target: " + targetLine);
        lines.add("Items per container: " + containerVolume);
        lines.add("Move rare items: " + onOff(!notMoveRares));
        lines.add("First level items only: " + onOff(onlyFirstLevelItems));
    }

    private void clearItemList(){
        itemNames.clear();
        itemMaximumWeights.clear();
        lastItemName = null;
        Utils.feedback("Item list cleared");
    }

    private enum InputKey implements Bot.InputKey {
        clear("Clear Items", "Clear the item name list", ""),
        st("Set Target", "Set the target item(under mouse pointer). Items from your inventory will be moved inside this item if it is a container or next to it otherwise.", ""),
        stid("Target By ID", "Set the id of target item. Items from your inventory will be moved inside this item if it is a container or next to it otherwise.", "<id>"),
        str("Target Container Root", "Set the target container(under mouse pointer). Items from your inventory will be moved to the root directory of that container.", ""),
        stcn("Container Volume", "Set the number of items to put inside each container. Use with \"stc\" key", "<count>"),
        stc("Target Container", "Set the target container(under mouse pointer) with another containers inside. " +
                "Items from your inventory will be moved to containers with provided name. " +
                "Bot will try to put 100 items inside each container. But you can change this value using \"" + stcn.name() + "\" key.", "<container name>"),
        sw("Max Weight", "Set the maximum weight for item to be moved, 0 for any weight. Affects the last added item name.", "<weight>"),
        a("Add Item", "Add item names to move to the targets. Separate several names with commas. " +
                "The maximum weight of moved item can be configured with \"" + sw.name() + "\" key", "<item name>"),
        r("Toggle Rares", "Toggle the moving of rare items. Disabled by default.", ""),
        fl("First Level Only", "Toggle the moving of only first level items of your inventory. " +
                "Items that match added keywords but lying inside a group or a container will not be touched. " +
                "Enabled by default", "");

        private final KeyInfo keyInfo;

        InputKey(String fullName, String description, String usage) {
            keyInfo = new KeyInfo(fullName, description, usage);
        }

        @Override
        public KeyInfo keyInfo() {
            return keyInfo;
        }
    }

    enum TargetType {
        Item,
        ContainerRoot,
        Containers
    }
}
