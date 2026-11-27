# Five-ship dash motion study

Design-only preview for DES-6. Uses the five approved 13 x 8 ship silhouettes at
the game's two-pixel scale. No gameplay, save files, purchases, collision rules,
invulnerability, cooldowns, or existing GhostTrail code are changed.

## Run (JDK 17 or later)

From any directory, run `bash /absolute/path/to/SDP-23621/scripts/preview-dash.sh`.
The script compiles only the effect and preview into a temporary directory,
opens a separate window, and removes the temporary build when the window closes.

| Key | Action |
| --- | --- |
| 1 / 2 / 3 / 4 / 5 | Standard / Fast Move / Fast Attack / Big Bullet / Two-Way Shot |
| Space | Play one dash in the selected direction |
| Left / Right | Select direction while idle |
| A | Toggle automatic repeat |
| S | Toggle 0.25x slow motion |
| P | Pause/resume |
| E | Compare with effects disabled/enabled |
| R | Reset position and playback |
| Esc | Close preview |

## Art specification and handoff

- Proposed motion: 180 ms, 110 screen pixels, easing out. These are mock preview
  values for art review; the gameplay team owns actual dash duration and distance.
- Up to three ship copies, sampled every 30 ms. Each fades over 120 ms, starting
  at approximately 45% opacity. Copies use the supplied ship shape and color.
- Neutral gray thrust with a white core, extending opposite horizontal movement.
  A short 40 ms startup changes to alternating 30 ms thrust frames. Thrust stops
  immediately when the dash ends; the remaining afterimages fade naturally.
- The reusable effect accepts a sprite mask, so other ships need no new ghost art.
  All five ships share one implementation. Thrust attaches to the occupied hull
  pixels on the middle sprite row, preserving each ship's transparent padding.
  Switching ships clears old trails and resets position and playback.
- Feed `DashVisualEffect.update` monotonic simulation milliseconds, actual ship
  positions, dash state, direction (-1/+1), and skin color. Draw before the current
  ship via `draw(Graphics2D, spriteMask)`. Reset on teleport, death or screen change.
- Pause by freezing simulation time. Team CS has since wired the effect into
  GameScreen as the dash dodge; see "In game" below.

## In game (Team CS dash dodge)

The dash replaces the earlier idea of a spin in place as the ship's dodge.

| What | Value |
| --- | --- |
| Key | Ctrl, one dash per press |
| Direction | The way the ship last moved (right at the start of a level) |
| Length | 11 frames, about 180 ms at 60 fps, easing out |
| Distance | 110 px, stopping at the screen sides |
| Dodge | Enemy bullets, boss attacks and ramming enemies pass through while dashing |
| Recharge | 1.5 s after the dash ends |

- The level countdown shows `P to pause, Ctrl to dash`.
- Arrow keys do nothing while the dash moves the ship; shooting still works.
- A thin bar under the ship fills while the dash recharges and disappears once
  the next dash is ready.
- The dash counts game frames, so pausing freezes the dash, its recharge and
  the afterimages.
- If the level ends mid-dash, the trail and the recharge bar are no longer drawn.
- To tune it, change `DURATION_FRAMES`, `DISTANCE` and `COOLDOWN_FRAMES` in
  `entity.ShipDash`.
- The dash key is set in `engine.ControlKeys`. It used to be Shift, which on
  Windows opens the Sticky Keys prompt when pressed five times in a row.

Checks: `entity.ShipDashTest` (dash timing, easing, direction, recharge) and
`DashDodgeTest` (Ctrl input, screen sides, dodging inside a real GameScreen,
afterimages and the recharge bar). Run both headless from the repo root.

![Dash dodge in game](screenshots/dash-dodge.png)

## Verification

Compile the two preview sources and `tests/DashVisualEffectTest.java`, then run
`java -Djava.awt.headless=true -cp <classes> DashVisualEffectTest`.
The tests cover sampling across frame rates, bounded history, fading, thrust
orientation, graphics state, pause/reset, and direction changes without teleporting.
Use `bash scripts/preview-dash.sh --snapshot /absolute/path/preview.png` for a
five-ship comparison. `--smoke` opens, cycles through all five ships, plays their
dashes, and closes automatically.

Validated locally: 61 preview/effect checks, 367 existing regression checks,
Java 17 target compilation (six pre-existing warnings), and real-window smoke playback.

## Reference

![Five-ship dash preview](screenshots/dash-five-ships.png)
