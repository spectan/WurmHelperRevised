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
    private float distance = 4;

    public GroundItemGetterBot() {
        registerInputHandler(GroundItemGetterBot.InputKey.a, this::addNewItemName);
        registerInputHandler(GroundItemGetterBot.InputKey.d, this::setDistance);
    }
    @Override
    public void work() throws Exception{
        setTimeout(500);
        while (isActive()) {
            waitOnPause();
            if (itemNames.size() > 0) {
                ServerConnectionListenerClass sscc = WurmHelper.hud.getWorld().getServerConnection().getServerConnectionListener();
                Map<Long, GroundItemCellRenderable> groundItems = Utils.getField(sscc, "groundItems");
                float x = WurmHelper.hud.getWorld().getPlayerPosX();
                float y = WurmHelper.hud.getWorld().getPlayerPosY();
                if (groundItems.size() > 0)
                    try {
                        for (Map.Entry<Long, GroundItemCellRenderable> entry : groundItems.entrySet()) {
                            GroundItemData groundItemData = Utils.getField(entry.getValue(), "item");
                            float itemX = groundItemData.getX();
                            float itemY = groundItemData.getY();
                            if (Math.pow(itemX - x, 2) + Math.pow(itemY - y, 2) <= distance * distance)
                                for (String item : itemNames)
                                    if (groundItemData.getName().contains(item)) {
                                        WurmHelper.hud.sendAction(PlayerAction.TAKE, groundItemData.getId());
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
        if (input == null || input.length < 1) {
            printInputKeyUsageString(GroundItemGetterBot.InputKey.a);
            return;
        }
        addItem(String.join(" ", input));
    }

    private void setDistance(String []input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(GroundItemGetterBot.InputKey.d);
            return;
        }
        try {
            distance = Float.parseFloat(input[0]);
            Utils.consolePrint("Distance was set to " + distance + " meters");
        } catch (NumberFormatException e) {
            Utils.consolePrint("Wrong distance value!");
        }
    }

    private void addItem(String item) {
        itemNames.add(item);
        Utils.consolePrint("Current item set in " + this.getClass().getSimpleName() + " - " + itemNames.toString());
    }

    enum InputKey implements Bot.InputKey {
        d("Distance", "Set the distance the bot should look around player in search for items",
                "distance(in meters, 1 tile is 4 meters)"),
        a("Add Item", "Add new item name to search list", "item_name");

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
