import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import engine.Core;
import engine.CoinDropManager;
import engine.GameSettings;
import engine.GameState;
import engine.DrawManager.SpriteType;
import entity.Bullet;
import entity.EnemyShip;
import entity.EnemyShipFormation;
import screen.GameScreen;

/** Standalone regression checks; run with isolated build/profile files. */
public final class TwoHitEnemyTest {
    private static int checks;

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

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    @SuppressWarnings("unchecked")
    private static Set<Bullet> bullets(GameScreen screen) throws Exception {
        return (Set<Bullet>) get(screen, "bullets");
    }

    private static void hit(GameScreen screen, EnemyShip enemy) throws Exception {
        bullets(screen).add(new Bullet(enemy.getPositionX(), enemy.getPositionY(), -4));
        call(screen, "manageCollisions");
    }

    public static void main(String[] args) throws Exception {
        Core.getLogger().setUseParentHandlers(false);
        for (int level = 1; level <= 10; level++) {
            Field field = Core.class.getDeclaredField("SETTINGS_LEVEL_" + level);
            field.setAccessible(true);
            GameSettings settings = (GameSettings) field.get(null);
            check(settings.isDiagonalMovement() == (level == 3 || level == 9),
                "Diagonal stage placement " + level);
            EnemyShipFormation formation = new EnemyShipFormation(settings);
            int elites = 0;
            int ordinary = 0;
            for (EnemyShip enemy : formation) {
                if (enemy.getHealth() == 2) elites++;
                else ordinary++;
            }
            check(elites == (level == 6 ? 4 : level == 9 ? 7 : 0),
                "Stage placement " + level);
            check(ordinary + elites == settings.getFormationWidth()
                * settings.getFormationHeight(), "Formation size " + level);
        }
        for (int level : new int[] {6, 9}) {
            Field field = Core.class.getDeclaredField("SETTINGS_LEVEL_" + level);
            field.setAccessible(true);
            GameSettings settings = (GameSettings) field.get(null);
            Set<String> layouts = new java.util.HashSet<>();
            boolean upperRowElite = false;
            for (int attempt = 0; attempt < 50; attempt++) {
                int elites = 0;
                StringBuilder layout = new StringBuilder();
                for (EnemyShip enemy : new EnemyShipFormation(settings)) {
                    if (enemy.getHealth() == 2) {
                        elites++;
                        layout.append(enemy.getPositionX()).append(',')
                            .append(enemy.getPositionY()).append(';');
                        upperRowElite |= enemy.getPositionY()
                            < 100 + 40 * (settings.getFormationHeight() - 1);
                    }
                }
                check(elites == (level == 6 ? 4 : 7), "Exact random count " + level);
                layouts.add(layout.toString());
            }
            check(layouts.size() > 1, "Layouts vary across starts " + level);
            check(upperRowElite, "Selection includes upper rows " + level);
        }
        EnemyShip strongHit = new EnemyShip(0, 0, SpriteType.EnemyShipA1, 2);
        check(!strongHit.takeDamage(0), "Zero damage must not kill");
        check(strongHit.takeDamage(2), "Damage amount must be respected");
        check(!strongHit.takeDamage(1), "Lethal hit cannot be repeated");

        GameSettings combined = new GameSettings(3, 2, 10, 1000, true, 2);
        check(combined.isDiagonalMovement() && combined.getTwoHitEnemyCount() == 2,
            "Both settings can be enabled together");
        EnemyShipFormation combinedFormation = new EnemyShipFormation(combined);
        combinedFormation.attach(new screen.Screen(448, 520, 60));
        java.util.Map<EnemyShip, int[]> positions = new java.util.HashMap<>();
        int combinedElites = 0;
        for (EnemyShip enemy : combinedFormation) {
            positions.put(enemy, new int[] {enemy.getPositionX(), enemy.getPositionY()});
            if (enemy.getHealth() == 2) combinedElites++;
        }
        check(combinedElites == 2, "Combined formation has two elites");
        for (int frame = 0; frame < 20; frame++) combinedFormation.update();
        for (EnemyShip enemy : combinedFormation) {
            int[] before = positions.get(enemy);
            check(enemy.getPositionX() - before[0] == 8
                && enemy.getPositionY() - before[1] == 4,
                "Combined formation moves diagonally with spacing preserved");
        }

        // GameScreen no longer builds a formation in normal stages (wave-based
        // enemies replaced it), so multi-hit, reward and wide-bullet checks for
        // the game screen live in EnemyOverhaulTest.
        System.out.println("PASS: " + checks + " checks");
    }
}
