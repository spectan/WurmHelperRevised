package net.ildar.wurm.bot;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.TilePicker;
import com.wurmonline.client.renderer.gui.CreationWindow;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.Pair;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.Utils;
import net.ildar.wurm.annotations.BotInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@BotInfo(name = "Digger", description =
        "Does the dirty job",
        abbreviation = "d")
public class DiggerBot extends Bot{
    private final int STEPS = 5;

    private long stepDuration;
    private volatile int clicks;
    private volatile WorkMode workMode;
    private volatile int diggingHeightLimit;
    private volatile boolean levellingDone;
    private volatile boolean toolRepairing = true;
    private volatile DiggingTileInfo diggingTileInfo;
    private AreaAssistant areaAssistant;
    private SlopeAwareMovement slopeMovement;
    private volatile InventoryMetaItem shovelItem;
    private volatile PlayerAction digAction;
    private Set<Pair<Integer, Integer>> invalidCorners;
    private volatile boolean surfaceMiningMode;
    private volatile InventoryMetaItem pickaxeItem;

    private static final Set<Tiles.Tile> DIRT_TILES = new HashSet<>(Arrays.asList(Tiles.Tile.TILE_DIRT, Tiles.Tile.TILE_GRASS, Tiles.Tile.TILE_SAND, Tiles.Tile.TILE_MYCELIUM, Tiles.Tile.TILE_TUNDRA, Tiles.Tile.TILE_STEPPE));

    public DiggerBot() {
        registerStaminaThresholdHandler(DiggerBot.InputKey.s);
        registerInputHandler(DiggerBot.InputKey.c, this::setClicksNumber);
        registerInputHandler(DiggerBot.InputKey.d, this::toggleDigging);
        registerInputHandler(DiggerBot.InputKey.dtile, this::toggleTileDiggingMode);
        registerInputHandler(DiggerBot.InputKey.dtp, input -> toggleDigToPileAction());
        registerInputHandler(DiggerBot.InputKey.l, input -> toggleLevelling());
        registerInputHandler(DiggerBot.InputKey.la, this::toggleLevellingArea);
        registerInputHandler(DiggerBot.InputKey.tr, input -> toggleToolRepairing());
        registerInputHandler(DiggerBot.InputKey.sm, input -> toggleSurfaceMining());
        registerInputHandler(DiggerBot.InputKey.tool, input -> selectTool());

        areaAssistant = new AreaAssistant(this);
        slopeMovement = new SlopeAwareMovement();
        areaAssistant.setMoveStrategy(slopeMovement::moveForward);
        areaAssistant.setMoveAheadDistance(1);
        areaAssistant.setMoveRightDistance(2);
        invalidCorners = ConcurrentHashMap.newKeySet();
        digAction = PlayerAction.DIG_TO_PILE;
        workMode = WorkMode.Unknown;
        stepDuration = 1000;
        staminaThreshold = 0.95f;
        timeout = 500;
        clicks = Utils.getMaxActionNumber();
    }

    @Override
    void describeSettings(List<String> lines) {
        lines.add("Clicks: " + clicks);
        String mode;
        switch (workMode) {
            case Digging: mode = "digging to height " + diggingHeightLimit; break;
            case DiggingTile: mode = "digging tile" + (diggingTileInfo != null ? " (" + diggingTileInfo.x + "," + diggingTileInfo.y + ")" : "") + " to height " + diggingHeightLimit; break;
            case Levelling: mode = "levelling selected tile"; break;
            case LevellingArea: mode = "levelling area to height " + diggingHeightLimit; break;
            default: mode = "none";
        }
        lines.add("Mode: " + mode);
        lines.add("Dig action: " + (digAction != null && digAction.getId() == PlayerAction.DIG_TO_PILE.getId() ? "dig to pile" : "dig"));
        lines.add("Repair: " + onOff(toolRepairing));
        lines.add("Surface mining: " + onOff(surfaceMiningMode));
        lines.add("Shovel: " + (shovelItem != null ? shovelItem.getDisplayName() : "auto (looked up on start)"));
        areaAssistant.describeSettings(lines);
    }

