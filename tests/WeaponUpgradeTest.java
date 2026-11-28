import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import engine.Core;
import engine.GameSettings;
import engine.GameState;
import engine.soundEngine.SoundManager;
import entity.Bullet;
import entity.BulletPool;
import entity.Ship;
import entity.enemy.Enemy;
import entity.enemy.EnemyBalance;
import entity.enemy.EnemyManager;
import entity.enemy.EnemyType;
import entity.enemy.MovePattern;
import entity.enemy.SpawnRequest;
import item.ItemAPI;
import item.ItemSystem;
import screen.GameScreen;
import screen.Screen;

/**
 * Standalone checks for the bullet upgrade items (Team CS): spread, pierce,
 * rapid fire and bullet speed. Run from the project root so the balance file
 * in res/ is used.
 */
public final class WeaponUpgradeTest {
    private static int checks;
    private static int requests;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void call(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static final ItemSystem.GameHooks HOOKS = new ItemSystem.GameHooks() {
        public int getLives() { return 3; }
        public void addLife() { }
        public void addScore(int points) { }
    };

    /** A new run with one active level for the given ship. */
    private static ItemSystem newRun(Ship ship) {
        ItemSystem items = ItemSystem.forLevel(1);
        items.beginLevel(1, 448, 520, ship, HOOKS);
        return items;
    }

    /** Picks up an item the given number of times; returns how many pickups were applied. */
    private static int pickUp(ItemSystem items, String itemId, int times) {
        int applied = 0;
        for (int i = 0; i < times; i++) {
            ItemAPI.GrantResult result = items.api().tryGrant(new ItemAPI.GrantRequest(
                "test-" + (++requests), itemId, ItemAPI.GrantSource.REWARD, ItemAPI.GrantTiming.NOW));
            if (result.isSuccess()) applied++;
        }
        return applied;
    }

    /** Upgrades (spread, bullet power) dropped since the last drain. */
    private static int spawnedUpgrades(ItemSystem items) {
        int count = 0;
        for (ItemAPI.ItemEvent event : items.drainEvents())
            if (event.type == ItemAPI.EventType.ITEM_SPAWNED
                && ("spread".equals(event.itemId) || "bullet_power".equals(event.itemId))) count++;
        return count;
    }

    private static Set<Bullet> shootOnce(Ship ship) {
        Set<Bullet> bullets = new HashSet<Bullet>();
        check(ship.shoot(bullets), "Ship shoots");
        return bullets;
    }

    private static EnemyManager waveManager(GameScreen screen, long seed) throws Exception {
        EnemyManager manager = new EnemyManager(448, 520, 40, EnemyBalance.defaults(), new Random(seed));
        Field field = GameScreen.class.getDeclaredField("enemyManager");
        field.setAccessible(true);
        field.set(screen, manager);
        return manager;
    }

    /** Moves enemies until the given one is on screen and can be hit. */
    private static Enemy bringIn(EnemyManager manager, Enemy enemy) {
        for (int frame = 0; frame < 600 && !(enemy.isHittable() && enemy.isOnScreen()
            && enemy.getCenterY() > 70); frame++)
            manager.updateEnemies(false, 224, 490);
        check(enemy.isHittable() && enemy.isOnScreen(), "Enemy enters the screen");
        return enemy;
    }

    private static int downed(List<Enemy> enemies) {
        int count = 0;
        for (Enemy enemy : enemies) if (enemy.isDestroyed()) count++;
        return count;
    }

    @SuppressWarnings("unchecked")
    private static Set<Bullet> bullets(GameScreen screen) throws Exception {
        return (Set<Bullet>) get(screen, "bullets");
    }

    public static void main(String[] args) throws Exception {
        Core.getLogger().setUseParentHandlers(false);
        SoundManager.getInstance().setMuted(true);

        // Bullet power's faster fire: same share of the base rate for every ship.
        Ship standard = new Ship(200, 500, Ship.ShipType.STANDARD);
        ItemSystem items = newRun(standard);
        check(pickUp(items, "bullet_power", 6) == 6, "Bullet power stacks six times");
        double slow = items.fireRateBonus(standard.getBaseFireRate()) / standard.getBaseFireRate();
        double fast = items.fireRateBonus(1000.0 / 375) / (1000.0 / 375);
        check(Math.abs(slow - fast) < 1e-9, "Faster fire gives every ship the same ratio");
        check(Math.abs(slow - 0.35 * Math.log(7) / Math.log(2)) < 1e-9, "Fire rate ratio at 6 stacks");
        check(items.bulletSpeedBonus() == 0, "No item changes bullet speed");
        for (String gone : new String[]{"rapid_fire", "bullet_speed", "pierce"})
            check(ItemAPI.EffectKind.BULLET_POWER.isRunUpgrade() && pickUp(items, gone, 1) == 0,
                "Old upgrade " + gone + " no longer exists");

        // Spread (Team Entities): 1 two front shots, 2 diagonal pair, 3 three front shots,
        // 4 back shot, 5 aimed shot, 6 wider diagonal pair. Left and right stay symmetric.
        Ship fan = new Ship(200, 500, Ship.ShipType.STANDARD);
        items = newRun(fan);
        check(shootOnce(fan).size() == 1, "No spread: one bullet");
        int[] fronts = {2, 2, 3, 3, 3, 3};
        int[] totals = {2, 4, 5, 6, 7, 9};
        // Nearest enemy up and to the right: the aimed shot goes there.
        List<java.awt.Point> targets = java.util.Arrays.asList(
            new java.awt.Point(400, 100), new java.awt.Point(313, 300));
        for (int stacks = 1; stacks <= 6; stacks++) {
            check(pickUp(items, "spread", 1) == 1, "Spread stack " + stacks);
            Ship ship = new Ship(200, 500, Ship.ShipType.STANDARD);
            ship.setAimTargets(() -> targets);
            items.update(ship, true);
            Set<Bullet> shot = shootOnce(ship);
            int centerX = ship.getPositionX() + ship.getWidth() / 2;
            int front = 0, back = 0, aimed = 0, sideSum = 0;
            List<Integer> frontX = new java.util.ArrayList<Integer>();
            List<Integer> diagonals = new java.util.ArrayList<Integer>();
            for (Bullet bullet : shot) {
                check(bullet.isPlayerShot(), "Spread bullets are the player's");
                if (bullet.getSpeed() > 0) { back++; continue; }
                if (bullet.getSpeedX() == 0) {
                    front++;
                    frontX.add(bullet.getPositionX() + bullet.getWidth() / 2 - centerX);
                } else if (bullet.getSpeed() != -6) {
                    // Aimed at (313, 300) from the ship's top center: right and up.
                    double want = Math.atan2(300 - ship.getPositionY(), 313 - centerX);
                    double got = Math.atan2(bullet.getSpeed(), bullet.getSpeedX());
                    check(Math.abs(want - got) < 0.2, "Aimed shot heads for the nearest enemy");
                    aimed++;
                } else {
                    diagonals.add(bullet.getSpeedX());
                    sideSum += bullet.getSpeedX();
                }
            }
            check(shot.size() == totals[stacks - 1], "Spread " + stacks + " fires " + totals[stacks - 1]);
            check(front == fronts[stacks - 1], "Front shots at spread " + stacks);
            int frontSum = 0;
            for (int x : frontX) frontSum += x;
            check(frontSum == 0, "Front shots centered at spread " + stacks);
            check(sideSum == 0, "Diagonals symmetric at spread " + stacks);
            check(diagonals.size() == (stacks >= 6 ? 4 : stacks >= 2 ? 2 : 0), "Diagonals at spread " + stacks);
            check(back == (stacks >= 4 ? 1 : 0), "Back shot at spread " + stacks);
            check(aimed == (stacks >= 5 ? 1 : 0), "Aimed shot at spread " + stacks);
        }
        check(pickUp(items, "spread", 1) == 0, "Spread stops at six stacks");
        Ship lonely = new Ship(200, 500, Ship.ShipType.STANDARD);
        items.update(lonely, true);
        int straightUp = 0;
        for (Bullet bullet : shootOnce(lonely))
            if (bullet.getSpeedX() == 0 && bullet.getSpeed() < 0) straightUp++;
        check(straightUp == 4, "No enemy: the aimed shot goes straight up");
        Ship twoWay = new Ship(200, 500, Ship.ShipType.TWO_WAY);
        items.update(twoWay, true);
        check(shootOnce(twoWay).size() == 12, "Two-way fires both barrels on every front shot");

        // Pierce: bullet state.
        Bullet bullet = BulletPool.getBullet(0, 0, -6);
        bullet.setPierce(1);
        Object first = new Object(), second = new Object();
        check(bullet.registerHit(first) && bullet.piercesThrough(), "First enemy is pierced");
        check(!bullet.registerHit(first), "Same enemy is not hit twice");
        check(!bullet.piercesThrough(), "No new hit, no pierce");
        check(bullet.registerHit(second) && !bullet.piercesThrough(), "Second enemy stops a 1-pierce bullet");
        Set<Bullet> used = new HashSet<Bullet>();
        used.add(bullet);
        BulletPool.recycle(used);
        Bullet reused = BulletPool.getBullet(0, 0, -6);
        check(reused.getPierce() == 0 && reused.registerHit(first), "Pooled bullet forgets pierce state");

        // Bullet power (Team Entities): rapid fire every stack, pierce every second stack, six stacks at most.
        Ship powered = new Ship(200, 500, Ship.ShipType.STANDARD);
        items = newRun(powered);
        int[] pierceAt = {0, 1, 1, 2, 2, 3};
        for (int stacks = 1; stacks <= 6; stacks++) {
            check(pickUp(items, "bullet_power", 1) == 1, "Bullet power stack " + stacks);
            check(Math.abs(items.fireRateBonus(1.0) - 0.35 * Math.log(1 + stacks) / Math.log(2)) < 1e-9,
                "Bullet power fire rate at " + stacks);
            check(items.pierceCount() == pierceAt[stacks - 1], "Bullet power pierce at " + stacks);
        }
        check(pickUp(items, "bullet_power", 1) == 0, "Bullet power stops at six stacks");
        items.update(powered, true);
        check(shootOnce(powered).iterator().next().getPierce() == 3, "Bullet power reaches the ship's bullets");
        items.endLevel();

        // Upgrade catch-up (Team Entities): stage 1 wants one upgrade by the time it is half over.
        Ship catcher = new Ship(200, 500, Ship.ShipType.STANDARD);
        Ship victim = new Ship(200, 200, Ship.ShipType.STANDARD);
        items = newRun(catcher);
        items.drainEvents();
        items.setStageProgress(0.9);
        items.onEnemyDefeated(victim, false);
        check(spawnedUpgrades(items) == 1, "Late in a stage without an upgrade, a kill drops one");
        items.onEnemyDefeated(victim, false);
        check(spawnedUpgrades(items) == 1, "A drop that was not caught keeps the catch-up going");
        check(pickUp(items, "spread", 1) == 1, "Catch the upgrade");
        items.drainEvents();
        int afterFloor = 0;
        for (int kill = 0; kill < 30; kill++) {
            items.onEnemyDefeated(victim, false);
            afterFloor += spawnedUpgrades(items);
        }
        check(afterFloor < 15, "Floor met: back to the normal upgrade chance");
        items.endLevel();
        items = newRun(catcher);
        items.drainEvents();
        items.setStageProgress(0.2);
        int early = 0;
        for (int kill = 0; kill < 30; kill++) {
            items.onEnemyDefeated(victim, false);
            early += spawnedUpgrades(items);
        }
        check(early < 15, "Early in the stage: no catch-up yet");
        items.setStageProgress(-1);
        int unknown = 0;
        for (int kill = 0; kill < 30; kill++) {
            items.onEnemyDefeated(victim, false);
            unknown += spawnedUpgrades(items);
        }
        check(unknown < 15, "Unknown progress (boss stage, timed run): no catch-up");
        items.endLevel();

        // Pierce in the game (wave enemies, Team Entities enemy overhaul).
        GameScreen screen = new GameScreen(new GameState(1, 0, 3, 0, 0, 0),
            new GameSettings(5, 2, 60, 2000), false, 448, 520, 60);
        screen.initialize();
        Field inputDelay = Screen.class.getDeclaredField("inputDelay");
        inputDelay.setAccessible(true);
        inputDelay.set(screen, Core.getCooldown(0));

        // The spread back shot flies down through the ship without hurting it (Team Entities).
        Ship player = (Ship) get(screen, "ship");
        Bullet backShot = BulletPool.getBullet(player.getPositionX() + 4, player.getPositionY(), 6);
        backShot.setPlayerShot(true);
        bullets(screen).add(backShot);
        int livesBefore = (Integer) get(screen, "lives");
        call(screen, "manageCollisions");
        check((Integer) get(screen, "lives") == livesBefore, "Back shot does not hit the player");
        bullets(screen).remove(backShot);
        Bullet enemyShot = BulletPool.getBullet(player.getPositionX() + 4, player.getPositionY(), 6);
        check(!enemyShot.isPlayerShot(), "Unmarked downward bullet is an enemy bullet");
        bullets(screen).add(enemyShot);
        call(screen, "manageCollisions");
        check((Integer) get(screen, "lives") == livesBefore - 1, "Enemy bullet still hits the player");
        bullets(screen).clear();

        // A 1-pierce bullet goes through one enemy of a column and stops at the next.
        EnemyManager manager = waveManager(screen, 15);
        List<Enemy> column = manager.spawn(
            SpawnRequest.of(EnemyType.NORMAL, MovePattern.STRAIGHT_DOWN), 224, 490);
        bringIn(manager, column.get(column.size() - 1));
        check(column.size() >= 3, "Column has at least three enemies");
        Bullet shot = new Bullet((int) column.get(0).getCenterX() - Bullet.DEFAULT_WIDTH / 2,
            40, -4, Bullet.DEFAULT_WIDTH, 480);
        shot.setPierce(1);
        bullets(screen).add(shot);
        call(screen, "manageCollisions");
        check(downed(column) == 1 && bullets(screen).contains(shot), "Bullet pierces the first enemy");
        call(screen, "manageCollisions");
        check(downed(column) == 2 && !bullets(screen).contains(shot), "Second enemy stops the bullet");
        call(screen, "manageCollisions");
        check(downed(column) == 2, "Stopped bullet hits nothing more");

        // A piercing bullet damages a tough enemy once and does not hit it again.
        manager = waveManager(screen, 11);
        Enemy tank = bringIn(manager, manager.spawn(
            SpawnRequest.of(EnemyType.TANK, MovePattern.STRAIGHT_DOWN), 224, 490).get(0));
        int health = tank.getHealth();
        Bullet through = new Bullet(tank.getPositionX(), tank.getPositionY(), -4);
        through.setPierce(1);
        bullets(screen).add(through);
        call(screen, "manageCollisions");
        call(screen, "manageCollisions");
        check(tank.getHealth() == health - 1 && bullets(screen).contains(through),
            "Tough enemy is hit once and pierced");
        Bullet plain = new Bullet(0, 0, -4);
        check(plain.getPierce() == 0, "Bullets without the item do not pierce");

        System.out.println("PASS: " + checks + " checks");
    }
}
