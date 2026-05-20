# Slope-Aware Climbing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add slope-aware climbing and stamina recovery to `DiggerBot` area digging, area leveling, and surface-mining movement so the bot stops damaging the player on steep terrain.

**Architecture:** Add a small opt-in movement helper that detects climb-required movement, toggles client climb state, records the last safe recovery position, and returns there when stamina reaches the bot's existing threshold. `AreaAssistant` gets an injectable movement strategy so only selected bots use the new behavior.

**Tech Stack:** Java 8, Maven, JUnit 4, Wurm Unlimited client APIs, Ago mod loader.

---

## File Structure

- Create `main/java/net/ildar/wurm/bot/SlopeAwareMovement.java`
  - Owns pure slope checks, climb toggling, climb-aware move execution, recovery state, and recovery waiting.
  - Package-private so only bot package users can consume it.
- Modify `main/java/net/ildar/wurm/bot/AreaAssistant.java`
  - Add a `MoveStrategy` interface with default behavior backed by `Utils.movePlayerBySteps`.
  - Add `setMoveStrategy(...)` so `DiggerBot` can opt in.
- Modify `main/java/net/ildar/wurm/bot/DiggerBot.java`
  - Create and install a `SlopeAwareMovement` instance.
  - Route tile-corner movement, area movement, and surface-mining movement through the helper.
  - Trigger recovery before queuing more dig/level actions when climbing has drained stamina to threshold.
- Leave `main/java/net/ildar/wurm/bot/MinerBot.java` unchanged.
  - `MinerBot` currently skips work on the surface with `if (WurmHelper.hud.getWorld().getPlayerLayer() >= 0)`, so it has no active surface-mining movement path to protect.
- Create `main/src/test/java/net/ildar/wurm/bot/SlopeAwareMovementTest.java`
  - Unit-test pure helper logic that does not need a live Wurm client.

---

### Task 1: Add Pure Slope Planning Tests

**Files:**
- Create: `main/src/test/java/net/ildar/wurm/bot/SlopeAwareMovementTest.java`
- Create later: `main/java/net/ildar/wurm/bot/SlopeAwareMovement.java`

- [ ] **Step 1: Write failing tests for slope classification and recovery state**

Create `main/src/test/java/net/ildar/wurm/bot/SlopeAwareMovementTest.java`:

```java
package net.ildar.wurm.bot;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SlopeAwareMovementTest {
    @Test
    public void movementAtThirtySlopeDoesNotRequireClimb() {
        SlopeAwareMovement.HeightProvider heights = (x, y) -> x <= 0 ? 0f : 3f;

        boolean requiresClimb = SlopeAwareMovement.requiresClimb(0f, 0f, 1f, 0f, heights);

        assertFalse(requiresClimb);
    }

    @Test
    public void movementAboveThirtySlopeRequiresClimb() {
        SlopeAwareMovement.HeightProvider heights = (x, y) -> x <= 0 ? 0f : 3.1f;

        boolean requiresClimb = SlopeAwareMovement.requiresClimb(0f, 0f, 1f, 0f, heights);

        assertTrue(requiresClimb);
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -q test
```

Expected: `SlopeAwareMovement` is missing and test compilation fails.

---

### Task 2: Implement `SlopeAwareMovement` Pure Logic

**Files:**
- Create: `main/java/net/ildar/wurm/bot/SlopeAwareMovement.java`
- Test: `main/src/test/java/net/ildar/wurm/bot/SlopeAwareMovementTest.java`

- [ ] **Step 1: Add the helper class with pure slope and recovery logic**

Create `main/java/net/ildar/wurm/bot/SlopeAwareMovement.java`:

