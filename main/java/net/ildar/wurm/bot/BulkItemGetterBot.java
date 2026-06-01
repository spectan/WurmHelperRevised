package net.ildar.wurm.bot;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.options.Options;
import com.wurmonline.client.renderer.gui.InventoryListComponent;
import com.wurmonline.shared.util.MaterialUtilities;

import net.ildar.wurm.Utils;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.annotations.BotInfo;

@BotInfo(name = "Bulk Item Getter", description =
        "Automatically transfers items to player's inventory from configured bulk storages. " +
        "The n-th  source item will be transferred to the n-th target item",
        abbreviation = "big")
public class BulkItemGetterBot extends Bot
{
    public static final Pattern quantityRegex = Pattern.compile("\\((\\d+)x\\)");

    public static volatile boolean closeBMLWindow;
    public static volatile int currentMoveQuantity = -1;
    private int moveQuantity = -1;
    ArrayList<ItemSpec> specs = new ArrayList<>();
    int selectedSpec = 0;
    
    public BulkItemGetterBot()
    {
        specs.add(new ItemSpec());
        
        registerInputHandler(Inputs.isn, input -> newSpec());
        registerInputHandler(Inputs.isd, input -> deleteSpec());
        registerInputHandler(Inputs.isc, this::selectSpec);
        registerInputHandler(Inputs.isl, input -> listSpecs());
        registerInputHandler(Inputs.c, this::setQuantity);
        registerInputHandler(Inputs.ss, input -> setSource(false));
        registerInputHandler(Inputs.ssxy, input -> setSource(true));
        registerInputHandler(Inputs.st, input -> setTarget());
    }
    
    @Override
    public void work() throws Exception
    {
        closeBMLWindow = false;
        currentMoveQuantity = -1;
        moveQuantity = -1;
        setTimeout(5000);
        registerEventProcessor(
            message -> message.contains("That item is already busy"),
            () -> closeBMLWindow = false
        );
        
        while(isActive())
        {
            waitOnPause();
            while (specs.size() <= 0)
                sleep(1000);
            
            for(ItemSpec spec: specs)
            {
                if(spec.target == null) continue;
                
                spec.updateSource(); // ignored when not fixed point
                if(spec.source == null) continue;
                
                if(spec.stockQuantity <= 0)
                    moveQuantity = -1;
                else
                {
                    final int targetQuantity = spec.countTargetQuantity();
                    moveQuantity = spec.stockQuantity - targetQuantity;
                    if(moveQuantity <= 0) continue; // fully stocked

                    int sourceQuantity = Integer.MAX_VALUE;
                    try {
                        Matcher matcher = quantityRegex.matcher(spec.source.getDisplayName());
                        if(matcher.find())
                            sourceQuantity = Integer.parseInt(matcher.group(1));
                        else
                            throw new IndexOutOfBoundsException();
                    } catch(NumberFormatException | IndexOutOfBoundsException err) {
                        Utils.consolePrint(
                            "%s: couldn't determine quantity of bulk item \"%s\"",
                            BulkItemGetterBot.class.getSimpleName(),
                            spec.source.getDisplayName()
                        );
                    }
                    if(moveQuantity > sourceQuantity)
                        moveQuantity = sourceQuantity;
                }
                
                currentMoveQuantity = moveQuantity;
                closeBMLWindow = true;
                // Utils.consolePrint("moving `%s` => `%s`", ItemSpec.getCanonicalName(spec.source), ItemSpec.getCanonicalName(spec.target));
                WurmHelper.hud.getWorld().getServerConnection().sendMoveSomeItems(spec.target.getId(), new long[]{spec.source.getId()});
                
                int sleeps = 0;
                while(closeBMLWindow && sleeps++ < 50) sleep(100);
                if(closeBMLWindow)
                {
                    Utils.consolePrint("Timed out after 5 seconds waiting for bulk item transfer question");
                    closeBMLWindow = false;
                    currentMoveQuantity = -1;
                }
            }
            // Utils.consolePrint("main loop sleep for %dms", timeout);
            sleep(timeout);
        }
    }
    
    void newSpec()
    {
        specs.add(new ItemSpec());
        selectedSpec = specs.size() - 1;
        Utils.consolePrint("Created and selected new spec with index %d", selectedSpec);
    }
    
