import java.awt.Color;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import engine.Core;
import engine.GameSettings;
import engine.GameState;
import engine.GhostTrail;
import engine.InputManager;
import entity.Ship;
import screen.GameScreen;

/** Standalone checks for moving the ship up and down; runs headless. */
public final class ShipVerticalMoveTest {
    private static final int WIDTH = 448;
    private static final int HEIGHT = 520;
    private static int checks;

    private static Object get(Object target, Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void press(int key, boolean down) throws Exception {
        ((boolean[]) get(null, InputManager.class, "keys"))[key] = down;
    }

    private static void frames(GameScreen screen, int count) throws Exception {
        Method move = GameScreen.class.getDeclaredMethod("moveShipVertically");
        move.setAccessible(true);
        for (int i = 0; i < count; i++) move.invoke(screen);
    }

    public static void main(String[] args) throws Exception {
        Core.getLogger().setUseParentHandlers(false);
        GameScreen screen = new GameScreen(new GameState(1, 0, 3, 0, 0, 0),
            new GameSettings(5, 2, 60, 2000), false, WIDTH, HEIGHT, 60);
        screen.initialize();
        Ship ship = (Ship) get(screen, GameScreen.class, "ship");
        int startX = ship.getPositionX();
        int startY = ship.getPositionY();
        int top = HEIGHT / 2;

        frames(screen, 1);
        check(ship.getPositionY() == startY, "No key, no vertical move");

        press(KeyEvent.VK_W, true);
        frames(screen, 1);
        check(ship.getPositionY() == startY - ship.getSpeed(), "W moves up by the ship speed");
        frames(screen, 500);
        check(ship.getPositionY() == top, "Ship stops exactly at the middle of the screen");
        press(KeyEvent.VK_W, false);

        press(KeyEvent.VK_DOWN, true);
        frames(screen, 1);
        check(ship.getPositionY() == top + ship.getSpeed(), "Down arrow moves down");
        frames(screen, 500);
        check(ship.getPositionY() == startY, "Ship stops at its starting row");
        press(KeyEvent.VK_DOWN, false);

        press(KeyEvent.VK_UP, true);
        frames(screen, 3);
        check(ship.getPositionY() == startY - 3 * ship.getSpeed(), "Up arrow moves up");
        press(KeyEvent.VK_UP, false);
        press(KeyEvent.VK_S, true);
        frames(screen, 3);
        check(ship.getPositionY() == startY, "S moves back down");
        press(KeyEvent.VK_S, false);
        check(ship.getPositionX() == startX, "Vertical moves leave x alone");

        // A ship moved by an odd step still lands exactly on the limits.
        ship.setPositionY(top + 1);
        ship.moveUp(top);
        check(ship.getPositionY() == top, "moveUp clamps to the top limit");
        ship.setPositionY(startY - 1);
        ship.moveDown(startY);
        check(ship.getPositionY() == startY, "moveDown clamps to the bottom limit");

        // The ghost trail shows when the ship only moves up or down.
        GhostTrail trail = new GhostTrail();
        for (int frame = 0; frame < 20; frame++)
            trail.record(100, startY - frame * 2, Color.GREEN);
        check(trail.isMoving(), "Ghost trail counts vertical movement");
        trail.clear();
        for (int frame = 0; frame < 20; frame++)
            trail.record(100, startY, Color.GREEN);
        check(!trail.isMoving(), "Ghost trail stays off while the ship is still");

        System.out.println("ShipVerticalMoveTest: " + checks + " checks passed");
    }
}
