package net.ildar.wurm.bot;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.gui.CreationWindow;
import com.wurmonline.shared.constants.PlayerAction;

import net.ildar.wurm.Utils;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.annotations.BotInfo;

@BotInfo(name = "Seller", description = "Sells items to tokens", abbreviation = "s")
public class SellerBot extends Bot
{
    final Set<String> items = ConcurrentHashMap.newKeySet();
    final Set<String> blacklist = ConcurrentHashMap.newKeySet();
    volatile long targetToken = -1;
    volatile String targetTokenName;
    volatile int maxSellActions = 3;
    
    Object progressBar;
    
    public SellerBot()
    {
        timeout = 5000;
        
        registerInputHandler(Inputs.a, this::addItem);
        registerInputHandler(Inputs.ca, input -> clearItems());
        registerInputHandler(Inputs.b, this::addBlacklist);
        registerInputHandler(Inputs.cb, input -> clearBlacklist());
        registerInputHandler(Inputs.st, input -> setTarget());
        registerInputHandler(Inputs.sc, this::setMaxSellActions);
        registerInputHandler(Inputs.gems, input -> setSellGems());
    }
    
    @Override
    public void work() throws Exception
    {
        CreationWindow cwindow = WurmHelper.hud.getCreationWindow();
        progressBar = Utils.getField(cwindow, "progressBar");
        
        List<InventoryMetaItem> toSell = new ArrayList<>();
        while(isActive())
        {
            waitOnPause();
            
            while(isActive() && (items.size() == 0 || targetToken < 0 || getProgress() > 0))
                sleep(1000);
            if(!isActive()) break;
            
            toSell.clear();
            for(String itemName: items)
            {
                List<InventoryMetaItem> matches = Utils.getInventoryItems(itemName);
                
                matches:
                for(InventoryMetaItem item: matches)
                {
                    for(String blacklistName: blacklist)
                        if(item.getBaseName().contains(blacklistName))
                            continue matches;
                    toSell.add(item);
                }
            }
            
            int queued = 0;
            long[] actionArgs = new long[]{targetToken};
            for(InventoryMetaItem item: toSell)
            {
                WurmHelper.hud.getWorld().getServerConnection().sendAction(
                    item.getId(),
                    actionArgs,
                    PlayerAction.SELL
                );
                
                if(++queued >= maxSellActions)
                    break;
            }
            
            sleep(timeout);
        }
    }
    
    float getProgress() throws Exception
    {
        return Utils.getField(progressBar, "progress");
    }
    
    void addItem(String[] args)
    {
        List<String> itemNames = parseNameList(args);
        if(itemNames.isEmpty())
        {
            printInputKeyUsageString(Inputs.a);
            return;
        }
        
        items.addAll(itemNames);
        Utils.consolePrint(
            "Selling: %s",
            String.join(", ", items)
        );
    }
    
    void clearItems()
    {
        items.clear();
        Utils.feedback("List of items to sell cleared");
    }
    
    void addBlacklist(String[] args)
    {
        List<String> names = parseNameList(args);
        if(names.isEmpty())
        {
            printInputKeyUsageString(Inputs.b);
            return;
        }
        
        blacklist.addAll(names);
        Utils.consolePrint(
            "Selling blacklist: %s",
            String.join(", ", blacklist)
        );
    }
    
    void clearBlacklist()
    {
        blacklist.clear();
        Utils.feedback("List of blacklisted items cleared");
    }
    
    void setTarget()
    {
        targetToken = -1;
        targetTokenName = null;
        try
        {
            PickableUnit pickableUnit = Utils.getField(WurmHelper.hud.getSelectBar(), "selectedUnit");
            if(pickableUnit == null || !pickableUnit.getHoverName().contains("settlement token"))
            {
                Utils.consolePrint("Select a deed token!");
                return;
            }
            targetTokenName = pickableUnit.getHoverName();
            targetToken = pickableUnit.getId();
            Utils.feedback("Target set to \"%s\"", targetTokenName);
        }
        catch (Exception err)
        {
            Utils.consolePrint("Couldn't find deed token");
            err.printStackTrace();
            return;
        }
    }
    
    void setMaxSellActions(String[] args)
    {
        Integer newCount = parseIntArg(args, Inputs.sc, 1, 100);
        if(newCount == null)
            return;
        maxSellActions = newCount;
        
        Utils.consolePrint("Bot will queue %d sell actions", maxSellActions);
    }
    
    void setSellGems()
    {
        items.clear();
        blacklist.clear();
        
        items.add("diamond");
        items.add("emerald");
        items.add("opal");
        items.add("ruby");
        items.add("sapphire");
        
        // exclude the rare variants
        blacklist.add("star");
        blacklist.add("black opal");
        Utils.feedback("Bot will sell common gems");
        Utils.consolePrint("Selling: %s", String.join(", ", items));
        Utils.consolePrint("Selling blacklist: %s", String.join(", ", blacklist));
    }
    
    @Override
    void describeSettings(List<String> lines)
    {
        lines.add("Items: " + (items.isEmpty() ? "none" : String.join(", ", items)));
        lines.add("Blacklist: " + (blacklist.isEmpty() ? "none" : String.join(", ", blacklist)));
        lines.add("Target token: " + (targetToken < 0 ? "not set" : targetTokenName != null ? "\"" + targetTokenName + "\"" : "id " + targetToken));
        lines.add("Sell count: " + maxSellActions);
    }
    
    enum Inputs implements InputKey
    {
        a("Add Item", "Add item names to be sold. Separate several names with commas", "<item name>"),
        ca("Clear Items", "Clear list of items to sell", ""),
        b("Add Blacklist", "Add blacklisted item names. Separate several names with commas", "<item name>"),
        cb("Clear Blacklist", "Clear blacklisted item names", ""),
        st("Set Target", "Set the selected settlement token to sell to", ""),
        sc("Sell Count", "Set max queued sell actions", "<count>"),
        gems("Gems", "Set up bot to sell common (non-star) gems", "");

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