    void deleteSpec()
    {
        if(specs.size() == 0 || selectedSpec == -1)
        {
            Utils.consolePrint("Don't have any specs (or somehow none selected) to delete!");
            return;
        }
        
        specs.remove(selectedSpec);
        Utils.consolePrint("Deleted spec %d", selectedSpec);
        selectedSpec = specs.size() - 1;
    }
    
    void selectSpec(String[] args)
    {
        if(args == null || args.length != 1)
        {
            printInputKeyUsageString(Inputs.isc);
            return;
        }
        
        final int numSets = specs.size();
        if(numSets == 0)
        {
            Utils.consolePrint(
                "No specs to select! Use %s subcommand to create one",
                Inputs.isn.name()
            );
            return;
        }
        
        try
        {
            int newSelection = Integer.parseInt(args[0]);
            if(newSelection >= numSets)
            {
                Utils.consolePrint(
                    "Only have %d specs, index must be in 0 .. %d",
                    numSets, numSets - 1
                );
                return;
            }
            
            selectedSpec = newSelection;
            Utils.consolePrint("Selected spec %d", selectedSpec);
        }
        catch(NumberFormatException err)
        {
            Utils.consolePrint("`%s` is not an integer", args[0]);
        }
    }
    
    void listSpecs()
    {
        if(specs.size() == 0)
        {
            Utils.consolePrint("No specs yet");
            return;
        }
        
        for(int index = 0; index < specs.size(); index++)
            Utils.consolePrint(
                "%s %d: %s",
                index == selectedSpec ? "*" : " ",
                index,
                String.join(", ", specs.get(index).toString())
            );
    }
    
    void setQuantity(String[] args)
    {
        if(args == null || args.length != 1)
        {
            printInputKeyUsageString(Inputs.isc);
            return;
        }
        
        if(specs.size() == 0 || selectedSpec == -1)
        {
            Utils.consolePrint("Don't have any specs (or somehow none selected) to set quantity of!");
            return;
        }
        
        try
        {
            int newQuantity = Integer.parseInt(args[0]);
            if(newQuantity <= 0) newQuantity = -1;
            specs.get(selectedSpec).stockQuantity = newQuantity;
            
            if(newQuantity > 0)
                Utils.consolePrint("Bot will keep at most %d items in stock", newQuantity);
            else
                Utils.consolePrint("Bot will keep as many items as possible in target");
        }
        catch(NumberFormatException err)
        {
            Utils.consolePrint("`%s` is not an integer", args[0]);
        }
    }
    
    void setSource(boolean fixed)
    {
        if(specs.size() == 0 || selectedSpec == -1)
        {
            Utils.consolePrint("Don't have any specs (or somehow none selected)");
            return;
        }
        specs.get(selectedSpec).setSource(fixed);
    }
    
    void setTarget()
    {
        if(specs.size() == 0 || selectedSpec == -1)
        {
            Utils.consolePrint("Don't have any specs (or somehow none selected)");
            return;
        }
        specs.get(selectedSpec).setTarget();
    }
    
    enum Inputs implements Bot.InputKey
    {
        isn("New Set", "Create a new item spec", ""),
        isd("Delete Set", "Delete currently chosen item spec", ""),
        isc("Select Set", "Choose an item spec to operate on", "number"),
        isl("List Sets", "List item specs", ""),
        
        c("Clicks", "Set quantity of source items to keep stocked in target", "number"),
        ss("Add Source", "Set the source item for chosen spec (in bulk storage) to what the user is currenly pointing to", ""),
        ssxy("Source XY", "Find source item(s) for chosen spec from a fixed point at current cursor position", ""),
        st("Set Target", "Set the target item for chosen spec to what the user is currently pointing to", ""),
        ;
        
        String fullName;
        String description;
        String usage;
        Inputs(String fullName, String description, String usage)
        {
            this.fullName = fullName;
            this.description = description;
            this.usage = usage;
        }

        @Override
        public String getName()
        {
            return name();
        }

        @Override
        public String getFullName() {
            return fullName;
        }
        @Override
        public String getDescription()
        {
            return description;
        }

        @Override
        public String getUsage()
        {
            return usage;
        }
    }
}

