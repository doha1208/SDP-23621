import java.awt.Color;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /** Starts a level with move.topLimit set to the value, holds W, returns the ship's y. */
    private static int highestWith(String topLimit) throws Exception {
        Path file = Files.createTempFile("ship", ".properties");
        Files.write(file, ("move.topLimit=" + topLimit + "\n").getBytes(StandardCharsets.UTF_8));
        System.setProperty("invaders.ship", file.toString());
        try {
            GameScreen screen = new GameScreen(new GameState(1, 0, 3, 0, 0, 0),
                new GameSettings(5, 2, 60, 2000), false, WIDTH, HEIGHT, 60);
            screen.initialize();
            press(KeyEvent.VK_W, true);
            frames(screen, 500);
            press(KeyEvent.VK_W, false);
            return ((Ship) get(screen, GameScreen.class, "ship")).getPositionY();
        } finally {
            System.clearProperty("invaders.ship");
            Files.delete(file);
        }
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

        // The top limit comes from res/ship.properties (move.topLimit).
        check(highestWith("0.25") == HEIGHT / 4, "move.topLimit=0.25 lets the ship reach a quarter from the top");
        check(highestWith("0") == 40, "move.topLimit=0 stops at the line under the score bar");
        check(highestWith("1") == startY, "move.topLimit=1 keeps the ship on its starting row");
        check(highestWith("abc") == top, "An invalid value falls back to the middle");
        check(highestWith("1.5") == top, "An out-of-range value falls back to the middle");

        System.out.println("ShipVerticalMoveTest: " + checks + " checks passed");
    }
}
