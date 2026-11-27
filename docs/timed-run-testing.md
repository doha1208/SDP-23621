# Timed run

> **Opt-in.** Normal Play goes through stages 1 to 10 again, each with its own
> enemy settings (`res/levels/stageN.properties`) and bosses on stages 5 and
> 10. Start the game with `-Dgame.timedRun=true` to play the timed run
> described below instead.

The timed run is one run: **120 active seconds → one boss → results**.
Countdowns and pauses do not consume combat time. The HUD shows `BOSS IN 2:00`,
then `BOSS`, and `CLEAR` on victory. Results say `Run Clear` or `Game Over`.

The combat uses stage-one enemy balance stretched to 120 seconds. At the
deadline, ordinary enemies, bullets and the meteor are removed without kill
rewards; already-earned falling diamonds are collected. One of the entity
team's two bosses is selected using its existing stage-five balance. Its own
summoned enemies and attacks remain enabled. There is no intermediate cash-out.
The same ship, lives, score, inventory and active effects continue into the boss.

A fatal hit wins a simultaneous timer/defeat boundary. On a boss kill, the defeat
animation clears the boss hazards, bullets and summons before the run ends.
A terminal result stops further collisions and awards clear rewards once. Victory banks pending diamonds;
death forfeits them. Pause → Quit to menu discards pending diamonds and returns
directly to the menu. Already-collected coins follow the existing persistence rule.
Pause → Restart starts the whole run again and refunds this attempt's coins;
temporary inventory/effects are reset while purchased equipment is reapplied.

The timed run counts as one completed level and awards one clear diamond.
A full timed clear counts as a campaign win for Infinity Void and Fleet Master.
Fleet Master still requires a clear with every ship and accepts saved stage 10
clears. Stage play (the default, and `-Dgame.startLevel=2` through `10`) keeps
its stage-based rewards and cash-out screen.
`-Dinvaders.bossOrder=fortress` or `raider` selects the boss for verification.
`-Dinvaders.levelSeed=1` keeps enemy and boss randomness repeatable.

## Automated checks

Run from the repository root with JDK 17. All checks are headless and muted.
Build into the isolated directory below: the game stores profiles beside its
class directory, so test rewards must not use a player's normal build directory.

PowerShell:

```powershell
New-Item -ItemType Directory -Force tmp/timed-run-test/bin | Out-Null
javac -encoding UTF-8 -Xlint:all -d tmp/timed-run-test/bin (Get-ChildItem -Recurse src -Filter *.java).FullName tests/TimedRunTest.java
java '-Djava.awt.headless=true' -cp "tmp/timed-run-test/bin;res" TimedRunTest
```

Linux/macOS:

```sh
mkdir -p tmp/timed-run-test/bin
javac -Xlint:all -d tmp/timed-run-test/bin $(find src -name '*.java') tests/TimedRunTest.java
java -Djava.awt.headless=true -cp tmp/timed-run-test/bin:res TimedRunTest
```

Coverage includes the exact 120-second boundary, countdown/pause exclusion,
real boss creation, state and item preservation, normal-spawn shutdown, clear
and simultaneous death, post-clear damage, idempotent settlement, restart
cleanup and quit-to-menu. The existing `TwoHitEnemyTest`, `FinalBossAttackTest`
and `EffectIntegrationTest` now use the current main versions. Collision
fixtures explicitly finish the countdown before shooting; the countdown
guard remains enabled in production. The item pierce and dash collision
fixtures also end their countdown before exercising live combat.

The extended timed-run test also drives both real boss implementations through
the game's collision/reward/clear/restart path, exercises movement and pause
with in-memory key states, and renders the actual game and result screens
without a desktop window. `DrawManager` retains the ordinary window path and
uses only its image buffer when running headless with no Frame.

Pass a directory to `TimedRunTest` to export those rendered frames:

```powershell
java '-Djava.awt.headless=true' -cp "tmp/timed-run-test/bin;res" TimedRunTest tmp/timed-run-frames
```

JDK 17 verification on 2026-11-24 passed with no compiler warnings:

| Suite | Checks |
| --- | ---: |
| TimedRunTest | 93 |
| TwoHitEnemyTest | 166 |
| FinalBossAttackTest (legacy boss/bullet checks) | 124 |
| EffectIntegrationTest | 77 |
| GameplayStarfieldTest | 29 |
| WeaponUpgradeTest | 44 |
| DashDodgeTest | 31 |
| ShipVerticalMoveTest | 17 |
| DashVisualEffectTest | 61 |
| ShipDashTest | 77 |
| Total | 719 |

CI runs seven gameplay suites in isolated build/save directories. All ten
suites above were run locally with headless graphics and muted audio.

## Render evidence

These are frames produced by the real rendering code during automated tests,
not captures of a desktop window or evidence of manual play:

- [Combat and HUD](screenshots/timed-run-combat.png)
- [Pause](screenshots/timed-run-pause.png)
- [Fortress boss](screenshots/timed-run-fortress.png)
- [Fortress defeat animation](screenshots/timed-run-fortress-defeat.png)
- [Raider boss](screenshots/timed-run-raider.png)
- [Clear result](screenshots/timed-run-clear-fortress.png)

## Integration status

Merged team main `04e9dc5` into this branch on 2026-11-24. In addition to the
earlier entity, background and ship updates, it includes weapon upgrades (#7),
tutorial practice (#9), dash preview (#11), item drops and attraction (#10),
vertical movement (#12) and dash dodge (#16).

Compilation, all ten automated suites and offscreen rendering are complete.
Team review/approval and a manual playthrough on a permitted test desktop are
still pending. No local desktop window, keyboard/mouse automation or audible
playback was used. Native focus/input, audio and a human end-to-end playthrough
are not claimed by these tests.
