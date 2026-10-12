package net.ildar.wurm.bot;

import com.wurmonline.client.comm.ServerConnectionListenerClass;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.GroundItemData;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.cell.GroundItemCellRenderable;
import com.wurmonline.client.renderer.gui.*;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.Pair;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.Utils;
import net.ildar.wurm.annotations.BotInfo;

import java.util.*;
import java.util.stream.Collectors;

@BotInfo(name = "Miner", description =
        "Mines rocks and smelts ores.",
        abbreviation = "m")
public class MinerBot extends Bot {
    private SmeltingOptions smeltingOptions = new SmeltingOptions();
    private volatile MiningMode miningMode = MiningMode.Unknown;
    private volatile InventoryMetaItem pickaxe;
    private static final String[] VALID_TOOLS = {"pickaxe"};
    private volatile long fixedTileId;
    private int[] lastTile;
    private Set<Pair<Integer, Integer>> errorTiles = new HashSet<>();
    private long lastMining;
    private volatile int clicks = 2;
    private volatile boolean shardsCombining;
    private volatile String shards = "rock shard";
    private volatile String  fuel = "kindling";
    private volatile long fuellingTimeout = 300000;
    private long lastFuelling;
    private volatile boolean moving;
    private int movingForwardBias;
    private boolean smelting = false;
    private volatile boolean verbose = false;
    private volatile boolean noOre;
    private Random random = new Random();
    private volatile Direction direction = Direction.FORWARD;

    public MinerBot() {
        registerStaminaThresholdHandler(MinerBot.InputKey.s);
        registerInputHandler(MinerBot.InputKey.c, this::setClicksNumber);
        registerInputHandler(MinerBot.InputKey.sc, input -> toggleShardsCombining());
        registerInputHandler(MinerBot.InputKey.scn, this::setCombiningShardsName);
        registerInputHandler(MinerBot.InputKey.fixed, input -> setFixedMiningMode());
        registerInputHandler(MinerBot.InputKey.st, input -> setSelectedTileMiningMode());
        registerInputHandler(MinerBot.InputKey.area, input -> setAreaMiningMode());
        registerInputHandler(MinerBot.InputKey.ft, input -> setFrontTileMiningMode());
        registerInputHandler(MinerBot.InputKey.o, input -> toggleOreMining());
        registerInputHandler(MinerBot.InputKey.m, input -> toggleMoving());
        registerInputHandler(MinerBot.InputKey.sm, input -> toggleSmelting());
        registerInputHandler(MinerBot.InputKey.at, this::addTarget);
        registerInputHandler(MinerBot.InputKey.ati, this::addTargetInventory);
        registerInputHandler(MinerBot.InputKey.atid, this::addTargetById);
        registerInputHandler(MinerBot.InputKey.sp, input -> setPile());
        registerInputHandler(MinerBot.InputKey.ssm, input -> setSmelter());
        registerInputHandler(MinerBot.InputKey.sft, this::setFuellingTimeout);
        registerInputHandler(MinerBot.InputKey.sfn, this::setFuelName);
        registerInputHandler(MinerBot.InputKey.v, input -> toggleVerboseMode());
        registerInputHandler(MinerBot.InputKey.dir, this::handleDirectionChange);
        registerInputHandler(MinerBot.InputKey.tool, input -> selectTool());
        staminaThreshold = 0.96f;
    }

    @Override
    void describeSettings(List<String> lines) {
        lines.add("Clicks: " + clicks);
        String mode;
        switch (miningMode) {
            case SelectedTile: mode = "selected tile"; break;
            case Area: mode = "3x3 area around player"; break;
            case FrontTile: mode = "tile in front"; break;
            case FixedTile: mode = "fixed tile (id " + fixedTileId + ")"; break;
            default: mode = "not set";
        }
        lines.add("Mining mode: " + mode);
        lines.add("Direction: " + direction.abbreviation);
        lines.add("Ore mining: " + onOff(!noOre));
        lines.add("Moving: " + onOff(moving));
        lines.add("Shard combining: " + onOff(shardsCombining) + " (shards: " + shards + ")");
        lines.add("Smelting: " + onOff(smelting));
        lines.add("Smelter: " + componentName(smeltingOptions.smelter));
        lines.add("Pile: " + componentName(smeltingOptions.pile));
        lines.add("Lump targets: " + (smeltingOptions.containers.isEmpty() ? "none" : smeltingOptions.containers.stream()
                .map(pair -> pair.getKey() + " (min QL " + String.format("%.2f", pair.getValue()) + ")")
                .collect(Collectors.joining(", "))));
        lines.add("Fuel: " + fuel + ", fuelling timeout: " + fuellingTimeout + " ms");
        lines.add("Verbose: " + onOff(verbose));
        lines.add("Pickaxe: " + (pickaxe != null ? pickaxe.getDisplayName() : "auto (looked up on start)"));
    }

