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
    private volatile boolean paused = false;
    /**
     * Effective stamina (stamina + damage) the player must exceed before work.
     * Bots that use it register the shared handler with {@link #registerStaminaThresholdHandler(InputKey)}
     */
    volatile float staminaThreshold;

    public Bot() {
        //register standard input handlers
        registerInputHandler(InputKeyBase.t, this::handleTimeoutChange);
        registerInputHandler(InputKeyBase.off, inputs -> deactivate());
        registerInputHandler(InputKeyBase.info, this::handleInfoCommand);
        registerInputHandler(InputKeyBase.pause, inputs -> togglePause());
    }

    //Bot implementations must do their stuff here
    abstract void work() throws Exception;

    @Override
    public final void run() {
        try {
            work();
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
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
            Utils.consolePrint(this.getClass().getSimpleName() + " was stopped");
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
        if (paused) {
            this.setResumed();
        } else {
            this.setPaused();
        }
    }

    public synchronized void setPaused() {
        paused = true;
        stopAllActions();
        Utils.consolePrint(getClass().getSimpleName() + " is paused.");
    }
    
    public boolean getPaused() {
        return paused;
    }

    public synchronized void setResumed() {
        paused = false;
        this.notifyAll();
        Utils.consolePrint(getClass().getSimpleName() + " is resumed.");
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
            output.append("\n  ");
            String displayName = inputKey.getFullName();
            if (displayName == null || displayName.isEmpty())
                output.append(inputKey.getName());
            else
                output.append(displayName).append(" (").append(inputKey.getName()).append(")");
        }
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
        InputHandler inputHandler = getInputHandler(data[0]);
        if (inputHandler == null) {
            Utils.consolePrint("Unknown key - " + data[0]);
            BotController.getInstance().printBotDescription(this.getClass());
            return;
        }
        String[] handlerParameters = null;
        if (data.length > 1)
            handlerParameters = Arrays.copyOfRange(data, 1, data.length);
        inputHandler.handle(handlerParameters);
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
        registerInputHandler(key, input -> {
            if (input == null || input.length != 1) {
                printInputKeyUsageString(key);
                return;
            }
            try {
                setStaminaThreshold(Float.parseFloat(input[0]));
            } catch (NumberFormatException e) {
                Utils.consolePrint("Wrong threshold value!");
            }
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
            String fullName = inputKey.getFullName();
            if (fullName != null && !fullName.isEmpty() && fullName.equalsIgnoreCase(lower))
                return inputKey;
        }
        return null;
    }

    private InputHandler getInputHandler(String key) {
        InputKey inputKey = getInputKey(key);
        if (inputKey == null) return null;
        return inputHandlers.get(inputKey);
    }

    final void registerEventProcessor(Function<String, Boolean> filter, Runnable callback) {
        registerMessageProcessor(":Event", filter, callback);
    }

    final void registerMessageProcessor(String tabName, Function<String, Boolean> filter, Runnable callback) {
        registeredMessageProcessors.add(Chat.registerMessageProcessor(tabName, filter, callback));
    }

    private void unregisterMessageProcessors() {
        registeredMessageProcessors.forEach(Chat::unregisterMessageProcessor);
    }

    private enum InputKeyBase implements InputKey {
        t("Timeout", "Set the timeout for bot. The bot will wait for specified time(in milliseconds) after each iteration/update",
                "timeout(in milliseconds)"),
        off("Off", "Deactivate the bot",
                ""),
        pause("Pause", "Pause/resume the bot",
                ""),
        info("Info", "Get information about configuration key",
                "key");

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
