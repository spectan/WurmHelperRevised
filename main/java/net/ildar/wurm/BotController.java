package net.ildar.wurm;

import net.ildar.wurm.bot.Bot;
import net.ildar.wurm.bot.RMIBot;

import java.util.*;

public class BotController {
    private static BotController instance;
    private List<BotRegistration> botList;
    private List<Bot> activeBots = new ArrayList<>();
    private boolean gPaused = false;

    public static synchronized BotController getInstance() {
        if (instance == null)
            instance = new BotController();
        return instance;
    }

    private BotController() {
        botList = BotRegistrationProvider.getBotList();
    }

    public void handleInput(String data[]) {
        if (data.length < 1) {
            Utils.consolePrint(getBotUsageString());
            Utils.writeToConsoleInputLine(WurmHelper.BOT_COMMAND + " ");
            return;
        }
        if (data[0].equals("off")) {
            deactivateAllBots();
            return;
        }
        if (data[0].equals("pause")) {
            pauseAllBots();
            Utils.writeToConsoleInputLine(WurmHelper.BOT_COMMAND + " pause");
            return;
        }
        Class<? extends Bot> botClass = getBotClass(data[0]);
        if (botClass == null) {
            Utils.consolePrint("Didn't find a bot with name or abbreviation \"" + data[0] + "\"");
            Utils.consolePrint(getBotUsageString());
            return;
        }

        if (data.length == 1) {
            printBotDescription(botClass);
            Utils.writeToConsoleInputLine(WurmHelper.BOT_COMMAND + " " + data[0] + " ");
            return;
        }
        Bot botInstance = getActiveInstance(botClass);
        if (botInstance != null) {
            if (botInstance.isInterrupted()) {
                Utils.consolePrint(botClass.getSimpleName() + " is trying to stop");
            } else if (data[1].equals("on")) {
                Utils.consolePrint(botClass.getSimpleName() + " is already on");
            } else {
                try {
                    botInstance.handleInput(Arrays.copyOfRange(data, 1, data.length));
                } catch (Exception e) {
                    Utils.consolePrint("Unable to configure  " + botClass.getSimpleName());
                    e.printStackTrace();
                }
            }
        } else {
            if (data[1].equals("on")) {
                Bot newBot = getInstance(botClass);
                if (newBot != null) {
                    newBot.start();
                    Utils.consolePrint(botClass.getSimpleName() + " is on!");
                    printBotDescription(botClass);
                } else {
                    Utils.consolePrint("Internal error on bot activation");
                }
            } else {
                Utils.consolePrint(botClass.getSimpleName() + " is not running!");
            }
        }
        Utils.writeToConsoleInputLine(WurmHelper.BOT_COMMAND + " " + data[0] + " ");
    }

    public synchronized boolean isActive(Bot bot) {
        return activeBots.contains(bot);
    }

    private synchronized void deactivateAllBots() {
        List<Bot> bots = new ArrayList<>(activeBots); // deactivation mutates activeBots
        List<String> stopped = new ArrayList<>();
        bots.forEach(bot -> {
            if ((bot instanceof RMIBot))
                Utils.consolePrint(
                    "Note: not shutting down %s, use \"%s off\" directly to stop it",
                    RMIBot.class.getSimpleName(),
                    getAbbreviation(RMIBot.class)
                );
            else {
                bot.deactivate();
                stopped.add(bot.getClass().getSimpleName());
            }
        });
        if (stopped.isEmpty()) {
            Utils.consolePrint("No bots were running.");
        } else {
            Utils.consolePrint("Stopped: " + String.join(", ", stopped));
        }
        gPaused = false;
    }

    public synchronized void onBotInterruption(Bot bot) {
        activeBots.remove(bot);
        if (activeBots.isEmpty())
            gPaused = false;
    }

    private synchronized void pauseAllBots() {
        if (activeBots.size() > 0) {
            gPaused = !gPaused;
            if (gPaused) {
                activeBots.forEach(Bot::setPaused);
            } else {
                activeBots.forEach(Bot::setResumed);
            }
            Utils.consolePrint("All bots have been " + (gPaused ? "paused!" : "resumed!"));
        } else {
            Utils.consolePrint("No bots are running!");
        }
    }

