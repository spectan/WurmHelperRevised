package net.ildar.wurm.bot;

import com.wurmonline.client.game.World;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.mesh.FoliageAge;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.mesh.TreeData;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.Pair;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.Utils;
import net.ildar.wurm.annotations.BotInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@BotInfo(name = "Forester", description =
        "A forester bot. Can pick and plant sprouts, cut trees/bushes and gather the harvest in 3x3 area around player. " +
        "Bot can be configured to process rectangular area of any size. " +
        "Sprouts, to prevent the inventory overflow, will be put to the containers. The name of containers can be configured. " +
        "Containers only in root directory of player's inventory will be taken into account. " +
        "New item names can be added(harvested fruits for example) to be moved to containers too. " +
        "Steppe and moss tiles will be cultivated if planting is enabled and player have shovel in his inventory. " +
        "Overaged trees are pruned by default (can be toggled off) unless deforestation is on, then they are cut down. ",
        abbreviation = "fr")
public class ForesterBot extends Bot {
    private static final String DEFAULT_CONTAINER_NAME = "backpack";
    private volatile int maxActions;
    private AreaAssistant areaAssistant = new AreaAssistant(this);

    private volatile long hatchetId;
    // modified from both the bot thread and chat callbacks; compound operations synchronize on the list
    private final List<Pair<Integer, Integer>> queuedTiles = Collections.synchronizedList(new ArrayList<>());

    private volatile long lastActionFinishedTime;

    private volatile String containerName = DEFAULT_CONTAINER_NAME;
    private final List<String> itemNamesToMove = new CopyOnWriteArrayList<>();

    private final Set<String> treeWhitelist = ConcurrentHashMap.newKeySet();
    private final Set<String> treeBlacklist = ConcurrentHashMap.newKeySet();
    private final Set<String> sproutBlacklist = ConcurrentHashMap.newKeySet();

    private volatile boolean cutAllSprouts;
    private volatile boolean harvesting;
    private volatile boolean planting;
    private volatile boolean shriveledTreesChopping;
    private volatile boolean deforesting;
    private volatile boolean overagedTreePruning = true;
    private volatile int toHarvest;

    public ForesterBot() {
        registerStaminaThresholdHandler(ForesterBot.InputKey.s);
        registerInputHandler(ForesterBot.InputKey.ca, input -> toggleAllTreesCutting());
        registerInputHandler(ForesterBot.InputKey.cs, input -> toggleShriveledTreesChopping());
        registerInputHandler(ForesterBot.InputKey.df, input -> toggleDeforestation());
        registerInputHandler(ForesterBot.InputKey.po, input -> toggleOveragedTreePruning());
        registerInputHandler(ForesterBot.InputKey.h, input -> toggleHarvesting());
        registerInputHandler(ForesterBot.InputKey.p, input -> togglePlanting());
        registerInputHandler(ForesterBot.InputKey.scn, this::setContainerName);
        registerInputHandler(ForesterBot.InputKey.na, this::setMaxActions);
        registerInputHandler(ForesterBot.InputKey.aim, this::addItemToMove);
        registerInputHandler(ForesterBot.InputKey.atw, this::addTreeWhitelist);
        registerInputHandler(ForesterBot.InputKey.ctw, input -> clearTreeWhitelist());
        registerInputHandler(ForesterBot.InputKey.atb, this::addTreeBlacklist);
        registerInputHandler(ForesterBot.InputKey.ctb, input -> clearTreeBlacklist());
        registerInputHandler(ForesterBot.InputKey.asb, this::addSproutBlacklist);
        registerInputHandler(ForesterBot.InputKey.csb, input -> clearSproutBlacklist());
        staminaThreshold = 0.95f;
        timeout = 300;
        maxActions = Utils.getMaxActionNumber();
    }

    @Override
    void describeSettings(List<String> lines) {
        lines.add("Clicks: " + maxActions);
        lines.add("Harvesting: " + onOff(harvesting));
        lines.add("Planting: " + onOff(planting));
        lines.add("Cut sprouts from all trees: " + onOff(cutAllSprouts) + (cutAllSprouts ? "" : " (very old only)"));
        lines.add("Cut shriveled trees: " + onOff(shriveledTreesChopping));
        lines.add("Deforestation: " + onOff(deforesting));
        lines.add("Prune overaged trees: " + onOff(overagedTreePruning));
        lines.add("Container name: " + containerName);
        lines.add("Extra items to move: " + (itemNamesToMove.isEmpty() ? "none" : String.join(", ", itemNamesToMove)));
        lines.add("Tree whitelist: " + (treeWhitelist.isEmpty() ? "none (all trees)" : String.join(", ", treeWhitelist)));
        lines.add("Tree blacklist: " + (treeBlacklist.isEmpty() ? "none" : String.join(", ", treeBlacklist)));
        lines.add("Sprout blacklist: " + (sproutBlacklist.isEmpty() ? "none" : String.join(", ", sproutBlacklist)));
        areaAssistant.describeSettings(lines);
    }

