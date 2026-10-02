package net.ildar.wurm.bot;

import com.wurmonline.client.game.PlayerObj;
import com.wurmonline.client.game.World;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.mesh.GrassData;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.Pair;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.Utils;
import net.ildar.wurm.annotations.BotInfo;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@BotInfo(name = "Forager", description =
        "Can forage, botanize, collect grass and flowers in an area surrounding player. " +
        "Bot can be configured to process rectangular area of any size. " +
        "Picked items, to prevent the inventory overflow, will be put to the containers. The name of containers can be configured. " +
        "Containers only in root directory of player's inventory will be taken into account. " +
        "Bot can be configured to drop picked items on the floor. ",
        abbreviation = "fg")
public class ForagerBot extends Bot {
    private static final String DEFAULT_CONTAINER_NAME = "backpack";
    private static final Set<String> forageSet = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "oregano","rosemary","lingonberry","pumpkin",
            "thyme","tomato","lovage","fennel plant",
            "acorn","cumin","wemp plants","corn",
            "potato","belladonna","mixed grass","cotton",
            "cabbage","ginger","raspberries","cocoa bean",
            "sage","blueberry","carrot","garlic",
            "rye","sassafras","strawberries","egg",
            "nettles","pea pod","parsley","wheat",
            "barley","onion","turmeric","basil",
            "mint","sugar beet","rice","cucumber",
            "lettuce","branch","woad","oat",
            "paprika", "nutmeg", "rock")));
    private static final Set<String> forageSetKeywords = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "fresh","seedling","sprout","mushroom","bouquet")));

    private Comparator<InventoryMetaItem> weightComparator = Comparator.comparingDouble(InventoryMetaItem::getWeight);
    private AreaAssistant areaAssistant = new AreaAssistant(this);
    private long sickleId;
    private int maxActions;
    private final List<Pair<Integer, Integer>> queuedTiles = new ArrayList<>();
    private List <Pair<Integer, Integer>> forageTilesInProcess = new ArrayList<>();
    private List <Pair<Integer, Integer>> botanizeTilesInProcess = new ArrayList<>();
    private Set<Pair<Integer, Integer>> foragedTiles = new HashSet<>();
    private Set<Pair<Integer, Integer>> botanizedTiles = new HashSet<>();
    private String containerName = DEFAULT_CONTAINER_NAME;
    private ForageType forageType = ForageType.Default;
    private BotanizeType botanizeType = BotanizeType.Default;
    private long lastActionFinishedTime;

    private boolean grassGathering = false;
    private boolean foraging = true;
    private boolean botanizing = true;
    private boolean dropping = false;
    private boolean dropWhenFull = false;
    private boolean verbose = false;
    private List<String> filterItemNames = new ArrayList<>();

    public ForagerBot() {
        registerStaminaThresholdHandler(ForagerBot.InputKey.s);
        registerInputHandler(ForagerBot.InputKey.g, input -> toggleGrassGathering());
        registerInputHandler(ForagerBot.InputKey.f, input -> toggleForaging());
        registerInputHandler(ForagerBot.InputKey.ftl, input -> showForagingTypes());
        registerInputHandler(ForagerBot.InputKey.ft, this::setForagingType);
        registerInputHandler(ForagerBot.InputKey.b, input -> toggleBotanizing());
        registerInputHandler(ForagerBot.InputKey.btl, input -> showBotanizingTypes());
        registerInputHandler(ForagerBot.InputKey.bt, this::setBotanizingType);
        registerInputHandler(ForagerBot.InputKey.d, input -> toggleDropping());
        registerInputHandler(ForagerBot.InputKey.dwf, input -> toggleDroppingWhenFull());
        registerInputHandler(ForagerBot.InputKey.dfa, this::addItemToFilter);
        registerInputHandler(ForagerBot.InputKey.dfc, input -> clearFilter());
        registerInputHandler(ForagerBot.InputKey.v, input -> toggleVerboseMode());
        registerInputHandler(ForagerBot.InputKey.scn, this::setContainerName);
        registerInputHandler(ForagerBot.InputKey.na, this::setMaxActions);
        staminaThreshold = 0.9f;
        timeout = 300;
        maxActions = Utils.getMaxActionNumber();
    }

    @Override
    void describeSettings(List<String> lines) {
        lines.add("Clicks: " + maxActions);
        lines.add("Foraging: " + onOff(foraging) + " (type: " + forageType.name() + ")");
        lines.add("Botanizing: " + onOff(botanizing) + " (type: " + botanizeType.name() + ")");
        lines.add("Grass gathering: " + onOff(grassGathering));
        lines.add("Dropping: " + onOff(dropping) + (dropping ? (dropWhenFull ? " (when inventory is full)" : " (after every action)") : ""));
        lines.add("Drop filter: " + (filterItemNames.isEmpty() ? "none" : String.join(", ", filterItemNames)));
        lines.add("Container name: " + containerName);
        lines.add("Verbose: " + onOff(verbose));
        areaAssistant.describeSettings(lines);
    }

    @Override
    public void work() throws Exception{
        World world = WurmHelper.hud.getWorld();
        PlayerObj player = world.getPlayer();
        registerEventProcessors();
        while (isActive()) {
            waitOnPause();
            float stamina = player.getStamina();
            float damage = player.getDamage();
            float forageSkill = player.getSkillSet().getSkillValue("foraging");
            float botanizeSkill = player.getSkillSet().getSkillValue("botanizing");

            if (Math.abs(lastActionFinishedTime - System.currentTimeMillis()) > 30000 && (stamina + damage) > staminaThreshold && queuedTiles.size() > 0) {
                synchronized (queuedTiles) {
                    if (verbose)
                        queuedTiles.forEach(tile -> Utils.consolePrint("Removing tile from queue - " + tile.getKey() + " " + tile.getValue()));
                    queuedTiles.clear();
                    forageTilesInProcess.clear();
                    botanizeTilesInProcess.clear();
                }
                if (verbose)
                    Utils.consolePrint(getClass().getSimpleName() + " queue cleared");
            }
            if ((stamina + damage) > staminaThreshold && queuedTiles.size() == 0) {
                int[][] checkedtiles = Utils.getAreaCoordinates();
                int tileIndex = -1;
                synchronized (queuedTiles) {
                    while (++tileIndex < 9 && queuedTiles.size() < maxActions) {
                        Pair<Integer, Integer> coordsPair = new Pair<>(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);
                        if (queuedTiles.contains(coordsPair))
                            continue;
                        Tiles.Tile tileType = world.getNearTerrainBuffer().getTileType(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);
                        byte tileData = world.getNearTerrainBuffer().getData(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);
                        if (tileType.isGrass() || tileType.isTree() || tileType.isBush()
                                || tileType.tilename.equals("Marsh")
                                || tileType.tilename.equals("Moss")
                                || tileType.tilename.equals("Steppe")) {
                            if (botanizing && !botanizedTiles.contains(coordsPair) && !botanizeTilesInProcess.contains(coordsPair)
                                    && (!tileType.tilename.equals("Marsh") || botanizeSkill > 27)
                                    && (!tileType.tilename.equals("Moss") || botanizeSkill > 35)) {
                                if (verbose)
                                    Utils.consolePrint("Start botanizing at tile - " + checkedtiles[tileIndex][0] + " " + checkedtiles[tileIndex][1]);
                                WurmHelper.hud.sendAction(botanizeType.action, Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0));
                                queuedTiles.add(coordsPair);
                                botanizeTilesInProcess.add(coordsPair);
                                lastActionFinishedTime = System.currentTimeMillis();
                            }
                        }
                        if (tileType.isGrass() || tileType.isTree() || tileType.isBush()
                                || tileType.tilename.equals("Steppe")
                                || tileType.tilename.equals("Tundra")
                                || tileType.tilename.equals("Marsh")) {
                            if (foraging && !foragedTiles.contains(coordsPair) && !forageTilesInProcess.contains(coordsPair) && queuedTiles.size() < maxActions
                                    && (!tileType.tilename.equals("Steppe") || forageSkill > 23)
                                    && (!tileType.tilename.equals("Tundra") || forageSkill > 33)
                                    && (!tileType.tilename.equals("Marsh") || forageSkill > 43)) {
                                if (verbose)
                                    Utils.consolePrint("Start foraging at tile - " + checkedtiles[tileIndex][0] + " " + checkedtiles[tileIndex][1]);
                                WurmHelper.hud.sendAction(forageType.action, Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0));
                                queuedTiles.add(coordsPair);
                                forageTilesInProcess.add(coordsPair);
                                lastActionFinishedTime = System.currentTimeMillis();
                            }
                        }
                        if (grassGathering && (tileType.isGrass() || tileType.isTree() || tileType.isBush())) {
                            if (GrassData.getFlowerTypeName(tileData).contains("flowers") && !tileType.isTree() && !tileType.isBush() && queuedTiles.size() < maxActions) {
                                WurmHelper.hud.getWorld().getServerConnection().sendAction(sickleId,
                                        new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                        new PlayerAction("",(short) 187, PlayerAction.ANYTHING));
                                queuedTiles.add(coordsPair);
                                if (verbose)
                                    Utils.consolePrint("Start cutting flowers at tile - " + checkedtiles[tileIndex][0] + " " + checkedtiles[tileIndex][1]);
                                lastActionFinishedTime = System.currentTimeMillis();
                            }
                            if (((tileType.isGrass() && GrassData.GrowthStage.decodeTileData(tileData) != GrassData.GrowthStage.SHORT) ||
                                    ((tileType.isTree() || tileType.isBush()) && GrassData.GrowthTreeStage.decodeTileData(tileData) != GrassData.GrowthTreeStage.LAWN
                                            && GrassData.GrowthTreeStage.decodeTileData(tileData) != GrassData.GrowthTreeStage.SHORT)) && queuedTiles.size() < maxActions) {
                                WurmHelper.hud.getWorld().getServerConnection().sendAction(sickleId,
                                        new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                        PlayerAction.GATHER);
                                queuedTiles.add(coordsPair);
                                if (verbose)
                                    Utils.consolePrint("Start cutting grass at tile - " + checkedtiles[tileIndex][0] + " " + checkedtiles[tileIndex][1]);
                                lastActionFinishedTime = System.currentTimeMillis();
                            }
                        }
                    }
                }
                if (queuedTiles.size() == 0 && areaAssistant.areaTourActivated())
                    areaAssistant.areaNextPosition();

                if(grassGathering) {
                    List <InventoryMetaItem> grass = Utils.getInventoryItems("mixed grass");
                    if(grass != null && grass.size() > 0) {
                        List <InventoryMetaItem> forCombining = new ArrayList<>();
                        grass.sort(weightComparator);
                        float totalWeight = 0;
                        for (InventoryMetaItem grassItem : grass)
                            if (grassItem.getWeight()+totalWeight < 3.2) {
                                forCombining.add(grassItem);
                                totalWeight += grassItem.getWeight();
                            }
                        if (forCombining.size() > 1) {
                            long[] targetIds = new long[Math.min(forCombining.size(), 64)];
                            for(tileIndex = 0; tileIndex < targetIds.length; tileIndex++)
                                targetIds[tileIndex] = forCombining.get(tileIndex).getId();
                            WurmHelper.hud.getWorld().getServerConnection().sendAction(
                                    targetIds[0], targetIds, PlayerAction.COMBINE);

                        }
                    }
                }

                List<InventoryMetaItem> firstLevelItems = Utils.getFirstLevelItems();
                if (!dropping) {
                    List<InventoryMetaItem> foragables = firstLevelItems.stream()
                            .filter(ForagerBot::isForagable)
                            .collect(Collectors.toList());
                    List<InventoryMetaItem> containers =  firstLevelItems.stream()
                            .filter(item->item.getBaseName().contains(containerName))
                            .collect(Collectors.toList());
                    long[] foragablesIds = Utils.getItemIds(foragables);
                    if (foragablesIds != null && foragables.size() > 20) {
                        for (InventoryMetaItem container : containers) {
                            if (container.getChildren() != null && container.getChildren().size() < 100) {
                                WurmHelper.hud.getWorld().getServerConnection().sendMoveSomeItems(
                                        container.getId(), foragablesIds);
                                break;
                            }
                        }
                    }
                }
                else if(!dropWhenFull) {
                    dropItems();
                }
            }
            sleep(timeout);
        }
    }

    public void dropItems(){
        if (dropping) {
            List<InventoryMetaItem> firstLevelItems = Utils.getFirstLevelItems();
            List<InventoryMetaItem> foragables = firstLevelItems.stream()
                    .filter(ForagerBot::isForagable)
                    .filter(item -> item.getRarity() == 0)
                    .collect(Collectors.toList());
            if (!filterItemNames.isEmpty()) {
                Iterator<InventoryMetaItem> iter = foragables.iterator();
                while (iter.hasNext()) {
                    InventoryMetaItem item = iter.next();
                    for (String name : filterItemNames) {
                        if (item.getBaseName().contains(name)) {
                            iter.remove();
                            break;
                        }
                    }
                }
            }
            long[] foragablesIds = Utils.getItemIds(foragables);
            if (foragablesIds != null)
                WurmHelper.hud.sendAction(PlayerAction.DROP, foragablesIds);
        }
    }

    public static boolean isForagable(InventoryMetaItem item) {
        return item != null && (forageSet.contains(item.getBaseName()) || forageSetKeywords.stream().anyMatch(keyword -> item.getBaseName().contains(keyword)));
    }

    private void registerEventProcessors() {
        registerEventProcessor(message -> message.contains("You are too far away"),
                this::actionNotQueued);
        registerEventProcessor(message -> message.contains("You're too busy"),
                this::actionNotQueued);
        registerEventProcessor(message -> (message.contains("You gather") && message.contains("mixed grass")
                        || message.contains("You pick some flowers")
                        || message.contains("You try to cut some short grass but you fail to get any significant amount.")),
                this::actionFinished);
        registerEventProcessor(message -> (message.contains("You find")
                        || message.contains("This area looks picked clean.")
                        || message.contains("You fail to find")),
                this::fbFinished);
        registerEventProcessor(message -> (message.contains("inventory is full") && dropWhenFull),
                this::dropItems);
    }
    private static <T extends Enum<T>> void showTypes(String header, T[] types, Function<T, String> abbreviation) {
        Utils.consolePrint(header + Arrays.stream(types)
                .map(type -> abbreviation.apply(type) + "(" + type.name() + ")")
                .collect(Collectors.joining(", ")));
    }

    private void showForagingTypes() {
        showTypes("Available foraging types - ", ForageType.values(), type -> type.abbreviation);
    }

    private void setForagingType(String []input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(ForagerBot.InputKey.ft);
            return;
        }
        ForageType forageType = ForageType.getByAbbreviation(input[0]);
        if (forageType == ForageType.Unknown) {
            Utils.consolePrint("Unknown foraging type. Use " +
                    "\"" + ForagerBot.InputKey.ftl.name() + "\" key to see available types");
            return;
        }
        this.forageType = forageType;
        Utils.feedback("Foraging type was set to " + forageType.name());
    }

    private void showBotanizingTypes() {
        showTypes("Available botanizing types - ", BotanizeType.values(), type -> type.abbreviation);
    }

    private void setBotanizingType(String []input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(ForagerBot.InputKey.bt);
            return;
        }
        BotanizeType botanizeType = BotanizeType.getByAbbreviation(input[0]);
        if (botanizeType == BotanizeType.Unknown) {
            Utils.consolePrint("Unknown botanizing type. Use " +
                    "\"" + ForagerBot.InputKey.btl.name() + "\" key to see available types");
            return;
        }
        this.botanizeType = botanizeType;
        Utils.feedback("Botanizing type was set to " + botanizeType.name());
    }

    private void setMaxActions(String [] input) {
        Integer value = parseIntArg(input, ForagerBot.InputKey.na, 1, 100);
        if (value == null)
            return;
        maxActions = value;
        Utils.consolePrint("Maximum actions was set " + maxActions);
    }

    private void setContainerName(String []input) {
        String name = joinArgs(input);
        if (name == null) {
            printInputKeyUsageString(ForagerBot.InputKey.scn);
            return;
        }
        containerName = name;
        Utils.feedback("Container name was set to \"" + containerName + "\"");
    }

    private void toggleVerboseMode() {
        verbose = !verbose;
        Utils.feedback("Verbose mode is " + onOff(verbose));
    }

    private void toggleDropping() {
        dropping = !dropping;
        Utils.feedback("Dropping is " + onOff(dropping));
    }
    private void toggleDroppingWhenFull() {
        dropWhenFull = !dropWhenFull;
        if (dropWhenFull)
            Utils.feedback("Drop when inventory full.");
        else
            Utils.feedback("Drop when action done.");
    }

    private void addItemToFilter(String[] input) {
        List<String> names = parseNameList(input);
        if (names.isEmpty()) {
            printInputKeyUsageString(InputKey.dfa);
            return;
        }
        filterItemNames.addAll(names);
        Utils.consolePrint("Added " + String.join(", ", names) + " to filter list. Current filter: [" + String.join(", ", filterItemNames) + "]");
    }

    private void clearFilter() {
        filterItemNames.clear();
        Utils.feedback("Cleared drop filter.");
    }

    private void toggleBotanizing() {
        botanizing = !botanizing;
        Utils.feedback("Botanizing is " + onOff(botanizing));
    }

    private void toggleForaging() {
        foraging = !foraging;
        Utils.feedback("Foraging is " + onOff(foraging));
    }

    private void toggleGrassGathering() {
        grassGathering = !grassGathering;
        if (grassGathering) {
            InventoryMetaItem sickle = Utils.locateToolItem("sickle");
            if (sickle == null) {
                Utils.consolePrint("You don't have a sickle! Grass gathering stays off");
                grassGathering = false;
                return;
            }
            sickleId = sickle.getId();
            Utils.consolePrint(this.getClass().getSimpleName() + " will use " + sickle.getDisplayName() + " with QL:" + sickle.getQuality() + " DMG:" + sickle.getDamage());
            Utils.feedback("Grass gathering is on");
        } else
            Utils.feedback("Grass gathering is off");
    }

    private void actionFinished() {
        synchronized (queuedTiles) {
            if (queuedTiles.size() > 0) {
                Pair<Integer, Integer> tile = queuedTiles.get(0);
                if (verbose)
                    Utils.consolePrint("Finish gathering grass at tile - " + tile.getKey() + " " + tile.getValue());
                queuedTiles.remove(0);
                lastActionFinishedTime = System.currentTimeMillis();
            }
        }
    }

    private void actionNotQueued() {
        synchronized (queuedTiles) {
            if (queuedTiles.size() > 0) {
                Pair<Integer, Integer> tile = queuedTiles.get(queuedTiles.size() - 1);
                if (!forageTilesInProcess.remove(tile))
                    botanizeTilesInProcess.remove(tile);
                if (verbose)
                    Utils.consolePrint("Too busy to queue tile - " + tile.getKey() + " " + tile.getValue());
                queuedTiles.remove(queuedTiles.size() - 1);
                lastActionFinishedTime = System.currentTimeMillis();
            }
        }
    }

    private void fbFinished() {
        synchronized (queuedTiles) {
            if (queuedTiles.size() > 0) {
                Pair<Integer, Integer> tile = queuedTiles.get(0);
                if (forageTilesInProcess.contains(tile)) {
                    if (verbose)
                        Utils.consolePrint("Finish foraging at tile - " + tile.getKey() + " " + tile.getValue());
                    foragedTiles.add(tile);
                    forageTilesInProcess.remove(tile);
                } else if (botanizeTilesInProcess.contains(tile)) {
                    if (verbose)
                        Utils.consolePrint("Finish botanizing at tile - " + tile.getKey() + " " + tile.getValue());
                    botanizedTiles.add(tile);
                    botanizeTilesInProcess.remove(tile);
                } else {
                    if (verbose)
                        Utils.consolePrint("found unchecked fb tile!");
                }
                queuedTiles.remove(0);
                lastActionFinishedTime = System.currentTimeMillis();
            }
        }
    }

    enum InputKey implements Bot.InputKey {
        s("Stamina", "Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold",
                "<threshold>"),
        g("Grass", "Toggle the grass gathering", ""),
        f("Forage", "Toggle the foraging", ""),
        ftl("Forage Types", "Show the list of foraging types", ""),
        ft("Forage Type", "Set the foraging type. Use the \"ftl\" key to see the available types", "<type>"),
        b("Botanizing", "Toggle the botanizing", ""),
        btl("Botanize Types", "Show the list of botanizing types", ""),
        bt("Botanize Type", "Set the botanizing type. Use the \"btl\" key to see the available types", "<type>"),
        d("Drop", "Toggle the dropping of collected items to the ground", ""),
        dwf("Drop When Full", "Change drop mode between drop when full inventory or drop after every action", ""),
        dfa("Add Item", "Add item name(s) to the drop filter, separated by commas. Items whose name contains a filter name won't be dropped", "<item name>[, <item name>...]"),
        dfc("Clear Items", "Clear the drop filter", ""),
        v("Verbose", "Toggle the verbose mode. " +
                "Additional information will be shown in console during the work of the bot in verbose mode", ""),
        scn("Container Name", "Set the name of the containers to put sprouts/harvest in (may contain spaces)", "<container name>"),
        na("Clicks", "Set the number of actions bot will do each time", "<clicks>");

        private final KeyInfo keyInfo;

        InputKey(String fullName, String description, String usage) {
            keyInfo = new KeyInfo(fullName, description, usage);
        }

        @Override
        public KeyInfo keyInfo() {
            return keyInfo;
        }
    }

    enum ForageType{
        Unknown(null, ""),
        Default(PlayerAction.FORAGE, "*"),
        Resources(PlayerAction.FORAGE_RESOURCE, "r"),
        Vegetables(PlayerAction.FORAGE_VEG, "v"),
        Berries(PlayerAction.FORAGE_BERRIES, "b");

        PlayerAction action;
        String abbreviation;
        ForageType(PlayerAction action, String abbreviation) {
            this.action = action;
            this.abbreviation = abbreviation;
        }
        static ForageType getByAbbreviation(String abbreviation) {
            for (ForageType forageType : values())
                if (forageType.abbreviation.equals(abbreviation))
                    return forageType;
            return Unknown;
        }
    }

    @SuppressWarnings("unused")
    enum BotanizeType{
        Unknown(null, ""),
        Default(PlayerAction.BOTANIZE, "*"),
        Herbs(PlayerAction.BOTANIZE_HERBS, "h"),
        Plants(PlayerAction.BOTANIZE_PLANTS, "p"),
        Resources(PlayerAction.BOTANIZE_RESOURCE, "r"),
        Seeds(PlayerAction.BOTANIZE_SEEDS, "se"),
        Spices(PlayerAction.BOTANIZE_SPICES, "sp");

        PlayerAction action;
        String abbreviation;
        BotanizeType(PlayerAction action, String abbreviation) {
            this.action = action;
            this.abbreviation = abbreviation;
        }
        static BotanizeType getByAbbreviation(String abbreviation) {
            for (BotanizeType botanizeType : values())
                if (botanizeType.abbreviation.equals(abbreviation))
                    return botanizeType;
            return Unknown;
        }
    }
}
