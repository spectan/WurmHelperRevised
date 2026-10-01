package net.ildar.wurm;

import net.ildar.wurm.bot.Bot;
import net.ildar.wurm.bot.RMIBot;

import java.util.*;

public class BotController {
    private static BotController instance;
    private List<BotRegistration> botList;
    private List<Bot> activeBots = new ArrayList<>();
    // bots configured while off; started (and moved to activeBots) by "bot X on"
    private final Map<Class<? extends Bot>, Bot> stagedBots = new HashMap<>();
    // bots paused by the global "bot pause", so the global resume leaves individually paused bots alone
    private final Set<Bot> globallyPaused = new HashSet<>();
    private boolean gPaused = false;

    public static synchronized BotController getInstance() {
        if (instance == null)
            instance = new BotController();
        return instance;
    }

    private BotController() {
        botList = BotRegistrationProvider.getBotList();
        botList.sort(Comparator.comparing(BotRegistration::getName, String.CASE_INSENSITIVE_ORDER));
    }

    public void handleInput(String data[]) {
        if (data.length < 1) {
            Utils.consolePrint(getBotUsageString());
            Utils.writeToConsoleInputLine(WurmHelper.BOT_COMMAND + " ");
            return;
        }
        String command = data[0].toLowerCase();
        if (command.equals("off")) {
            deactivateAllBots();
            return;
        }
        if (command.equals("pause")) {
            pauseAllBots();
            Utils.writeToConsoleInputLine(WurmHelper.BOT_COMMAND + " pause");
            return;
        }
        if (command.equals("list") || command.equals("status")) {
            printBotList();
            return;
        }
        // the bot can be named by its abbreviation or its full name, which may span several words ("tree cutter")
        Class<? extends Bot> botClass = null;
        int nameWords = 0;
        for (int words = data.length; words >= 1 && botClass == null; words--) {
            botClass = getBotClass(String.join(" ", Arrays.copyOfRange(data, 0, words)));
            nameWords = words;
        }
        if (botClass == null) {
            Utils.consolePrint("Didn't find a bot with name or abbreviation \"" + data[0] + "\"");
            Utils.consolePrint(getBotUsageString());
            return;
        }
        String abbreviation = getAbbreviation(botClass);
        String[] args = Arrays.copyOfRange(data, nameWords, data.length);

        if (args.length == 0) {
            printBotDescription(botClass);
            Utils.writeToConsoleInputLine(WurmHelper.BOT_COMMAND + " " + abbreviation + " ");
            return;
        }
        Bot botInstance = getActiveInstance(botClass);
        if (botInstance != null) {
            if (botInstance.isInterrupted()) {
                Utils.consolePrint(botClass.getSimpleName() + " is trying to stop");
            } else if (args[0].equalsIgnoreCase("on")) {
                Utils.consolePrint(botClass.getSimpleName() + " is already on");
            } else {
                configure(botInstance, args);
            }
        } else if (args[0].equalsIgnoreCase("on")) {
            // "bot X on <key> <args>" starts the bot, then applies the key, so keys that need
            // a running bot (e.g. starting a walk) work too
            Bot newBot = startBot(botClass);
            if (newBot != null && args.length > 1)
                configure(newBot, Arrays.copyOfRange(args, 1, args.length));
        } else if (args[0].equalsIgnoreCase("off")) {
            if (stagedBots.remove(botClass) != null)
                Utils.consolePrint(botClass.getSimpleName() + " is not running, its pending settings were discarded");
            else
                Utils.consolePrint(botClass.getSimpleName() + " is not running!");
        } else {
            // configure a bot that isn't running yet; the settings apply when it is turned on
            boolean wasStaged = stagedBots.containsKey(botClass);
            Bot stagedBot = getStagedInstance(botClass);
            if (stagedBot != null) {
                if (!wasStaged)
                    Utils.consolePrint(botClass.getSimpleName() + " is not running. Settings you make now are kept for when you turn it on with \"bot " + abbreviation + " on\"");
                configure(stagedBot, args);
            }
        }
        Utils.writeToConsoleInputLine(WurmHelper.BOT_COMMAND + " " + abbreviation + " ");
    }

