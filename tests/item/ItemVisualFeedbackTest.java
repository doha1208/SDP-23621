package item;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import engine.Core;
import entity.Ship;
import item.ItemAPI.*;

/** Presentation contracts and real item lifecycle regressions; no window or save fixtures required. */
public final class ItemVisualFeedbackTest {
    private static int checks;
    private static int request;
    private static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }
    private static final Bounds PLAYER = new Bounds(200, 450, 26, 16);
    private static final Font FONT = new Font(Font.MONOSPACED, Font.PLAIN, 10);

    private static String signature(BufferedImage image) {
        StringBuilder result = new StringBuilder();
        for (int y = 2; y < 11; y++)
            for (int x = 2; x < 11; x++) {
                int rgb = image.getRGB(x, y) & 0xFFFFFF;
                result.append(rgb != 0x0A121D ? '#' : '.');
            }
        return result.toString();
    }

    private static ItemFeedback feedback(ItemSystem items) throws Exception {
        Field field = ItemSystem.class.getDeclaredField("feedback");
        field.setAccessible(true);
        return (ItemFeedback) field.get(items);
    }

    private static void grant(ItemSystem items, String id) {
        check(items.api().tryGrant(new GrantRequest("visual-test-" + (++request), id,
            GrantSource.REWARD, GrantTiming.NOW)).isSuccess(), "Grant " + id);
    }

    public static void main(String[] args) throws Exception {
        Core.getLogger().setUseParentHandlers(false);
        Set<String> silhouettes = new HashSet<>();
        for (EffectKind kind : EffectKind.values()) {
            BufferedImage image = new BufferedImage(13, 13, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.MAGENTA);
            g.setFont(FONT);
            Rectangle clip = new Rectangle(0, 0, 13, 13);
            g.setClip(clip);
            ItemArtwork.drawIcon(g, kind, 0, 0, 13);
            check(g.getColor().equals(Color.MAGENTA) && g.getFont().equals(FONT)
                && g.getClipBounds().equals(clip), "Icon restores caller drawing state");
            check(silhouettes.add(signature(image)), "Distinct silhouette without relying on colour: " + kind);
            g.dispose();
        }
        ItemDefinitions catalog = new ItemDefinitions();
        List<EffectView> effects = new ArrayList<>();
        for (ItemInfo item : catalog.all())
            if (item.activationMode != ActivationMode.EQUIPPED && item.effectKind != EffectKind.LIFE)
                effects.add(new EffectView(effects.size() + 1, item,
                    item.durationMillis, item.charges, item.maxStacks == null ? null : item.maxStacks));
        List<ItemInfo> slots = Arrays.asList(catalog.find("shield"), catalog.find("freeze"),
            catalog.find("boost"), catalog.find("score_boost"));
        BufferedImage hud = new BufferedImage(448, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = hud.createGraphics();
        ItemArtwork.drawHud(g, new View(Collections.emptyList(), slots, effects, Collections.emptyList()), 448, FONT);
        check(hud.getRGB(2, 41) == 0 && hud.getRGB(2, 60) == 0, "Dense HUD stays clear of score and boss bar");
        // All eight active kinds fit beside four inventory slots at the real viewport.
        for (int i = 0; i < effects.size(); i++) {
            int right = 443;
            for (int j = effects.size() - 1; j >= i; j--) {
                EffectView e = effects.get(j);
                g.setFont(FONT.deriveFont(9f));
                String text = e.stacks != null ? "x" + e.stacks : ((e.remainingMillis - 1) / 1000 + 1) + "s";
                right -= 19 + g.getFontMetrics().stringWidth(text);
            }
            check(right >= 4 * 29 + 8, "Effect indicator is not hidden by inventory: " + effects.get(i).item.itemId);
        }
        g.dispose();

        Properties config = new Properties();
        config.setProperty("shield.durationMillis", "1900");
        config.setProperty("shield.charges", "2");
        ItemInfo tunedShield = new ItemDefinitions(new ItemBalance(config)).find("shield");
        check(ItemArtwork.describe(tunedShield, null).contains("2 hit(s) / 2s"), "Description uses tuned values");
        check(ItemArtwork.describe(tunedShield, new EffectView(1, tunedShield, 900L, 1)).contains("1 hit(s) / 1s"),
            "Active description uses remaining time and charges");

        ItemFeedback f = new ItemFeedback();
        f.advance(0, PLAYER);
        f.show(EffectKind.SHIELD, "Shield collected", "Press 1", false);
        f.pulse(EffectKind.SHIELD, PLAYER, false);
        BufferedImage a = new BufferedImage(448, 520, BufferedImage.TYPE_INT_ARGB);
        g = a.createGraphics();
        for (int i = 0; i < 100; i++) {
            f.drawNotice(g, 448, 520, FONT);
            f.drawPulses(g);
        }
        check(f.notice().contains("Press 1") && f.time() == 0, "Painting/pausing cannot consume notification time");
        f.action(EffectKind.SHIELD, "Shield active", "Block 1 hit", false);
        check(f.notice().startsWith("Shield active"), "Using an item immediately replaces its old pickup hint");
        f.advance(2400, PLAYER);
        check(f.notice() == null, "Consumed pickup notice cannot reappear after activation expires");

        View full = new View(Collections.singletonList(new DropView(1, tunedShield, PLAYER, true)),
            Arrays.asList(tunedShield, tunedShield, tunedShield), Collections.emptyList(), Collections.emptyList());
        f.checkFullInventory(full, PLAYER, true);
        check(f.notice().startsWith("Inventory full"), "Blocked contact explains how to free a slot");
        check(full.drops.size() == 1 && full.slots.size() == 3, "Warning leaves drop and inventory untouched");
        for (int i = 0; i < 200; i++) f.checkFullInventory(full, PLAYER, true);
        f.action(EffectKind.BOOST, "Boost active", "Temporary speed", false);
        f.advance(2400, PLAYER);
        check(f.notice() == null, "A successful action clears obsolete inventory warnings");
        f.clear();
        f.pulse(EffectKind.FREEZE, null, true);
        check(f.notice() == null, "Level reset clears presentation state");

        // Drive real game-facing API calls, then paint repeatedly and inspect the event queue.
        Ship ship = new Ship(200, 450, Ship.ShipType.STANDARD);
        ItemSystem items = ItemSystem.forLevel(1);
        items.beginLevel(1, 448, 520, ship, new ItemSystem.GameHooks() {
            public int getLives() { return 3; }
            public void addLife() { }
            public void addScore(int points) { }
        });
        items.update(ship, true);
        grant(items, "shield");
        check(items.getNotice().contains("Press 1"), "Direct grants explain the actual inventory key");
        check(items.useSlot(0) == UseResult.USED, "Shield use succeeds");
        check(items.getNotice().startsWith("Shield active"), "Real shield use receives active feedback");
        check(items.shield() != null && items.api().getView().slots.get(0) == null,
            "Feedback preserves shield application and slot consumption");
        g = a.createGraphics();
        for (int i = 0; i < 30; i++) {
            items.drawDrops(g);
            items.drawFeedback(g, ship);
            items.drawNotice(g, 448, 520, FONT);
        }
        g.dispose();
        List<ItemEvent> queued = items.drainEvents();
        check(queued.stream().filter(e -> e.type == EventType.ITEM_USED).count() == 1,
            "Painting preserves the queued item-use event for sound/achievements");
        check(queued.size() == 3, "Painting adds no duplicate grant/use/start events");
        long remaining = items.shield().remainingMillis;
        items.pauseClock();
        Thread.sleep(30);
        items.update(ship, true);
        check(items.shield().remainingMillis == remaining, "Paused elapsed time does not expire effects on resume");
        check(items.useSlot(0) == UseResult.EMPTY_SLOT, "Empty slot is still rejected");
        check(items.getNotice().contains("Slot 1 is empty"), "Failed use is immediately explained");
        feedback(items).clear();
        items.endLevel();
        check(items.getNotice() == null && items.shield() == null, "Level end clears effects and notices");
        System.out.println("ItemVisualFeedbackTest: " + checks + " checks passed");
    }
}