    private static String componentName(InventoryListComponent component) {
        if (component == null)
            return "not set";
        InventoryMetaItem root = Utils.getRootItem(component);
        return root != null ? root.getBaseName() : "set";
    }

    @Override
    public void work() throws Exception{
        if (pickaxe == null)
            pickaxe = Utils.locateToolItem("pickaxe");
        if (pickaxe == null) {
            Utils.consolePrint("You don't have a pickaxe!");
            deactivate();
            return;
        }
        lastMining = System.currentTimeMillis();
        errorTiles.clear();
        if (moving)
            Utils.stabilizePlayer();
        Utils.consolePrint(this.getClass().getSimpleName()
                + " will use " + pickaxe.getDisplayName()
                + " with QL:" + pickaxe.getQuality()
                + " DMG:" + pickaxe.getDamage());
        registerEventProcessors();
        while (isActive()) {
            waitOnPause();
            if (shardsCombining) {
                List<InventoryMetaItem> invShards = Utils.getInventoryItems(shards);

                if (invShards.size() > 1) {
                    long ids[] = new long[invShards.size()];
                    for (int i = 0; i < invShards.size(); i++)
                        ids[i] = invShards.get(i).getId();
                    if (verbose) Utils.consolePrint("Combining " + Arrays.toString(ids));
                    WurmHelper.hud.getWorld().getServerConnection().sendAction(
                            ids[0], ids, PlayerAction.COMBINE);
                } else {
                    int needed = 2 - invShards.size();

                    List<ItemListWindow> piles = new ArrayList<>();
                    for (WurmComponent wurmComponent : WurmHelper.getInstance().components)
                        if (wurmComponent instanceof ItemListWindow) {
                            InventoryMetaItem root = Utils.getRootItem(Utils.getField(wurmComponent, "component"));
                            if (root != null && root.getBaseName().toLowerCase().contains("pile of"))
                                piles.add((ItemListWindow) wurmComponent);
                        }

                    ServerConnectionListenerClass sscc = WurmHelper.hud.getWorld().getServerConnection().getServerConnectionListener();
                    Map<Long, GroundItemCellRenderable> groundItems = Utils.getField(sscc, "groundItems");
                    int tileX = WurmHelper.hud.getWorld().getPlayerCurrentTileX();
                    int tileY = WurmHelper.hud.getWorld().getPlayerCurrentTileY();
                    List<Long> closePileIds = new ArrayList<>();
                    boolean pileOpenRequested = false;
                    List<Map.Entry<Long, GroundItemCellRenderable>> groundItemEntries = snapshotEntries(groundItems, 5);
                    for (Map.Entry<Long, GroundItemCellRenderable> entry : groundItemEntries) {
                        GroundItemCellRenderable groundItem = entry.getValue();
                        GroundItemData groundItemData = Utils.getField(groundItem, "item");
                        int itemX = (int) (groundItemData.getX()/4);
                        int itemY = (int) (groundItemData.getY()/4);
                        String groundName = groundItemData.getName().toLowerCase();
                        if (itemX == tileX && itemY == tileY && groundName.contains("pile of")) {
                            long pileId = groundItemData.getId();
                            closePileIds.add(pileId);
                            boolean windowOpen = piles.stream().anyMatch(pile -> {
                                try {
                                    InventoryListComponent ilc = Utils.getField(pile, "component");
                                    InventoryMetaItem rootItem = Utils.getRootItem(ilc);
                                    if (rootItem != null)
                                        return rootItem.getId() == pileId;
                                } catch (IllegalAccessException | NoSuchFieldException e) {
                                    e.printStackTrace();
                                }
                                return false;
                            });
                            if (!windowOpen) {
                                if (verbose)
                                    Utils.consolePrint("Opening pile: " + groundItemData.getName() + " [id=" + pileId + "]");
                                WurmHelper.hud.sendAction(PlayerAction.OPEN, pileId);
                                pileOpenRequested = true;
                            } else if (verbose) {
                                Utils.consolePrint("Pile already open: " + groundItemData.getName() + " [id=" + pileId + "]");
                            }
                        }
                    }
                    if (verbose && closePileIds.isEmpty())
                        Utils.consolePrint("No piles of " + shards + " found on tile (" + tileX + "," + tileY + ")");

                    List<InventoryMetaItem> pileShards = new ArrayList<>();
                    for (ItemListWindow wurmComponent : piles) {
                        InventoryListComponent ilc = Utils.getField(wurmComponent, "component");
                        InventoryMetaItem rootItem = Utils.getRootItem(ilc);
                        if (rootItem == null) continue;
                        if (!groundItemEntries.isEmpty() && !closePileIds.contains(rootItem.getId())) {
                            if (verbose)
                                Utils.consolePrint("Closing distant pile window: " + rootItem.getBaseName() + " [id=" + rootItem.getId() + "]");
                            WurmHelper.hud.sendAction(PlayerAction.CLOSE, rootItem.getId());
                            continue;
                        }
                        List<InventoryMetaItem> componentItems = Utils.getInventoryItems(ilc, shards);
                        if (verbose)
                            Utils.consolePrint("Found " + (componentItems == null ? 0 : componentItems.size()) + " " + shards + " in pile " + rootItem.getBaseName());
                        if (componentItems != null)
                            for (InventoryMetaItem item : componentItems)
                                if (item.getRarity() == 0)
                                    pileShards.add(item);
                    }

                    pileShards.sort(Comparator.comparingDouble(InventoryMetaItem::getWeight));
                    float freeSpace = Utils.getMaxWeight() - Utils.getTotalWeight();
                    List<InventoryMetaItem> itemsToTake = new ArrayList<>();
                    for (InventoryMetaItem shard : pileShards) {
                        if (shouldTakeShard(freeSpace, shard.getWeight())) {
                            itemsToTake.add(shard);
                            freeSpace -= shard.getWeight();
                        } else {
                            break;
                        }
                    }

                    if (itemsToTake.size() >= needed) {
                        if (verbose) Utils.consolePrint("Taking " + itemsToTake.stream().map(InventoryMetaItem::getId).collect(Collectors.toList()));
                        for (InventoryMetaItem item : itemsToTake)
                            WurmHelper.hud.sendAction(PlayerAction.TAKE, item.getId());
                    } else if (invShards.size() == 1 && !pileOpenRequested) {
                        if (verbose) Utils.consolePrint("Cannot pick up enough shards to combine, dropping lone shard");
                        WurmHelper.hud.sendAction(PlayerAction.DROP, invShards.get(0).getId());
                    }
                }
            }

            if (WurmHelper.hud.getWorld().getPlayerLayer() >= 0) {
                sleep(timeout);
                continue;
            }
            if (canDoWork(staminaThreshold)) {
                boolean actionTaken = false;
                if (pickaxe.getDamage() > 10)
                    WurmHelper.hud.sendAction(PlayerAction.REPAIR, pickaxe.getId());
                switch (miningMode) {
                    case SelectedTile: {
                        PickableUnit tile = Utils.getField(WurmHelper.hud.getSelectBar(), "selectedUnit");
                        if (tile != null) {
                            sendMineActions(tile.getId());
                            actionTaken = true;
                        } else
                            Utils.consolePrint("No target selected!");
                        break;
                    }
                    case Area: {
                        int area[][] = Utils.getAreaCoordinates();
                        for (int i = 1; i < area.length; i += 2) {
                            Tiles.Tile type = WurmHelper.hud.getWorld().getCaveBuffer().getTileType(area[i][0], area[i][1]);
                            if (isMineableWall(type) && !isErrorTile(area[i][0], area[i][1])) {
                                sendMineActions(area[i]);
                                lastTile = area[i];
                                actionTaken = true;
                                break;
                            }
                            if (i == 7) i = -2;
                        }
                        break;
                    }
                    case FrontTile: {
                        int area[][] = Utils.getAreaCoordinates();
                        Tiles.Tile type = WurmHelper.hud.getWorld().getCaveBuffer().getTileType(area[7][0], area[7][1]);
                        if (isMineableWall(type) && !isErrorTile(area[7][0], area[7][1])) {
                            sendMineActions(area[7]);
                            actionTaken = true;
                            lastTile = area[7];
                        } else
                            Utils.consolePrint("Can't mine the tile in front of you");
                        break;
                    }
                    case FixedTile:
                        sendMineActions(fixedTileId);
                        actionTaken = true;
                        break;
                }
                if ((!actionTaken || Math.abs(lastMining - System.currentTimeMillis()) > 120000) && moving) {
                    int area[][] = Utils.getAreaCoordinates();
                    Tiles.Tile frontTileType = WurmHelper.hud.getWorld().getCaveBuffer().getTileType(area[7][0], area[7][1]);
                    Tiles.Tile rightTileType = WurmHelper.hud.getWorld().getCaveBuffer().getTileType(area[5][0], area[5][1]);
                    Tiles.Tile leftTileType = WurmHelper.hud.getWorld().getCaveBuffer().getTileType(area[3][0], area[3][1]);
                    Utils.stabilizePlayer();
                    Thread.sleep(100);
                    if (isMinableTile(frontTileType))
                        Utils.movePlayer(4);
                    else {
                        int turn = 0;
                        boolean leftMinable = isMinableTile(leftTileType);
                        boolean rightMinable = isMinableTile(rightTileType);
                        if (movingForwardBias >= 0) {
                            if (leftMinable)
                                turn = -1;
                            else if (rightMinable)
                                turn = 1;
                        } else {
                            if (rightMinable)
                                turn = 1;
                            else if (leftMinable)
                                turn = -1;
                        }
                        // Only pick the other side at random when both sides are passable
                        if (leftMinable && rightMinable && random.nextInt(Math.abs(movingForwardBias) + 1) == 0)
                            turn = -turn;
                        if (turn == 1) {
                            Utils.turnPlayer(90);
                            Thread.sleep(100);
                            Utils.movePlayer(4);
                            Thread.sleep(100);
                            Utils.turnPlayer(-90);
                            movingForwardBias++;
                        } else if (turn == -1) {
                            Utils.turnPlayer(-90);
                            Thread.sleep(100);
                            Utils.movePlayer(4);
                            Thread.sleep(100);
                            Utils.turnPlayer(90);
                            movingForwardBias--;
                        }
                    }
                    Thread.sleep(100);
                    Utils.stabilizePlayer();
                }
                InventoryMetaItem smelterItem = smelting ? Utils.getRootItem(smeltingOptions.smelter) : null;
                if (smelting && smelterItem == null) {
                    smelting = false;
                    Utils.consolePrint("Can't access the smelter! Smelting is off");
                }
                if (smelting) {
                    List<InventoryMetaItem> lumps = Utils.getInventoryItems(smeltingOptions.smelter, "lump")
                            .stream()
                            .filter(item -> item.getRarity() == 0)
                            .collect(Collectors.toList());
                    if (lumps.size() > 0) {
                        for (int i = smeltingOptions.containers.size() - 1; i >= 0; i--) {
                            List<Long> moveList = new ArrayList<>();
                            for (int j = 0; j < lumps.size(); j++) {
                                if (lumps.get(j).getQuality() >= smeltingOptions.containers.get(i).getValue())
                                    moveList.add(lumps.get(j).getId());
                            }
                            if (moveList.size() > 0) {
                                long[] moveItemIds = new long[moveList.size()];
                                for (int k = 0; k < moveList.size(); k++)
                                    moveItemIds[k] = moveList.get(k);
                                WurmHelper.hud.getWorld().getServerConnection()
                                        .sendMoveSomeItems(smeltingOptions.containers.get(i).getKey(), moveItemIds);
                                lumps.removeIf(item -> moveList.contains(item.getId()));
                            }
                        }
                    }
                    List<InventoryMetaItem> ores = Utils.getInventoryItems(smeltingOptions.pile, "ore");
                    if (ores.size() > 0) {
                        long[] oreIds = Utils.getItemIds(ores);
                        WurmHelper.hud.getWorld().getServerConnection()
                                .sendMoveSomeItems(smelterItem.getId(), oreIds);
                    }

                    if (Math.abs(lastFuelling - System.currentTimeMillis()) > fuellingTimeout) {
                        lastFuelling = System.currentTimeMillis();
                        InventoryMetaItem item = Utils.getInventoryItem(fuel);
                        if (item != null)
                            WurmHelper.hud.getWorld().getServerConnection().sendAction(item.getId(),
                                        new long[]{smelterItem.getId()},
                                        new PlayerAction("",(short)117, PlayerAction.ANYTHING));
                        else
                            Utils.consolePrint("No fuel in inventory!");
                    }
                }
            }
            sleep(timeout);
        }
        if (shardsCombining)
            dropInventoryShards();
    }

