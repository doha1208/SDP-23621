package item;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.util.EnumMap;
import java.util.Map;
import item.ItemAPI.*;

/** Shared pixel artwork and read-only item labels. No gameplay state lives here. */
public final class ItemArtwork {
    private static final Map<EffectKind, String[]> MASKS = new EnumMap<>(EffectKind.class);
    static {
        mask(EffectKind.LIFE, ".##...##.", "####.####", "#########", "#########", ".#######.", "..#####..", "...###...", "....#....", ".........");
        mask(EffectKind.SHIELD, ".#######.", "#########", "##.....##", "##.....##", "##..#..##", ".##.#.##.", "..#####..", "...###...", "....#....");
        mask(EffectKind.RAPID_FIRE, ".#..#..#.", "###.#####", "###.#####", ".#..#..#.", ".........", ".#..#..#.", ".#..#..#.", ".........", ".#..#..#.");
        mask(EffectKind.BULLET_SPEED, "....##...", "...##....", "..##.....", ".#######.", "....###..", "...###...", "..###....", ".##......", ".#.......");
        mask(EffectKind.SPREAD, "#...#...#", ".#..#..#.", "..#.#.#..", "...###...", "....#....", "....#....", "...###...", "...###...", ".........");
        mask(EffectKind.PIERCE, "....#....", "...###...", "..#####..", "....#....", "#########", "....#....", "#########", "....#....", "....#....");
        mask(EffectKind.FREEZE, "#...#...#", ".#..#..#.", "..#.#.#..", "...###...", "#########", "...###...", "..#.#.#..", ".#..#..#.", "#...#...#");
        mask(EffectKind.BOOST, "....#....", "...##....", "..###.#..", "..#####..", ".#######.", ".##...##.", ".##.#.##.", "..##.##..", "...###...");
        mask(EffectKind.SCORE_BOOST, "....#....", "...###...", ".#######.", "..#####..", "...###...", "..##.##..", ".##...##.", ".........", "..#####..");
        mask(EffectKind.EXTRA_SLOTS, ".........", ".###.###.", ".#.#.#.#.", ".###.###.", ".........", "...###...", "....#....", "...###...", ".........");
        mask(EffectKind.MAGNET, ".##...##.", ".##...##.", ".##...##.", ".##...##.", ".##...##.", ".###.###.", "..#####..", "...###...", ".........");
        mask(EffectKind.MAX_LIFE, ".##...##.", "####.####", "#########", ".#######.", "..#####..", "...###...", "....#....", ".......#.", "......###");
        mask(EffectKind.BULLET_POWER, "....#....", "...###...", "..#####..", "#.#####.#", "#.#####.#", "..#####..", "..#####..", ".#..#..#.", "#...#...#");
        mask(EffectKind.LONG_LASTING,".#######.", "..#...#..", "...#.#...", "....#....", "....#....", "...#.#...", "..#####..", ".#######.", ".........");
    }

    private ItemArtwork() { }

    private static void mask(EffectKind kind, String... rows) {
        MASKS.put(kind, rows);
    }

    /** An opaque dark tile and a distinct silhouette, still readable without colour. */
    public static void drawIcon(Graphics target, EffectKind kind, int x, int y, int size) {
        if (size < 11) return;
        Graphics g = target.create();
        try {
            g.clipRect(x, y, size, size);
            Color color = ItemSystem.colorOf(kind);
            g.setColor(new Color(10, 18, 29));
            g.fillRect(x, y, size, size);
            g.setColor(color.darker());
            g.drawRect(x, y, size - 1, size - 1);
            int pixel = Math.max(1, (size - 2) / 9);
            int left = x + (size - 9 * pixel) / 2;
            int top = y + (size - 9 * pixel) / 2;
            String[] rows = MASKS.get(kind);
            g.setColor(color);
            for (int row = 0; row < rows.length; row++)
                for (int col = 0; col < rows[row].length(); col++)
                    if (rows[row].charAt(col) == '#')
                        g.fillRect(left + col * pixel, top + row * pixel, pixel, pixel);
            g.setColor(new Color(238, 248, 255));
            g.fillRect(x + 2, y + 2, 1, 1);
        } finally {
            g.dispose();
        }
    }