    @SuppressWarnings("WeakerAccess")
    public synchronized <T extends Bot> T getInstance(Class<T> botClass) {
        T instance = null;
        try {
            Optional<Bot> optionalBot = activeBots.stream().filter(bot -> bot.getClass().equals(botClass)).findAny();
            if (!optionalBot.isPresent()) {
                instance = botClass.getDeclaredConstructor().newInstance();
                activeBots.add(instance);
            } else
                //noinspection unchecked
                instance = (T) optionalBot.get();
        } catch (ReflectiveOperationException e) {
            Utils.consolePrint("Failed to instantiate bot %s: %s", botClass.getName(), e.getMessage());
            e.printStackTrace();
        }
        return instance;
    }

    public void printBotDescription(Class<? extends Bot> botClass) {
        BotRegistration botRegistration = getBotRegistration(botClass);
        String description = "no description";
        String name = botClass.getSimpleName();
        if (botRegistration != null) {
            description = botRegistration.getDescription();
            name = botRegistration.getName();
        }
        Utils.consolePrint("=== " + name + " ===");
        Utils.consolePrint(description);
        Bot botInstance = getActiveInstance(botClass);
        if (botInstance != null) {
            String status = botInstance.getPaused() ? " (paused)" : "";
            Utils.consolePrint("Status: ON%s", status);
            Utils.consolePrint(botInstance.getUsageString());
        } else {
            Utils.consolePrint("Status: OFF");
            Utils.consolePrint("Type \"bot " + name + " on\" or \"bot " + getAbbreviation(botClass) + " on\" to activate the bot");
        }
    }

    public String getBotUsageString() {
        return "Usage: " + WurmHelper.BOT_COMMAND + " " + getBotUsageArguments();
    }

    //the usage of the bot command without the "Usage: bot " prefix
    public String getBotUsageArguments() {
        StringBuilder result = new StringBuilder("<bot>");
        for (BotRegistration botRegistration : botList)
            result.append("\n  ").append(botRegistration.getName()).append(" (").append(botRegistration.getAbbreviation()).append(")");
        result.append("\n  pause\n  off");
        return result.toString();
    }

    Class<? extends Bot> getBotClass(String nameOrAbbreviation) {
        for (BotRegistration botRegistration : botList) {
            if (botRegistration.getAbbreviation().equalsIgnoreCase(nameOrAbbreviation))
                return botRegistration.getBotClass();
            if (botRegistration.getName().equalsIgnoreCase(nameOrAbbreviation))
                return botRegistration.getBotClass();
        }
        return null;
    }

    //this method is being invoked from com.wurmonline.client.renderer.cell.GroundItemCellRenderable
    @SuppressWarnings({"WeakerAccess", "unchecked"})
    public synchronized <T extends Bot> T getActiveInstance(Class<T> botClass) {
        Optional<Bot> optionalBot = activeBots.stream()
                .filter(bot -> bot.getClass().equals(botClass))
                .findAny();
        return optionalBot.map(bot -> (T) bot).orElse(null);
    }

    public void printBotList() {
        if (botList.isEmpty()) {
            Utils.consolePrint("No bots registered.");
            return;
        }
        Utils.consolePrint("Available bots:");
        for (BotRegistration reg : botList) {
            Class<? extends Bot> botClass = reg.getBotClass();
            Bot bot = getActiveInstance(botClass);
            String status = "OFF";
            if (bot != null)
                status = bot.getPaused() ? "ON, paused" : "ON";
            Utils.consolePrint("  %s (%s) [%s]", reg.getName(), reg.getAbbreviation(), status);
        }
    }

    private String getAbbreviation(Class<? extends Bot> botClass) {
        BotRegistration botRegistration = getBotRegistration(botClass);
        return botRegistration != null ? botRegistration.getAbbreviation() : "*";
    }

    public BotRegistration getBotRegistration(Class<? extends Bot> botClass) {
        for (BotRegistration botRegistration : botList) {
            if (botRegistration.getBotClass().equals(botClass))
                return botRegistration;
        }
        return null;
    }
}