    static private boolean isMinableTile(Tiles.Tile type) {
        return type.tilename.equals("Cave") || type.tilename.equals("Reinforced cave");
    }

    private boolean isMineableWall(Tiles.Tile type) {
        return type.tilename.equals("Cave wall") || type.tilename.equals("Rocksalt") || (type.isOreCave() && !noOre);
    }

    static <K, V> List<Map.Entry<K, V>> snapshotEntries(Map<K, V> map, int maxTries) {
        for (int tries = 0; tries < maxTries; tries++) {
            try {
                return new ArrayList<>(map.entrySet());
            } catch (ConcurrentModificationException ignored) {
            }
        }
        return Collections.emptyList();
    }

    static boolean shouldTakeShard(float freeSpace, float shardWeight) {
        return shardWeight < freeSpace;
    }

    private void selectTool() {
        InventoryMetaItem tool = Utils.selectInventoryTool(VALID_TOOLS);
        if (tool != null) {
            pickaxe = tool;
            Utils.feedback(this.getClass().getSimpleName() + " will use " + tool.getDisplayName() + " with QL:" + tool.getQuality() + " DMG:" + tool.getDamage());
        }
    }

    private void handleDirectionChange(String[] input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(MinerBot.InputKey.dir);
            printCurrentDirection();
            return;
        }
        
