package net.ildar.wurm.bot;

import com.wurmonline.shared.constants.PlayerAction;
import net.ildar.wurm.Utils;
import net.ildar.wurm.WurmHelper;

class SlopeAwareMovement {
    static final float MAX_WALKABLE_SLOPE = 3.0f;
    static final float RECOVERY_STAMINA = 0.99f;

    interface HeightProvider {
        float getHeight(float x, float y);
    }

    interface ClimbControls {
        boolean isClimbing();

        void toggleClimbing();
    }

    interface ActionQueueControls {
        void cancelQueuedActions();
    }

    static boolean requiresClimb(float fromX, float fromY, float toX, float toY, HeightProvider heights) {
        return planMove(fromX, fromY, toX, toY, heights).requiresClimb();
    }

    static ClimbPlan planMove(float fromX, float fromY, float toX, float toY, HeightProvider heights) {
        float deltaX = toX - fromX;
        float deltaY = toY - fromY;
        float horizontalDistance = (float)Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        int segments = Math.max(2, (int)Math.ceil(horizontalDistance / 2f));
        float previousX = fromX;
        float previousY = fromY;
        float previousHeight = heights.getHeight(previousX, previousY);

        for (int segment = 1; segment <= segments; segment++) {
            float progress = (float)segment / segments;
            float sampleX = fromX + deltaX * progress;
            float sampleY = fromY + deltaY * progress;
            float sampleHeight = heights.getHeight(sampleX, sampleY);
            float segmentDistance = distance(previousX, previousY, sampleX, sampleY);
            float allowedHeightDelta = MAX_WALKABLE_SLOPE * segmentDistance / 4f;

            if (Math.abs(previousHeight - sampleHeight) > allowedHeightDelta)
                return ClimbPlan.requiresClimb(previousX, previousY);

            previousX = sampleX;
            previousY = sampleY;
            previousHeight = sampleHeight;
        }

        return ClimbPlan.walkable();
    }

    private static float distance(float fromX, float fromY, float toX, float toY) {
        float deltaX = toX - fromX;
        float deltaY = toY - fromY;
        return (float)Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    }

    static class RecoveryState {
        private boolean active;
        private float recoveryX;
        private float recoveryY;
        private float workX;
        private float workY;

        void markActive(float recoveryX, float recoveryY) {
            markActive(recoveryX, recoveryY, recoveryX, recoveryY);
        }

        void markActive(float recoveryX, float recoveryY, float workX, float workY) {
            this.active = true;
            this.recoveryX = recoveryX;
            this.recoveryY = recoveryY;
            updateWork(workX, workY);
        }

        void updateWork(float workX, float workY) {
            this.workX = workX;
            this.workY = workY;
        }

        void clear() {
            active = false;
        }

        boolean isActive() {
            return active;
        }

        float getRecoveryX() {
            return recoveryX;
        }

        float getRecoveryY() {
            return recoveryY;
        }

        float getWorkX() {
            return workX;
        }

        float getWorkY() {
            return workY;
        }

        boolean needsRecovery(float stamina, float damage, float staminaThreshold) {
            return active && stamina + damage <= staminaThreshold;
        }
    }

    static class ClimbPlan {
        private final boolean requiresClimb;
        private final float recoveryX;
        private final float recoveryY;

        private ClimbPlan(boolean requiresClimb, float recoveryX, float recoveryY) {
            this.requiresClimb = requiresClimb;
            this.recoveryX = recoveryX;
            this.recoveryY = recoveryY;
        }

        static ClimbPlan walkable() {
            return new ClimbPlan(false, 0f, 0f);
        }

        static ClimbPlan requiresClimb(float recoveryX, float recoveryY) {
            return new ClimbPlan(true, recoveryX, recoveryY);
        }

        boolean requiresClimb() {
            return requiresClimb;
        }

        float getRecoveryX() {
            return recoveryX;
        }

        float getRecoveryY() {
            return recoveryY;
        }
    }

    private final RecoveryState recoveryState = new RecoveryState();
    private final ClimbControls climbControls;
    private final ActionQueueControls actionQueueControls;

    SlopeAwareMovement() {
        this(new WurmClimbControls(), new WurmActionQueueControls());
    }

    SlopeAwareMovement(ClimbControls climbControls) {
        this(climbControls, new WurmActionQueueControls());
    }

    SlopeAwareMovement(ClimbControls climbControls, ActionQueueControls actionQueueControls) {
        this.climbControls = climbControls;
        this.actionQueueControls = actionQueueControls;
    }

    void markRecovery(float recoveryX, float recoveryY) {
        markRecovery(recoveryX, recoveryY, recoveryX, recoveryY);
    }

    void markRecovery(float recoveryX, float recoveryY, float workX, float workY) {
        if (recoveryState.isActive())
            recoveryState.updateWork(workX, workY);
        else
            recoveryState.markActive(recoveryX, recoveryY, workX, workY);
    }

    void updateWorkPosition(float workX, float workY) {
        if (!recoveryState.isActive())
            return;
        recoveryState.updateWork(workX, workY);
    }

    void clearRecovery() {
        recoveryState.clear();
    }

    boolean isRecovering() {
        return recoveryState.isActive();
    }

