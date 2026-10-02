package net.ildar.wurm.bot;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.Utils;
import net.ildar.wurm.annotations.BotInfo;

import java.util.List;

@BotInfo(name = "Prospector", description =
        "Prospects selected tile",
        abbreviation = "pr")
public class ProspectorBot extends Bot {
    private volatile int clicks;
    private volatile InventoryMetaItem toolItem;
    private static final String[] VALID_TOOLS = {"pickaxe"};

    public ProspectorBot() {
        registerStaminaThresholdHandler(ProspectorBot.InputKey.s);
        registerInputHandler(ProspectorBot.InputKey.c, this::setClicksNumber);
        registerInputHandler(ProspectorBot.InputKey.tool, input -> selectTool());
        staminaThreshold = 0.9f;
        clicks = 3;
    }

    @Override
    void describeSettings(List<String> lines) {
        lines.add("Clicks: " + clicks);
        InventoryMetaItem tool = toolItem;
        lines.add("Tool: " + (tool != null ? tool.getDisplayName() : "any pickaxe in the inventory"));
    }

    @Override
    public void work() throws Exception{
        if (toolItem == null)
            toolItem = Utils.locateToolItem("pickaxe");
        long pickaxeId;
        if (toolItem == null) {
            Utils.consolePrint("You don't have a pickaxe! Put one in your inventory, or select one and use \"bot pr tool\", then start the bot again");
            deactivate();
            return;
        } else {
            pickaxeId = toolItem.getId();
            Utils.consolePrint(this.getClass().getSimpleName() + " will use " + toolItem.getBaseName());
        }
        PickableUnit pickableUnit = Utils.getField(WurmHelper.hud.getSelectBar(), "selectedUnit");
        if (pickableUnit == null) {
            Utils.consolePrint("Select a cave wall (click it so it shows in the select bar), then start the bot again");
            deactivate();
            return;
        } else
            Utils.consolePrint(this.getClass().getSimpleName() + " will prospect " + pickableUnit.getHoverName());
        long caveWallId = pickableUnit.getId();
        while (isActive()) {
            waitOnPause();
            if (canDoWork(staminaThreshold)) {
                if (toolItem.getDamage() > 10)
                    WurmHelper.hud.sendAction(PlayerAction.REPAIR, pickaxeId);
                for(int i = 0; i < clicks; i++)
                    WurmHelper.hud.getWorld().getServerConnection().sendAction(pickaxeId, new long[]{caveWallId}, PlayerAction.PROSPECT);
            }
            sleep(timeout);
        }
    }

    private void selectTool() {
        InventoryMetaItem tool = Utils.selectInventoryTool(VALID_TOOLS);
        if (tool != null) {
            toolItem = tool;
            Utils.feedback(this.getClass().getSimpleName() + " will use " + tool.getDisplayName() + " with QL:" + tool.getQuality() + " DMG:" + tool.getDamage());
        }
    }

    private void setClicksNumber(String []input) {
        Integer value = parseIntArg(input, ProspectorBot.InputKey.c, 1, 10);
        if (value == null)
            return;
        clicks = value;
        Utils.consolePrint(getClass().getSimpleName() + " will do " + clicks + " clicks each time");
    }

    private enum InputKey implements Bot.InputKey {
        s("Stamina", "Set the stamina threshold (0 to 1, or a percentage). Player will not do any actions if his stamina is lower than specified threshold",
                "<threshold>"),
        c("Clicks", "Change the amount of clicks (1 to 10) the bot will do each time", "<clicks>"),
        tool("Tool", "Set the prospecting tool from the selected inventory item", "");

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