        Direction newDirection = Direction.getByAbbreviation(input[0]);
        if (newDirection == Direction.UNKNOWN) {
            printInputKeyUsageString(MinerBot.InputKey.dir);
            return;
        }

        direction = newDirection;
        Utils.feedback("Mining direction is now \"" + direction.abbreviation + "\"");
    }

    private void printCurrentDirection() {
        Utils.consolePrint("Current direction is \"" + direction.abbreviation + "\"");
    }

    private void toggleVerboseMode() {
        verbose = !verbose;
        Utils.feedback(getClass().getSimpleName() + " is " + (verbose?"":"not ") + "verbose");
    }

    private void setFuellingTimeout(String [] input) {
        Integer value = parseIntArg(input, MinerBot.InputKey.sft, 1000, 86400000);
        if (value == null)
            return;
        fuellingTimeout = value;
        Utils.consolePrint("New fuelling timeout is " + fuellingTimeout + " milliseconds");
    }

    private void setFuelName(String []input) {
        String fuelName = joinArgs(input);
        if (fuelName == null) {
            printInputKeyUsageString(MinerBot.InputKey.sfn);
            return;
        }
        this.fuel = fuelName;
        Utils.consolePrint("New fuel name is " + this.fuel);
    }

    private void addTarget(String []input) {
        Float minQuality = parseFloatArg(input, MinerBot.InputKey.at, 0, 100);
        if (minQuality == null)
            return;
        int x = WurmHelper.hud.getWorld().getClient().getXMouse();
        int y = WurmHelper.hud.getWorld().getClient().getYMouse();
        long[] container = WurmHelper.hud.getCommandTargetsFrom(x, y);
        if (container != null && container.length > 0) {
            addSmeltingTarget(container[0], null, minQuality);
        } else
            Utils.consolePrint("Couldn't find the target for " + getClass().getSimpleName());
    }

    private void addTargetById(String []input) {
        if (input == null || input.length != 2) {
            printInputKeyUsageString(MinerBot.InputKey.atid);
            return;
        }
        long id;
        try {
            id = Long.parseLong(input[0]);
        } catch(NumberFormatException e) {
            Utils.consolePrint("`" + input[0] + "` is not a valid id");
            printInputKeyUsageString(MinerBot.InputKey.atid);
            return;
        }
        Float q = parseFloatArg(new String[]{input[1]}, MinerBot.InputKey.atid, 0, 100);
        if (q != null)
            addSmeltingTarget(id, null, q);
    }

    private void addTargetInventory(String []input) {
        Float minQuality = parseFloatArg(input, MinerBot.InputKey.ati, 0, 100);
        if (minQuality != null)
            addTargetContainer(minQuality);
    }

    private void toggleSmelting() {
        smelting = !smelting;
        if (smelting) {
            if (smeltingOptions.smelter == null || smeltingOptions.pile == null || smeltingOptions.containers == null || smeltingOptions.containers.size() == 0) {
                Utils.consolePrint("You should set smelter, pile and containers first!");
                smelting = false;
            } else
                Utils.feedback("Smelting is on");
        } else
            Utils.feedback("Smelting is off");
    }

    private void toggleMoving() {
        moving = !moving;
        if (moving) {
            // when the bot is off this is done on start instead
            if (isAlive())
                Utils.stabilizePlayer();
            Utils.feedback(getClass().getSimpleName() + " will automatically move forward");
        }
        else
            Utils.feedback(getClass().getSimpleName() + " will NOT move automatically");
    }

    private void toggleOreMining() {
        noOre = !noOre;
        if (!noOre)
            Utils.feedback(getClass().getSimpleName() + " will mine ore tiles too");
        else
            Utils.feedback(getClass().getSimpleName() + " will NOT mine ore tiles");
    }

    private void setFrontTileMiningMode() {
        miningMode = MiningMode.FrontTile;
        errorTiles.clear();
        Utils.feedback(getClass().getSimpleName() + " will mine the tile in front of you");
    }

    private void setAreaMiningMode() {
        miningMode = MiningMode.Area;
        errorTiles.clear();
        Utils.feedback(getClass().getSimpleName() + " will mine the 3x3 area around you");
    }

    private void setSelectedTileMiningMode() {
        miningMode = MiningMode.SelectedTile;
        errorTiles.clear();
        Utils.feedback(getClass().getSimpleName() + " will mine the selected tile");
    }

    private void setClicksNumber(String []input) {
        Integer n = parseIntArg(input, MinerBot.InputKey.c, 1, 10);
        if (n == null)
            return;
        clicks = n;
        Utils.consolePrint(getClass().getSimpleName() + " will do " + clicks + " clicks each time");
    }

    private void setCombiningShardsName(String []input) {
        String name = joinArgs(input);
        if (name == null) {
            printInputKeyUsageString(MinerBot.InputKey.scn);
            return;
        }
        this.shards = name;
        Utils.consolePrint(getClass().getSimpleName() + " will combine " + this.shards
                + (shardsCombining ? "" : " (shard combining is off, turn it on with \"" + MinerBot.InputKey.sc.name() + "\")"));
    }

    private void setFixedMiningMode() {
        PickableUnit tile;
        try {
            tile = Utils.getField(WurmHelper.hud.getSelectBar(), "selectedUnit");
        } catch (IllegalAccessException | NoSuchFieldException e) {
            Utils.consolePrint("Error on getting tile information");
            return;
        }
        if (tile != null) {
            fixedTileId = tile.getId();
            miningMode = MiningMode.FixedTile;
            errorTiles.clear();
            Utils.feedback(getClass().getSimpleName() + " will mine the selected tile and remember it");
        } else
            Utils.consolePrint("No tile selected!");
    }

    private void toggleShardsCombining() {
        shardsCombining = !shardsCombining;
        if (shardsCombining)
            Utils.feedback(getClass().getSimpleName() + " will combine the " + shards + " around you");
        else {
            Utils.feedback("Shards combining is off");
            // only drop what the running bot picked up
            if (isAlive())
                dropInventoryShards();
        }
    }

    private void dropInventoryShards() {
        List<InventoryMetaItem> invShards = Utils.getInventoryItems(shards);
        if (invShards.isEmpty()) return;
        if (verbose)
            Utils.consolePrint("Dropping " + invShards.size() + " leftover " + shards + " from inventory");
        for (InventoryMetaItem shard : invShards)
            WurmHelper.hud.sendAction(PlayerAction.DROP, shard.getId());
    }

    private void tileError() {
        // lastTile is only tracked in Area and FrontTile modes
        if (lastTile != null && (miningMode == MiningMode.Area || miningMode == MiningMode.FrontTile))
            errorTiles.add(new Pair<>(lastTile[0], lastTile[1]));
    }

    private boolean isErrorTile(int x, int y) {
        Pair pair = new Pair<>(x, y);
        return errorTiles.contains(pair);
    }

    private void registerEventProcessors() {
        registerEventProcessor(message -> message.contains("The cave walls sound hollow")
                || message.contains("Another tunnel is too close")
                || message.contains("The cave walls look very unstable.")
                || message.contains("The cave walls look very unstable and dirt flows in")
                || message.contains("A dangerous crack is starting to form on the floor")
                || message.contains("The ground is too steep to mine at here")
                || message.contains("The ground sounds strangely hollow and brittle")
                || message.contains("You fail to produce anything here.")
                || message.contains("You hear falling rocks from the other side of the wall.")
                || message.contains("You cannot keep mining here. The rock is unusually hard")
                || message.contains("The roof sounds strangely hollow and you notice dirt flowing in, so you stop mining")
                || message.contains("The roof sounds dangerously weak and you must abandon this attempt")
                || message.contains("You are not allowed to mine here")
                || message.contains("The rock is too hard to mine")
                || message.contains("on the surface disturbs your operation")
                || message.contains("This tile is protected by the gods. You can not mine here")
                || message.contains("A felled tree on the surface disturbs your operation")
                || message.contains("Lowering the floor further would make the cavern unstable"), this::tileError);
        registerEventProcessor(message -> message.contains("You mine "), () -> lastMining = System.currentTimeMillis());
    }

    private void sendMineActions(int coords[]) {
        //wallside is 5 for north, 4 for west, 3 for south, 2 for east
        int x = WurmHelper.hud.getWorld().getPlayerCurrentTileX();
        int y = WurmHelper.hud.getWorld().getPlayerCurrentTileY();
        int wallSide = 0;
        if (x != coords[0] && y != coords[1]) {
            Tiles.Tile westTileType = WurmHelper.hud.getWorld().getCaveBuffer().getTileType(x - 1, y);
            Tiles.Tile eastTileType = WurmHelper.hud.getWorld().getCaveBuffer().getTileType(x + 1, y);
            Tiles.Tile northTileType = WurmHelper.hud.getWorld().getCaveBuffer().getTileType(x, y - 1);
            Tiles.Tile southTileType = WurmHelper.hud.getWorld().getCaveBuffer().getTileType(x, y + 1);
            if (coords[0] < x && isMinableTile(westTileType)) {
                if (coords[1] < y)
                    wallSide = 3;
                else if (coords[1] > y)
                    wallSide = 5;
            } else if (coords[0] > x && isMinableTile(eastTileType)) {
                if (coords[1] < y)
                    wallSide = 3;
                else if (coords[1] > y)
                    wallSide = 5;
            } else if (coords[1] < y && isMinableTile(northTileType)) {
                if (coords[0] > x)
                    wallSide = 4;
                else if (coords[0] < x)
                    wallSide = 2;
            } else if (coords[1] > y && isMinableTile(southTileType)) {
                if (coords[0] > x)
                    wallSide = 4;
                else if (coords[0] < x)
                    wallSide = 2;
            }
        } else if (coords[0] < x)
            wallSide = 2;
        else if (coords[0] > x)
            wallSide = 4;
        else if (coords[1] < y)
            wallSide = 3;
        else if (coords[1] > y)
            wallSide = 5;
        sendMineActions(Tiles.getTileId(coords[0], coords[1], wallSide, false));
    }

    private void sendMineActions(long tileId) {
        if (verbose) Utils.consolePrint("Mining tile " + tileId);

        for (int i = 0; i < clicks; i++)
            WurmHelper.hud.getWorld().getServerConnection().sendAction(
                    pickaxe.getId(),
                    new long[]{tileId},
                    direction.action);
    }

    private void addTargetContainer(float minQuality) {
        WurmComponent container = Utils.getTargetComponent(c -> c instanceof ItemListWindow);
        if (container == null) {
            Utils.consolePrint("Can't find the container!");
            return;
        }
        try {
            InventoryListComponent ilc = Utils.getField(container, "component");
            InventoryMetaItem rootItem = Utils.getRootItem(ilc);
            if (rootItem == null) {
                Utils.consolePrint("Target container has no root item");
                return;
            }
            addSmeltingTarget(rootItem.getId(), rootItem.getBaseName(), minQuality);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    private void addSmeltingTarget(long id, String name, float minQuality) {
        smeltingOptions.containers.add(new Pair<>(id, minQuality));
        smeltingOptions.containers.sort(Comparator.comparingDouble(Pair::getValue));
        Utils.feedback("Added a new target " + (name != null ? name + " " : "") + "with id - " + id +
                " and minimum quality - " + String.format("%.2f", minQuality));
    }

    private void setSmelter() {
        WurmComponent smelter = Utils.getTargetComponent(c -> c instanceof ItemListWindow);
        if (smelter == null) {
            Utils.consolePrint("Can't set the smelter!");
            return;
        }
        try {
            smeltingOptions.smelter = Utils.getField(smelter, "component");
            Utils.feedback("The smelter is set: " + componentName(smeltingOptions.smelter));
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    private void setPile() {
        WurmComponent pile = Utils.getTargetComponent(c -> c instanceof ItemListWindow);
        if (pile == null) {
            Utils.consolePrint("Can't set the pile!");
            return;
        }
        try {
            smeltingOptions.pile = Utils.getField(pile, "component");
            Utils.feedback("The pile is set: " + componentName(smeltingOptions.pile));
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    static class SmeltingOptions {
        InventoryListComponent smelter;
        InventoryListComponent pile;
        ArrayList<Pair<Long, Float>> containers = new ArrayList<>();
    }

    enum MiningMode{
        Unknown,
        SelectedTile,
        Area,
        FrontTile,
        FixedTile
    }

    private enum InputKey implements Bot.InputKey {
        s("Stamina", "Set the stamina threshold (0 to 1). Player will not do any actions if his stamina is lower than specified threshold",
                "<threshold>"),
        c("Clicks", "Change the amount of clicks bot will do each time (1 to 10)", "<clicks>"),
        sc("Shard Combining", "Toggle the combining of shards lying around the player in piles", ""),
        scn("Shards Name", "Change the name of shards to combine. See \"" + sc.name() + "\" key", "<shards name>"),
        fixed("Fixed Tile", "Set the fixed tile mining mode. Bot will remember selected tile and mine it", ""),
        st("Selected Tile", "Set the mining mode in which bot will mine currently selected tile", ""),
        area("Area Mining", "Set the 3x3 area mining mode in which bot will mine the 3x3 area around player (takes no arguments)", ""),
        ft("Front Tile", "Set the mining mode in which bot will mine a tile in front of a player", ""),
        o("Ore Mining", "Toggle the mining of ore tiles. Enabled by default", ""),
        m("Moving", "Toggle the automatic moving forward when bot have no work", ""),
        sm("Smelting", "Toggle the smelting of ores in selected pile. Set the smelter, pile and lump targets first", ""),
        at("Add Target", "Add the target (under the mouse cursor) for lumps with provided minimum quality (0-100)", "<min quality>"),
        ati("Add Target Inventory", "Add the target inventory (under the mouse cursor) for lumps with provided minimum quality (0-100)", "<min quality>"),
        atid("Add Target By ID", "Add the target with provided id for lumps with provided minimum quality (0-100)", "<id> <min quality>"),
        sp("Set Pile", "Set a pile (under the mouse cursor) for smelting ores", ""),
        ssm("Set Smelter", "Set a smelter (under the mouse cursor) for smelting ores", ""),
        sft("Fuel Timeout", "Set a smelter fuelling timeout (in milliseconds) for smelting ores", "<milliseconds>"),
        sfn("Fuel Name", "Set a name for the fuel for smelting ores", "<fuel name>"),
        v("Verbose", "Toggle the verbose mode. While verbose bot will show additional info in console", ""),
        dir("Direction", "Set mining direction. Possible directions are: f - forward, u - upward, d - downward. Forward is default direction", "<f|u|d>"),
        tool("Tool", "Set the mining tool from selected inventory item", "");

        private final KeyInfo keyInfo;

        InputKey(String fullName, String description, String usage) {
            keyInfo = new KeyInfo(fullName, description, usage);
        }

        @Override
        public KeyInfo keyInfo() {
            return keyInfo;
        }
    }

    enum Direction {
        UNKNOWN("", PlayerAction.MINE_FORWARD),
        FORWARD("f", PlayerAction.MINE_FORWARD),
        UPWARD("u", PlayerAction.MINE_UP),
        DOWNWARD("d", PlayerAction.MINE_DOWN);

        String abbreviation;
        PlayerAction action;
        Direction(String abbreviation, PlayerAction action)
        {
            this.abbreviation = abbreviation;
            this.action = action;
        }

        static Direction getByAbbreviation(String abbreviation) {
            for(Direction direction : values())
                if (direction.abbreviation.equals(abbreviation))
                    return direction;
            return UNKNOWN;
        }
    }
}