    private void configure(Bot bot, String[] args) {
        try {
            bot.handleInput(args);
        } catch (Exception e) {
            Utils.consolePrint("Unable to configure " + bot.getClass().getSimpleName() + ": " + e);
            e.printStackTrace();
        }
    }

    private synchronized Bot startBot(Class<? extends Bot> botClass) {
        Bot newBot = getInstance(botClass);
        if (newBot == null) {
            Utils.consolePrint("Internal error on bot activation");
            return null;
        }
        newBot.start();
        Utils.feedback(botClass.getSimpleName() + " is on!");
        printBotDescription(botClass);
        return newBot;
    }

    /**
     * @return the not yet started instance that holds the settings made while the bot was off, creating it if needed
     */
    private synchronized Bot getStagedInstance(Class<? extends Bot> botClass) {
        Bot bot = stagedBots.get(botClass);
        if (bot == null) {
            bot = newBot(botClass);
            if (bot != null)
                stagedBots.put(botClass, bot);
        }
        return bot;
    }

    private Bot newBot(Class<? extends Bot> botClass) {
        try {
            return botClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            Utils.consolePrint("Failed to instantiate bot %s: %s", botClass.getName(), e.getMessage());
            e.printStackTrace();
            return null;
        }
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
            Utils.feedback("Stopped: " + String.join(", ", stopped));
        }
        gPaused = false;
        globallyPaused.clear();
    }

    public synchronized void onBotInterruption(Bot bot) {
        activeBots.remove(bot);
        globallyPaused.remove(bot);
        if (activeBots.isEmpty())
            gPaused = false;
    }

    private synchronized void pauseAllBots() {
        if (activeBots.size() > 0) {
            gPaused = !gPaused;
            if (gPaused) {
                for (Bot bot : activeBots) {
                    if (!bot.getPaused()) {
                        bot.setPaused();
                        globallyPaused.add(bot);
                    }
                }
            } else {
                globallyPaused.forEach(Bot::setResumed);
                globallyPaused.clear();
            }
            Utils.feedback("All bots have been " + (gPaused ? "paused!" : "resumed!"));
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
                Bot staged = stagedBots.remove(botClass);
                //noinspection unchecked
                instance = staged != null ? (T) staged : botClass.getDeclaredConstructor().newInstance();
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
        } else {
            // show the keys of the configured instance if there is one; just looking doesn't configure the bot
            botInstance = stagedBots.containsKey(botClass) ? stagedBots.get(botClass) : newBot(botClass);
            Utils.consolePrint("Status: OFF. Type \"bot " + getAbbreviation(botClass) + " on\" to activate the bot. Keys can be set before turning it on");
        }
        if (botInstance != null)
            Utils.consolePrint(botInstance.getUsageString());
    }

    public String getBotUsageString() {
        return "Usage: " + WurmHelper.BOT_COMMAND + " " + getBotUsageArguments();
    }

    //the usage of the bot command without the "Usage: bot " prefix
    public String getBotUsageArguments() {
        StringBuilder result = new StringBuilder("<bot>");
        for (BotRegistration botRegistration : botList)
            result.append("\n  ").append(botRegistration.getName()).append(" (").append(botRegistration.getAbbreviation()).append(")");
        result.append("\n  list - show which bots are running\n  pause - pause/resume all bots\n  off - stop all bots");
        return result.toString();
    }

    Class<? extends Bot> getBotClass(String nameOrAbbreviation) {
        String normalized = Bot.normalizeName(nameOrAbbreviation);
        for (BotRegistration botRegistration : botList) {
            if (botRegistration.getAbbreviation().equalsIgnoreCase(nameOrAbbreviation))
                return botRegistration.getBotClass();
            if (Bot.normalizeName(botRegistration.getName()).equals(normalized))
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
            String status = stagedBots.containsKey(botClass) ? "OFF, configured" : "OFF";
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
