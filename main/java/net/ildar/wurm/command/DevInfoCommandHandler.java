package net.ildar.wurm.command;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.GroundItemData;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.cell.CreatureCellRenderable;
import com.wurmonline.client.renderer.cell.GroundItemCellRenderable;
import com.wurmonline.client.renderer.gui.InventoryListComponent;
import com.wurmonline.client.renderer.gui.InventoryWindow;
import com.wurmonline.client.renderer.gui.ItemListWindow;
import com.wurmonline.client.renderer.gui.WurmComponent;
import com.wurmonline.mesh.Tiles;
import net.ildar.wurm.Utils;
import net.ildar.wurm.WurmHelper;

import java.util.List;

public class DevInfoCommandHandler {
    public static void register(CommandRegistry registry) {
        registry.register("iteminfo", new SimpleHandler("",
                "Prints information about selected items under mouse cursor.") {
            @Override
            public void handle(String[] args) {
                printItemInformation();
            }
        });

        registry.register("tileinfo", new SimpleHandler("",
                "Prints information about tiles around player") {
            @Override
            public void handle(String[] args) {
                printTileInformation();
            }
        });

        registry.register("playerinfo", new SimpleHandler("",
                "Prints some information about the player") {
            @Override
            public void handle(String[] args) {
                printPlayerInformation();
            }
        });

        registry.register("creatureinfo", new SimpleHandler("",
                "Prints information about the hovered creature") {
            @Override
            public void handle(String[] args) {
                printCreatureInformation();
            }
        });
    }

    private static void printItemInformation() {
        WurmComponent inventoryComponent = Utils.getTargetComponent(c -> c instanceof ItemListWindow || c instanceof InventoryWindow);
        if (inventoryComponent == null) {
            final PickableUnit unit = WurmHelper.hud.getWorld().getCurrentHoveredObject();
            if (unit != null && (unit instanceof GroundItemCellRenderable))
                printGroundItemInfo((GroundItemCellRenderable) unit);
            else
                Utils.consolePrint("Not hovering over any inventory or ground item");
            return;
        }
        InventoryListComponent ilc;
        try {
            ilc = Utils.getField(inventoryComponent, "component");
        } catch (Exception e) {
            Utils.consolePrint("Unable to get inventory information");
            return;
        }
        List<InventoryMetaItem> items = Utils.getSelectedItems(ilc);
        if (items == null || items.size() == 0) {
            Utils.consolePrint("No items are selected");
            return;
        }
        for (InventoryMetaItem item : items)
            printItemInfo(item);
    }

    private static void printTileInformation() {
        int checkedtiles[][] = Utils.getAreaCoordinates();
        for (int[] checkedtile : checkedtiles) {
            Tiles.Tile tileType = WurmHelper.hud.getWorld().getNearTerrainBuffer().getTileType(checkedtile[0], checkedtile[1]);
            Utils.consolePrint("Tile (" + checkedtile[0] + ", " + checkedtile[1] + ") " + tileType.tilename);
        }
    }

    private static void printPlayerInformation() {
        Utils.consolePrint("Player \"" + WurmHelper.hud.getWorld().getPlayer().getPlayerName() + "\"");
        Utils.consolePrint("Stamina: " + WurmHelper.hud.getWorld().getPlayer().getStamina());
        Utils.consolePrint("Damage: " + WurmHelper.hud.getWorld().getPlayer().getDamage());
        Utils.consolePrint("Thirst: " + WurmHelper.hud.getWorld().getPlayer().getThirst());
        Utils.consolePrint("Hunger: " + WurmHelper.hud.getWorld().getPlayer().getHunger());
        Utils.consolePrint("X: " + WurmHelper.hud.getWorld().getPlayerPosX() / 4 + " Y: " + WurmHelper.hud.getWorld().getPlayerPosY() / 4 + " H: " + WurmHelper.hud.getWorld().getPlayerPosH());
        Utils.consolePrint("XRot: " + WurmHelper.hud.getWorld().getPlayerRotX() + " YRot: " + WurmHelper.hud.getWorld().getPlayerRotY());
        Utils.consolePrint("Layer: " + WurmHelper.hud.getWorld().getPlayerLayer());
    }

