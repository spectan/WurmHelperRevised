package net.ildar.wurm.bot;

import com.wurmonline.client.comm.ServerConnectionListenerClass;
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
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@BotInfo(name = "Tree Cutter", description =
        "Cuts trees",
        abbreviation = "tc")
public class TreeCutterBot extends Bot{
    private int maxActions;

    private TreeAge minTreeAge;
    private String treeType;
    private boolean bushCutting;
    private boolean sproutingTreeCutting;

    private long toolId;
    private InventoryMetaItem selectedTool;
    private volatile long lastActionFinishedTime;
    private static final int[] SPROUTING_AGE_IDS = {7,9,11,13};
    private static final String[] VALID_TOOLS = {"hatchet", "small axe", "axe", "huge axe", "longsword", "two handed sword", "short sword", "shovel", "pickaxe", "sickle", "rake", "scythe"};

    private AreaAssistant areaAssistant = new AreaAssistant(this);
    // modified from both the bot thread and chat callbacks; compound operations synchronize on the list
    private final List<Pair<Integer, Integer>> queuedTiles = Collections.synchronizedList(new ArrayList<>());

    public TreeCutterBot(){
        registerStaminaThresholdHandler(InputKey.s);
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
        staminaThreshold = 0.96f;
        maxActions = Utils.getMaxActionNumber();
    }

    @Override
    void describeSettings(List<String> lines) {
        lines.add("Clicks: " + maxActions);
        lines.add("Tree types: " + (treeType.isEmpty() ? "all" : treeType));
        lines.add("Minimal tree age: " + minTreeAge.name);
        lines.add("Bush cutting: " + onOff(bushCutting));
        lines.add("Sprouting trees cutting: " + onOff(sproutingTreeCutting));
        lines.add("Tool: " + (selectedTool != null ? selectedTool.getDisplayName() : "a hatchet from the inventory (looked up on start)"));
        areaAssistant.describeSettings(lines);
    }

