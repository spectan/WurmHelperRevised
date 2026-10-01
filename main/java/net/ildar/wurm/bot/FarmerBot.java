package net.ildar.wurm.bot;

import com.wurmonline.client.game.World;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.mesh.FieldData;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.Utils;
import net.ildar.wurm.annotations.BotInfo;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@BotInfo(name = "Farmer", description =
        "Tends the fields, plants the seeds, cultivates the ground, collects harvests",
        abbreviation = "f")
public class FarmerBot extends Bot {
    private AreaAssistant areaAssistant = new AreaAssistant(this);
    private boolean farmTending;
    private InventoryMetaItem rakeItem;
    private boolean harvesting;
    private InventoryMetaItem scytheItem;
    private boolean planting;
    private String seedsName;
    private boolean cultivating;
    private InventoryMetaItem shovelItem;
    private boolean dropping;
    private boolean repairing = true;
    private final List<String> dropNamesList = new CopyOnWriteArrayList<>();
    private int dropLimit;
    private boolean noSeedsReported;

    public FarmerBot() {
        registerStaminaThresholdHandler(FarmerBot.InputKey.s);
        registerInputHandler(FarmerBot.InputKey.ft, input -> toggleFarmTending());
        registerInputHandler(FarmerBot.InputKey.h, input -> toggleHarvesting());
        registerInputHandler(FarmerBot.InputKey.p, this::togglePlanting);
        registerInputHandler(FarmerBot.InputKey.c, input -> toggleCultivating());
        registerInputHandler(FarmerBot.InputKey.d, input -> toggleDropping());
        registerInputHandler(FarmerBot.InputKey.and, this::addDropItemName);
        registerInputHandler(FarmerBot.InputKey.r, input -> toggleRepairing());
        registerInputHandler(FarmerBot.InputKey.dl, this::setDropLimit);
    }

    @Override
    protected void work() throws Exception {
        setStaminaThreshold(0.9f);
        setTimeout(500);
        int maxActions = Utils.getMaxActionNumber();
        World world = WurmHelper.hud.getWorld();
        Set<String> cultivatedTiles = new HashSet<>(Arrays.asList(
                Tiles.Tile.TILE_STEPPE.tilename, Tiles.Tile.TILE_MOSS.tilename, Tiles.Tile.TILE_DIRT_PACKED.tilename));
        while (isActive()) {
            waitOnPause();
            if (canDoWork(staminaThreshold)) {
                int checkedtiles[][] = Utils.getAreaCoordinates();
                int initiatedActions = 0;
                int tileIndex = -1;

                List<InventoryMetaItem> seeds = null;
                int usedSeeds = 0;
                if (planting) {
                    seeds = Utils.getInventoryItems(seedsName);
                    if (seeds == null || seeds.size() == 0) {
                        if (!noSeedsReported)
                            Utils.consolePrint("The player doesn't have any seeds left to plant");
                        noSeedsReported = true;
                    } else
                        noSeedsReported = false;
                }
                if (cultivating)
                    checkToolDamage(shovelItem);
                if (farmTending)
                    checkToolDamage(rakeItem);
                if (harvesting)
                    checkToolDamage(scytheItem);
                while(++tileIndex < checkedtiles.length && initiatedActions < maxActions) {
                    Tiles.Tile tileType = world.getNearTerrainBuffer().getTileType(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);
                    byte tileData = world.getNearTerrainBuffer().getData(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);
                    if (cultivating) {
                        if (!tileType.isTree() && !tileType.isBush() && (tileType.isGrass() || cultivatedTiles.contains(tileType.tilename))) {
                            world.getServerConnection().sendAction(shovelItem.getId(),
                                    new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                    PlayerAction.CULTIVATE);
                            initiatedActions++;
                            continue;
                        }
                    }
                    if (farmTending){
                        if (tileType == com.wurmonline.mesh.Tiles.Tile.TILE_FIELD || tileType == com.wurmonline.mesh.Tiles.Tile.TILE_FIELD2)
                            if (!com.wurmonline.mesh.FieldData.isTended(tileData)) {
                                world.getServerConnection().sendAction(rakeItem.getId(),
                                        new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                        PlayerAction.FARM);
                                initiatedActions++;
                                continue;
                            }
                    }
                    if (harvesting) {
                        if (tileType == com.wurmonline.mesh.Tiles.Tile.TILE_FIELD || tileType == com.wurmonline.mesh.Tiles.Tile.TILE_FIELD2)
                            if (FieldData.getAgeName(tileData).equals("ripe")) {
                                world.getServerConnection().sendAction(scytheItem.getId(),
                                        new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                        PlayerAction.HARVEST);
                                initiatedActions++;
                                continue;
                            }
                    }
                    if (planting && seeds != null && usedSeeds < seeds.size()) {
                        if (tileType == Tiles.Tile.TILE_DIRT) {
                            world.getServerConnection().sendAction(seeds.get(usedSeeds++).getId(),
                                    new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                    PlayerAction.SOW);
                            initiatedActions++;
                            continue;
                        }
                    }
                }
                if (initiatedActions == 0)
                    areaAssistant.areaNextPosition();
            }
            if (dropping) {
                List<InventoryMetaItem> droplist = new ArrayList<>();
                List<InventoryMetaItem> inventoryItems = Utils.getSelectedItems(true, true);
                for(String dropName : dropNamesList)
                    droplist.addAll(Utils.getInventoryItems(inventoryItems, dropName));
                if (droplist.size() > 0) {
                    if (dropLimit != 0) {
                        if (droplist.size() > dropLimit) {
                            droplist = droplist.subList(dropLimit, droplist.size());
                        } else
                            droplist = new ArrayList<>();
                    }
                    droplist = droplist.stream().filter(item -> item.getRarity() == 0).collect(Collectors.toList());
                    if (droplist.size() > 0)
                        WurmHelper.hud.sendAction(PlayerAction.DROP, Utils.getItemIds(droplist));
                }
            }
            sleep(timeout);
        }
    }

