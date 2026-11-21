import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import javax.swing.JPanel;

import engine.Core;
import engine.GameSettings;
import engine.GameState;
import entity.Bullet;
import entity.Ship;
import entity.enemy.EnemyBullet;
import entity.enemy.EnemyManager;
import screen.GameScreen;

/**
 * Dash dodge inside a real GameScreen (Team CS): Shift dashes the way the
 * ship last moved, once per press, and stops at the screen sides; while
 * it dashes, bullets do not hit the ship. Run from the project root,
 * headless.
 */
public final class DashDodgeTest {
    private static final int DASH_FRAMES = 11;
    private static final int DASH_DISTANCE = 110;
    private static final JPanel SOURCE = new JPanel();
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args)
            throws Exception {
        Method m = target.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private static void shift(boolean down) {
        KeyEvent key = new KeyEvent(SOURCE, down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED,
                0, 0, KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED);
        if (down) Core.getInputManager().keyPressed(key);
        else Core.getInputManager().keyReleased(key);
    }

    /** One frame of dash input; true if the dash moved the ship. */
    private static boolean frame(GameScreen game, boolean left, boolean right) throws Exception {
        return (Boolean) call(game, "updateDash", new Class[] { boolean.class, boolean.class },
                left, right);
    }

    /** Puts an enemy bullet and an old-style falling bullet on the ship's center. */
    private static EnemyBullet onShip(Ship ship, List<EnemyBullet> enemyBullets, Set<Bullet> bullets) {
        int x = ship.getPositionX() + ship.getWidth() / 2;
        int y = ship.getPositionY() + ship.getHeight() / 2;
        EnemyBullet aimed = new EnemyBullet(x, y, 0, 3);
        enemyBullets.add(aimed);
        bullets.add(new Bullet(x - 3, y - 5, 4));
        return aimed;
    }

    /** Runs frames until the dash would be ready again. */
    private static void recharge(GameScreen game) throws Exception {
        for (int i = 0; i < 200; i++) frame(game, false, false);
    }

    public static void main(String[] args) throws Exception {
        Core.getLogger().setUseParentHandlers(false);
        GameSettings settings = (GameSettings) field(Core.class, "SETTINGS_LEVEL_6").get(null);
        GameScreen game = new GameScreen(new GameState(6, 0, 3, 0, 0, 0), settings, false, 448, 520, 60);
        game.initialize();
        Ship ship = (Ship) field(GameScreen.class, "ship").get(game);

        // Shift with no arrow key: the ship dashes right (its default direction).
        int start = ship.getPositionX();
        check(!frame(game, false, false), "No dash before Shift");
        shift(true);
        int dashFrames = 0;
        while (frame(game, false, false)) dashFrames++;
        check(dashFrames == DASH_FRAMES, "Dash lasts " + DASH_FRAMES + " frames, got " + dashFrames);
        check(ship.getPositionX() == start + DASH_DISTANCE, "Dash moves 110 px right");

        // Holding Shift does not dash again.
        recharge(game);
        check(ship.getPositionX() == start + DASH_DISTANCE, "Holding Shift dashes once");

        // After moving left, a new press dashes left, even with the key let go.
        shift(false);
        frame(game, true, false);
        shift(true);
        check(frame(game, false, false), "Next press dashes again");
        while (frame(game, false, false)) { }
        check(ship.getPositionX() == start, "Dash goes the way the ship last moved");

        // A dash stops at the screen side.
        shift(false);
        recharge(game);
        ship.setPositionX(30);
        shift(true);
        frame(game, true, false);
        while (frame(game, true, false)) { }
        check(ship.getPositionX() == 1, "Dash stops at the left side, got " + ship.getPositionX());
        shift(false);
        recharge(game);
        ship.setPositionX(400);
        shift(true);
        frame(game, false, true);
        while (frame(game, false, true)) { }
        check(ship.getPositionX() == 448 - 1 - ship.getWidth(), "Dash stops at the right side");
        shift(false);

        // While dashing, bullets on the ship fly on and take no life.
        recharge(game);
        ship.setPositionX(200);
        int lives = (Integer) field(GameScreen.class, "lives").get(game);
        List<EnemyBullet> enemyBullets =
                ((EnemyManager) field(GameScreen.class, "enemyManager").get(game)).getBullets();
        @SuppressWarnings("unchecked")
        Set<Bullet> bullets = (Set<Bullet>) field(GameScreen.class, "bullets").get(game);
        shift(true);
        check(frame(game, false, false), "Dash started for the dodge check");
        EnemyBullet aimed = onShip(ship, enemyBullets, bullets);
        call(game, "manageCollisions", new Class[] {});
        check((Integer) field(GameScreen.class, "lives").get(game) == lives, "Dash dodges bullets");
        check(!ship.isDestroyed(), "Ship is not destroyed while dashing");
        check(enemyBullets.contains(aimed) && bullets.size() == 1, "Dodged bullets fly on");

        // Once the dash ends, the same kind of bullet hits again.
        while (frame(game, false, false)) { }
        enemyBullets.clear();
        bullets.clear();
        onShip(ship, enemyBullets, bullets);
        call(game, "manageCollisions", new Class[] {});
        check((Integer) field(GameScreen.class, "lives").get(game) == lives - 1, "Hit lands after the dash");
        check(ship.isDestroyed(), "Ship explodes after the dash");
        shift(false);

        System.out.println("PASS: " + checks + " checks");
    }
}
