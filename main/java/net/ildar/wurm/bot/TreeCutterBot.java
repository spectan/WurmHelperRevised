package net.ildar.wurm.bot;

import com.wurmonline.client.comm.ServerConnectionListenerClass;
import com.wurmonline.client.game.PlayerObj;
import com.wurmonline.client.game.World;
import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.GroundItemData;
import com.wurmonline.client.renderer.cell.GroundItemCellRenderable;
import com.wurmonline.client.renderer.gui.CreationWindow;
import com.wurmonline.mesh.FoliageAge;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.mesh.TreeData;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.Pair;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.Utils;
import net.ildar.wurm.annotations.BotInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@BotInfo(description =
        "Cuts trees",
        abbreviation = "tc")
public class TreeCutterBot extends Bot{
    private float staminaThreshold;
    private int maxActions;

    private TreeAge minTreeAge;
    private String treeType;
    private boolean bushCutting;
    private boolean sproutingTreeCutting;

    private long hatchetId;
    private InventoryMetaItem selectedTool;
    private long lastActionFinishedTime;
    private Byte[] sproutingAgeId = {7,9,11,13};
    private static final String[] VALID_TOOLS = {"hatchet", "small axe", "axe", "huge axe", "longsword", "two handed sword", "short sword", "shovel", "pickaxe", "sickle", "rake", "scythe"};

    private AreaAssistant areaAssistant = new AreaAssistant(this);
    private List<Pair<Integer, Integer>> queuedTiles = new ArrayList<>();

    public TreeCutterBot(){
        registerInputHandler(InputKey.s, this::setStaminaThreshold);
        registerInputHandler(InputKey.c, this::setMaxActions);
        registerInputHandler(InputKey.a, this::setMinAge);
        registerInputHandler(InputKey.tool, input -> selectTool());
        registerInputHandler(InputKey.al, input-> showAgesList());
        registerInputHandler(InputKey.tt, this::setTreeType);
        registerInputHandler(InputKey.b, input-> toggleBushCutting());
        registerInputHandler(InputKey.sp, input-> toggleSproutingTreeCutting());

        areaAssistant.setMoveAheadDistance(1);
        areaAssistant.setMoveRightDistance(1);

        bushCutting = true;
        sproutingTreeCutting = true;
        minTreeAge=TreeAge.any;
        treeType="";
    }

