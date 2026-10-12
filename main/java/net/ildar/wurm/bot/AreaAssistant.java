package net.ildar.wurm.bot;

import net.ildar.wurm.WurmHelper;
import net.ildar.wurm.Utils;

import java.util.List;

class AreaAssistant {
    private final static int STEPS_IN_MOVE = 5;//each moving is divided to this number of steps for each tile

    private volatile int moveAheadDistance = 3;//in tiles
    private volatile int moveRightDistance = 3;//in tiles
    private volatile long stepTimeout = 1000;

    private Bot bot;
    private volatile MoveStrategy moveStrategy = Utils::movePlayerBySteps;
    private volatile int height = 0, width = 0;

    //start point - bottom left corner of area
    private volatile int movedAhead = 0, movedToRight = 0;
    private volatile int startX, startY;
    private volatile int startDirection;

    private volatile boolean turnedRight = false;

    AreaAssistant(Bot bot) {
        this.bot = bot;
        bot.registerInputHandler(InputKey.area, this::toggleAreaTour);
        bot.registerInputHandler(InputKey.area_speed, this::setAreaModeSpeed);
    }
    void areaNextPosition() throws InterruptedException{
        if (!areaTourActivated()) return;
        recalculateBiases();
        if (movedAhead < 0 || movedAhead > height - 1 || movedToRight < 0 || movedToRight > width - 1) {
            Utils.consolePrint("Player leaved the area");
            stopAreaTour();
            return;
        }
        turnPlayer();
        if (movedAhead < height - 1) {
            for (int tiles = 0; tiles < moveAheadDistance; tiles++) {
                if (movedAhead >= height - 1) break;
                moveStrategy.moveForward(4, STEPS_IN_MOVE, stepTimeout);
                movedAhead++;
            }
        } else if (movedToRight < width - 1) {
            if (turnedRight)
                Utils.turnPlayer(-90);
            else
                Utils.turnPlayer(90);
            Thread.sleep(300);
            for (int tiles = 0; tiles < moveRightDistance; tiles++) {
                if (movedToRight >= width - 1) break;
                moveStrategy.moveForward(4, STEPS_IN_MOVE, stepTimeout);
                movedToRight++;
            }
            if (turnedRight)
                Utils.turnPlayer(-90);
            else
                Utils.turnPlayer(90);
            turnedRight = !turnedRight;
        } else
            stopAreaTour();
        try {
            Utils.stabilizePlayer();
        } catch (Exception e) {
            Utils.consolePrint("AreaAssistant: stabilizePlayer failed - " + e.getMessage());
        }
    }

    private void turnPlayer(){
        if (turnedRight) {
            Utils.turnPlayer(((startDirection+2)%4) * 90, 0);
        } else {
            Utils.turnPlayer(startDirection * 90, 0);
        }
    }

    private void recalculateBiases() {
        int x = WurmHelper.hud.getWorld().getPlayerCurrentTileX();
        int y = WurmHelper.hud.getWorld().getPlayerCurrentTileY();
        switch (startDirection) {
            case 1://east, x is increasing
                movedAhead = x - startX;
                movedToRight = y - startY;
                break;
            case 2://south, y is increasing
                movedAhead = y - startY;
                movedToRight = startX - x;
                break;
            case 3://west, x is decreasing
                movedAhead = startX - x;
                movedToRight = startY - y;
                break;
            default://north, y is decreasing
                movedAhead = startY - y;
                movedToRight = x - startX;
                break;
        }
        if (turnedRight)
            movedAhead = height - movedAhead - 1;
    }

    private void stopAreaTour() {
        Utils.showOnScreenMessage("Area tour is ended");
        resetAreaTour();
    }

    private void resetAreaTour() {
        height = 0;
        width = 0;
        movedAhead = 0;
        movedToRight = 0;
        turnedRight = false;
    }

    boolean areaTourActivated() {
        return height != 0 && width != 0;
    }

    private void startAreaTour(int tilesForward, int tilesToRight) {
        height = tilesForward;
        width = tilesToRight;
        movedAhead = movedToRight = 0;
        startX = WurmHelper.hud.getWorld().getPlayerCurrentTileX();
        startY = WurmHelper.hud.getWorld().getPlayerCurrentTileY();
        turnedRight = false;
        Utils.stabilizePlayer();
        startDirection = Math.round(WurmHelper.hud.getWorld().getPlayerRotX() / 90) % 4;
    }