```java
package net.ildar.wurm.bot;

import com.wurmonline.client.game.PlayerObj;
import net.ildar.wurm.Utils;
import net.ildar.wurm.WurmHelper;

class SlopeAwareMovement {
    static final float MAX_WALKABLE_SLOPE = 3.0f;
    static final float RECOVERY_STAMINA = 0.99f;

    interface HeightProvider {
        float getHeight(float x, float y);
    }

    static boolean requiresClimb(float fromX, float fromY, float toX, float toY, HeightProvider heights) {
        float fromHeight = heights.getHeight(fromX, fromY);
        float toHeight = heights.getHeight(toX, toY);
        return Math.abs(fromHeight - toHeight) > MAX_WALKABLE_SLOPE;
    }

    static class RecoveryState {
        private boolean active;
        private float recoveryX;
        private float recoveryY;

        void markActive(float recoveryX, float recoveryY) {
            this.active = true;
            this.recoveryX = recoveryX;
            this.recoveryY = recoveryY;
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

        boolean needsRecovery(float stamina, float damage, float staminaThreshold) {
            return active && stamina + damage <= staminaThreshold;
        }
    }

    private final RecoveryState recoveryState = new RecoveryState();

    boolean needsRecovery(float stamina, float damage, float staminaThreshold) {
        return recoveryState.needsRecovery(stamina, damage, staminaThreshold);
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run:

```powershell
mvn -q test
```

Expected: all tests pass.

---

### Task 3: Add Client Movement and Climb Control

**Files:**
- Modify: `main/java/net/ildar/wurm/bot/SlopeAwareMovement.java`
- Test: `main/src/test/java/net/ildar/wurm/bot/SlopeAwareMovementTest.java`

- [ ] **Step 1: Extend the helper with live Wurm movement methods**

Add these methods to `SlopeAwareMovement` below the existing `needsRecovery(...)` method:

```java
    void moveForward(float distance, int steps, long duration) throws InterruptedException {
        float x = WurmHelper.hud.getWorld().getPlayerPosX();
        float y = WurmHelper.hud.getWorld().getPlayerPosY();
        float xRot = Utils.getField(WurmHelper.hud.getWorld().getPlayer(), "xRotUsed");
        float targetX = x + (float)(distance * Math.sin((double)xRot / 180 * Math.PI));
        float targetY = y + (float)(-distance * Math.cos((double)xRot / 180 * Math.PI));
        moveTo(targetX, targetY, steps, duration);
    }

    void moveTo(float targetX, float targetY, int steps, long duration) throws InterruptedException {
        float x = WurmHelper.hud.getWorld().getPlayerPosX();
        float y = WurmHelper.hud.getWorld().getPlayerPosY();
        HeightProvider heights = (hx, hy) -> WurmHelper.hud.getWorld().getNearTerrainBuffer().getInterpolatedHeight(hx, hy);

        if (!requiresClimb(x, y, targetX, targetY, heights)) {
            Utils.movePlayerBySteps(targetX, targetY, steps, duration);
            return;
        }

        recoveryState.markActive(x, y);
        setClimbing(true);
        Utils.movePlayerBySteps(targetX, targetY, steps, duration);
    }

    boolean recoverIfNeeded(float stamina, float damage, float staminaThreshold, long stepDuration) throws InterruptedException {
        if (!needsRecovery(stamina, damage, staminaThreshold))
            return false;

        Utils.consolePrint("Returning to safe ground to recover stamina.");
        Utils.movePlayerBySteps(recoveryState.getRecoveryX(), recoveryState.getRecoveryY(), 5, stepDuration);
        setClimbing(false);
        waitForStaminaRecovery();
        recoveryState.clear();
        return true;
    }

    private void waitForStaminaRecovery() throws InterruptedException {
        while (WurmHelper.hud.getWorld().getPlayer().getStamina()
                + WurmHelper.hud.getWorld().getPlayer().getDamage() < RECOVERY_STAMINA) {
            Thread.sleep(500);
        }
    }

    private void setClimbing(boolean climbing) {
        PlayerObj player = WurmHelper.hud.getWorld().getPlayer();
        player.setClimbing(climbing);
        WurmHelper.hud.setToggle(0, climbing ? 1 : 0);
    }
