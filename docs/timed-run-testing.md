# Timed run

Normal Play uses one run: **120 active seconds → one boss → results**.
Countdowns and pauses do not consume combat time. The HUD shows `BOSS IN 2:00`,
then `BOSS`, and `CLEAR` on victory. Results say `Run Clear` or `Game Over`.

The combat uses stage-one enemy balance stretched to 120 seconds. At the
deadline, ordinary enemies, bullets and the meteor are removed without kill
rewards; already-earned falling diamonds are collected. One of the entity
team's two bosses is selected using its existing stage-five balance. Its own
summoned enemies and attacks remain enabled. There is no intermediate cash-out.
The same ship, lives, score, inventory and active effects continue into the boss.

A fatal hit wins a simultaneous timer/defeat boundary. A terminal result stops
further collisions and awards clear rewards once. Victory banks pending diamonds;
death forfeits them. Pause → Quit to menu discards pending diamonds and returns
directly to the menu. Already-collected coins follow the existing persistence rule.
Pause → Restart starts the whole run again and refunds this attempt's coins;
temporary inventory/effects are reset while purchased equipment is reapplied.

The default run counts as one completed level and awards one clear diamond;
it does not pretend that the old ten stages were completed. Existing stage
diagnostics (`-Dgame.startLevel=2` through `10`) remain available to other teams.
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
guard itself remains enabled in production. All standalone tests mute audio.

The extended timed-run test also drives both real boss implementations through
the game's collision/reward/clear/restart path, exercises movement and pause
with in-memory key states, and renders the actual game and result screens
without a desktop window. `DrawManager` retains the ordinary window path and
uses only its image buffer when running headless with no Frame.

Pass a directory to `TimedRunTest` to export those rendered frames:

```powershell
java '-Djava.awt.headless=true' -cp "tmp/timed-run-test/bin;res" TimedRunTest tmp/timed-run-frames
```

JDK 17 verification on 2026-11-13 passed with no compiler warnings:

| Suite | Checks |
| --- | ---: |
| TimedRunTest | 80 |
| TwoHitEnemyTest | 166 |
| FinalBossAttackTest (legacy boss/bullet checks) | 124 |
| EffectIntegrationTest | 77 |
| GameplayStarfieldTest | 29 |
| Total | 476 |

CI runs each suite in its own build/save directory. The starfield suite now
supports headless rendering; its display-based invocation still works.

## Render evidence

These are frames produced by the real rendering code during automated tests,
not captures of a desktop window or evidence of manual play:

- [Combat and HUD](screenshots/timed-run-combat.png)
- [Pause](screenshots/timed-run-pause.png)
- [Fortress boss](screenshots/timed-run-fortress.png)
- [Raider boss](screenshots/timed-run-raider.png)
- [Clear result](screenshots/timed-run-clear-fortress.png)

## Integration status

Merged team main `afff2b9` into this branch on 2026-11-13. It contains the
merged entity-team PRs #1–#4, scrolling background #6 and ship sprites #8.
Their latest boss fixes, collision rules, background and sprites are preserved;
the PR diff against main contains the level-team work and regression support.
The independent item-team PR #7 remains outside this change.

Compilation, all five automated suites and offscreen rendering are complete.
Team review/approval and a manual playthrough on a permitted test desktop are
still pending. No local desktop window, keyboard/mouse automation or audible
playback was used. Native focus/input, audio and a human end-to-end playthrough
are not claimed by these tests.
