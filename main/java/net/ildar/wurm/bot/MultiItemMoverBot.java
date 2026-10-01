package net.ildar.wurm.bot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.gui.InventoryListComponent;
import com.wurmonline.client.renderer.gui.InventoryWindow;
import com.wurmonline.client.renderer.gui.ItemListWindow;
import com.wurmonline.client.renderer.gui.WurmComponent;

import net.ildar.wurm.Utils;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.annotations.BotInfo;

@BotInfo(name = "Multi Item Mover", abbreviation = "mim", description = "Moves many sets of items to their own containers")
public class MultiItemMoverBot extends Bot
{
    boolean toplevelOnly = true;
    final List<ItemSet> itemSets = new CopyOnWriteArrayList<>();
    int selectedSet = 0;
    
    public MultiItemMoverBot()
    {
        timeout = 5000;
        itemSets.add(new ItemSet());
        
        registerInputHandler(Inputs.fl, input -> toggleToplevelOnly());
        registerInputHandler(Inputs.isn, input -> newSet());
        registerInputHandler(Inputs.isd, input -> deleteSet());
        registerInputHandler(Inputs.isc, this::selectSet);
        registerInputHandler(Inputs.isl, input -> listSets());
        registerInputHandler(Inputs.st, input -> setTarget(false));
        registerInputHandler(Inputs.str, input -> setTarget(true));
        registerInputHandler(Inputs.a, this::addItem);
        registerInputHandler(Inputs.clear, input -> clearItems());
    }

    @Override
    public void work() throws Exception
    {
        while(isActive())
        {
            waitOnPause();
            while(itemSets.size() == 0)
                sleep(1000);
            
            List<InventoryMetaItem> inventoryItems;
            for(ItemSet set: itemSets)
            {
                if(!set.haveTarget() || set.itemNames.size() == 0)
                    continue;
                
                // refresh as items may have been matched by earlier sets
                inventoryItems = getInventoryItems();
                
                List<InventoryMetaItem> toMove = new ArrayList<>();
                for(InventoryMetaItem item: inventoryItems)
                {
                    if(item.getRarity() != 0) continue;
                    
                    for(String matchName: set.itemNames)
                        if(item.getBaseName().contains(matchName))
                        {
                            toMove.add(item);
                            break;
                        }
                }
                
                set.moveItems(toMove);
            }
            
            sleep(timeout);
        }
    }
    
    List<InventoryMetaItem> getInventoryItems()
    {
        if(toplevelOnly)
            return Utils.getFirstLevelItems();
        else
            return Utils.getSelectedItems(WurmHelper.hud.getInventoryWindow().getInventoryListComponent(), true, true);
    }
    
    void toggleToplevelOnly()
    {
        toplevelOnly = !toplevelOnly;
        Utils.feedback(
            "Bot will move %s items",
            toplevelOnly ? "only top level" : "all"
        );
    }
    
    void newSet()
    {
        itemSets.add(new ItemSet());
        selectedSet = itemSets.size() - 1;
        Utils.consolePrint("Created and selected new item set with index %d", selectedSet);
    }
    
    void deleteSet()
    {
        if(itemSets.size() == 0 || selectedSet == -1)
        {
            Utils.consolePrint("Don't have any item sets (or somehow none selected) to delete!");
            return;
        }
        
        itemSets.remove(selectedSet);
        Utils.consolePrint("Deleted item set %d", selectedSet);
        selectedSet = itemSets.size() - 1;
    }
    
    void selectSet(String[] args)
    {
        if(args == null || args.length != 1)
        {
            printInputKeyUsageString(Inputs.isc);
            return;
        }
        
        final int numSets = itemSets.size();
        if(numSets == 0)
        {
            Utils.consolePrint(
                "No item sets to select! Use %s subcommand to create one",
                Inputs.isn.name()
            );
            return;
        }
        
        Integer newSelection = parseIntArg(args, Inputs.isc, 0, numSets - 1);
        if(newSelection == null)
            return;
        
        selectedSet = newSelection;
        Utils.feedback("Selected item set %d", selectedSet);
    }
    
    void listSets()
    {
        if(itemSets.size() == 0)
        {
            Utils.consolePrint("No item sets yet");
            return;
        }
        
        for(int index = 0; index < itemSets.size(); index++)
            Utils.consolePrint(
                "%s %d: %s",
                index == selectedSet ? "*" : " ",
                index,
                String.join(", ", itemSets.get(index).itemNames)
            );
    }
    
    void setTarget(boolean isRoot)
    {
        if(itemSets.size() == 0 || selectedSet == -1)
        {
            Utils.consolePrint("Don't have any item sets (or somehow none selected)");
            return;
        }
        itemSets.get(selectedSet).setTarget(isRoot);
    }
    