    private void registerEventProcessors() {
        registerEventProcessor(message ->
                        message.contains("is too steep for your skill level") ||
                                message.contains("ground is flat here") ||
                                message.contains("You finish levelling"),
                () -> levellingDone = true);
        registerEventProcessor(message -> message.contains("You can not dig in the solid rock") ||
                message.contains("You hit the rock in a corner") ||
                message.contains("The road would be too steep to traverse") ||
                message.contains("The water is too deep or too shallow to dig using that tool") ||
                message.contains("You are not skilled enough to dig in such steep slopes") ||
                message.contains("You cannot dig in such terrain") ||
                message.contains("You hit rock") ||
                message.contains("Your shovel fails to penetrate the earth no matter what you try. Weird") ||
                message.contains("You suddenly become very weak, and your arm muscles fail you. You just can not dig here it seems") ||
                message.contains("You can't figure out how to remove the stone. You must become a bit better at digging first") ||
                message.contains("You need to be stronger to dig on roads") ||
                message.contains("The object nearby prevents digging further down"), this::handleInvalidCorner);
    }

    @Override
    protected void work() throws Exception{
        if (shovelItem == null)
            shovelItem = Utils.locateToolItem("shovel");
        if (shovelItem == null) {
            Utils.consolePrint("Player doesn't have a shovel!");
            return;
        }
        if (surfaceMiningMode && pickaxeItem == null)
            pickaxeItem = Utils.locateToolItem("pickaxe");
        if (surfaceMiningMode && pickaxeItem == null) {
            Utils.consolePrint("Player doesn't have a pickaxe!");
            return;
        }
        CreationWindow creationWindow = WurmHelper.hud.getCreationWindow();
        Object progressBar = Utils.getField(creationWindow, "progressBar");
        registerEventProcessors();
        try {
            while (isActive()) {
                waitOnPause();
                if (toolRepairing) {
                    if (surfaceMiningMode && pickaxeItem.getDamage() > 10)
                        WurmHelper.hud.sendAction(PlayerAction.REPAIR, pickaxeItem.getId());
                    if (!surfaceMiningMode && shovelItem.getDamage() > 10)
                        WurmHelper.hud.sendAction(PlayerAction.REPAIR, shovelItem.getId());
                }
                float progress = Utils.getField(progressBar, "progress");
                stopDiggingIfHeightIsLower(progressBar);
                if (progress == 0f && slopeMovement.recoverIfNeeded(
                        WurmHelper.hud.getWorld().getPlayer().getStamina(),
                        WurmHelper.hud.getWorld().getPlayer().getDamage(),
                        staminaThreshold, stepDuration))
                    continue;
                if (hasStamina(staminaThreshold) && progress == 0f) {
                    switch (workMode) {
                        case Digging: {
                            boolean actionsMade = doDigActions();
                            if (!actionsMade) {
                                workMode = WorkMode.Unknown;
                                slopeMovement.stopClimbing();
                                Utils.showOnScreenMessage("Digging is over");
                                clearInvalidCorners();
                            }
                            break;
                        }
                        case DiggingTile:{
                            boolean actionsMade = doDigActions();
                            if (!actionsMade) {
                                if (validCornersExists())
                                    moveToNextTileCorner();
                                else {
                                    slopeMovement.moveTo(diggingTileInfo.x * 4 + 2, diggingTileInfo.y * 4 + 2, STEPS, stepDuration);
                                    if (areaAssistant.areaTourActivated()) {
                                        while(areaAssistant.areaTourActivated()) {
                                            areaAssistant.areaNextPosition();
                                            diggingTileInfo.x = (int)(WurmHelper.hud.getWorld().getPlayerPosX() / 4);
                                            diggingTileInfo.y = (int)(WurmHelper.hud.getWorld().getPlayerPosY() / 4);
                                            if (validCornersExists()) {
                                                moveToNextTileCorner();
                                                break;
                                            }
                                        }
                                    } else {
                                        Utils.showOnScreenMessage("The digging is over");
                                        workMode = WorkMode.Unknown;
                                        slopeMovement.stopClimbing();
                                        clearInvalidCorners();
                                    }
                                }
                            }
                            break;
                        }
                        case Levelling: {
                            if (levellingDone) {
                                finishLeveling();
                                break;
                            }
                            PickableUnit pickableUnit = Utils.getField(WurmHelper.hud.getSelectBar(), "selectedUnit");
                            if (pickableUnit != null && pickableUnit instanceof TilePicker) {
                                if (pickableUnit.getHoverName().contains("(flat)")) {
                                    finishLeveling();
                                    break;
                                }
                                WurmHelper.hud.getWorld().getServerConnection().sendAction(shovelItem.getId(),
                                        new long[]{pickableUnit.getId()},
                                        PlayerAction.LEVEL);
                            } else {
                                Utils.consolePrint("Dirt tile is not selected!");
                            }
                            break;
                        }
                        case LevellingArea: {
                            int[][] area = Utils.getAreaCoordinates();
                            boolean actionTaken = false;
                            for (int i = 0; i < area.length; i += 2) {
                                if (i == 4) continue;
                                Tiles.Tile tileType = WurmHelper.hud.getWorld().getNearTerrainBuffer().getTileType(area[i][0], area[i][1]);
                                boolean levelableTile = tileType == Tiles.Tile.TILE_DIRT || tileType == Tiles.Tile.TILE_GRASS;
                                if (levelableTile && needLevelling(area[i][0], area[i][1])) {
                                    WurmHelper.hud.getWorld().getServerConnection().sendAction(shovelItem.getId(),
                                            new long[]{Tiles.getTileId(area[i][0], area[i][1], 0)},
                                            PlayerAction.LEVEL);
                                    actionTaken = true;
                                    break;
                                }
                            }
                            if (!actionTaken) {
                                areaAssistant.areaNextPosition();
                            }
                            break;
                        }
                    }
                }
                sleep(timeout);
            }
        } finally {
            slopeMovement.stopClimbing();
            slopeMovement.forceClimbingOff();
        }
    }

