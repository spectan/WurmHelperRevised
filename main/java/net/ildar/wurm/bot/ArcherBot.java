package net.ildar.wurm.bot;

import com.wurmonline.client.comm.ServerConnectionListenerClass;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.cell.CreatureCellRenderable;
import com.wurmonline.client.renderer.gui.CreationWindow;
import com.wurmonline.client.renderer.gui.PaperDollInventory;
import com.wurmonline.client.renderer.gui.PaperDollSlot;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.Utils;
import net.ildar.wurm.annotations.BotInfo;

import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;

@BotInfo(name = "Archer", description =
        "Automatically shoots at selected target with currently equipped bow. " +
        "When the string breaks tries to place a new one. " +
        "Deactivates on target death.",
        abbreviation = "ar")
public class ArcherBot extends Bot {
    private volatile boolean stringBreaks;

    private InventoryMetaItem bow;

    public ArcherBot() {
        registerStaminaThresholdHandler(ArcherBot.InputKey.s);
        registerInputHandler(ArcherBot.InputKey.string, input -> stringTheBow());
        staminaThreshold = 0.9f;
    }

    @Override
    void describeSettings(List<String> lines) {
        lines.add("Restring the bow: " + (stringBreaks ? "pending" : "no"));
    }

    @Override
    public void work() throws Exception{
        PaperDollInventory pdi = Utils.getField(WurmHelper.hud, "paperdollInventory");
        Map<Long, PaperDollSlot> frameList = Utils.getField(pdi, "frameList");
        for (Map.Entry<Long, PaperDollSlot> frame : frameList.entrySet()) {
            PaperDollSlot slot = frame.getValue();
            if (slot == null || slot.getEquippedItem() == null) continue;
            if (slot.getEquipmentSlot() == 1) {
                bow = slot.getEquippedItem().getItem();
                Utils.consolePrint(this.getClass().getSimpleName() + " will use " + bow.getDisplayName() + " with QL:" + bow.getQuality() + " DMG:" + bow.getDamage());
            }
        }
        if (bow == null) {
            Utils.consolePrint("Equip a bow first, then start the bot again");
            deactivate();
            return;
        }

        PickableUnit pickableUnit = Utils.getField(WurmHelper.hud.getSelectBar(), "selectedUnit");
        if (pickableUnit == null){
            Utils.consolePrint("Select a creature or an archery target (click it so it shows in the select bar), then start the bot again");
            deactivate();
            return;
        }
        Utils.consolePrint(this.getClass().getSimpleName() + " will shoot at " + pickableUnit.getHoverName());
        long mobId = pickableUnit.getId();
        boolean isArcheryTarget=pickableUnit.getHoverName().contains("archery target");

        int maxActions = Utils.getMaxActionNumber();
        CreationWindow creationWindow = WurmHelper.hud.getCreationWindow();
        registerEventProcessors();
        while (isActive()) {
            waitOnPause();
            if (canDoWork(staminaThreshold) && creationWindow.getActionInUse() == 0) {
                if (stringBreaks) {
                        InventoryMetaItem bowstring = Utils.getInventoryItem("bow string");
                        if (bowstring != null) {
                            WurmHelper.hud.getWorld().getServerConnection().sendAction(bowstring.getId(),
                                    new long[]{bow.getId()}, new PlayerAction("",(short) 132, PlayerAction.ANYTHING));//change bowstring
                        }
                }
                for (int i = 0; i < maxActions; i++)
                    WurmHelper.hud.getWorld().getServerConnection().sendAction(bow.getId(), new long[]{mobId}, (!isArcheryTarget ? PlayerAction.SHOOT : new PlayerAction("",(short) 134, PlayerAction.ANYTHING)));

                ServerConnectionListenerClass sscc = WurmHelper.hud.getWorld().getServerConnection().getServerConnectionListener();
                Map<Long, CreatureCellRenderable> creatures = Utils.getField(sscc, "creatures");
                boolean mobAlive = false;
                if (creatures!=null && !isArcheryTarget) {
                    try {
                        for(Map.Entry<Long, CreatureCellRenderable> entry:creatures.entrySet())
                            if (entry.getValue().getId() == mobId) mobAlive = true;
                    } catch (ConcurrentModificationException e) {
                        mobAlive = true;
                    }
                }
                if (!mobAlive && !isArcheryTarget){
                    Utils.consolePrint("Mob dead or too far away!");
                    Utils.showOnScreenMessage("Deactivating archerbot!");
                    deactivate();
                }
            }
            sleep(timeout);
        }
    }

    private void registerEventProcessors() {
        registerEventProcessor(message -> message.contains("You string the "), () -> stringBreaks = false);
        registerEventProcessor(message -> message.contains("The string breaks!"), () -> stringBreaks = true);
        registerMessageProcessor(":Combat", message -> message.contains("The string breaks!"), () -> stringBreaks = true);
    }

    private void stringTheBow() {
        Utils.feedback(getClass().getSimpleName() + " will try to string the bow" + (isAlive() ? "" : " when it starts"));
        stringBreaks = true;
    }

    private enum InputKey implements Bot.InputKey {
        s("Stamina", "Set the stamina threshold (0 to 1, or a percentage). Player will not do any actions if his stamina is lower than specified threshold",
                "<threshold>"),
        string("String", "String the equipped bow with a bow string from the inventory",
                "");

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
