package net.ildar.wurm.bot;

import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.Utils;
import net.ildar.wurm.annotations.BotInfo;

import java.util.List;

@BotInfo(name = "Meditation", description =
        "Meditates on the carpet. Assumes that there are no restrictions on meditation skill.",
        abbreviation = "md")
public class MeditationBot extends Bot {
    private long lastRepair;
    private volatile long repairTimeout;
    private volatile int clicks = 3;
    private volatile boolean repairInitiated;
    private volatile int clicked;
    private static final int MAX_MEDITATION_ATTEMPTS = 60;

    public MeditationBot() {
        registerStaminaThresholdHandler(MeditationBot.InputKey.s);
        registerInputHandler(MeditationBot.InputKey.c, this::setClicksNumber);
        registerInputHandler(MeditationBot.InputKey.rt, this::setRepairTimeout);
        repairTimeout = 60000;
        staminaThreshold = 0.5f;
    }

    @Override
    void describeSettings(List<String> lines) {
        lines.add("Clicks: " + clicks);
        lines.add("Rug repair timeout: " + repairTimeout + " ms");
    }

    @Override
    protected void work() throws Exception{
        PickableUnit pickableUnit = Utils.getField(WurmHelper.hud.getSelectBar(), "selectedUnit");
        if (pickableUnit == null || !pickableUnit.getHoverName().contains("meditation rug")) {
            Utils.consolePrint("Select a meditation rug (click it so it shows in the select bar), then start the bot again");
            deactivate();
            return;
        } else
            Utils.consolePrint(this.getClass().getSimpleName() + " will use " + pickableUnit.getHoverName() );
        long carpetId = pickableUnit.getId();
        registerEventProcessors();
        PlayerAction meditationAction = new PlayerAction("",(short) 384, PlayerAction.ANYTHING);
        while (isActive()) {
            waitOnPause();
            if (Math.abs(lastRepair - System.currentTimeMillis()) > repairTimeout) {
                repairInitiated = false;
                int count = 0;
                while(!repairInitiated && count++ < 30) {
                    WurmHelper.hud.sendAction(PlayerAction.REPAIR, carpetId);
                    sleep(1000);
                }
                if (repairInitiated) {
                    lastRepair = System.currentTimeMillis();
                } else
                    Utils.consolePrint("Couldn't repair a meditation rug!");
            }
            clicked = 0;
            int attempts = 0;
            while(isActive() && clicked < clicks) {
                if (hasStamina(staminaThreshold)) {
                    if (attempts++ >= MAX_MEDITATION_ATTEMPTS) {
                        Utils.consolePrint("Couldn't start meditating after " + MAX_MEDITATION_ATTEMPTS + " attempts!");
                        break;
                    }
                    WurmHelper.hud.sendAction(meditationAction, carpetId);
                }
                sleep(1000);
            }
            sleep(timeout);
        }
    }

    private void registerEventProcessors() {
        registerEventProcessor(message -> message.contains("You repair")
                || message.contains("You start repairing")
                || message.contains("doesn't need repairing")
                || message.contains("you will start repairing"), () -> repairInitiated = true);
        registerEventProcessor(message -> message.contains("You start meditating.")
                || message.contains("you will start meditating again."), () -> clicked++);
    }

    private void setRepairTimeout(String []input){
        Integer value = parseIntArg(input, MeditationBot.InputKey.rt, 100, Integer.MAX_VALUE);
        if (value == null)
            return;
        repairTimeout = value;
        Utils.consolePrint("Current carpet repair timeout is " + repairTimeout + " milliseconds");
    }

    private void setClicksNumber(String[] input) {
        Integer value = parseIntArg(input, MeditationBot.InputKey.c, 1, 100);
        if (value == null)
            return;
        clicks = value;
        Utils.consolePrint(getClass().getSimpleName() + " will do " + clicks + " actions each time");
    }

    private enum InputKey implements Bot.InputKey {
        s("Stamina", "Set the stamina threshold (0 to 1, or a percentage). Player will not do any actions if his stamina is lower than specified threshold",
                "<threshold>"),
        c("Clicks", "Set the amount of actions the bot will do each time", "<clicks>"),
        rt("Repair Timeout", "Set how often (in milliseconds) the meditation rug is repaired", "<milliseconds>");

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
