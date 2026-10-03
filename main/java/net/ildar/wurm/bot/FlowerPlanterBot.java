package net.ildar.wurm.bot;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.client.renderer.gui.CreationWindow;
import com.wurmonline.mesh.GrassData;
import com.wurmonline.mesh.Tiles;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.Utils;
import net.ildar.wurm.annotations.BotInfo;

import java.util.List;

@BotInfo(name = "Flower Planter", description =
        "Skills up player's gardening skill by planting and picking flowers in surrounding area",
        abbreviation = "fp")
public class FlowerPlanterBot extends Bot {
    private boolean noBouquets;

    public FlowerPlanterBot() {
        registerStaminaThresholdHandler(FlowerPlanterBot.InputKey.s);
        timeout = 300;
        staminaThreshold = 0.96f;
    }

    @Override
    void describeSettings(List<String> lines) {
        // only the timeout and the stamina threshold, which the base class prints
    }

    @Override
    public void work() throws Exception{
        CreationWindow creationWindow = WurmHelper.hud.getCreationWindow();
        int maxActions = Utils.getMaxActionNumber();
        InventoryMetaItem sickle = Utils.locateToolItem("sickle");
        InventoryMetaItem shovel = Utils.locateToolItem("shovel");
        if (sickle == null) {
            Utils.consolePrint("You don't have a sickle! " + this.getClass().getSimpleName() + " won't start");
            deactivate();
            return;
        }
        if (shovel == null) {
            Utils.consolePrint("You don't have a shovel! " + this.getClass().getSimpleName() + " won't start");
            deactivate();
            return;
        }
        long sickleId = sickle.getId();
        long shovelId = shovel.getId();

        BotState state = BotState.PLANT;
        while (isActive()) {
            waitOnPause();
            int checkedtiles[][] = Utils.getAreaCoordinates();
            int sentactions = 0;

            if (canDoWork(staminaThreshold) && creationWindow.getActionInUse() == 0) {
                switch (state) {
                    case PLANT:
                        long[] flowerIds = new long[maxActions];
                        int flowersFound = 0;
                        List<InventoryMetaItem> bouquets = Utils.getInventoryItems("bouquet of");
                        if (bouquets.isEmpty() && !noBouquets)
                            Utils.consolePrint("The player doesn't have any bouquets to plant");
                        noBouquets = bouquets.isEmpty();
                        for (InventoryMetaItem item : bouquets) {
                            flowerIds[flowersFound++] = item.getId();
                            if (flowersFound >= maxActions)
                                break;
                        }
                        for(int i = 0; i < 9 && sentactions < flowersFound; i++) {
                            Tiles.Tile type = WurmHelper.hud.getWorld().getNearTerrainBuffer().getTileType(checkedtiles[i][0], checkedtiles[i][1]);
                            if (type.tilename.equals("Dirt")) {
                                WurmHelper.hud.getWorld().getServerConnection().sendAction(flowerIds[sentactions],
                                        new long[]{Tiles.getTileId(checkedtiles[i][0], checkedtiles[i][1], 0)},
                                        new PlayerAction("",(short)186, PlayerAction.ANYTHING));
                                ++sentactions;
                            }
                        }
                        state = BotState.PICK;
                        break;
                    case PICK:
                        for(int i = 0; i < 9 && sentactions < maxActions; i++) {
                            Tiles.Tile type = WurmHelper.hud.getWorld().getNearTerrainBuffer().getTileType(checkedtiles[i][0], checkedtiles[i][1]);
                            byte data = WurmHelper.hud.getWorld().getNearTerrainBuffer().getData(checkedtiles[i][0], checkedtiles[i][1]);
                            if (type.isGrass() && GrassData.getFlowerTypeName(data).contains("flowers")) {
                                WurmHelper.hud.getWorld().getServerConnection().sendAction(sickleId,
                                        new long[]{Tiles.getTileId(checkedtiles[i][0], checkedtiles[i][1], 0)},
                                        new PlayerAction("",(short)187, PlayerAction.ANYTHING));
                                ++sentactions;
                            }
                        }
                        state = BotState.CULTIVATE;
                        break;
                    case CULTIVATE:
                        if (noBouquets) {
                            state = BotState.PLANT;
                            break;
                        }
                        for(int i = 0; i < 9 && sentactions < maxActions; i++) {
                            Tiles.Tile type = WurmHelper.hud.getWorld().getNearTerrainBuffer().getTileType(checkedtiles[i][0], checkedtiles[i][1]);
                            byte data = WurmHelper.hud.getWorld().getNearTerrainBuffer().getData(checkedtiles[i][0], checkedtiles[i][1]);
                            if (type.isGrass()&& !GrassData.getFlowerTypeName(data).contains("flowers")) {
                                WurmHelper.hud.getWorld().getServerConnection().sendAction(shovelId,
                                        new long[]{Tiles.getTileId(checkedtiles[i][0], checkedtiles[i][1], 0)},
                                        PlayerAction.CULTIVATE);
                                ++sentactions;
                            }
                        }
                        state = BotState.PLANT;
                        break;
                }
            }

            sleep(timeout);
        }
    }

    enum BotState{
        PLANT,
        PICK,
        CULTIVATE
    }

    private enum InputKey implements Bot.InputKey {
        s("Stamina", "Set the stamina threshold (0 to 1, or a percentage). Player will not do any actions if his stamina is lower than specified threshold",
                "<threshold>");

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
