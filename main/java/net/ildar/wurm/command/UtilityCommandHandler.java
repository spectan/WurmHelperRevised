package net.ildar.wurm.command;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.gui.InventoryListComponent;
import com.wurmonline.client.renderer.gui.InventoryWindow;
import com.wurmonline.client.renderer.gui.ItemListWindow;
import com.wurmonline.client.renderer.gui.WurmComponent;
import com.wurmonline.client.util.Computer;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.QuickAction;
import net.ildar.wurm.Utils;
import net.ildar.wurm.WurmHelper;

import java.util.ArrayList;
import java.util.List;

public class UtilityCommandHandler {

    public static void register(CommandRegistry registry) {
        registry.register("sleep", new SimpleHandler("timeout(milliseconds)",
                "Freezes the game for specified time.") {
            @Override
            public void handle(String[] args) {
                if (args != null && args.length == 1) {
                    try {
                        Thread.sleep(Long.parseLong(args[0]));
                    } catch (NumberFormatException e) {
                        Utils.consolePrint("Bad value");
                    } catch (InterruptedException e) {
                        Utils.consolePrint("Interrupted");
                    }
                } else {
                    printUsage("sleep");
                }
            }
        });

        registry.register("combine", new SimpleHandler("",
                "Combines selected items in your inventory.") {
            @Override
            public void handle(String[] args) {
                long[] itemsToCombine = WurmHelper.hud.getInventoryWindow().getInventoryListComponent().getSelectedCommandTargets();
                if (itemsToCombine == null || itemsToCombine.length == 0) {
                    Utils.consolePrint("No selected items!");
                    return;
                }
                WurmHelper.hud.getWorld().getServerConnection().sendAction(itemsToCombine[0], itemsToCombine, PlayerAction.COMBINE);
            }
        });

        registry.register("getid", new SimpleHandler("",
                "Copy the id of hovered object to the clipboard") {
            @Override
            public void handle(String[] args) {
                int x = WurmHelper.hud.getWorld().getClient().getXMouse();
                int y = WurmHelper.hud.getWorld().getClient().getYMouse();
                long[] ids = WurmHelper.hud.getCommandTargetsFrom(x, y);
                Long targetId = null;
                if (ids != null && ids.length > 0) {
                    targetId = ids[0];
                } else {
                    PickableUnit pickableUnit = WurmHelper.hud.getWorld().getCurrentHoveredObject();
                    if (pickableUnit != null) {
                        targetId = pickableUnit.getId();
                    }
                }
                if (targetId != null) {
                    String idStr = String.valueOf(targetId);
                    Computer.setClipboardContents(idStr);
                    Utils.showOnScreenMessage("The item id was added to clipboard");
                    Utils.consolePrint("Copied ID %s to clipboard", idStr);
                } else {
                    Utils.showOnScreenMessage("Hover the mouse over the item first");
                }
            }
        });

        registry.register("mts", new SimpleHandler("item_name favor_level [coefficient]",
                "Move specified items to opened altar inventory. " +
                "The amount of moved items depends on specified favor (with coefficient) you want to get from these items when you sacrifice them.") {
            @Override
            public void handle(String[] args) {
                if (args == null || args.length < 2) {
                    printUsage("mts");
                    return;
                }
                float coefficient = 1;
                if (args.length == 3) {
                    try {
                        coefficient = Float.parseFloat(args[2]);
                    } catch (NumberFormatException e) {
                        Utils.consolePrint("Wrong coefficient value. Should be float");
                        return;
                    }
                }
                float favorLevel;
                try {
                    favorLevel = Float.parseFloat(args[1]);
                } catch (NumberFormatException e) {
                    Utils.consolePrint("Invalid float level number. Should be float");
                    return;
                }
                moveToSacrifice(args[0], favorLevel, coefficient);
            }
        });

        registry.register("action", new SimpleHandler("abbreviation",
                "Use the appropriate tool from player's inventory with provided action abbreviation on the hovered object. " +
                "See the list of available actions with \"actionlist\" command") {
            @Override
            public void handle(String[] args) {
                if (args == null || args.length == 0) {
                    printUsage("action");
                    return;
                }
                StringBuilder abbreviation = new StringBuilder(args[0]);
                for (int i = 1; i < args.length; i++) {
                    abbreviation.append(" ").append(args[i]);
                }
                QuickAction action = QuickAction.getByAbbreviation(abbreviation.toString());
                if (action == null) {
                    Utils.consolePrint("Unknown action abbreviation - " + abbreviation.toString());
                    showActionList();
                    return;
                }
                InventoryMetaItem toolItem = Utils.locateToolItem(action.toolName);
                if (toolItem == null && action == QuickAction.Butcher) {
                    Utils.consolePrint("A player don't have " + QuickAction.Butcher.toolName + ", trying to find carving knife...");
                    toolItem = Utils.locateToolItem("carving knife");
                    if (toolItem == null)
                        Utils.consolePrint("But the player don't have a carving knife too");
                }
                if (toolItem == null) {
                    Utils.consolePrint("A player don't have " + action.toolName);
                    return;
                }
                int x = WurmHelper.hud.getWorld().getClient().getXMouse();
                int y = WurmHelper.hud.getWorld().getClient().getYMouse();
                long[] ids = WurmHelper.hud.getCommandTargetsFrom(x, y);
                if (ids != null && ids.length > 0)
                    WurmHelper.hud.getWorld().getServerConnection().sendAction(toolItem.getId(), new long[]{ids[0]}, action.playerAction);
                else {
                    PickableUnit pickableUnit = WurmHelper.hud.getWorld().getCurrentHoveredObject();
                    if (pickableUnit != null)
                        WurmHelper.hud.getWorld().getServerConnection().sendAction(toolItem.getId(), new long[]{pickableUnit.getId()}, action.playerAction);
                }
            }
        });

        registry.register("actionlist", new SimpleHandler("",
                "Show the list of available actions to use with \"action\" key") {
            @Override
            public void handle(String[] args) {
                showActionList();
            }
        });
    }