    float getRecoveryX() {
        return recoveryState.getRecoveryX();
    }

    float getRecoveryY() {
        return recoveryState.getRecoveryY();
    }

    float getWorkX() {
        return recoveryState.getWorkX();
    }

    float getWorkY() {
        return recoveryState.getWorkY();
    }

    boolean needsRecovery(float stamina, float damage, float staminaThreshold) {
        return recoveryState.needsRecovery(stamina, damage, staminaThreshold);
    }

    void moveForward(float distance, int steps, long duration) throws InterruptedException {
        try {
            float x = WurmHelper.hud.getWorld().getPlayerPosX();
            float y = WurmHelper.hud.getWorld().getPlayerPosY();
            float xRot = Utils.getField(WurmHelper.hud.getWorld().getPlayer(), "xRotUsed");
            float targetX = x + (float)(distance * Math.sin((double)xRot / 180 * Math.PI));
            float targetY = y + (float)(-distance * Math.cos((double)xRot / 180 * Math.PI));

            moveTo(targetX, targetY, steps, duration);
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            Utils.consolePrint("Unexpected error while moving slope-aware - " + e.getMessage());
            Utils.consolePrint(e.toString());
            throw new RuntimeException("Slope-aware movement failed", e);
        }
    }

    void moveTo(float targetX, float targetY, int steps, long duration) throws InterruptedException {
        float x = WurmHelper.hud.getWorld().getPlayerPosX();
        float y = WurmHelper.hud.getWorld().getPlayerPosY();
        HeightProvider heights = (sampleX, sampleY) ->
                WurmHelper.hud.getWorld().getNearTerrainBuffer().getInterpolatedHeight(sampleX, sampleY);
        ClimbPlan climbPlan = planMove(x, y, targetX, targetY, heights);
        updateWorkPosition(targetX, targetY);

        if (!climbPlan.requiresClimb()) {
            Utils.movePlayerBySteps(targetX, targetY, steps, duration);
            return;
        }

        markRecovery(climbPlan.getRecoveryX(), climbPlan.getRecoveryY(), targetX, targetY);
        setClimbing(true);
        try {
            Utils.movePlayerBySteps(targetX, targetY, steps, duration);
        } catch (InterruptedException e) {
            stopClimbing();
            throw e;
        } catch (RuntimeException e) {
            stopClimbing();
            throw e;
        }
    }

    boolean recoverIfNeeded(float stamina, float damage, float staminaThreshold, long stepDuration) throws InterruptedException {
        if (!needsRecovery(stamina, damage, staminaThreshold))
            return false;

        float workX = recoveryState.getWorkX();
        float workY = recoveryState.getWorkY();
        try {
            prepareForRecoveryMove();
            Utils.movePlayerBySteps(recoveryState.getRecoveryX(), recoveryState.getRecoveryY(), 5, stepDuration);
            setClimbing(false);
            waitForRecovery();
            try {
                setClimbing(true);
                Utils.movePlayerBySteps(workX, workY, 5, stepDuration);
            } catch (Exception e) {
                setClimbing(false); // emergency: never leave climb on
                throw e;
            }
        } catch (InterruptedException e) {
            stopClimbing();
            throw e;
        } catch (RuntimeException e) {
            stopClimbing();
            throw e;
        }
        return true;
    }

    void prepareForRecoveryMove() {
        actionQueueControls.cancelQueuedActions();
    }

    void stopClimbing() {
        if (!recoveryState.isActive())
            return;
        setClimbing(false);
        recoveryState.clear();
    }

    void forceClimbingOff() {
        setClimbing(false);
        recoveryState.clear();
    }

    boolean hasRecovered(float stamina, float damage) {
        return stamina + damage >= RECOVERY_STAMINA;
    }

    private void waitForRecovery() throws InterruptedException {
        int maxIterations = 120; // 60 second safety cap
        int iteration = 0;
        while (!hasRecovered(
                WurmHelper.hud.getWorld().getPlayer().getStamina(),
                WurmHelper.hud.getWorld().getPlayer().getDamage())) {
            if (++iteration > maxIterations) {
                Utils.consolePrint("SlopeAwareMovement: stamina did not recover within 60 seconds, aborting recovery");
                throw new RuntimeException("Stamina recovery timeout");
            }
            Thread.sleep(500);
        }
    }

    private void setClimbing(boolean climbing) {
        if (climbControls.isClimbing() != climbing)
            climbControls.toggleClimbing();
    }

    private static class WurmClimbControls implements ClimbControls {
        @Override
        public boolean isClimbing() {
            try {
                Object[] stateButtons = Utils.getField(WurmHelper.hud, "stateButtons");
                return Utils.getField(stateButtons[0], "enabled");
            } catch (Exception e) {
                Utils.consolePrint("Unable to read climbing state - " + e.getMessage());
                return false;
            }
        }

        @Override
        public void toggleClimbing() {
            WurmHelper.hud.toggleStateButton(0);
        }
    }

    private static class WurmActionQueueControls implements ActionQueueControls {
        @Override
        public void cancelQueuedActions() {
            for (int i = 0; i < Utils.getMaxActionNumber(); i++) {
                WurmHelper.hud.sendAction(PlayerAction.STOP, 0);
            }
        }
    }
}
