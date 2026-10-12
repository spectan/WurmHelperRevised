package net.ildar.wurm.bot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.wurmonline.client.comm.ServerConnectionListenerClass;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.GroundItemData;
import com.wurmonline.client.renderer.cell.GroundItemCellRenderable;
import com.wurmonline.client.renderer.gui.InventoryListComponent;
import com.wurmonline.client.renderer.gui.InventoryWindow;
import com.wurmonline.client.renderer.gui.ItemListWindow;
import com.wurmonline.client.renderer.gui.WurmComponent;
import com.wurmonline.shared.constants.PlayerAction;

import net.ildar.wurm.Utils;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.annotations.BotInfo;

@BotInfo(name = "Pile Collector", description =
        "Collects piles of items to bulk containers. Default name for target items is \"dirt\"",
        abbreviation = "pc")
public class PileCollector extends Bot {
    private final float MAX_DISTANCE = 4;
    private Set<Long> openedPiles = new HashSet<>();
    private volatile InventoryListComponent targetLc;
    private volatile String containerName = "large crate";
    private volatile int containerCapacity = 300;
    private volatile String targetItemName = "dirt";
    private volatile float minQuality = 0;
    private volatile String customContainer = null;
    
    // items picked up from ground that failed predicates and were dropped back to ground
    // (cleared from the console thread when the filters change)
    private final Set<Long> ignoredItems = ConcurrentHashMap.newKeySet();

    public PileCollector() {
        timeout = 500;
        registerInputHandler(PileCollector.InputKey.stn, this::setTargetName);
        registerInputHandler(PileCollector.InputKey.st, this::setTargetInventoryName);
        registerInputHandler(PileCollector.InputKey.stcc, this::setContainerCapacity);
        registerInputHandler(PileCollector.InputKey.mq, this::setMinQuality);
        registerInputHandler(PileCollector.InputKey.cc, this::setCustomContainer);
    }

    @Override
    protected void work() throws Exception {
        ServerConnectionListenerClass sscc = WurmHelper.hud.getWorld().getServerConnection().getServerConnectionListener();
        Set<Long> pickedUpItems = new HashSet<>();
        openedPiles.clear();
        while (isActive()) {
            waitOnPause();
            Map<Long, GroundItemCellRenderable> groundItemsMap = Utils.getField(sscc, "groundItems");
            List<GroundItemCellRenderable> groundItems;
            try {
                // the map is modified by the client thread, so take a snapshot first
                groundItems = new ArrayList<>(groundItemsMap.values());
            } catch (ConcurrentModificationException e) {
                groundItems = Collections.emptyList();
            }
            float x = WurmHelper.hud.getWorld().getPlayerPosX();
            float y = WurmHelper.hud.getWorld().getPlayerPosY();
            if (groundItems.size() > 0 && targetLc != null) {
                try {
                    for (GroundItemCellRenderable groundItem : groundItems) {
                        GroundItemData groundItemData = Utils.getField(groundItem, "item");
                        float itemX = groundItemData.getX();
                        float itemY = groundItemData.getY();
                        long itemID = groundItemData.getId();
                        if (Math.pow(itemX - x, 2) + Math.pow(itemY - y, 2) <= MAX_DISTANCE * MAX_DISTANCE) {
                            final boolean isContainer = shouldSearch(groundItemData.getName().toLowerCase());
                            if (isContainer && !openedPiles.contains(itemID))
                                WurmHelper.hud.sendAction(PlayerAction.OPEN, itemID);
                            else if (!isContainer && groundItemData.getName().contains(targetItemName) && !ignoredItems.contains(itemID)) {
                                // we can't get quality of items on the ground (not synced with client)
                                // so they have to be temporarily moved into player inventory
                                // (not sure why this was also done previously, moving ground items directly works?)
                                WurmHelper.hud.sendAction(PlayerAction.TAKE, itemID);
                                pickedUpItems.add(itemID);
                            }
                        }
                    }
                } catch (ConcurrentModificationException ignored) {}
                
                for(WurmComponent wurmComponent : WurmHelper.getInstance().components) {
                    final boolean isContainerWindow = wurmComponent instanceof ItemListWindow;
                    if (!(isContainerWindow || wurmComponent instanceof InventoryWindow))
                        continue;
                    
                    List<InventoryMetaItem> targetItems;
                    if (isContainerWindow) {
                        InventoryListComponent ilc = Utils.getField(wurmComponent, "component");
                        if (ilc == null)
                            continue;
                            
                        InventoryMetaItem rootItem = Utils.getRootItem(ilc);
                        if (rootItem == null || !shouldSearch(rootItem.getBaseName().toLowerCase()))
                            continue;
                        
                        openedPiles.add(rootItem.getId());
                        targetItems = Utils.getInventoryItems(ilc, targetItemName);
                    } else {
                        targetItems = Utils.getInventoryItems(targetItemName);
                    }
                    
                    Map<Boolean, List<InventoryMetaItem>> splitItems = targetItems
                        .stream()
                        .collect(Collectors.partitioningBy(item ->
                            item.getRarity() == 0 &&
                            item.getQuality() >= minQuality
                        ))
                    ;
                    if (moveToContainers(splitItems.get(true)))
                        for (InventoryMetaItem item : splitItems.get(true))
                            pickedUpItems.remove(item.getId());
                    
                    if (!isContainerWindow)
                        for (InventoryMetaItem item: splitItems.get(false)) {
                            final long itemID = item.getId();
                            if (!pickedUpItems.contains(itemID))
                                continue;
                            
                            pickedUpItems.remove(itemID);
                            ignoredItems.add(itemID);
                            WurmHelper.hud.sendAction(
                                item.getBaseName().matches("dirt|heap of sand") ?
                                    PlayerAction.DROP_AS_PILE :
                                    PlayerAction.DROP
                                ,
                                itemID
                            );
                        }
                }
            }
            sleep(timeout);
        }
    }
    
