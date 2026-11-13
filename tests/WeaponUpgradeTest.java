import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import engine.Core;
import engine.GameSettings;
import engine.GameState;
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
        items.beginLevel(1, 448, ship, HOOKS);
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

        // Rapid fire: same share of the base rate for every ship.
        Ship standard = new Ship(200, 500, Ship.ShipType.STANDARD);
        ItemSystem items = newRun(standard);
        check(pickUp(items, "rapid_fire", 6) == 6, "Rapid fire stacks six times");
        double slow = items.fireRateBonus(standard.getBaseFireRate()) / standard.getBaseFireRate();
        double fast = items.fireRateBonus(1000.0 / 375) / (1000.0 / 375);
        check(Math.abs(slow - fast) < 1e-9, "Rapid fire gives every ship the same ratio");
        check(Math.abs(slow - 0.35 * Math.log(7) / Math.log(2)) < 1e-9, "Rapid fire ratio at 6 stacks");

        // Bullet speed: +1 px/frame per stack, three stacks at most.
        check(pickUp(items, "bullet_speed", 4) == 3, "Bullet speed stops at three stacks");
        check(items.bulletSpeedBonus() == 3.0, "Bullet speed adds 1 px/frame per stack");
        items.update(standard, true);
        standard.setItemBonuses(0, items.bulletSpeedBonus());
        check(shootOnce(standard).iterator().next().getSpeed() == -9, "Bullet flies at 9 px/frame");

        // Spread: one more lane per stack, symmetric fan.
        Ship fan = new Ship(200, 500, Ship.ShipType.STANDARD);
        items = newRun(fan);
        check(shootOnce(fan).size() == 1, "No spread: one bullet");
        int[][] expected = {{-1, 1}, {-1, 0, 1}, {-2, -1, 1, 2}, {-2, -1, 0, 1, 2}};
        for (int stacks = 1; stacks <= 4; stacks++) {
            check(pickUp(items, "spread", 1) == 1, "Spread stack " + stacks);
            Ship ship = new Ship(200, 500, Ship.ShipType.STANDARD);
            items.update(ship, true);
            Set<Bullet> shot = shootOnce(ship);
            check(shot.size() == stacks + 1, "Spread " + stacks + " fires " + (stacks + 1) + " lanes");
            int[] sideways = new int[shot.size()];
            int i = 0;
            for (Bullet bullet : shot) sideways[i++] = bullet.getSpeedX();
            java.util.Arrays.sort(sideways);
            check(java.util.Arrays.equals(sideways, expected[stacks - 1]), "Fan lanes for " + stacks);
        }
        check(pickUp(items, "spread", 1) == 0, "Spread stops at four stacks");
        Ship twoWay = new Ship(200, 500, Ship.ShipType.TWO_WAY);
        items.update(twoWay, true);
        check(shootOnce(twoWay).size() == 10, "Two-way fires both barrels on every lane");

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
        Ship piercer = new Ship(200, 500, Ship.ShipType.STANDARD);
        items = newRun(piercer);
        check(pickUp(items, "pierce", 4) == 3, "Pierce stops at three stacks");
        items.update(piercer, true);
        check(shootOnce(piercer).iterator().next().getPierce() == 3, "Ship bullets carry pierce");
        items.endLevel();

        // Pierce in the game (wave enemies, Team Entities enemy overhaul).
        GameScreen screen = new GameScreen(new GameState(1, 0, 3, 0, 0, 0),
            new GameSettings(5, 2, 60, 2000), false, 448, 520, 60);
        screen.initialize();

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
