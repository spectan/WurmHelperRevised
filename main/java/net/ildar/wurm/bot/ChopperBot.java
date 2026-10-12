package net.ildar.wurm.bot;

import com.wurmonline.client.comm.ServerConnectionListenerClass;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.GroundItemData;
import com.wurmonline.client.renderer.cell.GroundItemCellRenderable;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.Utils;
import net.ildar.wurm.annotations.BotInfo;

import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;

@BotInfo(name = "Chopper", description =
        "Automatically chops felled trees near player",
        abbreviation = "ch")
public class ChopperBot extends Bot {
    private volatile float distance = 4;
    private AreaAssistant areaAssistant = new AreaAssistant(this);
    private volatile int clicks;
    private volatile InventoryMetaItem toolItem;
    private static final String[] VALID_TOOLS = {"hatchet"};

    public ChopperBot() {
        registerStaminaThresholdHandler(ChopperBot.InputKey.s);
        registerInputHandler(ChopperBot.InputKey.d, this::setDistance);
        registerInputHandler(ChopperBot.InputKey.c, this::setClickNumber);
        registerInputHandler(ChopperBot.InputKey.tool, input -> selectTool());

        areaAssistant.setMoveAheadDistance(1);
        areaAssistant.setMoveRightDistance(1);
        staminaThreshold = 0.96f;
        clicks = Math.max(1, Utils.getMaxActionNumber());
    }

    @Override
    void describeSettings(List<String> lines) {
        lines.add("Distance: " + distance + " meters");
        lines.add("Clicks: " + clicks);
        InventoryMetaItem tool = toolItem;
        lines.add("Tool: " + (tool != null ? tool.getDisplayName() : "any hatchet in the inventory"));
        areaAssistant.describeSettings(lines);
    }

    @Override
    public void work() throws Exception{
        if (toolItem == null)
            toolItem = Utils.locateToolItem("hatchet");
        long hatchetId;
        if (toolItem == null) {
            Utils.consolePrint("You don't have a hatchet! Put one in your inventory, or select one and use \"bot ch tool\", then start the bot again");
            deactivate();
            return;
        } else {
            hatchetId = toolItem.getId();
            Utils.consolePrint(this.getClass().getSimpleName() + " will use " + toolItem.getDisplayName() + " to chop felled trees.");
            Utils.consolePrint("QL:" + toolItem.getQuality() + " DMG:" + toolItem.getDamage());
        }
        ServerConnectionListenerClass sscc = WurmHelper.hud.getWorld().getServerConnection().getServerConnectionListener();
        while (isActive()) {
            waitOnPause();
            if (canDoWork(staminaThreshold)) {
                Map<Long, GroundItemCellRenderable> groundItems = Utils.getField(sscc, "groundItems");
                float x = WurmHelper.hud.getWorld().getPlayerPosX();
                float y = WurmHelper.hud.getWorld().getPlayerPosY();
                boolean didSomething = false;
                if (groundItems != null && groundItems.size() > 0) {
                    try {
                        for (Map.Entry<Long, GroundItemCellRenderable> entry : groundItems.entrySet()) {
                            GroundItemData groundItemData = Utils.getField(entry.getValue(), "item");
                            float itemX = groundItemData.getX();
                            float itemY = groundItemData.getY();
                            if (Math.pow(itemX - x, 2) + Math.pow(itemY - y, 2) <= distance * distance)
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
                if (!didSomething && areaAssistant.areaTourActivated()) {
                    areaAssistant.areaNextPosition();
                    continue;
                }
            }
            sleep(timeout);
        }
    }

    private void setDistance(String[] input) {
        Float value = parseFloatArg(input, ChopperBot.InputKey.d, 0.1f, 100);
        if (value == null)
            return;
        distance = value;
        Utils.consolePrint("New lookup distance is " + distance + " meters");
    }

    private void setClickNumber(String[] input) {
        Integer value = parseIntArg(input, ChopperBot.InputKey.c, 1, 100);
        if (value != null)
            setClicks(value);
    }

    private void setClicks(int clicks) {
        this.clicks = clicks;
        Utils.consolePrint(getClass().getSimpleName() + " will do " + clicks + " chops each time");
    }

    private void selectTool() {
        InventoryMetaItem tool = Utils.selectInventoryTool(VALID_TOOLS);
        if (tool != null) {
            toolItem = tool;
            Utils.feedback(this.getClass().getSimpleName() + " will use " + tool.getDisplayName() + " with QL:" + tool.getQuality() + " DMG:" + tool.getDamage());
        }
    }

    private enum InputKey implements Bot.InputKey {
        s("Stamina", "Set the stamina threshold (0 to 1, or a percentage). Player will not do any actions if his stamina is lower than specified threshold",
                "<threshold>"),
        d("Distance", "Set the distance (in meters) the bot should look around player in search for a felled tree",
                "<distance>"),
        c("Clicks", "Set the amount of chops the bot will do each time", "<clicks>"),
        tool("Tool", "Set the chopping tool from the selected inventory item", "");

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