    @Override
    public void work() throws Exception {
        setStaminaThreshold(0.96f);
        setMaxActions(Utils.getMaxActionNumber());
        World world = WurmHelper.hud.getWorld();
        PlayerObj player = world.getPlayer();
        lastActionFinishedTime = System.currentTimeMillis();

        if (selectedTool == null) {
            InventoryMetaItem hatchet = Utils.locateToolItem("hatchet");
            if (hatchet == null) {
                Utils.consolePrint("You don't have a hatchet! " + this.getClass().getSimpleName() + " won't start");
                deactivate();
                return;
            }
            selectedTool = hatchet;
            hatchetId = hatchet.getId();
        }
        Utils.consolePrint(this.getClass().getSimpleName() + " will use " + selectedTool.getDisplayName() + " with QL:" + selectedTool.getQuality() + " DMG:" + selectedTool.getDamage());
        CreationWindow creationWindow = WurmHelper.hud.getCreationWindow();
        Object progressBar = Utils.getField(creationWindow, "progressBar");

        ServerConnectionListenerClass sscc = WurmHelper.hud.getWorld().getServerConnection().getServerConnectionListener();
        registerEventProcessors();
        while (isActive()) {
            waitOnPause();
            float progress = Utils.getField(progressBar, "progress");

            if (Math.abs(lastActionFinishedTime - System.currentTimeMillis()) > 10000 && hasStamina(staminaThreshold))
                queuedTiles.clear();

            if (hasStamina(staminaThreshold) && queuedTiles.size() == 0) {
                int checkedtiles[][] = Utils.getAreaCoordinates();
                int tileIndex = -1;

                while (++tileIndex < 9 && queuedTiles.size() < maxActions){
                    Pair<Integer, Integer> coordsPair = new Pair<>(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);
                    if (queuedTiles.contains(coordsPair))
                        continue;

                    Map<Long, GroundItemCellRenderable> beeItems = Utils.getField(sscc, "groundItems");
                    for(int tries = 0; tries < 5; tries++) {
                        try {
                            beeItems = beeItems.entrySet().stream().filter(entry -> {
                                try {
                                    GroundItemData groundItemData = Utils.getField(entry.getValue(), "item");
                                    return groundItemData.getName().contains("hive");
                                } catch (Exception e) {
                                    Utils.consolePrint(e.getMessage());
                                }
                                return false;
                            }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                            break;
                        } catch(ConcurrentModificationException err) {
                            if(tries < 4)
                                continue;
                            else {
                                Utils.consolePrint(
                                    "%s: Unable to find beehive items due to repeated ConcurrentModificationException",
                                    TreeCutterBot.class.getSimpleName()
                                );
                                beeItems = Collections.emptyMap();
                            }
                        }
                    }
                    Tiles.Tile tileType = world.getNearTerrainBuffer().getTileType(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);
                    byte tileData = world.getNearTerrainBuffer().getData(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);

                    if (tileType.isTree() || tileType.isBush() && bushCutting) {
                        FoliageAge fage = FoliageAge.getFoliageAge(tileData);
                        TreeData.TreeType ttype = tileType.getTreeType(tileData);

                        boolean isRightAge=fage.getAgeId() >= minTreeAge.id;
                        boolean isCutSprouts = sproutingTreeCutting || !Arrays.asList(sproutingAgeId).contains(fage.getAgeId());
                        boolean isRightType = treeType.equals("") || treeType.contains(TreeData.TreeType.fromInt(ttype.getTypeId()).toString().toLowerCase());
                        boolean isHive = false;
                        if (beeItems.size() > 0) {
                            for (Map.Entry<Long, GroundItemCellRenderable> entry : beeItems.entrySet()) {
                                Long beeTile = Tiles.getTileId((int) (entry.getValue().getXPos() / 4.0f), (int) (entry.getValue().getYPos() / 4.0f), 0);
                                if (Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0) == beeTile) {
                                    isHive = true;
                                    break;
                                }
                            }
                        }
                        if(isRightAge && isCutSprouts && isRightType && !isHive){
                            world.getServerConnection().sendAction(hatchetId,
                                    new long[]{Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0)},
                                    PlayerAction.CUT_DOWN);
                            lastActionFinishedTime = System.currentTimeMillis();
                            queuedTiles.add(coordsPair);
                        }
                    }
                }
                if (queuedTiles.size() == 0 && areaAssistant.areaTourActivated() && progress == 0f)
                    areaAssistant.areaNextPosition();

            }
            sleep(timeout);
        }
    }

    private void registerEventProcessors() {
        registerEventProcessor(message -> message.contains("You are too far away") ,
                this::actionNotQueued);
        registerEventProcessor(message -> (message.contains("You stop cutting down.")
                        || message.contains("You cut down the ")
                        || message.contains("You chip away some wood")),
                this::actionFinished);
    }

    private void actionFinished() {
        if (queuedTiles.size() > 0) {
            queuedTiles.remove(0);
            lastActionFinishedTime = System.currentTimeMillis();
        }
    }

    private void actionNotQueued() {
        if (queuedTiles.size() > 0) {
            queuedTiles.remove(queuedTiles.size()  - 1);
            lastActionFinishedTime = System.currentTimeMillis();
        }
    }

    private void setStaminaThreshold(String input[]) {
        if (input == null || input.length != 1)
            printInputKeyUsageString(TreeCutterBot.InputKey.s);
        else {
            try {
                float threshold = Float.parseFloat(input[0]);
                setStaminaThreshold(threshold);
            } catch (Exception e) {
                Utils.consolePrint("Wrong threshold value!");
            }
        }
    }

    private void setTreeType(String[] strings) {
        if (strings == null ) {
            printInputKeyUsageString(TreeCutterBot.InputKey.tt);
            return;
        }

        treeType = String.join(" ", strings).toLowerCase();
        Utils.consolePrint("The bot cut " +treeType);
    }
    private void toggleBushCutting() {
        bushCutting=!bushCutting;
        if (bushCutting)
            Utils.consolePrint("Bushes cutting is on!");
        else
            Utils.consolePrint("Bushes cutting is off!");
    }
    private void toggleSproutingTreeCutting() {
        sproutingTreeCutting = !sproutingTreeCutting;
        if (sproutingTreeCutting)
            Utils.consolePrint("Sprouting trees cutting is on!");
        else
            Utils.consolePrint("Sprouting trees cutting is off!");
    }
    private void setMinAge(String[] input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(TreeCutterBot.InputKey.a);
            return;
        }
        minTreeAge = TreeAge.getByNameOrAbbreviation(input[0]);

        Utils.consolePrint("Minimal tree age set to " +minTreeAge.name+"!");
    }

    private void setMaxActions(String[] input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(TreeCutterBot.InputKey.c);
            return;
        }
        try {
            setMaxActions(Integer.parseInt(input[0]));
        } catch (NumberFormatException e) {
            Utils.consolePrint("Invalid max actions value: " + input[0]);
        }
    }

    private void setMaxActions(int num){
        this.maxActions = num;
        Utils.consolePrint(getClass().getSimpleName() + " will do " + maxActions + " chops each time");
    }

    private void selectTool() {
        InventoryMetaItem tool = Utils.selectInventoryTool(VALID_TOOLS);
        if (tool != null) {
            selectedTool = tool;
            hatchetId = tool.getId();
            Utils.consolePrint(this.getClass().getSimpleName() + " will use " + tool.getDisplayName() + " with QL:" + tool.getQuality() + " DMG:" + tool.getDamage());
        }
    }

    private void setStaminaThreshold(float s) {
        staminaThreshold = s;
        Utils.consolePrint("Current threshold for stamina is " + staminaThreshold);
    }

    private void showAgesList() {
        Utils.consolePrint("Age abbreviation");
        for(TreeAge age : TreeAge.values())
            Utils.consolePrint(age.name + " " + age.name());
    }

    private enum InputKey implements Bot.InputKey {
        s("Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold",
                "threshold(float value between 0 and 1)"),
        tt("Set tree types for chopping. Chop all trees by default", "birch oak"),
        c("Set chops number", "1"),
        a("Set minimal tree age for chopping. Chop all trees by default", "ov"),
        tool("Set the cutting tool from selected inventory item.", "tool"),
        al("Get ages abbreviation list", ""),
        b("Toggle bush cutting. Enabled by default", ""),
        sp("Toggle sprouting trees cutting. Enabled by default", "");

        public String description;
        public String usage;
        InputKey(String description, String usage) {
            this.description = description;
            this.usage = usage;
        }

        @Override
        public String getName() {
            return name();
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public String getUsage() {
            return usage;
        }
    }

    private enum TreeAge {
        //any, mature, old, very old, overaged, shivered
        any(0,"any"),
        m(4,"mature"),
        o(8,"old"),
        vo(12,"very old"),
        oa(14,"overaged"),
        s(15,"shriveled");

        TreeAge(int id, String name){
            this.id=id;
            this.name=name;
        }

        public int id;
        public String name;

        static TreeAge getByNameOrAbbreviation(String input) {
            for (TreeAge treeAge : values())
                if (treeAge.name().equals(input) || treeAge.name.equals(input))//name() is collection element name, not name parameter
                    return treeAge;
            return any;
        }
    }

}
