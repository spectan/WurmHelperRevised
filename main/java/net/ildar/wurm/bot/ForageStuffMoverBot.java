package net.ildar.wurm.bot;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.gui.InventoryListComponent;
import com.wurmonline.client.renderer.gui.InventoryWindow;
import com.wurmonline.client.renderer.gui.ItemListWindow;
import com.wurmonline.client.renderer.gui.WurmComponent;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.Utils;
import net.ildar.wurm.annotations.BotInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@BotInfo(name = "Forage Stuff Mover", description =
        "Moves foragable and botanizable items from your inventory to the target inventories. " +
        "Optionally you can toggle the moving of rocks or rare items on and off.",
        abbreviation = "fsm")
public class ForageStuffMoverBot extends Bot {
    private final List<Long> targets = new CopyOnWriteArrayList<>();
    private final Map<Long, String> targetNames = new ConcurrentHashMap<>();
    private volatile boolean moveRareItems;
    private volatile boolean notMoveRocks;

    public ForageStuffMoverBot() {
        registerInputHandler(ForageStuffMoverBot.InputKey.at, input -> addTarget());
        registerInputHandler(ForageStuffMoverBot.InputKey.r, input -> toggleMovingRareItems());
        registerInputHandler(ForageStuffMoverBot.InputKey.mr, input -> toggleMovingRocks());
    }

    @Override
    void describeSettings(List<String> lines) {
        if (targets.isEmpty())
            lines.add("Target: none (hover over a container and use \"at\")");
        else
            lines.add("Target: " + targetNames.getOrDefault(targets.get(0), String.valueOf(targets.get(0))));
        lines.add("Move rare items: " + onOff(moveRareItems));
        lines.add("Move rocks: " + onOff(!notMoveRocks));
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
            String name = getTargetName(target, x, y);
            targetNames.put(target, name);
            if (!this.targets.contains(target))
                this.targets.add(target);
            Utils.feedback("New target is " + name);
        } else
            Utils.consolePrint("Can't find the target. Hover the mouse over a container and try again");
    }

    /**
     * Find a readable name for the hovered target: a world object or an item in an opened inventory window
     */
    private String getTargetName(long target, int x, int y) {
        try {
            PickableUnit hovered = WurmHelper.hud.getWorld().getCurrentHoveredObject();
            if (hovered != null && hovered.getId() == target)
                return hovered.getHoverName();
            WurmComponent window = Utils.getComponentAtPoint(x, y, c -> c instanceof ItemListWindow || c instanceof InventoryWindow);
            if (window != null) {
                InventoryListComponent ilc = Utils.getField(window, "component");
                for (InventoryMetaItem item : Utils.getInventoryItemsAtPoint(ilc, x, y))
                    if (item.getId() == target)
                        return item.getDisplayName();
            }
        } catch (Exception ignored) {
        }
        return "item " + target;
    }

    private void toggleMovingRareItems() {
        moveRareItems = !moveRareItems;
        Utils.feedback("Rare items will " + (moveRareItems ? "" : "NOT ") + "be moved");
    }

    private void toggleMovingRocks() {
        notMoveRocks = !notMoveRocks;
        Utils.feedback("Rocks will " + (notMoveRocks ? "NOT " : "") + "be moved");
    }

    enum InputKey implements Bot.InputKey {
        at("Add Target", "Add the container under the mouse as a target. Foragable and botanizable items will be moved to the first target added", ""),
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