    private void finishLeveling() {
        workMode = WorkMode.Unknown;
        slopeMovement.stopClimbing();
        Utils.showOnScreenMessage("The levelling is over");
        levellingDone = false;
    }

    private boolean needLevelling(int x, int y) {
        int minH = Integer.MAX_VALUE;
        int maxH = Integer.MIN_VALUE;
        boolean highCornerSurroundedWithDirt = true;
        for(int dx = 0; dx < 2; dx++) {
            for(int dy = 0; dy < 2; dy++) {
                int h = (int) (WurmHelper.hud.getWorld().getNearTerrainBuffer().getHeight(x + dx, y + dy) * 10);
                if (h > maxH) {
                    maxH = h;
                    if (maxH > diggingHeightLimit && highCornerSurroundedWithDirt) {
                        highCornerSurroundedWithDirt = !isRockTileNear(x + dx, y + dy);
                    }
                }
                if (h < minH)
                    minH = h;
            }
        }
        if (maxH > diggingHeightLimit) {
            if (!highCornerSurroundedWithDirt) {
                Utils.consolePrint("The tile (" + x + ", " + y + ") has higher rock corners");
                return false;
            } else
                return maxH != minH;
        }
        return minH < diggingHeightLimit && maxH != minH;
    }

    private void stopDiggingIfHeightIsLower(Object progressBar) throws Exception{
        int x = Math.round(WurmHelper.hud.getWorld().getPlayerPosX() / 4);
        int y = Math.round(WurmHelper.hud.getWorld().getPlayerPosY() / 4);
        int h = (int) (WurmHelper.hud.getWorld().getNearTerrainBuffer().getHeight(x, y)* 10);
        String actionKey = "digging";
        if (surfaceMiningMode)
            actionKey = "mining";
        if (h <= diggingHeightLimit) {
            String actionName = Utils.getField(progressBar, "title");
            if (actionName != null && actionName.contains(actionKey))
                WurmHelper.hud.sendAction(PlayerAction.STOP, 0);
        }
    }

