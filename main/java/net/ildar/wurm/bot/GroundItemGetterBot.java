package net.ildar.wurm.bot;

import com.wurmonline.client.comm.ServerConnectionListenerClass;
import com.wurmonline.client.renderer.GroundItemData;
import com.wurmonline.client.renderer.cell.GroundItemCellRenderable;
import com.wurmonline.client.renderer.cell.StaticModelRenderable;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.Utils;
import net.ildar.wurm.annotations.BotInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@BotInfo(name = "Ground Item Getter", description =
        "Collects items from the ground around player.",
        abbreviation = "gig")
public class GroundItemGetterBot extends Bot {
    private final Set <String> itemNames = ConcurrentHashMap.newKeySet();
    private final Set<Long> pendingItemIds = ConcurrentHashMap.newKeySet();
    private volatile float distance = 4;

    public GroundItemGetterBot() {
        registerInputHandler(GroundItemGetterBot.InputKey.a, this::addNewItemName);
        registerInputHandler(GroundItemGetterBot.InputKey.d, this::setDistance);
        timeout = 500;
    }

    @Override
    void describeSettings(List<String> lines) {
        lines.add("Distance: " + distance + " meters");
        lines.add("Items: " + (itemNames.isEmpty() ? "none (add some with \"a <item name>\")" : String.join(", ", itemNames)));
    }

    @Override
    public void work() throws Exception{
        while (isActive()) {
            waitOnPause();
            if (itemNames.size() > 0) {
                ServerConnectionListenerClass sscc = WurmHelper.hud.getWorld().getServerConnection().getServerConnectionListener();
                Map<Long, GroundItemCellRenderable> groundItems = Utils.getField(sscc, "groundItems");
                float x = WurmHelper.hud.getWorld().getPlayerPosX();
                float y = WurmHelper.hud.getWorld().getPlayerPosY();
                if (groundItems.size() > 0)
                    try {
                        Map<Long, GroundItemCellRenderable> groundItemsSnapshot = new HashMap<>(groundItems);
                        pendingItemIds.retainAll(groundItemsSnapshot.keySet());
                        int maxActions = Utils.getMaxActionNumber();
                        int sentActions = 0;
                        for (Map.Entry<Long, GroundItemCellRenderable> entry : groundItemsSnapshot.entrySet()) {
                            if (sentActions >= maxActions)
                                break;
                            GroundItemData groundItemData = Utils.getField(entry.getValue(), "item");
                            float itemX = groundItemData.getX();
                            float itemY = groundItemData.getY();
                            if (Math.pow(itemX - x, 2) + Math.pow(itemY - y, 2) <= distance * distance)
                                for (String item : itemNames)
                                    if (groundItemData.getName().contains(item)) {
                                        if (pendingItemIds.add(groundItemData.getId())) {
                                            WurmHelper.hud.sendAction(PlayerAction.TAKE, groundItemData.getId());
                                            sentActions++;
                                        }
                                        break;
                                    }
                        }
                    } catch (ConcurrentModificationException ignored) {
                    }
            }
            sleep(timeout);
        }
    }

    @SuppressWarnings("unused")
    public void processNewItem(StaticModelRenderable staticModelRenderable) {
        try {
            float x = WurmHelper.hud.getWorld().getPlayerPosX();
            float y = WurmHelper.hud.getWorld().getPlayerPosY();
            float itemX = Utils.getField(staticModelRenderable, "x");
            float itemY = Utils.getField(staticModelRenderable, "y");
            if (Math.pow(itemX-x, 2)+Math.pow(itemY-y, 2) <= distance * distance)
                for(String item:itemNames)
                    if (staticModelRenderable.getHoverName().contains(item)) {
                        WurmHelper.hud.sendAction(PlayerAction.TAKE, staticModelRenderable.getId());
                        break;
                    }
        }
        catch(IllegalAccessException|NoSuchFieldException e) {
            Utils.consolePrint("Got exception while processing new item in " + GroundItemGetterBot.class.getSimpleName());
        }
    }

    private void addNewItemName(String []input) {
        List<String> names = parseNameList(input);
        if (names.isEmpty()) {
            printInputKeyUsageString(GroundItemGetterBot.InputKey.a);
            return;
        }
        itemNames.addAll(names);
        Utils.consolePrint("Current item set in " + this.getClass().getSimpleName() + " - " + itemNames.toString());
    }

    private void setDistance(String []input) {
        Float value = parseFloatArg(input, GroundItemGetterBot.InputKey.d, 0.1f, 100);
        if (value == null)
            return;
        distance = value;
        Utils.consolePrint("Distance was set to " + distance + " meters");
    }

    enum InputKey implements Bot.InputKey {
        d("Distance", "Set the distance (in meters, 1 tile is 4 meters) the bot should look around player in search for items",
                "<distance>"),
        a("Add Item", "Add item names to the search list. Separate several names with commas, e.g. \"log, small rock\"", "<item name>");

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