    @Override
    public void work() throws Exception {
        World world = WurmHelper.hud.getWorld();
        InventoryMetaItem sickle = Utils.locateToolItem("sickle");
        InventoryMetaItem bucket = Utils.locateToolItem("bucket");
        lastActionFinishedTime = System.currentTimeMillis();
        long sickleId;
        if (sickle == null) {
            Utils.consolePrint("You don't have a sickle! " + this.getClass().getSimpleName() + " won't start");
            deactivate();
            return;
        } else {
            sickleId = sickle.getId();
            Utils.consolePrint(this.getClass().getSimpleName() + " will use " + sickle.getDisplayName() + " with QL:" + sickle.getQuality() + " DMG:" + sickle.getDamage());
        }
        if (bucket != null)
            Utils.consolePrint(this.getClass().getSimpleName() + " will use " + bucket.getDisplayName() + " with QL:" + bucket.getQuality() + " DMG:" + bucket.getDamage());
        registerEventProcessors();
        while (isActive()) {
            waitOnPause();
            if (Math.abs(lastActionFinishedTime - System.currentTimeMillis()) > 10000 && hasStamina(staminaThreshold))
                queuedTiles.clear();
            if (Math.abs(lastActionFinishedTime - System.currentTimeMillis()) > 20000 && hasStamina(staminaThreshold))
                toHarvest = 0;

            if (hasStamina(staminaThreshold) && queuedTiles.size() == 0 && toHarvest == 0) {
                int checkedtiles[][] = Utils.getAreaCoordinates();
                int tileIndex = -1;
                Set<Long> usedSprouts = new HashSet<>();
                List<InventoryMetaItem> plantableSprouts = null;
                InventoryMetaItem shovel = null;
                boolean shovelSearched = false;
                while (++tileIndex < 9 && queuedTiles.size() + toHarvest < maxActions) {
                    Pair<Integer, Integer> coordsPair = new Pair<>(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);
                    if (queuedTiles.contains(coordsPair))
                        continue;
                    Tiles.Tile tileType = world.getNearTerrainBuffer().getTileType(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);
                    byte tileData = world.getNearTerrainBuffer().getData(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);
                    final String treeTypeName = tileType.getTileName(tileData).toLowerCase();
                    
                    if (tileType.isTree() || tileType.isBush()) {
                        if(!isTreeAllowed(treeTypeName))
                            continue;
                        
                        FoliageAge fage = FoliageAge.getFoliageAge(tileData);
                        if (harvesting && fage.getAgeId() > FoliageAge.YOUNG_FOUR.getAgeId()
                                && fage.getAgeId() < FoliageAge.OVERAGED.getAgeId()
                                && tileType.usesNewData()  && (tileData & 0x8) > 0) {
                            if(tileType.getTreeType(tileData) == TreeData.TreeType.MAPLE && bucket!=null)
                                world.getServerConnection().sendAction(bucket.getId(),
                                        new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                        PlayerAction.HARVEST);
                            else
                                world.getServerConnection().sendAction(sickleId,
                                    new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                    PlayerAction.HARVEST);
                            increaseHarvests(fage);
                            lastActionFinishedTime = System.currentTimeMillis();
                        } else if (fage.getAgeName().contains("overaged")) {
                            if (deforesting) {
                                world.getServerConnection().sendAction(hatchetId,
                                        new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                        PlayerAction.CUT_DOWN);
                                queuedTiles.add(coordsPair);
                                lastActionFinishedTime = System.currentTimeMillis();
                            } else if (overagedTreePruning) {
                                world.getServerConnection().sendAction(sickleId,
                                        new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                        PlayerAction.PRUNE);
                                queuedTiles.add(coordsPair);
                                lastActionFinishedTime = System.currentTimeMillis();
                            }
                        } else if (fage.getAgeName().contains("sprouting") && shouldPickSprout(treeTypeName) && (cutAllSprouts || fage.getAgeName().contains("very old"))) {
                            world.getServerConnection().sendAction(sickleId,
                                    new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                    PlayerAction.PICK_SPROUT);
                            queuedTiles.add(coordsPair);
                            lastActionFinishedTime = System.currentTimeMillis();
                        } else if (deforesting || shriveledTreesChopping && fage.getAgeName().contains("shriveled")) {
                            world.getServerConnection().sendAction(hatchetId,
                                    new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                    PlayerAction.CUT_DOWN);
                            queuedTiles.add(coordsPair);
                            lastActionFinishedTime = System.currentTimeMillis();
                        }
                    }
                    if (planting && (tileType.isGrass() || tileType.tilename.equals("Mycelium") || tileType.tilename.equals("Dirt"))) {
                        if (plantableSprouts == null)
                            plantableSprouts = Utils.getInventoryItems("sprout")
                                    .stream()
                                    .filter(item -> (item.getRarity() == 0))
                                    .collect(Collectors.toList());
                        for (InventoryMetaItem sprout : plantableSprouts) {
                            if (!usedSprouts.contains(sprout.getId())) {
                                world.getServerConnection().sendAction(sprout.getId(),
                                        new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                        PlayerAction.PLANT_CENTER);
                                usedSprouts.add(sprout.getId());
                                queuedTiles.add(coordsPair);
                                lastActionFinishedTime = System.currentTimeMillis();
                                break;
                            }
                        }
                    }
                    if (planting && (tileType.tilename.equals("Steppe")||tileType.tilename.equals("Moss"))) {
                        if (!shovelSearched) {
                            shovel = Utils.locateToolItem("shovel");
                            shovelSearched = true;
                        }
                        if (shovel != null) {
                            world.getServerConnection().sendAction(shovel.getId(),
                                    new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                    PlayerAction.CULTIVATE);
                            lastActionFinishedTime = System.currentTimeMillis();
                            queuedTiles.add(coordsPair);
                        }
                    }
                }
                if (queuedTiles.size() == 0 && toHarvest == 0 && areaAssistant.areaTourActivated())
                    areaAssistant.areaNextPosition();

                List<InventoryMetaItem> firstLevelItems = Utils.getFirstLevelItems();
                List<InventoryMetaItem> sprouts = firstLevelItems
                        .stream()
                        .filter(this::itemShouldBeMoved)
                        .collect(Collectors.toList());
                if (sprouts.size() > 0) {
                    List<InventoryMetaItem> containers = firstLevelItems.stream()
                            .filter(item->item.getBaseName().contains(containerName))
                            .collect(Collectors.toList());
                    for (InventoryMetaItem container : containers)
                        if (container.getChildren() != null && container.getChildren().size() < 100) {
                            long[] sproutIds = sprouts.stream().mapToLong(InventoryMetaItem::getId).toArray();
                            WurmHelper.hud.getWorld().getServerConnection().sendMoveSomeItems(
                                    container.getId(), sproutIds);
                            break;
                        }
                }
            }
            sleep(timeout);
        }
    }

