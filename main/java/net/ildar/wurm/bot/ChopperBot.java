package net.ildar.wurm.bot;

import com.wurmonline.client.comm.ServerConnectionListenerClass;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.GroundItemData;
import com.wurmonline.client.renderer.cell.GroundItemCellRenderable;
import com.wurmonline.client.renderer.gui.CreationWindow;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.Utils;
import net.ildar.wurm.annotations.BotInfo;

import java.util.ConcurrentModificationException;
import java.util.Map;

@BotInfo(description =
        "Automatically chops felled trees near player",
        abbreviation = "ch")
public class ChopperBot extends Bot {
    private float distance = 4;
    private AreaAssistant areaAssistant = new AreaAssistant(this);
    private float staminaThreshold;
    private int clicks;
    private InventoryMetaItem toolItem;
    private static final String[] VALID_TOOLS = {"hatchet"};

    public ChopperBot() {
        registerInputHandler(ChopperBot.InputKey.s, this::setStaminaThreshold);
        registerInputHandler(ChopperBot.InputKey.d, this::setDistance);
        registerInputHandler(ChopperBot.InputKey.c, this::setClickNumber);
        registerInputHandler(ChopperBot.InputKey.tool, input -> selectTool());

        areaAssistant.setMoveAheadDistance(1);
        areaAssistant.setMoveRightDistance(1);
    }

    @Override
    public void work() throws Exception{
        setStaminaThreshold(0.96f);
        setClicks(Utils.getMaxActionNumber());
        if (toolItem == null)
            toolItem = Utils.locateToolItem("hatchet");
        long hatchetId;
        if (toolItem == null) {
            Utils.consolePrint("You don't have a hatchet!");
            return;
        } else {
            hatchetId = toolItem.getId();
            Utils.consolePrint(this.getClass().getSimpleName() + " will use " + toolItem.getDisplayName() + " to chop shriveled trees.");
            Utils.consolePrint("QL:" + toolItem.getQuality() + " DMG:" + toolItem.getDamage());
        }
        CreationWindow creationWindow = WurmHelper.hud.getCreationWindow();
        Object progressBar = Utils.getField(creationWindow, "progressBar");
        ServerConnectionListenerClass sscc = WurmHelper.hud.getWorld().getServerConnection().getServerConnectionListener();
        while (isActive()) {
            waitOnPause();
            if (canDoWork(staminaThreshold)) {
                Map<Long, GroundItemCellRenderable> groundItems = Utils.getField(sscc, "groundItems");
                float x = WurmHelper.hud.getWorld().getPlayerPosX();
                float y = WurmHelper.hud.getWorld().getPlayerPosY();
                boolean didSomething = false;
                if (groundItems.size() > 0) {
                    try {
                        for (Map.Entry<Long, GroundItemCellRenderable> entry : groundItems.entrySet()) {
                            GroundItemData groundItemData = Utils.getField(entry.getValue(), "item");
                            float itemX = groundItemData.getX();
                            float itemY = groundItemData.getY();
                            if (Math.sqrt(Math.pow(itemX - x, 2) + Math.pow(itemY - y, 2)) <= distance)
                                if (groundItemData.getName().contains("felled tree")) {
                                    for (int i = 0; i < clicks; i++)
                                        WurmHelper.hud.getWorld().getServerConnection().sendAction(hatchetId, new long[]{groundItemData.getId()}, PlayerAction.CHOP_UP);
                                    didSomething = true;
                                    break;
                                }
                        }
                    } catch (ConcurrentModificationException e) {
                        Utils.consolePrint("Got concurrent modification exception!");
                    }
                }
                if (!didSomething) {
                    areaAssistant.areaNextPosition();
                    continue;
                }
            }
            sleep(timeout);
        }
    }

    private void setDistance(String[] input) {
        if (input == null || input.length == 0) {
            printInputKeyUsageString(ChopperBot.InputKey.d);
            return;
        }
        try {
            distance = Float.parseFloat(input[0]);
            Utils.consolePrint("New lookup distance is " + distance + " meters");
        } catch (NumberFormatException e) {
            Utils.consolePrint("Wrong distance value!");
        }
    }

    private void setStaminaThreshold(String[] input) {
        if (input == null || input.length != 1)
            printInputKeyUsageString(ChopperBot.InputKey.s);
        else {
            try {
                float threshold = Float.parseFloat(input[0]);
                setStaminaThreshold(threshold);
            } catch (Exception e) {
                Utils.consolePrint("Wrong threshold value!");
            }
        }
    }

    private void setStaminaThreshold(float s) {
        staminaThreshold = s;
        Utils.consolePrint("Current threshold for stamina is " + staminaThreshold);
    }

    private void setClickNumber(String[] input) {
        if (input == null || input.length != 1)
            printInputKeyUsageString(ChopperBot.InputKey.c);
        else {
            try {
                int clicks = Integer.parseInt(input[0]);
                setClicks(clicks);
            } catch (Exception e) {
                Utils.consolePrint("Wrong value!");
            }
        }
    }

    private void setClicks(int clicks) {
        this.clicks = clicks;
        Utils.consolePrint(getClass().getSimpleName() + " will do " + clicks + " chops each time");
    }

    private void selectTool() {
        InventoryMetaItem tool = Utils.selectInventoryTool(VALID_TOOLS);
        if (tool != null) {
            toolItem = tool;
            Utils.consolePrint(this.getClass().getSimpleName() + " will use " + tool.getDisplayName() + " with QL:" + tool.getQuality() + " DMG:" + tool.getDamage());
        }
    }

    private enum InputKey implements Bot.InputKey {
        s("Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold",
                "threshold(float value between 0 and 1)"),
        d("Set the distance the bot should look around player in search for a felled tree",
                "distance(in meters)"),
        c("Set the amount of chops the bot will do each time", "c(integer value)"),
        tool("Set the chopping tool from selected inventory item.", "tool");

        private String description;
        private String usage;
        InputKey(String description, String usage) {
            this.description = description;
            this.usage = usage;
        }

        @Override
        public String getName() {
            return name();
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public String getUsage() {
            return usage;
        }
    }
}