    /** Keeps all inventory and effect indicators inside the existing y=43..59 HUD strip. */
    public static void drawHud(Graphics target, View view, int width, Font font) {
        Graphics g = target.create();
        try {
            g.clipRect(0, 42, width, 18);
            g.setColor(new Color(8, 14, 24));
            g.fillRect(0, 42, width, 18);
            g.setFont(font.deriveFont(9f));
            int x = 5;
            // Team Entities: with auto-use pickups the slots stay empty, so empty slots are not drawn.
            boolean anyStored = false;
            for (ItemInfo stored : view.slots) anyStored |= stored != null;
            for (int i = 0; anyStored && i < view.slots.size(); i++) {
                g.setColor(new Color(167, 185, 204));
                g.drawString(engine.ControlKeys.itemSlotLabel(i), x, 55);
                if (view.slots.get(i) == null) {
                    g.setColor(new Color(54, 68, 86));
                    g.drawRect(x + 9, 44, 14, 14);
                    g.drawLine(x + 13, 51, x + 19, 51);
                } else drawIcon(g, view.slots.get(i).effectKind, x + 9, 44, 15);
                x += 29;
            }
            int right = width - 5;
            FontMetrics fm = g.getFontMetrics();
            for (int i = view.effects.size() - 1; i >= 0; i--) {
                EffectView effect = view.effects.get(i);
                if (effect.item.activationMode == ActivationMode.EQUIPPED) continue;
                String text = effect.stacks != null ? "x" + effect.stacks
                    : effect.remainingMillis != null ? seconds(effect.remainingMillis) + "s" : "ON";
                int cell = 15 + fm.stringWidth(text) + 4;
                right -= cell;
                if (right < x + 3) {
                    // Narrow screens still expose the presence of further effects.
                    g.setColor(Color.LIGHT_GRAY);
                    g.drawString("+", x + 2, 55);
                    break;
                }
                drawIcon(g, effect.item.effectKind, right, 44, 13);
                g.setColor(new Color(228, 238, 249));
                g.drawString(text, right + 15, 54);
                double duration = effect.item.durationMillis == null ? 1 : effect.item.durationMillis;
                for (EffectView equipped : view.effects)
                    if (equipped.item.effectKind == EffectKind.LONG_LASTING)
                        duration += equipped.item.magnitude;
                double progress = effect.stacks != null && effect.item.maxStacks != null
                    ? (double) effect.stacks / effect.item.maxStacks
                    : effect.remainingMillis != null && effect.item.durationMillis != null
                    ? (double) effect.remainingMillis / duration : 1;
                g.setColor(ItemSystem.colorOf(effect.item.effectKind));
                g.fillRect(right + 15, 57, (int) Math.round((cell - 19) * Math.min(1, progress)), 2);
            }
        } finally {
            g.dispose();
        }
    }

    /** One short explanation; all variable numbers come from the current definition/state. */
    public static String describe(ItemInfo item, EffectView effect) {
        String time = seconds(effect != null && effect.remainingMillis != null
            ? effect.remainingMillis : item.durationMillis == null ? 0 : item.durationMillis) + "s";
        switch (item.effectKind) {
            case LIFE: return "Extra life; bonus points at the cap";
            case SHIELD: return "Block " + (effect != null && effect.remainingCharges != null
                ? effect.remainingCharges : item.charges) + " hit(s) / " + time;
            case FREEZE: return "Stop enemy movement / " + time;
            case BOOST: return "+" + percent(item.magnitude) + "% move + fire / " + time;
            case SCORE_BOOST: return "Score x" + number(item.magnitude) + " / " + time;
            case RAPID_FIRE: return "Faster fire / " + stack(item, effect);
            case BULLET_SPEED: return "Faster bullets / " + stack(item, effect);
            case SPREAD: return "More shot directions / " + stack(item, effect);
            case PIERCE: return "Pass through more enemies / " + stack(item, effect);
            case BULLET_POWER: return "Faster fire + pierce / " + stack(item, effect);
            default: return item.description;
        }
    }

    private static String stack(ItemInfo item, EffectView effect) {
        return "x" + (effect == null || effect.stacks == null ? 1 : effect.stacks)
            + "/" + item.maxStacks + " this run";
    }
    private static long seconds(long millis) { return millis <= 0 ? 0 : (millis - 1) / 1000 + 1; }
    private static long percent(double multiplier) { return Math.round((multiplier - 1) * 100); }
    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