    /**
     *
     * @return true if actions were made
     */
    private boolean doDigActions() {
        int x = Math.round(WurmHelper.hud.getWorld().getPlayerPosX() / 4);
        int y = Math.round(WurmHelper.hud.getWorld().getPlayerPosY() / 4);
        Tiles.Tile tileType = WurmHelper.hud.getWorld().getNearTerrainBuffer().getTileType(x, y);
        if (isCornerInvalid(x, y))
            return false;
        int h = (int) (WurmHelper.hud.getWorld().getNearTerrainBuffer().getHeight(x, y)* 10);
        if (h > diggingHeightLimit) {
            if (WurmHelper.hud.getWorld().getPlayerLayer() < 0)
                return false;
            int neededClicks = Math.min(h - diggingHeightLimit, clicks);
            if (surfaceMiningMode) {
                boolean actionSent = false;
                for (int i = 0; i < neededClicks; i++) {
                    if(isTileRock(tileType)) {
                        WurmHelper.hud.getWorld().getServerConnection().sendAction(pickaxeItem.getId(),
                                new long[]{Tiles.getTileId(x, y, 0)},
                                PlayerAction.MINE_FORWARD);
                        actionSent = true;
                    }else if(isTileDirt(tileType)){
                        WurmHelper.hud.getWorld().getServerConnection().sendAction(shovelItem.getId(),
                                new long[]{Tiles.getTileId(x, y, 0)},
                                digAction);
                        actionSent = true;
                    }
                }
                return actionSent;
            } else {
                for (int i = 0; i < neededClicks; i++) {
                    WurmHelper.hud.getWorld().getServerConnection().sendAction(shovelItem.getId(),
                            new long[]{Tiles.getTileId(x, y, 0)},
                            digAction);
                }
                return true;
            }
        }
        return false;
    }

    private boolean areSurroundingTilesRocks(int x, int y) {
        for(int dx = 0; dx < 2; dx++)
            for(int dy = 0; dy < 2; dy++) {
                Tiles.Tile tileType = WurmHelper.hud.getWorld().getNearTerrainBuffer().getTileType(x-dx, y-dy);
                if (tileType != Tiles.Tile.TILE_ROCK)
                    return false;
            }
        return true;
    }

    private boolean isRockTileNear(int x, int y) {
        for(int dx = 0; dx < 2; dx++)
            for(int dy = 0; dy < 2; dy++) {
                Tiles.Tile tileType = WurmHelper.hud.getWorld().getNearTerrainBuffer().getTileType(x-dx, y-dy);
                if (tileType == Tiles.Tile.TILE_ROCK) {
                    return true;
                }
            }
        return false;
    }

    private void handleInvalidCorner() {
        int x = Math.round(WurmHelper.hud.getWorld().getPlayerPosX() / 4);
        int y = Math.round(WurmHelper.hud.getWorld().getPlayerPosY() / 4);
        invalidCorners.add(new Pair<>(x, y));
    }

    private void clearInvalidCorners(){
        invalidCorners.clear();
    }

    private boolean validCornersExists() {
        if (workMode != WorkMode.DiggingTile)
            return false;
        for(int dx = 0; dx < 2; dx++)
            for(int dy = 0; dy < 2; dy++) {
                int x = diggingTileInfo.x + dx;
                int y = diggingTileInfo.y + dy;
                if (isCornerInvalid(x, y)) {
                    continue;
                }
                int h = (int) (WurmHelper.hud.getWorld().getNearTerrainBuffer().getHeight(x, y) * 10);
                if (h > diggingHeightLimit)
                    return true;
            }
        return false;
    }

    private boolean isTileRock(int x, int y){
        Tiles.Tile t = WurmHelper.hud.getWorld().getNearTerrainBuffer().getTileType(x,y);
        return isTileRock(t);
    }
    private boolean isTileRock(Tiles.Tile t){
        return t == Tiles.Tile.TILE_ROCK;
    }
    private boolean isTileDirt(Tiles.Tile t){
        return DIRT_TILES.contains(t);
    }