    private boolean itemShouldBeMoved(InventoryMetaItem item) {
        if (item.getBaseName().contains("sprout"))
            return true;
        for(String itemName : itemNamesToMove) {
            if (item.getBaseName().contains(itemName))
                return true;
        }
        return false;
    }

    private void registerEventProcessors() {
        registerEventProcessor(message -> message.contains("You are too far away") ,
                this::actionNotQueued);
        registerEventProcessor(message -> message.contains("You make a lot of errors and need to take a break"),
                this::actionFinished);
        registerEventProcessor(message -> (message.contains("You cut a sprout")
                        || message.contains("It does not make sense to prune")
                        || message.contains("You prune the ") || message.contains("You stop pruning")
                        || message.contains("You stop picking") || message.contains("has no sprout to pick")
                        || message.contains("You stop cutting down.")
                        || message.contains("You cut down the ") || message.contains("You plant the sprout.")
                        || message.contains("You chip away some wood")
                        || message.contains("The ground is cultivated and ready to sow now.")),
                this::actionFinished);
        registerEventProcessor(message -> message.contains("You harvest "),
                this::harvestedSomething);
    }

    private synchronized void increaseHarvests(FoliageAge fage) {
        float f = WurmHelper.hud.getWorld().getPlayer().getSkillSet().getSkillValue("forestry");
        int maxHarvest = 1;
        if (f > 80)
            maxHarvest = 4;
        else if (f > 53)
            maxHarvest = 3;
        else if (f > 26)
            maxHarvest = 2;
        String age = fage.getAgeName();
        if (age.contains("very old")) {
            toHarvest += maxHarvest;
        }
        else if (fage.getAgeId() == FoliageAge.OLD_ONE.getAgeId() || fage.getAgeId() == FoliageAge.OLD_ONE_SPROUTING.getAgeId()) {
            toHarvest += Math.max(1, maxHarvest - 2);
        }
        else if (age.contains("old")) {
            toHarvest += Math.max(1, maxHarvest - 1);
        }
        else if (age.contains("mature")) {
            toHarvest++;
        }
    }