    void addItem(String[] args)
    {
        List<String> itemNames = parseNameList(args);
        if(itemNames.isEmpty())
        {
            printInputKeyUsageString(Inputs.a);
            return;
        }
        
        if(itemSets.size() == 0 || selectedSet == -1)
        {
            Utils.consolePrint("Don't have any item sets (or somehow none selected)");
            return;
        }
        
        ItemSet selected = itemSets.get(selectedSet);
        for(String name: itemNames)
            selected.itemNames.add(name);
        Utils.consolePrint(
            "Items for set %d now: %s",
            selectedSet,
            String.join(", ", selected.itemNames)
        );
    }
    
    void clearItems()
    {
        if(itemSets.size() == 0 || selectedSet == -1)
        {
            Utils.consolePrint("Don't have any item sets (or somehow none selected)");
            return;
        }
        
        itemSets.get(selectedSet).itemNames.clear();
        Utils.feedback("Cleared items for set %d", selectedSet);
    }
    
    static class ItemSet
    {
        final Set<String> itemNames = ConcurrentHashMap.newKeySet();
        boolean isRootTarget;
        volatile long target;
        volatile String targetName;
        volatile InventoryListComponent targetComponent;
        volatile InventoryMetaItem targetRoot;
        
        boolean haveTarget()
        {
            return target > 0 || isRootTarget && targetComponent != null && targetRoot != null;
        }
        
        void setTarget(boolean isRoot)
        {
            isRootTarget = isRoot;
            target = -1;
            targetName = null;
            targetComponent = null;
            targetRoot = null;
            if(isRoot)
            {
                WurmComponent inventoryComponent = Utils.getTargetComponent(c -> c instanceof ItemListWindow || c instanceof InventoryWindow);
                if(inventoryComponent == null)
                {
                    Utils.consolePrint("Couldn't find an open container");
                    return;
                }
                
                InventoryListComponent listComponent;
                try
                {
                    listComponent = Utils.getField(inventoryComponent, "component");
                }
                catch(Exception err)
                {
                    Utils.consolePrint("Couldn't get container's ListComponent");
                    err.printStackTrace();
                    return;
                }
                
                InventoryMetaItem root = Utils.getRootItem(listComponent);
                if(root == null)
                {
                    Utils.consolePrint("ListComponent has no root item?");
                    return;
                }
                
                targetComponent = listComponent;
                targetRoot = root;
                Utils.feedback("New target is \"%s\"", root.getBaseName());
            }
            else
            {
                int x = WurmHelper.hud.getWorld().getClient().getXMouse();
                int y = WurmHelper.hud.getWorld().getClient().getYMouse();
                long[] targets = WurmHelper.hud.getCommandTargetsFrom(x, y);
                
                if(targets != null && targets.length > 0)
                {
                    target = targets[0];
                    targetName = ItemMoverBot.describeHoveredTarget(targets[0], x, y);
                    Utils.feedback("New target is %s", targetName);
                }
                else
                    Utils.consolePrint("Couldn't find item to target");
            }
        }
        
        String describeTarget()
        {
            if(isRootTarget)
            {
                InventoryMetaItem root = targetRoot;
                return root != null ? "\"" + root.getBaseName() + "\"" : "not set";
            }
            if(target > 0)
                return targetName != null ? targetName : "item with id " + target;
            return "not set";
        }
        
        void moveItems(List<InventoryMetaItem> items)
        {
            if(items.size() == 0) return;
            
            long dest = target;
            if(isRootTarget)
                dest = targetRoot.getId();
            
            long[] ids = Utils.getItemIds(items);
            WurmHelper.hud.getWorld().getServerConnection().sendMoveSomeItems(dest, ids);
                
        }
    }
    
    @Override
    void describeSettings(List<String> lines)
    {
        lines.add("First level items only: " + onOff(toplevelOnly));
        List<ItemSet> sets = new ArrayList<>(itemSets);
        for(int index = 0; index < sets.size(); index++)
        {
            ItemSet set = sets.get(index);
            lines.add(String.format(
                "Set %d%s: items: %s; target: %s",
                index,
                index == selectedSet ? " (selected)" : "",
                set.itemNames.isEmpty() ? "none" : String.join(", ", set.itemNames),
                set.describeTarget()
            ));
        }
    }
    
    private enum Inputs implements Bot.InputKey
    {
        fl("First Level Only", "Toggle moving of only top-level items", ""),
        
        isn("New Set", "Create new item set", ""),
        isd("Delete Set", "Delete current item set", ""),
        isc("Select Set", "Choose item set to operate on", "<index>"),
        isl("List Sets", "Show item sets", ""),
        
        st("Set Target", "Set target item for chosen set", ""),
        str("Target Container Root", "Set target container for chosen set", ""),
        a("Add Item", "Add item names to chosen set. Separate several names with commas", "<item name>"),
        clear("Clear Items", "Clear list of items in chosen set", ""),
        ;

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
