package net.ildar.wurm.bot;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.Utils;
import net.ildar.wurm.annotations.BotInfo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@BotInfo(name = "Forage Stuff Mover", description =
        "Moves foragable and botanizable items from your inventory to the target inventories. " +
        "Optionally you can toggle the moving of rocks or rare items on and off.",
        abbreviation = "fsm")
public class ForageStuffMoverBot extends Bot {
    private final List<Long> targets = new CopyOnWriteArrayList<>();
    private boolean moveRareItems;
    private boolean notMoveRocks;

    public ForageStuffMoverBot() {
        registerInputHandler(ForageStuffMoverBot.InputKey.at, input -> addTarget());
        registerInputHandler(ForageStuffMoverBot.InputKey.r, input -> toggleMovingRareItems());
        registerInputHandler(ForageStuffMoverBot.InputKey.mr, input -> toggleMovingRocks());
    }

    @Override
    public void work() throws Exception{
        String lastStatus = null;
        while (isActive()) {
            waitOnPause();
            List<InventoryMetaItem> foragables = Utils.getSelectedItems(WurmHelper.hud.getInventoryWindow().getInventoryListComponent(), true, true);
            List<InventoryMetaItem> moveList = foragables.stream()
                    .filter(item -> ForagerBot.isForagable(item) && !(notMoveRocks && item.getBaseName().contains("rock")))
                    .filter(item -> moveRareItems || item.getRarity() == 0)
                    .limit(100)
                    .collect(Collectors.toList());
            String status = null;
            if (moveList.size() == 0) {
                status = "Nothing to move";
            } else if (targets.size() == 0) {
                status = "No target containers to move to";
            } else {
                long[] moveIds = Utils.getItemIds(moveList);
                // sending the same items to every target would just shuffle them into the last one
                WurmHelper.hud.getWorld().getServerConnection().sendMoveSomeItems(targets.get(0), moveIds);
            }
            if (status != null && !status.equals(lastStatus))
                Utils.consolePrint(status);
            lastStatus = status;
            sleep(timeout);
        }
    }

    private void addTarget() {
        int x = WurmHelper.hud.getWorld().getClient().getXMouse();
        int y = WurmHelper.hud.getWorld().getClient().getYMouse();
        long[] targets = WurmHelper.hud.getCommandTargetsFrom(x, y);
        if (targets != null && targets.length > 0) {
            long target = targets[0];
            this.targets.add(target);
            Utils.consolePrint("New target is " + target);
        } else
            Utils.consolePrint("Can't find the target");
    }

    private void toggleMovingRareItems() {
        moveRareItems = !moveRareItems;
        Utils.consolePrint("Rare items will be " + (moveRareItems?"":"NOT") + " moved");
    }

    private void toggleMovingRocks() {
        notMoveRocks = !notMoveRocks;
        Utils.consolePrint("Rocks will be " + (notMoveRocks?"NOT":"") + " moved");
    }

    enum InputKey implements Bot.InputKey {
        at("Add Target", "Add new target item. Foragable and botanizable items will be moved to that destination", ""),
        r("Toggle Rares", "Toggle moving of rare items", ""),
        mr("Toggle Rocks", "Toggle moving of rocks", "");

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