    private void checkToolDamage(InventoryMetaItem toolItem) {
        if (repairing && toolItem.getDamage() > 10)
            WurmHelper.hud.sendAction(PlayerAction.REPAIR, toolItem.getId());
    }

    private void addDropItemName(String []input) {
        if (!dropping) {
            Utils.consolePrint("The dropping is off. Can't add new item name to drop");
            return;
        }
        if (input == null || input.length == 0) {
            printInputKeyUsageString(FarmerBot.InputKey.and);
            return;
        }
        String name = String.join(" ", input);
        dropNamesList.add(name);
        Utils.consolePrint("New name of item to drop was added - \"" + name + "\"");
    }
    private void toggleDropping() {
        dropping = !dropping;
        if (dropping) {
            Utils.consolePrint("The dropping of harvested items is on");
            dropNamesList.clear();
        } else
            Utils.consolePrint("The dropping of harvested items is off");
    }

    private void toggleCultivating() {
        if (!cultivating) {
            shovelItem = Utils.locateToolItem("shovel");
            if (shovelItem == null) {
                Utils.consolePrint("The player doesn't have a shovel!");
                return;
            }
            Utils.consolePrint(this.getClass().getSimpleName() + " will use " + shovelItem.getDisplayName() + " with QL:" + shovelItem.getQuality() + " DMG:" + shovelItem.getDamage());
            cultivating = true;
            Utils.consolePrint("The cultivation is on");
        } else {
            cultivating = false;
            Utils.consolePrint("The cultivation is off");
        }
    }

    private void togglePlanting(String []input) {
        if (!planting) {
            if (input == null || input.length == 0) {
                printInputKeyUsageString(FarmerBot.InputKey.p);
                return;
            }
            this.seedsName = String.join(" ", input);
            Utils.consolePrint(this.getClass().getSimpleName() + " will plant " + this.seedsName);
            planting = true;
        } else {
            planting = false;
            Utils.consolePrint("Planting is off");
        }
    }

    private void toggleHarvesting() {
        if (!harvesting) {
            scytheItem = Utils.locateToolItem("scythe");
            if (scytheItem == null) {
                Utils.consolePrint("The player doesn't have a scythe!");
                return;
            }
            Utils.consolePrint(this.getClass().getSimpleName() + " will use " + scytheItem.getDisplayName() + " with QL:" + scytheItem.getQuality() + " DMG:" + scytheItem.getDamage());
            harvesting = true;
            Utils.consolePrint("The harvesting is on");
        } else {
            harvesting = false;
            Utils.consolePrint("The harvesting is off");
        }
    }

    private void toggleFarmTending() {
        if (!farmTending) {
            rakeItem = Utils.locateToolItem("rake");
            if (rakeItem == null) {
                Utils.consolePrint("The player doesn't have a rake!");
                return;
            }
            Utils.consolePrint(this.getClass().getSimpleName() + " will use " + rakeItem.getDisplayName() + " with QL:" + rakeItem.getQuality() + " DMG:" + rakeItem.getDamage());
            farmTending = true;
            Utils.consolePrint("The farm tending is on");
        } else {
            farmTending = false;
            Utils.consolePrint("The farm tending is off");
        }
    }

    private void toggleRepairing() {
        repairing = !repairing;
        Utils.consolePrint("The tool repairing is " + (repairing?"on":"off"));
    }

    private void setDropLimit(String[] input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(FarmerBot.InputKey.dl);
            return;
        }
        try{
            dropLimit = Integer.parseInt(input[0]);
            Utils.consolePrint("New drop limit is " + dropLimit);
        } catch (NumberFormatException e) {
            Utils.consolePrint("Wrong drop limit value!");
        }
    }

    enum InputKey implements Bot.InputKey {
        s("Stamina", "Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold",
                "threshold(float value between 0 and 1)"),
        r("Toggle Repair", "Toggle the tool repairing", ""),
        ft("Farm Tending", "Toggle the farm tending", ""),
        h("Harvest Mode", "Toggle the harvesting", ""),
        p("Planting", "Toggle the planting. Provide the name of the seeds to plant", "seeds_name"),
        c("Cultivation", "Toggle the dirt cultivation", ""),
        and("Add Drop Item", "Add new item name to drop on the ground", "itemName"),
        d("Dropping", "Toggle the dropping of harvested items. Add item names to drop by \"" + and.name() + "\" key", ""),
        dl("Drop Limit", "Set the drop limit, configured number of harvests won't be dropped", "number");

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
