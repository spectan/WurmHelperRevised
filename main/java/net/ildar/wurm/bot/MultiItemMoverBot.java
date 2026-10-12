package net.ildar.wurm.bot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    volatile boolean toplevelOnly = true;
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
        registerInputHandler(Inputs.ss, input -> setSource());
        registerInputHandler(Inputs.nt, this::setNested);
        registerInputHandler(Inputs.nh, this::setNestedHosts);
        registerInputHandler(Inputs.pc, this::setPerContainer);
        registerInputHandler(Inputs.nc, input -> clearNested());
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
                if(set.itemNames.size() == 0)
                    continue;

                if(set.isNested())
                {
                    serveNested(set);
                    continue;
                }

                if(!set.haveTarget())
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
    
    void setSource()
    {
        if(itemSets.size() == 0 || selectedSet == -1)
        {
            Utils.consolePrint("Don't have any item sets (or somehow none selected)");
            return;
        }
        itemSets.get(selectedSet).setSource();
    }

    void setNested(String[] args)
    {
        String inner = joinArgs(args);
        if(inner == null)
        {
            printInputKeyUsageString(Inputs.nt);
            return;
        }
        if(itemSets.size() == 0 || selectedSet == -1)
        {
            Utils.consolePrint("Don't have any item sets (or somehow none selected)");
            return;
        }
        ItemSet set = itemSets.get(selectedSet);
        set.nestedName = inner.trim().toLowerCase(Locale.ROOT);
        set.isRootTarget = false;
        set.target = -1;
        set.sentAt.clear();
        Utils.feedback(
            "Set %d fills every \"%s\" inside every open %s window%s",
            selectedSet,
            set.nestedName,
            String.join(" or ", set.hosts),
            set.sourceComponent == null ? " - now set the source (key " + Inputs.ss.name() + ")" : ""
        );
    }

    void setNestedHosts(String[] args)
    {
        List<String> names = parseNameList(args);
        if(names.isEmpty())
        {
            printInputKeyUsageString(Inputs.nh);
            return;
        }
        if(itemSets.size() == 0 || selectedSet == -1)
        {
            Utils.consolePrint("Don't have any item sets (or somehow none selected)");
            return;
        }
        List<String> lower = new ArrayList<>();
        for(String n: names)
            lower.add(n.toLowerCase(Locale.ROOT));
        itemSets.get(selectedSet).hosts = lower;
        Utils.feedback("Set %d looks inside open windows of: %s", selectedSet, String.join(", ", lower));
    }

    void setPerContainer(String[] args)
    {
        Integer count = parseIntArg(args, Inputs.pc, 1, 1000);
        if(count == null)
            return;
        if(itemSets.size() == 0 || selectedSet == -1)
        {
            Utils.consolePrint("Don't have any item sets (or somehow none selected)");
            return;
        }
        itemSets.get(selectedSet).perContainer = count;
        Utils.feedback("Set %d puts %d of each item into every container", selectedSet, count);
    }

    void clearNested()
    {
        if(itemSets.size() == 0 || selectedSet == -1)
        {
            Utils.consolePrint("Don't have any item sets (or somehow none selected)");
            return;
        }
        itemSets.get(selectedSet).clearNested();
        Utils.feedback("Set %d is a plain set again", selectedSet);
    }

    /** The game's item as the planner sees it. */
    static NestedFill.Node wrap(final InventoryMetaItem item)
    {
        return new NestedFill.Node()
        {
            @Override public long id() { return item.getId(); }
            @Override public String baseName() { return item.getBaseName(); }
            @Override public String displayName() { return item.getDisplayName(); }
            @Override public List<NestedFill.Node> children()
            {
                List<InventoryMetaItem> kids = item.getChildren();
                if(kids == null)
                    return Collections.emptyList();
                try
                {
                    List<NestedFill.Node> out = new ArrayList<>(kids.size());
                    for(InventoryMetaItem kid: new ArrayList<>(kids))
                        out.add(wrap(kid));
                    return out;
                }
                catch(ConcurrentModificationException err)
                {
                    return Collections.emptyList();   // the list changed under us: the next pass sees it
                }
            }
        };
    }

    /**
     * A nested set: take the set's items from its source window and put N of each into every container named
     * like the set's inner name, inside every open oven / forge window.
     */
    void serveNested(ItemSet set) throws Exception
    {
        InventoryMetaItem srcRoot = set.sourceComponent == null ? null : Utils.getRootItem(set.sourceComponent);
        if(srcRoot == null)
        {
            set.note(set.sourceComponent == null
                ? "No source window set (hover the container window and use key " + Inputs.ss.name() + ")"
                : "The source window was closed - open it again and set it with key " + Inputs.ss.name());
            return;
        }

        // every open container window except the source: the ovens / forges are among them
        List<NestedFill.Node> roots = new ArrayList<>();
        List<WurmComponent> components = WurmHelper.getInstance().components;
        if(components != null)
            for(WurmComponent component: new ArrayList<>(components))
            {
                if(!(component instanceof ItemListWindow))
                    continue;
                InventoryListComponent list;
                try
                {
                    list = Utils.getField(component, "component");
                }
                catch(Exception err)
                {
                    continue;
                }
                InventoryMetaItem root = list == null ? null : Utils.getRootItem(list);
                if(root != null && root.getId() != srcRoot.getId())
                    roots.add(wrap(root));
            }

        List<NestedFill.Node> containers = NestedFill.findContainers(roots, set.hosts, set.nestedName);
        if(containers.isEmpty())
        {
            set.note(String.format(
                "No \"%s\" found in any open %s window (open the %s and open the bags in it)",
                set.nestedName, String.join(" or ", set.hosts), String.join(" or ", set.hosts)
            ));
            return;
        }

        // what the source can give, per item name (the same case-insensitive name match the planner uses)
        List<InventoryMetaItem> sourceItems = Utils.getSelectedItems(set.sourceComponent, true, true);
        List<String> names = new ArrayList<>(set.itemNames);
        Map<String, List<NestedFill.Node>> sources = new HashMap<>();
        for(String name: names)
        {
            String word = name.toLowerCase(Locale.ROOT);
            List<NestedFill.Node> found = new ArrayList<>();
            if(sourceItems != null)
                for(InventoryMetaItem item: sourceItems)
                    if(item.getId() != srcRoot.getId() && item.getRarity() == 0
                        && item.getBaseName().toLowerCase(Locale.ROOT).contains(word))
                        found.add(wrap(item));
            sources.put(name, found);
        }

        List<NestedFill.Move> moves = NestedFill.plan(containers, names, set.perContainer, sources, set::recentlySent);
        if(moves.isEmpty())
        {
            set.note(containers.size() + " container(s) hold what they should");
            return;
        }
        set.note("Filling " + containers.size() + " container(s): " + moves.size() + " transfer(s) this pass");

        int unanswered = 0;
        for(NestedFill.Move move: moves)
        {
            if(!isActive())
                return;
            waitOnPause();

            set.markSent(move.dest, move.itemName);
            if(move.bulk)
            {
                if(!sendBulk(move.dest, move.sourceId, move.quantity))
                    unanswered++;
            }
            else
            {
                long[] ids = new long[move.ids.size()];
                for(int i = 0; i < ids.length; i++)
                    ids[i] = move.ids.get(i);
                WurmHelper.hud.getWorld().getServerConnection().sendMoveSomeItems(move.dest, ids);
            }
        }
        if(unanswered > 0)
            Utils.consolePrint("%d bulk transfer(s) got no quantity question (a full container, or an item too heavy?)", unanswered);
    }

    /** One bulk entry -> N into dest, through the quantity question; false when the question never came. */
    private boolean sendBulk(long dest, long sourceId, int quantity) throws InterruptedException
    {
        synchronized(BulkItemGetterBot.bulkLock)
        {
            BulkItemGetterBot.currentMoveQuantity = quantity;
            BulkItemGetterBot.closeBMLWindow = true;
            WurmHelper.hud.getWorld().getServerConnection().sendMoveSomeItems(dest, new long[]{sourceId});

            int sleeps = 0;
            while(BulkItemGetterBot.closeBMLWindow && sleeps++ < 50)
                sleep(100);
            if(BulkItemGetterBot.closeBMLWindow)
            {
                BulkItemGetterBot.closeBMLWindow = false;
                BulkItemGetterBot.currentMoveQuantity = -1;
                return false;
            }
            return true;
        }
    }

    static class ItemSet
    {
        /** How long a container is left alone after something was sent to it (its contents update late). */
        static final long SENT_HOLD_MS = 8000;

        /** Nested mode: the inner container's name (null = a plain set), the host windows' words, the amount. */
        volatile String nestedName;
        volatile List<String> hosts = Arrays.asList("oven", "forge");
        volatile int perContainer = 1;
        volatile InventoryListComponent sourceComponent;
        final Map<String, Long> sentAt = new java.util.concurrent.ConcurrentHashMap<>();
        private volatile String lastNote;

        boolean isNested()
        {
            return nestedName != null;
        }

        void clearNested()
        {
            nestedName = null;
            sourceComponent = null;
            sentAt.clear();
            lastNote = null;
        }

        void setSource()
        {
            WurmComponent window = Utils.getTargetComponent(c -> c instanceof ItemListWindow);
            InventoryListComponent list = Utils.getInventoryForComponent(window);
            if(list == null)
                return;
            sourceComponent = list;
            InventoryMetaItem root = Utils.getRootItem(list);
            Utils.feedback("New source is \"%s\"", root == null ? "?" : root.getBaseName());
        }

        boolean recentlySent(long dest, String name)
        {
            Long at = sentAt.get(dest + "|" + name);
            return at != null && System.currentTimeMillis() - at < SENT_HOLD_MS;
        }

        void markSent(long dest, String name)
        {
            sentAt.put(dest + "|" + name, System.currentTimeMillis());
        }

        /** Said once per change: the bot passes every few seconds and would repeat itself. */
        void note(String text)
        {
            if(text.equals(lastNote))
                return;
            lastNote = text;
            Utils.consolePrint("%s", text);
        }

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
            clearNested();
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
            if(isNested())
            {
                InventoryMetaItem src = sourceComponent == null ? null : Utils.getRootItem(sourceComponent);
                return String.format(
                    "%d of each into every \"%s\" in open %s windows, from %s",
                    perContainer, nestedName, String.join(" or ", hosts),
                    src != null ? "\"" + src.getBaseName() + "\"" : "no source set"
                );
            }
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
            {
                InventoryMetaItem root = Utils.getRootItem(targetComponent);
                if(root == null)
                    return;
                dest = root.getId();
            }
            
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

        ss("Set Source", "Set the container window under the mouse as the source of chosen set (nested mode: e.g. a food storage bin)", ""),
        nt("Nested Target", "Fill every container with this name (at any depth) inside every open oven/forge window, " +
            "taking the set's items from the source. For example: nt frying pan", "<container name>"),
        nh("Nested Hosts", "Words in the names of the windows that count as hosts. Default: oven, forge", "<names>"),
        pc("Per Container", "How many of each item go into every container (those holding fewer get topped up). Default 1", "<count>"),
        nc("Clear Nested", "Make the chosen set a plain set again (forgets the source and the nested target)", ""),
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
