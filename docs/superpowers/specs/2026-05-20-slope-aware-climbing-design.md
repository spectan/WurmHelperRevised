# Slope-Aware Climbing for Area Work

## Goal

Prevent area digging and surface mining from repeatedly moving the player onto unsafe slopes without climb mode, while preserving the existing area-work flow.

The first implementation targets:

- `DiggerBot` area/tile movement
- `DiggerBot` surface-mining movement

`MinerBot` currently skips work when the player is on the surface, so its cave movement is out of scope. Ordinary cave mining and unrelated area bots are also out of scope.

## Current Behavior

`AreaAssistant` advances through an area by calling `Utils.movePlayerBySteps(...)`. That helper directly sets player position and interpolated height. It does not check terrain slope, climbing state, stamina recovery, or fall-risk conditions.

`DiggerBot` already handles some steep-slope action failures by marking corners invalid, but that happens after a failed dig/level action. It does not prevent movement onto a dangerous slope.

`PathingBot` has slope passability logic, but `DiggerBot` does not use it for area movement.

## Proposed Behavior

Add opt-in slope-aware area movement for `DiggerBot`, including its surface-mining mode.

Before each area move:

1. Determine whether the next movement segment requires climb mode based on terrain height differences.
2. If normal walking is safe, move as today.
3. If climb is required, record the current position as the recovery position.
4. Turn climbing on.
5. Move to the target work position.
6. Allow the bot to perform actions while stamina remains above that bot's configured stamina threshold.
7. When stamina reaches the threshold, stop queuing actions.
8. Move back to the recorded recovery position while climbing is still on.
9. Turn climbing off.
10. Wait for stamina to recover near full before resuming the area pass.

The recovery target is the last known safe position in the current area path, not a global search target.

## Climb Toggle

Use Wurm's existing climbing state path rather than inventing a new action. The client exposes `PlayerObj.setClimbing(boolean)`, and the HUD state button path for climbing is index `0`. The implementation should centralize climb toggling behind a helper so call sites do not hard-code this detail.

The helper should avoid redundant toggles when it already believes the desired climb state is active.

## Movement Safety

Slope checks should compare the terrain heights along the move from the current position to the destination. The initial threshold should match the existing `PathingBot` normal-walk limit of 30 slope.

If a segment exceeds the normal walking threshold, it is considered climb-required. If the movement is still too risky even with climb, the bot should stop area mode and print a console message rather than continuing to move.

## Stamina and Recovery

Each bot keeps using its existing stamina threshold for when work should stop.

While climbing:

- Do not queue additional dig, level, or surface-mining actions once `stamina + damage` is at or below the bot's threshold.
- Return to the recovery position before turning climb off.
- Wait until `stamina + damage` reaches `0.99` before resuming.

This mirrors the current stamina convention in the bots, where effective stamina is checked as `stamina + damage`.

## Error Handling

If recovery movement fails or the recovery position is no longer safe:

- Stop area mode for the current bot.
- Turn climb off if possible.
- Print a clear console message explaining that slope-aware movement stopped to avoid unsafe movement.

If climb state cannot be toggled:

- Stop the area movement attempt.
- Leave the bot active but do not advance to the next area position.
- Print a clear console message.

## Testing

Add unit-level coverage for pure helper logic:

- A movement segment below the slope threshold does not require climb.
- A segment above the slope threshold requires climb.
- Recovery waits until the configured recovery threshold.
- Climb-required movement chooses the previous safe position as its recovery target.

Full in-client verification will still require manual testing in Wurm Unlimited because climb state and terrain movement are driven by client classes.

## Non-Goals

- Do not implement global pathfinding to find arbitrary safe recovery spots.
- Do not change unrelated `AreaAssistant` users such as forestry, foraging, farming, or archaeology.
- Do not change `MinerBot` cave mining movement.
- Do not make the bot continue climbing indefinitely when stamina cannot recover.
