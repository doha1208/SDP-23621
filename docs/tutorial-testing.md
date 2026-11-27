# Tutorial stage

Every new game starts with a safe tutorial before Stage 1. It teaches the
game's keys (set in `engine.ControlKeys`) in six steps:

1. Move with WASD or the arrow keys (any direction).
2. Press CTRL to dash.
3. Hold SPACE to fire.
4. Defeat the practice enemy.
5. Collect the falling Shield.
6. Press Z to use it (Z, X and C use item slots 1 to 3).

Then press ENTER to start Stage 1. ENTER also skips straight to Stage 1 at
any step, including right after opening the tutorial. ESC returns to the main
menu; it does not start the game. There is no practice boss fight.

Practice does not award points or consume lives in the normal game. Completing
or skipping the tutorial starts Stage 1 with the normal initial game state.
The tutorial remains available on every new game; completion is not saved.

ENTER must be released and pressed again if held while entering from the
menu. If ENTER and ESC are pressed together, returning to the menu takes
priority. S moves the ship down; it no longer skips the tutorial.

## Manual checks

1. Run `engine.Core` with `res` on the classpath (in IntelliJ, mark `res` as
   Resources Root). Select Play. The tutorial should appear before Stage 1.
2. Press SPACE without moving: the movement step must remain active.
3. Move up, down, left or right: the dash step appears. The ship stays
   between the arena top and its starting row.
4. Press CTRL: the ship dashes sideways, and the firing step and practice enemy appear.
5. Shoot away from the enemy: the step must not advance until a shot hits it.
6. Hit the enemy, collect the Shield, then press Z: the completion message
   appears and the ship gets the shield. The old number key 1 does nothing.
7. Press ENTER: Stage 1 begins with a fresh score and three lives.
8. Start a new game and press ESC during the tutorial: return to the menu without
   entering Stage 1 or the score screen. Starting again resets the tutorial.
9. Move against each edge: the player must stay inside the play area.

## Skip and regression checks

Use a new game for each case. Record the tested commit, result, and screenshots
when reporting a manual test; the checklist below is not a claim that it was run.

| Case | Action | Expected result |
| --- | --- | --- |
| Initial skip | Press ENTER immediately on entering the tutorial, before moving. | Stage 1 begins; no need to wait for the input delay. |
| Firing step | Move and dash, then press ENTER before firing. | Stage 1 begins. |
| Active bullets | Fire away from the enemy, then press ENTER while a bullet is visible. | Stage 1 begins without leftover tutorial bullets or enemies. |
| Completion | Finish all six steps, then press ENTER. | Stage 1 begins. |
| Held menu key | Hold ENTER while selecting Play. | Tutorial stays visible until ENTER is released and pressed again. |
| Moving down | Press S at any step. | The ship moves down; the tutorial does not end. |
| Return to menu | Press ESC at each step, including completion. | Main menu appears, with no score screen or new game started. |
| Conflicting keys | Press ENTER and ESC together. | Return to menu. |
| Fresh game state | Compare a skipped run with a completed tutorial run. | Both start at Stage 1 with score 0, three lives and no practice kill/shot credit. |
| Repeat | Return to menu and select Play again. | Tutorial resets; skip remains available. |

Capture the full tutorial window showing `ENTER: start Stage 1 | ESC: menu`,
then Stage 1 after skipping. Confirm the hint fits and remains readable.

## Integration

`src/engine/Core.java` is a shared file. It shows `TutorialScreen` before the
existing game loop; mention this in the PR and obtain the required
shared-file review. The tutorial returns the existing start-game code (2),
so Core's normal Stage 1 setup is reused, and its `run()` finally block
recycles practice bullets on both skip and menu exits.

## Automated offscreen checks (Java 17)

The optional test harness stays in the [KFC fork](https://github.com/1322528197ldy-tech/KFC-Invaders-SDP-23621/tree/47b9f1196e12de6b7196d8d39d348023104974f4/tests/kfc/KfcTutorialChecks.java), rather than adding a team-specific test directory to the course repository. It is not part of the game or the shared CI configuration. It predates the dash, item and key changes above, so its step and key checks need updating before use.
Compile production sources into a fresh temporary classes directory, then compile the linked harness against those classes. Run `KfcTutorialChecks <temporary-screenshot-directory>` with that classes directory and `res` on the classpath. The temporary location isolates generated saves. Java 17 test-only Unsafe warnings are expected. These checks do not replace a human playthrough.