class ItemSpec
{
    InventoryMetaItem target = null;
    InventoryMetaItem source = null;
    boolean fixedPointSrc = false;
    int fixedX = -1;
    int fixedY = -1;
    int stockQuantity = -1;
    
    public ItemSpec()
    {
        // move to player's inventory by default
        targetPlayerInventory();
        if(target != null)
            Utils.consolePrint("New item spec will move to player's inventory");
    }
    
    public void setSource(boolean fixed)
    {
        final int mouseX = WurmHelper.hud.getWorld().getClient().getXMouse();
        final int mouseY = WurmHelper.hud.getWorld().getClient().getYMouse();
        
        source = null;
        fixedX = -1;
        fixedY = -1;
        fixedPointSrc = fixed;
        if(fixed)
        {
            fixedX = mouseX;
            fixedY = mouseY;
            Utils.consolePrint("Current item set will pull items at mouse coordinates %d,%d", fixedX, fixedY);
        }
        else
            updateSource(mouseX, mouseY);
    }
    
    public void setTarget()
    {
        // TODO: readd support for targeting subcontainers, separate command for this case
        target = Utils.getRootItem(Utils.getTargetInventory());
        
        // player's main inventory is child of above root
        if(target == Utils.getRootItem(WurmHelper.hud.getInventoryWindow().getInventoryListComponent()))
            targetPlayerInventory();
        
        if(target != null)
            Utils.consolePrint("New target is %s", target.getDisplayName());
        else
            Utils.consolePrint("Couldn't find any target containers");
    }
    
    private void targetPlayerInventory()
    {
        InventoryMetaItem root = Utils.getRootItem(WurmHelper.hud.getInventoryWindow().getInventoryListComponent());
        if (root == null || root.getChildren() == null) {
            target = null;
            return;
        }
        target = root.getChildren()
            .stream()
            // target specifically the main inventory sub-item, so counting works
            .filter(item -> item.getBaseName().equals("inventory"))
            .findFirst()
            .orElse(null)
        ;
    }
    
    public void updateSource()
    {
        if(!fixedPointSrc)
            return;
        
        updateSource(fixedX, fixedY);
    }
    
    private void updateSource(int x, int y)
    {
        InventoryListComponent inv = Utils.getInventoryAtPoint(x, y);
        if(inv == null) {
            Utils.consolePrint(
                "%s: couldn't find any inventories at programmed coordinates",
                BulkItemGetterBot.class.getSimpleName()
            );
            source = null;
            return;
        }

        List<InventoryMetaItem> items = Utils.getInventoryItemsAtPoint(inv, x, y);
        
        if(items.size() == 0)
        {
            Utils.consolePrint("Couldn't set source: no items found");
            return;
        }
        else if(items.size() > 1)
            Utils.consolePrint("More than one item found, defaulting to the first");
        
        source = items.get(0);
        Utils.consolePrint("Source is now: %s", source.getDisplayName());
    }
    
    @Override
    public String toString()
    {
        final String srcName = fixedPointSrc ?
            String.format(
                "items at %d,%d (currently %s)",
                fixedX,
                fixedY,
                source == null ? "<unset>" : source.getDisplayName()
            ) :
            source != null ? source.getDisplayName() : "<unset>"
        ;
        final String destName = target == null ?
            "<unset>" :
            target.getDisplayName()
        ;
        return String.format("%s => %s", srcName, destName);
    }
    
    public int countTargetQuantity()
    {
        if(source == null || target == null)
            return 0;
        
        final String canonicalName = getCanonicalName(source);
        return Utils.getInventoryItems(
            target.getChildren(),
            item -> getCanonicalName(item).equalsIgnoreCase(canonicalName)
        ).size();
    }
    
    public static String getCanonicalName(InventoryMetaItem item)
    {
        String baseName = item.getBaseName();
        if(baseName.charAt(baseName.length() - 1) == 's') // BSB items have plurals in all name fields...
            baseName = baseName.substring(0, baseName.length() - 1);
        
        StringBuilder sb = new StringBuilder();
        if(Options.materialAsSuffix.value())
            MaterialUtilities.appendNameWithMaterialSuffix(sb, baseName, item.getMaterialId());
        else
            MaterialUtilities.appendNameWithMaterial(sb, baseName, item.getMaterialId());
        return sb.toString();
    }
}