    private static void printCreatureInformation() {
        try {
            PickableUnit unit = Utils.getField(WurmHelper.hud.getWorld(), "currentHoveredObject");
            if (!(unit instanceof CreatureCellRenderable)) {
                Utils.consolePrint("Not hovering over creature");
                return;
            }
            CreatureCellRenderable creature = (CreatureCellRenderable) unit;

            Utils.consolePrint("Creature `%s`", creature.getHoverName());
            Utils.consolePrint("    ID: %s", creature.getId());
            Utils.consolePrint("    Pos: %s,%s (height %s)", creature.getXPos(), creature.getYPos(), creature.getHPos());
            Utils.consolePrint("    Distance: %s", creature.getLengthFromPlayer());
            Utils.consolePrint("    Layer: %s", creature.getLayer());
            Utils.consolePrint("    Model: %s", creature.getModelName());
            Utils.consolePrint("    Health: %s", creature.getPercentHealth());
        } catch (Exception err) {
            Utils.consolePrint(
                "Got %s when trying to print creature info: %s",
                err.getClass().getName(),
                err.getMessage()
            );
        }
    }

    private static void printItemInfo(InventoryMetaItem item) {
        if (item == null) {
            Utils.consolePrint("Null item");
            return;
        }
        Utils.consolePrint("Item - \"" + item.getBaseName() + " with id " + item.getId());
        Utils.consolePrint(" QL:" + String.format("%.2f", item.getQuality()) + " DMG:" + String.format("%.2f", item.getDamage()) + " Weight:" + item.getWeight());
        Utils.consolePrint(" Rarity:" + item.getRarity() + " Color:" + String.format("(%d,%d,%d)", (int)(item.getR()*255), (int)(item.getG()*255), (int)(item.getB()*255)));
        List<InventoryMetaItem> children = item.getChildren();
        int childCound = children != null ? children.size() : 0;
        Utils.consolePrint(" Improve icon id:" + item.getImproveIconId() + " Child count:" + childCound + " Material id:" + item.getMaterialId());
        Utils.consolePrint(" Aux data:" + item.getAuxData() + " Price:" + item.getPrice() + " Temperature:" + item.getTemperature() + " " + item.getTemperatureStateText());
        Utils.consolePrint(" Custom name:" + item.getCustomName() + " Group name:" + item.getGroupName() + " Display name:" + item.getDisplayName());
        Utils.consolePrint(" Type:" + item.getType() + " Type bits:" + item.getTypeBits() + " Parent id:" + item.getParentId());
        Utils.consolePrint(" Color override:" + item.isColorOverride() + " Marked for update:" + item.isMarkedForUpdate() + " Unfinished:" + item.isUnfinished());
    }

    private static void printGroundItemInfo(GroundItemCellRenderable item) {
        GroundItemData data;
        try {
            data = Utils.getField(item, "item");
        } catch (Exception err) {
            Utils.consolePrint("Couldn't get GroundItemData for ground item");
            return;
        }

        Utils.consolePrint("Ground item \"%s\" (\"%s\") with id %d", data.getName(), data.getHoverText(), item.getId());
        Utils.consolePrint(" Position: %.3f,%.3f (height %.3f) in layer %d", item.getXPos(), item.getYPos(), item.getHPos(), item.getLayer());
        Utils.consolePrint(" Distance from player: %.3fm", item.getLengthFromPlayer());
        Utils.consolePrint(" Color: %d,%d,%d",
            (int)(255 * data.getR()) & 0xFF,
            (int)(255 * data.getG()) & 0xFF,
            (int)(255 * data.getB()) & 0xFF
        );
        Utils.consolePrint(" Model name: %s", data.getModelName());
        Utils.consolePrint(" Description: \"%s\"", data.getDescription());
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
    }
}