    private boolean isCornerInvalid(int x, int y) {
        if (invalidCorners.contains(new Pair<>(x, y)))
            return true;
        if (surfaceMiningMode && !areSurroundingTilesRocks(x, y) && isTileRock(x,y) )
            return true;
        if (!surfaceMiningMode && isRockTileNear(x, y))
            return true;
        return false;
    }

    private void moveToNextTileCorner() throws InterruptedException{
        if (workMode != WorkMode.DiggingTile)
            return;
        int x = Math.round(WurmHelper.hud.getWorld().getPlayerPosX() / 4);
        int y = Math.round(WurmHelper.hud.getWorld().getPlayerPosY() / 4);
        if (x < diggingTileInfo.x || x > diggingTileInfo.x + 1 || y < diggingTileInfo.y || y > diggingTileInfo.y + 1) {
            workMode = WorkMode.Unknown;
            slopeMovement.stopClimbing();
            Utils.showOnScreenMessage("You moved from tile too far away");
            return;
        }
        int destX = x;
        int destY = y;
        int tries = 0;
        boolean cornerFound = false;
        while(tries++ <= 4) {
            if (destX == diggingTileInfo.x && destY == diggingTileInfo.y)
                ++destX;
            else if (destX == diggingTileInfo.x + 1 && destY == diggingTileInfo.y)
                ++destY;
            else if (destX == diggingTileInfo.x + 1 && destY == diggingTileInfo.y + 1)
                --destX;
            else if (destX == diggingTileInfo.x && destY == diggingTileInfo.y + 1)
                --destY;
            if (isCornerInvalid(destX, destY))
                continue;
            int h = (int) (WurmHelper.hud.getWorld().getNearTerrainBuffer().getHeight(destX, destY) * 10);
            if (h > diggingHeightLimit) {
                cornerFound = true;
                break;
            }
        }
        if (cornerFound)
            slopeMovement.moveTo(destX * 4, destY * 4, STEPS, stepDuration);
    }

    private void toggleSurfaceMining() {
        if (!surfaceMiningMode) {
            pickaxeItem = Utils.locateToolItem("pickaxe");
            if (pickaxeItem == null) {
                Utils.consolePrint("You don't have a pickaxe");
                return;
            }
            surfaceMiningMode = true;
            Utils.feedback("Surface mining is on");
        } else {
            surfaceMiningMode = false;
            Utils.feedback("Surface mining is off");
        }
    }

    private void toggleLevelling() {
        if (workMode != WorkMode.Levelling) {
            workMode = WorkMode.Levelling;
            levellingDone = false;
            Utils.feedback("The levelling of selected tile is on");
        } else {
            workMode = WorkMode.Unknown;
            Utils.feedback("The levelling is off");
        }
    }


    private void toggleLevellingArea(String [] input) {
        if (input == null || input.length == 0) {
            if (workMode == WorkMode.LevellingArea) {
                workMode = WorkMode.Unknown;
                Utils.feedback("The area levelling is off");
            } else
                printInputKeyUsageString(DiggerBot.InputKey.la);
            return;
        }
        Integer height = parseIntArg(input, DiggerBot.InputKey.la, -100000, 100000);
        if (height == null)
            return;
        diggingHeightLimit = height;
        workMode = WorkMode.LevellingArea;
        areaAssistant.setMoveRightDistance(1);
        Utils.feedback("The levelling of surrounding area to height " + diggingHeightLimit + " is on");
    }

    private void toggleDigToPileAction() {
        if (digAction.getId() == PlayerAction.DIG_TO_PILE.getId()) {
            digAction = PlayerAction.DIG;
            Utils.feedback("The " + getClass().getSimpleName() + " will make default \"Dig\" actions");
        } else {
            digAction = PlayerAction.DIG_TO_PILE;
            Utils.feedback("The " + getClass().getSimpleName() + " will make \"Dig to pile\" actions");
        }
    }