```

- [ ] **Step 2: Run compile/tests**

Run:

```powershell
mvn -q test
```

Expected: all tests pass and `SlopeAwareMovement` compiles against the Wurm client jars.

---

### Task 4: Make `AreaAssistant` Movement Injectable

**Files:**
- Modify: `main/java/net/ildar/wurm/bot/AreaAssistant.java`

- [ ] **Step 1: Add a movement strategy**

Inside `AreaAssistant`, add this interface near the fields:

```java
    interface MoveStrategy {
        void moveForward(float distance, int steps, long duration) throws InterruptedException;
    }

    private MoveStrategy moveStrategy = Utils::movePlayerBySteps;
```

Add this setter near `setMoveRightDistance(...)`:

```java
    void setMoveStrategy(MoveStrategy moveStrategy) {
        this.moveStrategy = moveStrategy == null ? Utils::movePlayerBySteps : moveStrategy;
    }
```

Replace both `Utils.movePlayerBySteps(4, STEPS_IN_MOVE, stepTimeout);` calls with:

```java
moveStrategy.moveForward(4, STEPS_IN_MOVE, stepTimeout);
```

- [ ] **Step 2: Run compile/tests**

Run:

```powershell
mvn -q test
```

Expected: all tests pass.

---

### Task 5: Wire Slope-Aware Movement Into `DiggerBot`

**Files:**
- Modify: `main/java/net/ildar/wurm/bot/DiggerBot.java`

- [ ] **Step 1: Add helper field and install it**

Add a field:

```java
    private SlopeAwareMovement slopeMovement;
```

In the constructor after `areaAssistant = new AreaAssistant(this);`, add:

```java
        slopeMovement = new SlopeAwareMovement();
        areaAssistant.setMoveStrategy(slopeMovement::moveForward);
```

- [ ] **Step 2: Recover before queuing work**

In `work()`, after reading `stamina`, `damage`, and `progress`, insert:

```java
            if (progress == 0f && slopeMovement.recoverIfNeeded(stamina, damage, staminaThreshold, stepDuration))
                continue;
```

- [ ] **Step 3: Route direct tile-corner moves through the helper**

Replace:

```java
Utils.movePlayerBySteps(diggingTileInfo.x * 4 + 2, diggingTileInfo.y * 4 + 2, STEPS, stepDuration);
```

with:

```java
slopeMovement.moveTo(diggingTileInfo.x * 4 + 2, diggingTileInfo.y * 4 + 2, STEPS, stepDuration);
```

Replace:

```java
Utils.movePlayerBySteps(destX * 4, destY * 4, STEPS, stepDuration);
```

with:

```java
slopeMovement.moveTo(destX * 4, destY * 4, STEPS, stepDuration);
```

- [ ] **Step 4: Run compile/tests**

Run:

```powershell
mvn -q test
```

Expected: all tests pass.

---

### Task 6: Verify `MinerBot` Is Not Changed

**Files:**
- Read: `main/java/net/ildar/wurm/bot/MinerBot.java`

- [ ] **Step 1: Confirm surface work is not currently active in `MinerBot`**

Confirm this guard remains unchanged in `MinerBot.work()`:

```java
            if (WurmHelper.hud.getWorld().getPlayerLayer() >= 0) {
                sleep(timeout);
                continue;
            }
```

- [ ] **Step 2: Run compile/tests**

Run:

```powershell
mvn -q test
```

Expected: all tests pass.

---

### Task 7: Full Verification

**Files:**
- Verify all modified files.

- [ ] **Step 1: Run full package build**

Run:

```powershell
mvn -q install
```

Expected: exit code `0`, updated `WurmHelper.zip` generated in repo root.

- [ ] **Step 2: Check diff hygiene**

Run:

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors. Generated `target/` folders and `WurmHelper.zip` remain ignored.

- [ ] **Step 3: Manual Wurm Unlimited smoke test**

In-client scenario:

1. Start Wurm Unlimited with the rebuilt mod.
2. Enable `DiggerBot` tile/area mode on a steep surface slope.
3. Confirm the bot toggles climbing on before crossing the steep segment.
4. Confirm it stops queuing actions at the configured stamina threshold.
5. Confirm it returns to the previous safe position, toggles climbing off, waits for stamina near full, and resumes.
6. Repeat with `DiggerBot` surface-mining mode enabled.

Expected: no repeated falling loop and no climb mode left on after recovery.
