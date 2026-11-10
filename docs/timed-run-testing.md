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
java -Djava.awt.headless=true -cp "tmp/timed-run-test/bin;res" TimedRunTest
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
and `EffectIntegrationTest` refer to the old enemy/boss implementation; they
are not evidence for the entity team's replacement implementation.

## Integration status

This work depends on the cumulative entity-team changes in PR #4 (including
#1–#3). Review the level-team commits separately until those prerequisites
merge. The local verification does not include an interactive playthrough or
gameplay screenshots. Before final integration, verify both boss variants,
HUD readability, pause/restart controls and result/menu navigation in a test
environment where opening the game is allowed.
