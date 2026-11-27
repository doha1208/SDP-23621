package item;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import engine.Core;
import engine.DrawManager;
import engine.Frame;
import engine.GameSettings;
import engine.GameState;
import entity.Ship;
import item.ItemAPI.*;
import screen.GameScreen;
import screen.Screen;

/** Real game rendering and pause integration. Run with a display and isolated save files. */
public final class ItemVisualGameTest {
    private static int checks, request;
    private static Field field(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); return f;
    }
    private static Object get(Object target, String name) throws Exception {
        return field(target.getClass(), name).get(target);
    }
    private static void call(GameScreen game, String name) throws Exception {
        Method method = GameScreen.class.getDeclaredMethod(name);
        method.setAccessible(true); method.invoke(game);
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void grant(ItemSystem items, String id) {
        check(items.api().tryGrant(new GrantRequest("visual-game-" + (++request), id,
            GrantSource.REWARD, GrantTiming.NOW)).isSuccess(), "Grant " + id);
    }
    private static void snapshot(File output, String name) throws Exception {
        if (output != null) ImageIO.write((BufferedImage) field(DrawManager.class, "backBuffer").get(null),
            "png", new File(output, name + ".png"));
    }
    private static void atlas(File output) throws Exception {
        if (output == null) return;
        BufferedImage image = new BufferedImage(640, 430, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(8, 14, 24)); g.fillRect(0, 0, 640, 430);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 18)); g.setColor(Color.WHITE);
        g.drawString("ITEM PIXEL ART / 13 KINDS", 22, 30);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        g.setColor(new Color(168, 191, 216));
        g.drawString("Native drop (18px) + HUD (13px) + enlarged silhouette", 22, 52);
        int index = 0;
        for (ItemInfo item : new ItemDefinitions().all()) {
            int x = 22 + index % 4 * 155, y = 78 + index / 4 * 85;
            ItemArtwork.drawIcon(g, item.effectKind, x, y, 18);
            ItemArtwork.drawIcon(g, item.effectKind, x + 28, y, 13);
            ItemArtwork.drawIcon(g, item.effectKind, x + 55, y - 4, 38);
            g.setColor(Color.WHITE); g.drawString(item.displayName, x, y + 52);
            index++;
        }
        g.dispose(); ImageIO.write(image, "png", new File(output, "item-icons.png"));
    }
    public static void main(String[] args) throws Exception {
        Core.getLogger().setUseParentHandlers(false);
        File output = args.length == 0 ? null : new File(args[0]);
        if (output != null) output.mkdirs();
        atlas(output);
        final Frame[] frame = new Frame[1];
        SwingUtilities.invokeAndWait(() -> {
            frame[0] = new Frame(448, 548);
            frame[0].setTitle("Invaders - Item feedback verification");
        });
        try {
            Core.getDrawManager().setFrame(frame[0]);
            GameSettings settings = (GameSettings) field(Core.class, "SETTINGS_LEVEL_1").get(null);
            GameScreen game = new GameScreen(new GameState(1, 0, 3, 0, 0, 0), settings,
                false, frame[0].getWidth(), frame[0].getHeight(), 60);
            game.initialize();
            field(Screen.class, "inputDelay").set(game, Core.getCooldown(0));
            Ship ship = (Ship) get(game, "ship");
            ItemSystem items = (ItemSystem) get(game, "items");
            items.update(ship, true);
            for (String id : new String[]{"rapid_fire", "bullet_speed", "spread", "pierce"}) grant(items, id);
            grant(items, "rapid_fire");
            ItemFeedback feedback = (ItemFeedback) get(items, "feedback");
            feedback.clear();
            grant(items, "shield");
            call(game, "draw"); snapshot(output, "game-item-received");
            check(items.getNotice().contains("Press Z"), "Pickup explains the real key");
            check(items.useSlot(0) == UseResult.USED, "Shield activates");
            call(game, "draw"); snapshot(output, "game-shield-active");
            check(items.getNotice().startsWith("Shield active"), "Using replaces the pickup instruction");
            for (String id : new String[]{"freeze", "boost", "score_boost"}) {
                grant(items, id);
                check(items.useSlot(0) == UseResult.USED, "Activate " + id);
            }
            for (String id : new String[]{"shield", "freeze", "boost"}) grant(items, id);
            check(items.api().getView().effects.size() == 8, "All eight gameplay effects are present");
            ItemManager manager = (ItemManager) get(items, "manager");
            ItemDropSystem drops = (ItemDropSystem) get(manager, "drops");
            ((Random) get(drops, "random")).setSeed(23621);
            for (int i = 0; i < 9; i++)
                items.api().onEnemyDefeated(DropSource.SPECIAL_ENEMY, 40 + i * 44, 165 + (i % 3) * 48);
            feedback.advance(0, new Bounds(ship.getPositionX(), ship.getPositionY(), ship.getWidth(), ship.getHeight()));
            feedback.action(EffectKind.FREEZE, "Freeze active", ItemArtwork.describe(items.api().getItemInfo("freeze"), null), false);
            feedback.pulse(EffectKind.FREEZE, null, true);
            feedback.advance(170, new Bounds(ship.getPositionX(), ship.getPositionY(), ship.getWidth(), ship.getHeight()));
            call(game, "draw"); snapshot(output, "game-all-item-states");
            // Pause is driven through GameScreen.update, not just a clock unit test.
            long duration = items.shield().remainingMillis;
            String notice = items.getNotice();
            field(GameScreen.class, "paused").setBoolean(game, true);
            field(GameScreen.class, "pauseStartedAt").setLong(game, System.currentTimeMillis());
            call(game, "update");
            Thread.sleep(120);
            call(game, "update");
            check(items.shield().remainingMillis == duration && items.getNotice().equals(notice),
                "Pause retains effect time and notification");
            snapshot(output, "game-item-pause");
            call(game, "resumeGameplay");
            call(game, "update");
            check(items.shield().remainingMillis == duration, "Resume discards paused wall time");
            items.useSlot(1);
            check(items.api().getView().slots.get(1) != null, "Already active rejection preserves the held item");
            check(items.getNotice().contains("already active"), "Rejection shows its cause");
            call(game, "draw"); snapshot(output, "game-item-rejected");
            // Optional short hold is for human/native screenshot inspection, not gameplay automation.
            if (args.length > 1 && args[1].equals("--hold")) Thread.sleep(45000);
            System.out.println("ItemVisualGameTest: " + checks + " checks passed");
        } finally {
            SwingUtilities.invokeAndWait(() -> frame[0].dispose());
        }
        System.exit(0);
    }
}