    @Override
    public void work() throws Exception {
        World world = WurmHelper.hud.getWorld();
        lastActionFinishedTime = System.currentTimeMillis();

        if (selectedTool == null) {
            InventoryMetaItem hatchet = Utils.locateToolItem("hatchet");
            if (hatchet == null) {
                Utils.consolePrint("You don't have a valid cutting tool! " + this.getClass().getSimpleName() + " won't start");
                deactivate();
                return;
            }
            selectedTool = hatchet;
            toolId = hatchet.getId();
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
                Set<Long> hiveTiles = getHiveTileIds(sscc);

                while (++tileIndex < 9 && queuedTiles.size() < maxActions){
                    Pair<Integer, Integer> coordsPair = new Pair<>(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);
                    if (queuedTiles.contains(coordsPair))
                        continue;

                    Tiles.Tile tileType = world.getNearTerrainBuffer().getTileType(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);
                    byte tileData = world.getNearTerrainBuffer().getData(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1]);

                    if (tileType.isTree() || tileType.isBush() && bushCutting) {
                        FoliageAge fage = FoliageAge.getFoliageAge(tileData);
                        TreeData.TreeType ttype = tileType.getTreeType(tileData);

                        boolean isRightAge=fage.getAgeId() >= minTreeAge.id;
                        boolean isCutSprouts = sproutingTreeCutting || !isSproutingAge(fage.getAgeId());
                        boolean isRightType = treeType.equals("") || treeType.contains(ttype.toString().toLowerCase());
                        boolean isHive = hiveTiles.contains(Tiles.getTileId(checkedtiles[tileIndex][0], checkedtiles[tileIndex][1], 0));
                        if(isRightAge && isCutSprouts && isRightType && !isHive){
                            world.getServerConnection().sendAction(toolId,
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

    /**
     * Collect tile ids of all hives lying on the ground nearby
     */
    private Set<Long> getHiveTileIds(ServerConnectionListenerClass sscc) throws Exception {
        Map<Long, GroundItemCellRenderable> groundItems = Utils.getField(sscc, "groundItems");
        for(int tries = 0; tries < 5; tries++) {
            try {
                Set<Long> hiveTiles = new HashSet<>();
                for (GroundItemCellRenderable groundItem : groundItems.values()) {
                    try {
                        GroundItemData groundItemData = Utils.getField(groundItem, "item");
                        if (groundItemData.getName().contains("hive"))
                            hiveTiles.add(Tiles.getTileId((int) (groundItem.getXPos() / 4.0f), (int) (groundItem.getYPos() / 4.0f), 0));
                    } catch (Exception e) {
                        Utils.consolePrint(e.getMessage());
                    }
                }
                return hiveTiles;
            } catch(ConcurrentModificationException ignored) {
            }
        }
        Utils.consolePrint(
            "%s: Unable to find beehive items due to repeated ConcurrentModificationException",
            TreeCutterBot.class.getSimpleName()
        );
        return Collections.emptySet();
    }

    private static boolean isSproutingAge(int ageId) {
        for (int sproutingAgeId : SPROUTING_AGE_IDS)
            if (sproutingAgeId == ageId)
                return true;
        return false;
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
    }

    private void setTreeType(String[] strings) {
        String types = joinArgs(strings);
        if (types == null) {
            printInputKeyUsageString(TreeCutterBot.InputKey.tt);
            return;
        }
        types = types.toLowerCase();
        if (types.equals("all") || types.equals("any")) {
            treeType = "";
            Utils.feedback("The bot will cut all tree types");
            return;
        }
        treeType = types;
        Utils.feedback("The bot will cut " + treeType);
    }
    private void toggleBushCutting() {
        bushCutting=!bushCutting;
        Utils.feedback("Bush cutting is " + onOff(bushCutting));
    }
    private void toggleSproutingTreeCutting() {
        sproutingTreeCutting = !sproutingTreeCutting;
        Utils.feedback("Sprouting trees cutting is " + onOff(sproutingTreeCutting));
    }
    private void setMinAge(String[] input) {
        String ageName = joinArgs(input);
        if (ageName == null) {
            printInputKeyUsageString(TreeCutterBot.InputKey.a);
            return;
        }
        TreeAge age = TreeAge.getByNameOrAbbreviation(ageName.toLowerCase());
        if (age == null) {
            Utils.consolePrint("Unknown tree age \"" + ageName + "\". Use the \"" + InputKey.al.name() + "\" key to list the ages");
            return;
        }
        minTreeAge = age;
        Utils.feedback("Minimal tree age set to " + minTreeAge.name);
    }

    private void setMaxActions(String[] input) {
        Integer num = parseIntArg(input, TreeCutterBot.InputKey.c, 1, 100);
        if (num == null)
            return;
        this.maxActions = num;
        Utils.consolePrint(getClass().getSimpleName() + " will do " + maxActions + " chops each time");
    }

    private void selectTool() {
        InventoryMetaItem tool = Utils.selectInventoryTool(VALID_TOOLS);
        if (tool != null) {
            selectedTool = tool;
            toolId = tool.getId();
            Utils.feedback(this.getClass().getSimpleName() + " will use " + tool.getDisplayName() + " with QL:" + tool.getQuality() + " DMG:" + tool.getDamage());
        }
    }

    private void showAgesList() {
        Utils.consolePrint("Age abbreviation");
        for(TreeAge age : TreeAge.values())
            Utils.consolePrint(age.name + " " + age.name());
    }

    private enum InputKey implements Bot.InputKey {
        s("Stamina", "Set the stamina threshold. Player will not do any actions if his stamina is lower than specified threshold",
                "<threshold>"),
        tt("Tree Type", "Set tree types for chopping, e.g. \"birch oak\" or \"birch, oak\". Use \"all\" to chop all trees again (the default)", "<tree types>"),
        c("Clicks", "Set the number of chops queued each time", "<clicks>"),
        a("Age Limit", "Set minimal tree age for chopping by name or abbreviation, e.g. \"oa\" or \"old\" (see the \"al\" key). Chop all trees by default", "<age>"),
        tool("Tool", "Use the item selected in your inventory as the cutting tool (a hatchet is looked up on start otherwise)", ""),
        al("Age List", "Show the tree ages and their abbreviations", ""),
        b("Bush Cutting", "Toggle bush cutting. Enabled by default", ""),
        sp("Sprout Cutting", "Toggle sprouting trees cutting. Enabled by default", "");

        private final KeyInfo keyInfo;

        InputKey(String fullName, String description, String usage) {
            keyInfo = new KeyInfo(fullName, description, usage);
        }

        @Override
        public KeyInfo keyInfo() {
            return keyInfo;
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
            return null;
        }
    }

}
