package entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import engine.Core;
import entity.Ship.ShipType;

/**
 * Standalone checks for the per-ship stats (Team CS): the values in
 * res/ship-stats.properties, how invalid values are handled, and that every
 * ship shoots with its own bullet speed, bullet count and bullet size. Run
 * from the project root so res/ship-stats.properties is used.
 */
public final class ShipStatsTest {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    /** speed, shootingInterval, bulletSpeed, bulletsPerShot, bulletWidth, bulletHeight. */
    private static void expectStats(ShipType type, int... expected) {
        int[] actual = { type.getSpeed(), type.getShootingInterval(),
            type.getBulletSpeed(), type.getBulletsPerShot(),
            type.getBulletWidth(), type.getBulletHeight() };
        for (int i = 0; i < expected.length; i++)
            check(actual[i] == expected[i], type + " stat " + i + " is "
                + actual[i] + ", expected " + expected[i]);
    }

    /** One shot from a fresh ship, bullets sorted from left to right. */
    private static List<Bullet> shoot(Ship ship) {
        Set<Bullet> bullets = new HashSet<Bullet>();
        check(ship.shoot(bullets), ship.getType() + " shoots");
        List<Bullet> sorted = new ArrayList<Bullet>(bullets);
        sorted.sort((a, b) -> Integer.compare(a.getPositionX(), b.getPositionX()));
        return sorted;
    }

    private static int centerOf(Bullet bullet) {
        return bullet.getPositionX() + bullet.getWidth() / 2;
    }

    public static void main(String[] args) {
        Core.getLogger().setUseParentHandlers(false);

        // The shipped file keeps every ship exactly as it played before.
        expectStats(ShipType.STANDARD, 2, 750, 6, 1, 6, 10);
        expectStats(ShipType.FAST_MOVE, 4, 750, 6, 1, 6, 10);
        expectStats(ShipType.TWO_WAY, 1, 1000, 6, 2, 6, 10);
        expectStats(ShipType.FAST_ATTACK, 2, 375, 6, 1, 6, 10);
        expectStats(ShipType.BIG_BULLET, 2, 1000, 6, 1, 18, 30);

        // Parsing: only whole numbers above 0 replace the default.
        Properties settings = new Properties();
        settings.setProperty("ok", "5");
        settings.setProperty("spaces", " 7 ");
        settings.setProperty("blank", " ");
        settings.setProperty("zero", "0");
        settings.setProperty("negative", "-3");
        settings.setProperty("text", "fast");
        settings.setProperty("decimal", "2.5");
        check(ShipStats.positive(settings, "ok", 1) == 5, "Valid value is used");
        check(ShipStats.positive(settings, "spaces", 1) == 7, "Value is trimmed");
        check(ShipStats.positive(settings, "missing", 3) == 3, "Missing key keeps default");
        for (String key : new String[] { "blank", "zero", "negative", "text", "decimal" })
            check(ShipStats.positive(settings, key, 4) == 4, "Invalid " + key + " keeps default");

        // Shooting follows each type's stats.
        for (ShipType type : ShipType.values()) {
            Ship ship = new Ship(200, 400, type);
            List<Bullet> bullets = shoot(ship);
            check(bullets.size() == type.getBulletsPerShot(), type + " bullet count");
            int center = ship.getPositionX() + ship.getWidth() / 2;
            int sum = 0;
            for (Bullet bullet : bullets) {
                check(bullet.getSpeed() == -type.getBulletSpeed(), type + " bullet speed");
                check(bullet.getWidth() == type.getBulletWidth()
                    && bullet.getHeight() == type.getBulletHeight(), type + " bullet size");
                sum += centerOf(bullet) - center;
            }
            check(sum == 0, type + " barrels are symmetric around the center");
        }
        List<Bullet> twoWay = shoot(new Ship(200, 400, ShipType.TWO_WAY));
        check(centerOf(twoWay.get(1)) - centerOf(twoWay.get(0)) == 16,
            "Two-way barrels stay 16 px apart");

        // Items still stack on top of the ship's own stats.
        Ship boosted = new Ship(200, 400, ShipType.FAST_ATTACK);
        boosted.setItemBonuses(0, 2);
        check(shoot(boosted).get(0).getSpeed() == -(6 + 2), "Bullet speed item adds to the ship's speed");
        Ship spread = new Ship(200, 400, ShipType.TWO_WAY);
        spread.setWeaponMods(1, 0);
        check(shoot(spread).size() == 4, "Spread doubles every barrel's shot");

        System.out.println("PASS: " + checks + " checks");
    }
}