    void setMoveAheadDistance(int moveAheadDistance) {
        this.moveAheadDistance = moveAheadDistance;
    }

    void setMoveRightDistance(int moveRightDistance) {
        this.moveRightDistance = moveRightDistance;
    }

    void setMoveStrategy(MoveStrategy moveStrategy) {
        this.moveStrategy = moveStrategy;
    }

    /**
     * Without arguments: stop the running tour (or print usage when there is none).
     * With a size: start a tour of that size. While a tour runs, the same size stops it (so one keybind
     * toggles the tour) and a different size resizes it
     */
    void toggleAreaTour(String[] input) {
        if (!bot.requireRunning()) return;
        String botName = bot.getClass().getSimpleName();
        if (input == null || input.length == 0) {
            if (areaTourActivated()) {
                resetAreaTour();
                Utils.feedback("Area mode is off for " + botName);
            } else
                bot.printInputKeyUsageString(InputKey.area);
            return;
        }
        if (input.length != 2) {
            bot.printInputKeyUsageString(InputKey.area);
            return;
        }
        int tilesForward, tilesToRight;
        try {
            tilesForward = Integer.parseInt(input[0]);
            tilesToRight = Integer.parseInt(input[1]);
        } catch (NumberFormatException e) {
            Utils.consolePrint("Wrong area size! Both values must be whole numbers");
            bot.printInputKeyUsageString(InputKey.area);
            return;
        }
        if (tilesForward < 1 || tilesToRight < 1) {
            Utils.consolePrint("Wrong area size! Both values must be at least 1");
            bot.printInputKeyUsageString(InputKey.area);
            return;
        }
        if (areaTourActivated() && tilesForward == height && tilesToRight == width) {
            resetAreaTour();
            Utils.feedback("Area mode is off for " + botName);
        } else if (areaTourActivated()) {
            // keep the start point and the progress, only the bounds change
            height = tilesForward;
            width = tilesToRight;
            Utils.feedback(String.format("Area mode for %s resized to %d tiles ahead x %d tiles to the right",
                    botName, tilesForward, tilesToRight));
        } else {
            startAreaTour(tilesForward, tilesToRight);
            Utils.feedback(String.format("Area mode is on for %s: %d tiles ahead x %d tiles to the right",
                    botName, tilesForward, tilesToRight));
        }
    }

    private void setAreaModeSpeed(String[] input) {
        Float speed = bot.parseFloatArg(input, InputKey.area_speed, 0.01f, 100f);
        if (speed == null)
            return;
        this.stepTimeout = (long) (1000 / speed);
        Utils.consolePrint(String.format("The speed for area mode was set to %.2f tiles per second", speed));
    }

    /**
     * Add the area mode settings to the owning bot's status lines
     */
    void describeSettings(List<String> lines) {
        if (areaTourActivated())
            lines.add(String.format("Area mode: on (%d tiles ahead x %d tiles to the right)", height, width));
        else
            lines.add("Area mode: off");
        lines.add(String.format("Area speed: %.2f tiles per second", 1000f / stepTimeout));
    }

    private enum InputKey implements Bot.InputKey {
        area("Area Mode", "Start the area processing mode for an area of the given size, starting from the bottom left corner where the player stands and facing forward. " +
                "While it is running, the same size (or no arguments) stops it and a different size resizes it", "[<tiles ahead> <tiles to the right>]"),
        area_speed("Area Speed", "Set the moving speed for area mode in tiles per second (0.01 to 100). Default value is 1 tile per second.", "<tiles per second>");

        private final Bot.KeyInfo keyInfo;

        InputKey(String fullName, String description, String usage) {
            keyInfo = new Bot.KeyInfo(fullName, description, usage);
        }

        @Override
        public Bot.KeyInfo keyInfo() {
            return keyInfo;
        }
    }

    interface MoveStrategy {
        void moveForward(float distance, int steps, long duration) throws InterruptedException;
    }
}
