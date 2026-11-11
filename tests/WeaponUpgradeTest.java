import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import engine.Core;
import engine.GameSettings;
import engine.GameState;
import entity.Bullet;
import entity.BulletPool;
import entity.EnemyShip;
import entity.EnemyShipFormation;
import entity.Ship;
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

        // Pierce in the game: a 1-pierce bullet goes through one enemy and stops at the next.
        GameScreen screen = new GameScreen(new GameState(1, 0, 3, 0, 0, 0),
            new GameSettings(1, 3, 10, 1000), false, 448, 520, 60);
        screen.initialize();
        EnemyShipFormation formation = (EnemyShipFormation) get(screen, "enemyShipFormation");
        EnemyShip lower = null, upper = null;
        for (EnemyShip enemy : formation) {
            if (lower == null || enemy.getPositionY() > lower.getPositionY()) lower = enemy;
            if (upper == null || enemy.getPositionY() < upper.getPositionY()) upper = enemy;
        }
        Bullet shot = new Bullet(lower.getPositionX(), lower.getPositionY(), -4);
        shot.setPierce(1);
        bullets(screen).add(shot);
        call(screen, "manageCollisions");
        check(lower.isDestroyed() && bullets(screen).contains(shot), "Bullet pierces the first enemy");
        call(screen, "manageCollisions");
        check(bullets(screen).contains(shot), "Destroyed enemy is not hit again");
        shot.setPositionX(upper.getPositionX());
        shot.setPositionY(upper.getPositionY());
        call(screen, "manageCollisions");
        check(upper.isDestroyed() && !bullets(screen).contains(shot), "Second enemy stops the bullet");
        Bullet plain = new Bullet(0, 0, -4);
        check(plain.getPierce() == 0, "Bullets without the item do not pierce");

        System.out.println("PASS: " + checks + " checks");
    }
}