    private boolean shouldSearch(String itemName) {
        return
            itemName.contains("pile of ") ||
            customContainer != null && itemName.contains(customContainer)
        ;
    }

    private boolean moveToContainers(List<InventoryMetaItem> targetItems) {
        if (targetItems != null && targetItems.size() > 0) {
            List<InventoryMetaItem> containers = Utils.getInventoryItems(targetLc, containerName);
            if (containers == null || containers.size() == 0) {
                Utils.consolePrint("No target containers!");
                return false;
            }
            for(InventoryMetaItem container : containers) {
                List<InventoryMetaItem> containerContents = container.getChildren();
                int itemsCount = 0;
                if (containerContents != null) {
                    for(InventoryMetaItem contentItem : containerContents) {
                        String customName = contentItem.getCustomName();
                        if (customName != null && customName.length() > 1) {
                            try {
                                itemsCount += Integer.parseInt(customName.substring(0, customName.length() - 1));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
                if (itemsCount < containerCapacity) {
                    WurmHelper.hud.getWorld().getServerConnection().sendMoveSomeItems(container.getId(), Utils.getItemIds(targetItems));
                    return true;
                }
            }
        }
        return false;
    }

    private void setTargetName(String []input) {
        String name = joinArgs(input);
        if (name == null) {
            printInputKeyUsageString(PileCollector.InputKey.stn);
            return;
        }
        this.targetItemName = name;
        Utils.consolePrint("New name for target items is \"" + this.targetItemName + "\"");
        ignoredItems.clear(); // items that didn't match previously may match now
    }

    private void setTargetInventoryName(String []input) {
        WurmComponent wurmComponent = Utils.getTargetComponent(c -> c instanceof ItemListWindow);
        if (wurmComponent == null) {
            Utils.consolePrint("Can't find an inventory");
            return;
        }
        InventoryListComponent lc;
        try {
            lc = Utils.getField(wurmComponent, "component");
        } catch (IllegalAccessException | NoSuchFieldException e) {
            Utils.consolePrint("Error on configuring the target");
            return;
        }
        targetLc = lc;
        String name = joinArgs(input);
        if (name != null) {
            this.containerName = name;
        }
        InventoryMetaItem rootItem = Utils.getRootItem(lc);
        Utils.feedback("Items will go to \"" + containerName + "\" containers inside \""
                + (rootItem != null ? rootItem.getBaseName() : "the target inventory") + "\"");
    }

    private void setContainerCapacity(String []input) {
        Integer capacity = parseIntArg(input, PileCollector.InputKey.stcc, 1, 100000);
        if (capacity == null)
            return;
        containerCapacity = capacity;
        Utils.consolePrint("New container capacity is " + containerCapacity);
    }
    
    private void setMinQuality(String[] input) {
        Float quality = parseFloatArg(input, PileCollector.InputKey.mq, 0, 100);
        if (quality == null)
            return;
        minQuality = quality;
        if (minQuality > 0 && minQuality < 1)
            Utils.consolePrint(
                "Did you mean `%s %2$.1f` to move items >= %2$.1f QL?",
                PileCollector.InputKey.mq.getName(),
                minQuality * 100
            );
        Utils.consolePrint("Items with QL >= %.1f will be collected", minQuality);
        ignoredItems.clear(); // items that didn't match previously may match now
    }
    
    private void setCustomContainer(String[] input) {
        String name = joinArgs(input);
        customContainer = name == null ? null : name.toLowerCase();
        Utils.consolePrint(
            customContainer == null ?
                "Only piles of items will be searched" :
                "Piles of items and containers named like `%s` will be searched",
            customContainer
        );
    }

    @Override
    void describeSettings(List<String> lines) {
        lines.add("Target items: " + targetItemName);
        InventoryListComponent lc = targetLc;
        InventoryMetaItem rootItem = lc != null ? Utils.getRootItem(lc) : null;
        lines.add("Target: " + (lc == null ? "not set" : "\"" + containerName + "\" containers inside "
                + (rootItem != null ? "\"" + rootItem.getBaseName() + "\"" : "a closed inventory window")));
        lines.add("Container name: " + containerName);
        lines.add("Container capacity: " + containerCapacity);
        lines.add("Min quality: " + minQuality);
        lines.add("Custom container: " + (customContainer != null ? customContainer : "none"));
    }

    private enum InputKey implements Bot.InputKey {
        stn("Target Name", "Set the name for target items. Default name is \"dirt\"", "<item name>"),
        st("Set Target", "Set the target bulk inventory(under mouse pointer) to put items to. Provide an optional name of containers inside inventory. Default is \"large crate\"", "[container name]"),
        stcc("Container Capacity", "Set the capacity for target container. Default value is 300", "<capacity>"),
        mq("Min Quality", "Set minimum quality (0 to 100) of items to be collected", "<quality>"),
        cc("Custom Container", "Set additional container name to search for target items, or clear it when called without a name", "[container name]");

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
