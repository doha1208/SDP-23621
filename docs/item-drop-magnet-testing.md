# Item drops and magnet

Items fly off in a random direction and bounce off all four walls of the play
area (left, right, the item HUD row at the top, and the screen bottom). After
`drop.bounceMillis` they stop bouncing, keep going straight through the next
wall and disappear once fully outside the play area (Team Entities, 2026-11-27).
Pickup checks follow every movement segment, including wall bounces. Caught
items work at once (`pickup.autoUse`).

An equipped magnet attracts drops within a center-to-center radius. Attraction
takes priority over bouncing and remains active after leaving the initial
radius. Attracted drops follow the player's current position, including upward
movement, and are collected only on contact. A drop that cannot be collected on
magnet contact converts to the configured score. Without the magnet, failed
pickups remain available until they leave the screen.

Normal/special drop probabilities and weighted item selection are unchanged.

## Balance settings

Edit `res/item-balance.properties`, then start a new game run. All settings below
must be positive; invalid values fail at load time.

| Setting | Default | Meaning |
| --- | ---: | --- |
| `drop.speed` | 150 | Flying speed in pixels/second; each drop uses 0.85 to 1.15 times this value. |
| `drop.bounceMillis` | 6000 | How long a drop bounces before it flies out past a wall. |
| `equip.magnet.range` | 140 | Center-to-center attraction radius in pixels. |
| `equip.magnet.speed` | 260 | Attraction speed in pixels/second. |
| `equip.magnet.convertScore` | 50 | Score for an attracted drop that cannot be kept on contact. |

`drop.groundLifetimeMillis`, `drop.fallSpeed`, `drop.horizontalSpeed` and
`drop.directionChangeMillis` remain only for compatibility and no longer change
how drops move.

## Automated checks

Use JDK 17+ from the repository root. Keep build and save files outside the
checkout; the game stores progress beside its classes directory.

```sh
check_dir=$(mktemp -d)
mkdir -p "$check_dir/classes" "$check_dir/screenshots"
find src tests -name '*.java' > "$check_dir/sources.txt"
javac --release 17 -Xlint:all -d "$check_dir/classes" @"$check_dir/sources.txt"
java -Djava.awt.headless=true -cp "$check_dir/classes:res" item.ItemDropMagnetTest
java -Djava.awt.headless=true -cp "$check_dir/classes:res" TwoHitEnemyTest
java -Djava.awt.headless=true -cp "$check_dir/classes:res" FinalBossAttackTest
java -Djava.awt.headless=true -cp "$check_dir/classes:res" EffectIntegrationTest
xvfb-run -a java -cp "$check_dir/classes:res" GameplayStarfieldTest "$check_dir/screenshots"
java -Djava.awt.headless=true -cp "$check_dir/classes:res" item.ItemDropMagnetDemo "$check_dir/screenshots/item-drop-magnet.png"
```

The results below predate the bouncing movement (the item suite is not in this
repository).

The item regression checks cover downward random motion, direction changes,
wall reflections, screen exit, swept contact, magnet radius and persistent
tracking, upward attraction, and score conversion when pickup fails. The
existing game suites cover enemies, bosses, effects, rendering and pause/resume.

Validated with JDK 21 compiling for Java 17:

| Suite | Checks passed |
| --- | ---: |
| `ItemDropMagnetTest` | 904 |
| `TwoHitEnemyTest` | 166 |
| `FinalBossAttackTest` | 124 |
| `EffectIntegrationTest` | 77 |
| `GameplayStarfieldTest` (real window under Xvfb) | 29 |
| **Total** | **1,300** |

The item suite also verifies the real game's score hook: no award before
contact, default +50 and configured +73, each awarded once. Two existing
`this-escape` compilation warnings remain in `Frame` and `EnemyShip`.

The reproducible demo below records production item positions and uses the
real item renderer, ship sprite and score hook. Trails and the range circle
are diagnostic overlays, not new in-game UI. It shows random descent, nearby
attraction before contact, and full-inventory conversion after contact.

![Item drop and magnet test scenes](screenshots/item-drop-magnet.png)

## Manual checks

1. Start `engine.Core`, select Play, and skip the tutorial with ENTER if desired.
2. Destroy enemies and watch drops fly in all directions and bounce off every
   wall, including the top (just below the item HUD row) and the bottom.
3. Leave a drop alone: after about six seconds it stops bouncing, flies out
   past the next wall and disappears.
4. Equip Magnet before a new run. Approach a drop until it starts following,
   then move away; it should continue following until contact.
5. Move above an attracted drop and confirm it follows upward.
6. Reach the max stacks of a stacking item (or set `pickup.autoUse=false` and
   fill the inventory), then attract another one: contact should remove it and
   award the configured conversion score once.