    private static void showActionList() {
        for (QuickAction action : QuickAction.values()) {
            Utils.consolePrint("\"" + action.abbreviation + "\" is to " + action.name() + " with tool \"" + action.toolName + "\"");
        }
    }

    private static void moveToSacrifice(String itemName, float favorLevel, float coefficient) {
        WurmComponent inventoryComponent = Utils.getTargetComponent(c -> c instanceof ItemListWindow || c instanceof InventoryWindow);
        if (inventoryComponent == null) {
            Utils.consolePrint("Didn't find an inventory under the mouse cursor");
            return;
        }
        InventoryListComponent ilc;
        try {
            ilc = Utils.getField(inventoryComponent, "component");
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        List<InventoryMetaItem> items = Utils.getInventoryItems(itemName);
        if (items == null || items.size() == 0) {
            Utils.consolePrint("No items");
            return;
        }
        List<InventoryMetaItem> itemsToMove = new ArrayList<>();
        float favor = WurmHelper.hud.getWorld().getPlayer().getSkillSet().getSkillValue("favor");
        for (InventoryMetaItem item : items) {
            if (favor >= favorLevel) break;
            itemsToMove.add(item);
            favor += Utils.itemFavor(item, coefficient);
        }
        if (itemsToMove.size() == 0) {
            Utils.consolePrint("No items to move");
            return;
        }
        List<WurmComponent> components = WurmHelper.getInstance().components;
        if (components == null) {
            Utils.consolePrint("Components list is empty!");
            return;
        }
        for (WurmComponent component : components) {
            if (component instanceof ItemListWindow) {
                try {
                    ilc = Utils.getField(component, "component");
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
                InventoryMetaItem rootItem = Utils.getRootItem(ilc);
                if (rootItem == null) {
                    Utils.consolePrint("Internal error on moving items");
                    return;
                }
                if (!rootItem.getBaseName().contains("altar of")) continue;
                if (rootItem.getChildren() != null && rootItem.getChildren().size() > 0) {
                    Utils.showOnScreenMessage("An altar is not empty!");
                    return;
                }
                WurmHelper.hud.getWorld().getServerConnection().sendMoveSomeItems(rootItem.getId(), Utils.getItemIds(itemsToMove));
                WurmHelper.hud.sendAction(PlayerAction.SACRIFICE, rootItem.getId());
                return;
            }
        }
        Utils.consolePrint("Didn't find an opened altar");
    }

    private static abstract class SimpleHandler implements ConsoleCommandHandler {
        private final String usage;
        private final String description;

        SimpleHandler(String usage, String description) {
            this.usage = usage;
            this.description = description;
        }

        @Override
        public String getUsage() {
            return usage;
        }

        @Override
        public String getDescription() {
            return description;
        }

        protected void printUsage(String name) {
            Utils.consolePrint("Usage: " + name + " " + usage);
        }
    }
}