    private synchronized void harvestedSomething() {
        if (--toHarvest < 0)
            toHarvest = 0;
        lastActionFinishedTime = System.currentTimeMillis();
    }

    private void addItemToMove(String []input) {
        List<String> names = parseNameList(input);
        if (names.isEmpty()) {
            printInputKeyUsageString(ForesterBot.InputKey.aim);
            return;
        }
        itemNamesToMove.addAll(names);
        Utils.consolePrint("Items with name \"" + String.join("\", \"", names) + "\" will be moved to containers");
    }

    private void setMaxActions(String [] input) {
        Integer value = parseIntArg(input, ForesterBot.InputKey.na, 1, 100);
        if (value == null)
            return;
        maxActions = value;
        Utils.consolePrint("Maximum actions was set " + maxActions);
    }

    private void setContainerName(String []input) {
        String name = joinArgs(input);
        if (name == null) {
            printInputKeyUsageString(ForesterBot.InputKey.scn);
            return;
        }
        containerName = name;
        Utils.feedback("Container name was set to \"" + containerName + "\"");
    }

    private void togglePlanting() {
        planting = !planting;
        Utils.feedback("Planting is " + onOff(planting));
    }

    private void toggleHarvesting() {
        harvesting = !harvesting;
        Utils.feedback("Harvesting is " + onOff(harvesting));
    }

    private void toggleAllTreesCutting() {
        cutAllSprouts = !cutAllSprouts;
        if (cutAllSprouts)
            Utils.feedback(this.getClass().getSimpleName() + " will cut sprouts from trees and bushes of any age");
        else
            Utils.feedback(this.getClass().getSimpleName() + " will cut sprouts only from very old trees and bushes");
    }

    private void actionFinished() {
        synchronized (queuedTiles) {
            if (queuedTiles.size() > 0) {
                queuedTiles.remove(0);
                lastActionFinishedTime = System.currentTimeMillis();
            }
        }
    }

    private void actionNotQueued() {
        synchronized (queuedTiles) {
            if (queuedTiles.size() > 0) {
                queuedTiles.remove(queuedTiles.size()  - 1);
                lastActionFinishedTime = System.currentTimeMillis();
            }
        }
        toHarvest = 0;
    }

    private void toggleShriveledTreesChopping() {
        if (!shriveledTreesChopping) {
            InventoryMetaItem hatchet = Utils.locateToolItem("hatchet");
            if (hatchet == null) {
                Utils.consolePrint("You don't have a hatchet!");
            } else {
                shriveledTreesChopping = true;
                hatchetId = hatchet.getId();
                Utils.consolePrint(this.getClass().getSimpleName() + " will use " + hatchet.getDisplayName() + " to chop shriveled trees.");
                Utils.consolePrint("QL:" + hatchet.getQuality() + " DMG:" + hatchet.getDamage());
                Utils.feedback("Auto chopping shriveled trees is on");
            }
        } else {
            shriveledTreesChopping = false;
            Utils.feedback("Auto chopping shriveled trees is off");
        }
    }

    private void toggleOveragedTreePruning() {
        overagedTreePruning = !overagedTreePruning;
        Utils.feedback("Pruning of overaged trees is " + onOff(overagedTreePruning));
    }

