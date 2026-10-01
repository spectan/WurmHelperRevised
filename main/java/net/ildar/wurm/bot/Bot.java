package net.ildar.wurm.bot;

import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class Bot extends Thread {
    /**
     * A timeout an each bot implementation should use between iterations
     */
    volatile long timeout = 1000;
    /**
     * The bot implementation should register his input handlers with {@link #registerInputHandler(InputKey, InputHandler)}
     */
    private Map<InputKey, InputHandler> inputHandlers = new HashMap<>();
    /**
     * Store all registered message processors here to unregister them on bot deactivation to prevent memory leaks
     */
    private List<Chat.MessageProcessor> registeredMessageProcessors = new ArrayList<>();
    private final List<Runnable> pendingMessageProcessors = new ArrayList<>();
    private volatile boolean paused = false;
    /**
     * Effective stamina (stamina + damage) the player must exceed before work.
     * Bots that use it register the shared handler with {@link #registerStaminaThresholdHandler(InputKey)}
     */
    volatile float staminaThreshold;
    private boolean usesStaminaThreshold = false;
    /**
     * Set when the user (not the bot itself) asked the bot to stop, to tell a requested stop from an unexpected one
     */
    private volatile boolean stopRequested = false;

    public Bot() {
        //register standard input handlers
        registerInputHandler(InputKeyBase.t, this::handleTimeoutChange);
        registerInputHandler(InputKeyBase.off, inputs -> deactivate());
        registerInputHandler(InputKeyBase.info, this::handleInfoCommand);
        registerInputHandler(InputKeyBase.pause, inputs -> togglePause());
        registerInputHandler(InputKeyBase.status, inputs -> printStatus());
    }

    //Bot implementations must do their stuff here
    abstract void work() throws Exception;

    @Override
    public final void run() {
        String failure = null;
        try {
            synchronized (this) {
                pendingMessageProcessors.forEach(Runnable::run);
                pendingMessageProcessors.clear();
            }
            work();
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            failure = e.getMessage() != null ? e.getMessage() : e.toString();
            Utils.consolePrint(this.getClass().getSimpleName() + " has encountered an error - " + e.getMessage());
            Utils.consolePrint(e.toString());
            e.printStackTrace();
        } finally {
            try {
                stopAllActions();
            } catch (Exception ignored) {
                // hud may be unavailable during early crash
            }
            unregisterMessageProcessors();
            BotController.getInstance().onBotInterruption(this);
            if (stopRequested) {
                Utils.feedback(this.getClass().getSimpleName() + " was stopped");
            } else {
                // the bot stopped by itself (an error, a missing tool...) - make sure the player notices
                Utils.alert(this.getClass().getSimpleName() + " stopped" + (failure != null ? ": " + failure : "") + ". See the console for details");
            }
        }
    }

    /**
     * The bot is stopping by interruption(see {@link #deactivate()}.
     * Sometimes the interruption status of a thread is cleared(ignored,lost) in the bot(the bot developer should avoid that),
     * so we check current bot instance for presence in active bot list
     */
    boolean isActive() {
        return BotController.getInstance().isActive(this) && !isInterrupted();
    }

    synchronized void waitOnPause() throws InterruptedException {
        while (paused) {
            this.wait();
        }
    }

    private void togglePause() {
        if (!isAlive()) {
            Utils.consolePrint(getClass().getSimpleName() + " is not running");
            return;
        }
        if (paused) {
            this.setResumed();
        } else {
            this.setPaused();
        }
    }

    public synchronized void setPaused() {
        paused = true;
        stopAllActions();
        Utils.feedback(getClass().getSimpleName() + " is paused.");
    }
    
    public boolean getPaused() {
        return paused;
    }

    public synchronized void setResumed() {
        paused = false;
        this.notifyAll();
        Utils.feedback(getClass().getSimpleName() + " is resumed.");
    }

    /**
     * Cancel every queued player action
     */
    static void stopAllActions() {
        int maxActions = Utils.getMaxActionNumber();
        for (int i = 0; i < maxActions; i++) {
            WurmHelper.hud.sendAction(PlayerAction.STOP, 0);
        }
    }

    public void deactivate() {
        if (Thread.currentThread() != this)
            stopRequested = true;
        if (!super.isAlive()) {
            BotController.getInstance().onBotInterruption(this);
            return;
        }
        Utils.consolePrint("Deactivating " + getClass().getSimpleName());
        interrupt();
    }

    /**
     * Check whether the player has enough effective stamina to perform work.
     * Effective stamina = stamina + damage.
     */
    protected boolean hasStamina(float threshold) {
        return Utils.getPlayerStamina() > threshold;
    }

    /**
     * Check whether the creation progress bar is idle (progress == 0).
     */
    protected boolean isProgressZero() throws Exception {
        Object progressBar = Utils.getField(WurmHelper.hud.getCreationWindow(), "progressBar");
        float progress = Utils.getField(progressBar, "progress");
        return progress == 0f;
    }

    /**
     * Combined stamina + progress check. True when the player has stamina
     * AND no action is currently in progress.
     */
    protected boolean canDoWork(float threshold) throws Exception {
        return hasStamina(threshold) && isProgressZero();
    }

    /**
     * Fail a command that needs the bot thread to be running (for example one that acts right away),
     * telling the user how to start the bot. Returns true when the bot is running
     */
    final boolean requireRunning() {
        if (isAlive())
            return true;
        Utils.consolePrint("Start the bot first: bot " + getAbbreviation() + " on");
        return false;
    }

    /**
     * Bots add a line per setting the user can change, shown by the "status" key
     */
    void describeSettings(List<String> lines) {
    }

    private void printStatus() {
        String state = !isAlive() ? "OFF (settings will apply when it is turned on)" : (paused ? "ON, paused" : "ON");
        Utils.consolePrint("=== " + getClass().getSimpleName() + " - " + state + " ===");
        List<String> lines = new ArrayList<>();
        lines.add("Timeout: " + timeout + " ms");
        if (usesStaminaThreshold)
            lines.add("Stamina threshold: " + staminaThreshold);
        describeSettings(lines);
        for (String line : lines)
            Utils.consolePrint("  " + line);
    }

    static String onOff(boolean value) {
        return value ? "on" : "off";
    }

    /**
     * @return the arguments joined by spaces, or null when there are none
     */
    static String joinArgs(String[] input) {
        if (input == null || input.length == 0)
            return null;
        String joined = String.join(" ", input).trim();
        return joined.isEmpty() ? null : joined;
    }

    /**
     * Split the arguments into a list of names separated by commas, so "small barrel, large crate" gives two names
     * @return the names, empty when there are none
     */
    static List<String> parseNameList(String[] input) {
        List<String> names = new ArrayList<>();
        String joined = joinArgs(input);
        if (joined == null)
            return names;
        for (String name : joined.split(",")) {
            name = name.trim();
            if (!name.isEmpty())
                names.add(name);
        }
        return names;
    }

    /**
     * Parse a single whole-number argument in [min, max], printing what went wrong and the key usage on failure
     * @return the value, or null when the input is missing or invalid
     */
    final Integer parseIntArg(String[] input, InputKey key, int min, int max) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(key);
            return null;
        }
        try {
            int value = Integer.parseInt(input[0]);
            if (value < min || value > max) {
                Utils.consolePrint("`" + input[0] + "` is out of range (" + min + " to " + max + ")");
                printInputKeyUsageString(key);
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            Utils.consolePrint("`" + input[0] + "` is not a whole number");
            printInputKeyUsageString(key);
            return null;
        }
    }

    /**
     * Parse a single number argument in [min, max], printing what went wrong and the key usage on failure
     * @return the value, or null when the input is missing or invalid
     */
    final Float parseFloatArg(String[] input, InputKey key, float min, float max) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(key);
            return null;
        }
        try {
            float value = Float.parseFloat(input[0]);
            if (Float.isNaN(value) || value < min || value > max) {
                Utils.consolePrint("`" + input[0] + "` is out of range (" + min + " to " + max + ")");
                printInputKeyUsageString(key);
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            Utils.consolePrint("`" + input[0] + "` is not a number");
            printInputKeyUsageString(key);
            return null;
        }
    }

    private String getAbbreviation() {
        BotRegistration botRegistration = BotController.getInstance().getBotRegistration(this.getClass());
        if (botRegistration == null) return null;
        return botRegistration.getAbbreviation();
    }

    public String getUsageString() {
        StringBuilder output = new StringBuilder();
        output.append("Usage: bot ").append(getAbbreviation()).append(" <command>");
        List<InputKey> sortedInputKeys = inputHandlers.keySet().stream()
                .sorted(Comparator.comparing(InputKey::getName))
                .collect(Collectors.toList());
        for (InputKey inputKey : sortedInputKeys) {
            output.append("\n  ").append(inputKey.getName());
            String usage = inputKey.getUsage();
            if (usage != null && !usage.isEmpty())
                output.append(" ").append(usage);
            String displayName = inputKey.getFullName();
            if (displayName != null && !displayName.isEmpty())
                output.append(" (").append(displayName).append(")");
            String description = inputKey.getDescription();
            if (description != null && !description.isEmpty())
                output.append(" - ").append(description);
        }
        output.append("\nKeys can also be typed by their full name, e.g. \"stamina\" or \"add item\"");
        return output.toString();
    }

    void printInputKeyUsageString(InputKey inputKey) {
        Utils.consolePrint("Usage: bot " + getAbbreviation() + " " + inputKey.getName() + " " + inputKey.getUsage());
    }

    /**
     * Handle the console input for current bot instance
     *
     * @param data console input
     */
    public void handleInput(String[] data) {
        if (data == null || data.length == 0 || data[0] == null)
            return;
        // a key can be typed by its short name or its full name, which may span several words ("add item")
        for (int words = data.length; words >= 1; words--) {
            InputHandler inputHandler = getInputHandler(String.join(" ", Arrays.copyOfRange(data, 0, words)));
            if (inputHandler == null)
                continue;
            String[] handlerParameters = null;
            if (data.length > words)
                handlerParameters = Arrays.copyOfRange(data, words, data.length);
            inputHandler.handle(handlerParameters);
            return;
        }
        Utils.consolePrint("Unknown key - " + data[0]);
        Utils.consolePrint(getUsageString());
    }

    private void handleInfoCommand(String[] input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(InputKeyBase.info);
            return;
        }
        InputKey inputKey = getInputKey(input[0]);
        if (inputKey == null) {
            Utils.consolePrint("Unknown key");
            Utils.consolePrint(getUsageString());
            return;
        }
        Utils.consolePrint(inputKey.getDescription());
    }

    private void handleTimeoutChange(String[] input) {
        if (input == null || input.length != 1) {
            printInputKeyUsageString(InputKeyBase.t);
            return;
        }
        try {
            int timeout = Integer.parseInt(input[0]);
            setTimeout(timeout);
        } catch (NumberFormatException e) {
            Utils.consolePrint("Wrong timeout value!");
        }
    }

    /**
     * Register the shared "set stamina threshold" handler under the given key
     */
    final void registerStaminaThresholdHandler(InputKey key) {
        usesStaminaThreshold = true;
        registerInputHandler(key, input -> {
            if (input == null || input.length != 1) {
                printInputKeyUsageString(key);
                return;
            }
            float threshold;
            try {
                threshold = Float.parseFloat(input[0]);
            } catch (NumberFormatException e) {
                Utils.consolePrint("`" + input[0] + "` is not a number");
                printInputKeyUsageString(key);
                return;
            }
            // the threshold is compared with stamina + damage, so values up to 2 are meaningful.
            // Larger values are taken as percentages: "90" means 0.9
            if (threshold > 2 && threshold <= 100)
                threshold /= 100;
            if (Float.isNaN(threshold) || threshold < 0 || threshold > 2) {
                Utils.consolePrint("The stamina threshold must be between 0 and 2 (stamina + damage), or a percentage from 3 to 100");
                return;
            }
            setStaminaThreshold(threshold);
        });
    }

    final void setStaminaThreshold(float s) {
        staminaThreshold = s;
        Utils.consolePrint("Current threshold for stamina is " + staminaThreshold);
    }

    final void setTimeout(int timeout) {
        if (timeout < 100) {
            Utils.consolePrint("Too small timeout!");
            timeout = 100;
        }
        this.timeout = timeout;
        Utils.consolePrint("Current timeout is " + timeout + " milliseconds");
    }

    /**
     * The enumeration type of the key must have a usage and description string fields for each item
     */
    final void registerInputHandler(InputKey key, InputHandler inputHandler) {
        InputKey oldKey = getInputKey(key.getName());
        if (oldKey != null)
            inputHandlers.remove(oldKey);
        inputHandlers.put(key, inputHandler);
    }

    private InputKey getInputKey(String key) {
        if (key == null) return null;
        String lower = key.toLowerCase();
        for (InputKey inputKey : inputHandlers.keySet()) {
            if (inputKey.getName().equals(lower))
                return inputKey;
        }
        String normalized = normalizeName(key);
        if (normalized.isEmpty()) return null;
        for (InputKey inputKey : inputHandlers.keySet()) {
            String fullName = inputKey.getFullName();
            if (fullName != null && normalizeName(fullName).equals(normalized))
                return inputKey;
        }
        return null;
    }

    /**
     * Lower-case and strip spaces, dashes and underscores so "Add Item", "add item" and "additem" match
     */
    public static String normalizeName(String name) {
        return name.toLowerCase().replaceAll("[\\s_-]", "");
    }

    private InputHandler getInputHandler(String key) {
        InputKey inputKey = getInputKey(key);
        if (inputKey == null) return null;
        return inputHandlers.get(inputKey);
    }

    final void registerEventProcessor(Function<String, Boolean> filter, Runnable callback) {
        registerMessageProcessor(":Event", filter, callback);
    }

    final synchronized void registerMessageProcessor(String tabName, Function<String, Boolean> filter, Runnable callback) {
        // a bot configured while off may never be started, so hold its processors until it runs
        // (they are unregistered when the bot thread ends)
        if (getState() == State.NEW)
            pendingMessageProcessors.add(() -> registerMessageProcessor(tabName, filter, callback));
        else
            registeredMessageProcessors.add(Chat.registerMessageProcessor(tabName, filter, callback));
    }

    private synchronized void unregisterMessageProcessors() {
        registeredMessageProcessors.forEach(Chat::unregisterMessageProcessor);
    }

    private enum InputKeyBase implements InputKey {
        t("Timeout", "Set the timeout for bot. The bot will wait for specified time(in milliseconds) after each iteration/update",
                "<milliseconds>"),
        off("Off", "Deactivate the bot",
                ""),
        pause("Pause", "Pause/resume the bot",
                ""),
        info("Info", "Get information about configuration key",
                "<key>"),
        status("Status", "Show the bot's current settings",
                "");

        private final KeyInfo keyInfo;

        InputKeyBase(String fullName, String description, String usage) {
            keyInfo = new KeyInfo(fullName, description, usage);
        }

        @Override
        public KeyInfo keyInfo() {
            return keyInfo;
        }
    }

    protected interface InputHandler {
        void handle(String[] inputData);
    }

    /**
     * Implemented by each bot's key enum. The enum supplies its {@link KeyInfo}; {@code name()} comes from Enum
     */
    interface InputKey {
        String name();

        KeyInfo keyInfo();

        default String getName() {
            return name();
        }

        default String getFullName() {
            return keyInfo().fullName;
        }

        default String getDescription() {
            return keyInfo().description;
        }

        default String getUsage() {
            return keyInfo().usage;
        }
    }

    static final class KeyInfo {
        final String fullName;
        final String description;
        final String usage;

        KeyInfo(String fullName, String description, String usage) {
            this.fullName = fullName;
            this.description = description;
            this.usage = usage;
        }
    }

}
