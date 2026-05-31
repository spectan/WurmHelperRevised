package net.ildar.wurm.bot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SlopeAwareMovementTest {
    @Test
    public void movementAtThirtySlopeDoesNotRequireClimb() {
        SlopeAwareMovement.HeightProvider heights = (x, y) -> x * 3f / 4f;

        boolean requiresClimb = SlopeAwareMovement.requiresClimb(0f, 0f, 4f, 0f, heights);

        assertFalse(requiresClimb);
    }

    @Test
    public void movementAboveThirtySlopeRequiresClimb() {
        SlopeAwareMovement.HeightProvider heights = (x, y) -> x * 3.1f / 4f;

        boolean requiresClimb = SlopeAwareMovement.requiresClimb(0f, 0f, 4f, 0f, heights);

        assertTrue(requiresClimb);
    }

    @Test
    public void longGentleMoveAboveOneTileSlopeDoesNotRequireClimb() {
        SlopeAwareMovement.HeightProvider heights = (x, y) -> x * 5.9f / 8f;

        boolean requiresClimb = SlopeAwareMovement.requiresClimb(0f, 0f, 8f, 0f, heights);

        assertFalse(requiresClimb);
    }

    @Test
    public void oneTileSmoothSlopeAboveThirtyRequiresClimb() {
        SlopeAwareMovement.HeightProvider heights = (x, y) -> x * 3.1f / 4f;

        boolean requiresClimb = SlopeAwareMovement.requiresClimb(0f, 0f, 4f, 0f, heights);

        assertTrue(requiresClimb);
    }

    @Test
    public void midpointSpikeRequiresClimbEvenWhenEndpointsAreSafe() {
        SlopeAwareMovement.HeightProvider heights = (x, y) -> x >= 5.9f && x <= 6.1f ? 10f : 0f;

        boolean requiresClimb = SlopeAwareMovement.requiresClimb(0f, 0f, 8f, 0f, heights);

        assertTrue(requiresClimb);
    }

    @Test
    public void oneTileMidpointSpikeRequiresClimbEvenWhenEndpointsAreSafe() {
        SlopeAwareMovement.HeightProvider heights = (x, y) -> x >= 1.9f && x <= 2.1f ? 10f : 0f;

        boolean requiresClimb = SlopeAwareMovement.requiresClimb(0f, 0f, 4f, 0f, heights);

        assertTrue(requiresClimb);
    }

    @Test
    public void climbPlanUsesLastSafeSampleAsRecoveryPoint() {
        SlopeAwareMovement.HeightProvider heights = (x, y) -> x >= 7.9f ? 10f : 0f;

        SlopeAwareMovement.ClimbPlan plan = SlopeAwareMovement.planMove(0f, 0f, 8f, 0f, heights);

        assertTrue(plan.requiresClimb());
        assertEquals(6f, plan.getRecoveryX(), 0f);
        assertEquals(0f, plan.getRecoveryY(), 0f);
    }

    @Test
    public void walkableClimbPlanDoesNotRequireClimb() {
        SlopeAwareMovement.HeightProvider heights = (x, y) -> x * 5.9f / 8f;

        SlopeAwareMovement.ClimbPlan plan = SlopeAwareMovement.planMove(0f, 0f, 8f, 0f, heights);

        assertFalse(plan.requiresClimb());
    }

    @Test
    public void activeClimbNeedsRecoveryAtConfiguredThreshold() {
        SlopeAwareMovement.RecoveryState state = new SlopeAwareMovement.RecoveryState();
        state.markActive(10f, 20f);

        assertTrue(state.needsRecovery(0.94f, 0.01f, 0.95f));
        assertFalse(state.needsRecovery(0.95f, 0.01f, 0.95f));
    }

    @Test
    public void inactiveClimbDoesNotNeedRecovery() {
        SlopeAwareMovement.RecoveryState state = new SlopeAwareMovement.RecoveryState();

        assertFalse(state.needsRecovery(0.10f, 0f, 0.95f));
    }

    @Test
    public void instanceMarkRecoveryMakesNeedsRecoveryTrueAtThreshold() {
        SlopeAwareMovement movement = new SlopeAwareMovement();

        movement.markRecovery(10f, 20f);

        assertTrue(movement.isRecovering());
        assertEquals(10f, movement.getRecoveryX(), 0f);
        assertEquals(20f, movement.getRecoveryY(), 0f);
        assertTrue(movement.needsRecovery(0.94f, 0.01f, 0.95f));
    }

    @Test
    public void activeRecoveryPointIsNotOverwritten() {
        SlopeAwareMovement movement = new SlopeAwareMovement();

        movement.markRecovery(10f, 20f, 15f, 25f);
        movement.markRecovery(30f, 40f, 35f, 45f);

        assertEquals(10f, movement.getRecoveryX(), 0f);
        assertEquals(20f, movement.getRecoveryY(), 0f);
    }

    @Test
    public void activeRecoveryWorkPointUpdatesToLatestClimbDestination() {
        SlopeAwareMovement movement = new SlopeAwareMovement();

        movement.markRecovery(10f, 20f, 15f, 25f);
        movement.markRecovery(30f, 40f, 35f, 45f);

        assertEquals(35f, movement.getWorkX(), 0f);
        assertEquals(45f, movement.getWorkY(), 0f);
    }

    @Test
    public void hasRecoveredUsesRecoveryStamina() {
        SlopeAwareMovement movement = new SlopeAwareMovement();

        assertFalse(movement.hasRecovered(0.98f, 0f));
        assertTrue(movement.hasRecovered(0.98f, 0.01f));
    }

    @Test
    public void stopClimbingDoesNotToggleWhenClientAlreadyShowsClimbingOff() {
        FakeClimbControls climbControls = new FakeClimbControls(false);
        SlopeAwareMovement movement = new SlopeAwareMovement(climbControls);

        movement.markRecovery(10f, 20f);
        movement.stopClimbing();

        assertEquals(0, climbControls.toggles);
    }

    @Test
    public void stopClimbingTogglesClientWhenClimbingIsStillOn() {
        FakeClimbControls climbControls = new FakeClimbControls(true);
        SlopeAwareMovement movement = new SlopeAwareMovement(climbControls);

        movement.markRecovery(10f, 20f);
        movement.stopClimbing();

        assertEquals(1, climbControls.toggles);
        assertFalse(climbControls.climbing);
    }

    @Test
    public void forceClimbingOffTogglesOffEvenWhenNotRecovering() {
        FakeClimbControls climbControls = new FakeClimbControls(true);
        SlopeAwareMovement movement = new SlopeAwareMovement(climbControls);

        // climbing is on but recovery state is inactive (edge case)
        movement.forceClimbingOff();

        assertFalse(climbControls.climbing);
        assertEquals(1, climbControls.toggles);
        assertFalse(movement.isRecovering());
    }

    @Test
    public void forceClimbingOffWorksWhenRecovering() {
        FakeClimbControls climbControls = new FakeClimbControls(true);
        SlopeAwareMovement movement = new SlopeAwareMovement(climbControls);

        movement.markRecovery(10f, 20f);
        assertTrue(movement.isRecovering());

        movement.forceClimbingOff();

        assertFalse(climbControls.climbing);
        assertFalse(movement.isRecovering());
    }

    @Test
    public void prepareForRecoveryCancelsQueuedActionsBeforeMovingAway() {
        FakeActionQueueControls actionQueueControls = new FakeActionQueueControls();
        SlopeAwareMovement movement = new SlopeAwareMovement(new FakeClimbControls(true), actionQueueControls);

        movement.prepareForRecoveryMove();

        assertEquals(1, actionQueueControls.cancelCalls);
    }

    private static class FakeClimbControls implements SlopeAwareMovement.ClimbControls {
        private boolean climbing;
        private int toggles;

        private FakeClimbControls(boolean climbing) {
            this.climbing = climbing;
        }

        @Override
        public boolean isClimbing() {
            return climbing;
        }

        @Override
        public void toggleClimbing() {
            climbing = !climbing;
            toggles++;
        }
    }

    private static class FakeActionQueueControls implements SlopeAwareMovement.ActionQueueControls {
        private int cancelCalls;

        @Override
        public void cancelQueuedActions() {
            cancelCalls++;
        }
    }
}