    private void toggleDeforestation() {
        deforesting = !deforesting;
        if (deforesting) {
            InventoryMetaItem hatchet = Utils.locateToolItem("hatchet");
            if (hatchet == null) {
                Utils.consolePrint("You don't have a hatchet!");
                deforesting = false;
            } else {
                hatchetId = hatchet.getId();
                Utils.consolePrint(this.getClass().getSimpleName() + " will use " + hatchet.getDisplayName() + " to chop trees.");
                Utils.consolePrint("QL:" + hatchet.getQuality() + " DMG:" + hatchet.getDamage());
                Utils.feedback("Deforesting is on");
                if (planting) {
                    planting = false;
                    Utils.feedback("Planting is off");
                }
            }
        }
        else
            Utils.feedback("Deforesting is off");
    }

    private boolean isTreeAllowed(String treeType)
    {
        if(!treeWhitelist.isEmpty())
        {
            boolean found = false;
            for(String name: treeWhitelist)
                if(treeType.contains(name))
                {
                    found = true;
                    break;
                }
            
            if(!found) return false;
        }
        
        for(String name: treeBlacklist)
            if(treeType.contains(name))
                return false;
        
        return true;
    }
    
    private boolean shouldPickSprout(String treeType)
    {
        for(String name: sproutBlacklist)
            if(treeType.contains(name))
                return false;
        return true;
    }
    
    private void addTreeWhitelist(String[] args)
    {
        if(joinArgs(args) == null)
        {
            printInputKeyUsageString(InputKey.atw);
            return;
        }
        
        for(String name: parseNameList(args))
            treeWhitelist.add(name.toLowerCase());
        
        Utils.consolePrint(
            "Bot will only work on trees of type: %s",
            String.join(", ", treeWhitelist)
        );
    }
    
    private void clearTreeWhitelist()
    {
        treeWhitelist.clear();
        Utils.consolePrint("Bot will work on all tree types");
    }
    
    private void addTreeBlacklist(String[] args)
    {
        if(joinArgs(args) == null)
        {
            printInputKeyUsageString(InputKey.atb);
            return;
        }
        
        for(String name: parseNameList(args))
            treeBlacklist.add(name.toLowerCase());
        
        Utils.consolePrint(
            "Bot will skip working on tree types: %s",
            String.join(", ", treeBlacklist)
        );
    }
    
    private void clearTreeBlacklist()
    {
        treeBlacklist.clear();
        Utils.consolePrint("Bot will not skip any tree types");
    }
    
    private void addSproutBlacklist(String[] args)
    {
        if(joinArgs(args) == null)
        {
            printInputKeyUsageString(InputKey.asb);
            return;
        }
        
        for(String name: parseNameList(args))
            sproutBlacklist.add(name.toLowerCase());
        
        Utils.consolePrint(
            "Bot will skip picking sprouts from tree types: %s",
            String.join(", ", sproutBlacklist)
        );
    }
    
    private void clearSproutBlacklist()
    {
        sproutBlacklist.clear();
        Utils.consolePrint("Bot will pick sprouts from all tree types");
    }

    enum InputKey implements Bot.InputKey {
        s("Stamina", "Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold",
                "<threshold>"),
        ca("Cut All Sprouts", "Toggle the cutting of sprouts from all trees", ""),
        cs("Cut Shriveled", "Toggle the cutting of shriveled trees", ""),
        df("Deforestation", "Toggle the cutting of all trees (deforestation)", ""),
        po("Prune Overaged", "Toggle the pruning of overaged trees (on by default). Ignored while deforestation is on", ""),
        h("Harvest Mode", "Toggle the harvesting", ""),
        p("Planting", "Toggle the planting", ""),
        scn("Container Name", "Set the name of the containers to put sprouts/harvest in (may contain spaces)", "<container name>"),
        na("Clicks", "Set the number of actions bot will do each time", "<clicks>"),
        aim("Add Item", "Add item name(s), separated by commas, to move into the containers along with sprouts", "<item name>[, <item name>...]"),
        atw("Add Tree Whitelist", "Add whitelisted tree type(s), separated by commas. When the whitelist is not empty only those trees are processed", "<tree name>[, <tree name>...]"),
        ctw("Clear Tree Whitelist", "Clear whitelisted tree types", ""),
        atb("Add Tree Blacklist", "Add blacklisted tree type(s), separated by commas. Those trees are skipped", "<tree name>[, <tree name>...]"),
        ctb("Clear Tree Blacklist", "Clear blacklisted tree types", ""),
        asb("Add Sprout Blacklist", "Add tree type(s), separated by commas, to not pick sprouts from", "<tree name>[, <tree name>...]"),
        csb("Clear Sprout Blacklist", "Clear blacklisted tree types for sprout picking", "");

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
