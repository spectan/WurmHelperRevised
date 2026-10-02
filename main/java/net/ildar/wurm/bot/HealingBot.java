package net.ildar.wurm.bot;

import com.wurmonline.client.game.inventory.InventoryMetaItem;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.Utils;
import net.ildar.wurm.annotations.BotInfo;

import java.util.*;

@BotInfo(name = "Healing", description =
        "Heals the player's wounds with cotton found in inventory",
        abbreviation = "h")
public class HealingBot extends Bot {
    private final Set<String> WOUND_NAMES = new HashSet<>(Arrays.asList("Cut", "Bite", "Bruise", "Burn", "Hole", "Acid", "Infection"));
    private volatile float minDamage = 0;

    public HealingBot() {
        registerInputHandler(HealingBot.InputKey.md, this::setMinimumDamage);
        timeout = 500;
    }

    @Override
    void describeSettings(List<String> lines) {
        lines.add(String.format("Minimum wound damage: %.2f", minDamage));
    }

    @Override
    protected void work() throws Exception{
        while (isActive()) {
            waitOnPause();
            if (!isProgressZero()) {
                sleep(timeout);
                continue;
            }
            float damage = WurmHelper.hud.getWorld().getPlayer().getDamage();
            if (damage == 0) {
                Utils.consolePrint("The player is fully healed");
                return;
            }
            InventoryMetaItem cottonItem = Utils.getInventoryItems("cotton").stream().filter(item -> Utils.normalizeBaseName(item).equals("cotton")).findFirst().orElse(null);
            if (cottonItem == null) {
                Utils.consolePrint("The player doesn't have any cotton!");
                return;
            }
            InventoryMetaItem inventoryRoot = Utils.getRootItem(WurmHelper.hud.getInventoryWindow().getInventoryListComponent());
            if (inventoryRoot == null) {
                sleep(timeout);
                continue;
            }
            Deque<InventoryMetaItem> inventoryItems = new ArrayDeque<>();
            inventoryItems.add(inventoryRoot);
            List<InventoryMetaItem> wounds = new ArrayList<>();
            while (!inventoryItems.isEmpty()) {
                InventoryMetaItem item = inventoryItems.poll();
                if (WOUND_NAMES.contains(item.getBaseName())
                        && !item.getDisplayName().contains("bandaged")
                        && item.getDamage() > minDamage)
                    wounds.add(item);
                if (item.getChildren() != null)
                    inventoryItems.addAll(item.getChildren());
            }
            if (wounds.size() == 0) {
                Utils.consolePrint("All wounds were treated");
                return;
            }
            wounds.sort(Comparator.comparingDouble(InventoryMetaItem::getDamage).reversed());
            int maxActionNumber = Utils.getMaxActionNumber();
            int i = 0;
            for (InventoryMetaItem wound : wounds) {
                WurmHelper.hud.getWorld().getServerConnection().sendAction(cottonItem.getId(), new long[]{wound.getId()}, PlayerAction.FIRSTAID);
                if (++i >= maxActionNumber)
                    break;
            }
            sleep(timeout);
        }
    }

    private void setMinimumDamage(String[] input) {
        Float value = parseFloatArg(input, HealingBot.InputKey.md, 0, 100);
        if (value == null)
            return;
        minDamage = value;
        Utils.consolePrint(String.format("The wound must have damage greater than %.2f in order to be treated", minDamage));
    }


    private enum InputKey implements Bot.InputKey {
        md("Min Damage", "Only treat wounds with damage greater than this value (0 to 100)", "<damage>");

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
