package net.ildar.wurm.bot;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.gui.CreationWindow;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.Utils;
import net.ildar.wurm.annotations.BotInfo;

@BotInfo(name = "Prospector", description =
        "Prospects selected tile",
        abbreviation = "pr")
public class ProspectorBot extends Bot {
    private float staminaThreshold;
    private int clicks;
    private InventoryMetaItem toolItem;
    private static final String[] VALID_TOOLS = {"pickaxe"};

    public ProspectorBot() {
        registerInputHandler(ProspectorBot.InputKey.s, this::setStaminaThreshold);
        registerInputHandler(ProspectorBot.InputKey.c, this::setClicksNumber);
        registerInputHandler(ProspectorBot.InputKey.tool, input -> selectTool());
    }

    @Override
    public void work() throws Exception{
        if (toolItem == null)
            toolItem = Utils.locateToolItem("pickaxe");
        long pickaxeId;
        if (toolItem == null) {
            Utils.consolePrint("You don't have a pickaxe");
            deactivate();
            return;
        } else {
            pickaxeId = toolItem.getId();
            Utils.consolePrint(this.getClass().getSimpleName() + " will use " + toolItem.getBaseName());
        }
        PickableUnit pickableUnit = Utils.getField(WurmHelper.hud.getSelectBar(), "selectedUnit");
        if (pickableUnit == null) {
            Utils.consolePrint("Select cave wall!");
            deactivate();
            return;
        } else
            Utils.consolePrint(this.getClass().getSimpleName() + " will prospect " + pickableUnit.getHoverName());
        long caveWallId = pickableUnit.getId();
        CreationWindow creationWindow = WurmHelper.hud.getCreationWindow();
        Object progressBar = Utils.getField(creationWindow, "progressBar");
        setStaminaThreshold(0.9f);
        setClicks(3);
        while (isActive()) {
            waitOnPause();
            float stamina = WurmHelper.hud.getWorld().getPlayer().getStamina();
            float damage = WurmHelper.hud.getWorld().getPlayer().getDamage();
            float progress = Utils.getField(progressBar, "progress");
            if ((stamina+damage) > staminaThreshold && progress == 0f) {
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
            Utils.consolePrint(this.getClass().getSimpleName() + " will use " + tool.getDisplayName() + " with QL:" + tool.getQuality() + " DMG:" + tool.getDamage());
        }
    }

    private void setStaminaThreshold(String input[]) {
        if (input == null || input.length != 1)
            printInputKeyUsageString(ProspectorBot.InputKey.s);
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

    private void setClicksNumber(String []input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(ProspectorBot.InputKey.c);
            return;
        }
        try {
            setClicks(Integer.parseInt(input[0]));
        } catch (NumberFormatException e) {
            Utils.consolePrint("Bad value!");
        }
    }

    private void setClicks(int n) {
        if (n < 1) n = 1;
        if (n > 10) n = 10;
        clicks = n;
        Utils.consolePrint(getClass().getSimpleName() + " will do " + clicks + " clicks each time");
    }

    private enum InputKey implements Bot.InputKey {
        s("Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold",
                "threshold(float value between 0 and 1)"),
        c("Change the amount of clicks bot will do each time", "n(integer value)"),
        tool("Set the prospecting tool from selected inventory item.", "tool");

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