    private void toggleDigging(String []input) {
        if (input == null || input.length == 0) {
            if (workMode == WorkMode.Digging) {
                workMode = WorkMode.Unknown;
                clearInvalidCorners();
                Utils.feedback("Digging was disabled");
            } else
                printInputKeyUsageString(DiggerBot.InputKey.d);
            return;
        }
        Integer height = parseIntArg(input, DiggerBot.InputKey.d, -100000, 100000);
        if (height == null)
            return;
        diggingHeightLimit = height;
        workMode = WorkMode.Digging;
        Utils.feedback(this.getClass().getSimpleName() +
                " will dig until " + diggingHeightLimit + " height is reached");
    }

    private void toggleTileDiggingMode(String []input) {
        if (input == null || input.length == 0) {
            if (workMode == WorkMode.DiggingTile) {
                workMode = WorkMode.Unknown;
                clearInvalidCorners();
                Utils.feedback("Digging of tile was disabled");
            } else
                printInputKeyUsageString(DiggerBot.InputKey.dtile);
            return;
        }
        Integer height = parseIntArg(input, DiggerBot.InputKey.dtile, -100000, 100000);
        if (height == null)
            return;
        try {
            DiggingTileInfo tileInfo = new DiggingTileInfo();
            tileInfo.x = (int)(WurmHelper.hud.getWorld().getPlayerPosX() / 4);
            tileInfo.y = (int)(WurmHelper.hud.getWorld().getPlayerPosY() / 4);
            diggingHeightLimit = height;
            diggingTileInfo = tileInfo;
            workMode = WorkMode.DiggingTile;
            areaAssistant.setMoveRightDistance(2);
            Utils.feedback("The digging of tile (" + diggingTileInfo.x + "," + diggingTileInfo.y + ") to height " + diggingHeightLimit + " is on");
        } catch(NumberFormatException e) {
            Utils.consolePrint("Error on turning the tile digging on: can't parse a number - " + e.getMessage());
        } catch(Exception e) {
            Utils.consolePrint("Error on turning the tile digging on: " + e.getMessage());
        }
    }

    private void setClicksNumber(String input[]) {
        Integer value = parseIntArg(input, DiggerBot.InputKey.c, 1, 100);
        if (value != null)
            setClicks(value);
    }

    private void setClicks(int clicks) {
        this.clicks = clicks;
        Utils.consolePrint(getClass().getSimpleName() + " will do " + clicks + " actions each time");
    }

    private void selectTool() {
        InventoryMetaItem tool = Utils.selectInventoryTool(new String[]{"shovel"});
        if (tool != null) {
            shovelItem = tool;
            Utils.feedback(this.getClass().getSimpleName() + " will use " + tool.getDisplayName() + " with QL:" + tool.getQuality() + " DMG:" + tool.getDamage());
        }
    }

    private void toggleToolRepairing() {
        toolRepairing = !toolRepairing;
        Utils.feedback("The repairing of the tool is " + onOff(toolRepairing));
    }

    private enum WorkMode{
        Unknown,
        Digging,
        DiggingTile,
        Levelling,
        LevellingArea
    }

    private static class DiggingTileInfo {
        int x;
        int y;
    }

    private enum InputKey implements Bot.InputKey {
        s("Stamina", "Set the stamina threshold (0 to 1). Player will not do any actions if his stamina is lower than specified threshold",
                "<threshold>"),
        d("Dig", "Dig until the specified height (in slopes) is reached. With a height it sets the height and turns digging on; without one it turns digging off", "[<height>]"),
        dtp("Dig To Pile", "Toggle the use of \"Dig to pile\" action instead of \"Dig\"", ""),
        dtile("Tile Digging", "Dig all 4 corners of the current tile until the specified height (in slopes) is reached. With a height it turns tile digging on; without one it turns it off", "[<height>]"),
        c("Clicks", "Set the amount of actions the bot will do each time", "<clicks>"),
        l("Levelling", "Toggle the levelling of selected tile", ""),
        la("Area Levelling", "Level the area around player to the specified height (in slopes). With a height it turns area levelling on; without one it turns it off", "[<height>]"),
        tr("Repair", "Toggle the repairing of the tool", ""),
        sm("Surface Mining", "Toggle the surface mining. The bot will do the same but with the pickaxe on the rock", ""),
        tool("Tool", "Set the digging tool from selected inventory item", "");

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
