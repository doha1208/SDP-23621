package item;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import item.ItemAPI.*;

/** Small, bounded presentation state advanced only by game time, never by painting. */
final class ItemFeedback {
    private static final long NOTICE_TIME = 2400;
    private static final long PULSE_TIME = 550;
    private final Deque<Notice> notices = new ArrayDeque<>();
    private final List<Pulse> pulses = new ArrayList<>();
    private long time;
    private long lastWarning = -2000;
    private Bounds player;

    private static final class Notice {
        final EffectKind kind;
        final String title, detail;
        final boolean warning;
        long remaining = NOTICE_TIME;
        Notice(EffectKind kind, String title, String detail, boolean warning) {
            this.kind = kind; this.title = title; this.detail = detail; this.warning = warning;
        }
    }
    private static final class Pulse {
        final EffectKind kind;
        final double x, y;
        final boolean activation;
        long age;
        Pulse(EffectKind kind, Bounds bounds, boolean activation) {
            this.kind = kind; x = bounds.x + bounds.width / 2; y = bounds.y + bounds.height / 2;
            this.activation = activation;
        }
    }

    void advance(long delta, Bounds bounds) {
        player = bounds;
        time += delta;
        if (!notices.isEmpty()) {
            notices.peek().remaining -= delta;
            if (notices.peek().remaining <= 0) notices.remove();
        }
        for (Pulse pulse : pulses) pulse.age += delta;
        pulses.removeIf(pulse -> pulse.age >= PULSE_TIME);
    }

    void clear() {
        notices.clear();
        pulses.clear();
        player = null;
        lastWarning = time - 2000;
    }

    long time() { return time; }

    void show(EffectKind kind, String title, String detail, boolean warning) {
        enqueue(kind, title, detail, warning, false);
    }

    void action(EffectKind kind, String title, String detail, boolean warning) {
        enqueue(kind, title, detail, warning, true);
    }

    private void enqueue(EffectKind kind, String title, String detail, boolean warning, boolean first) {
        if (warning && time - lastWarning < 1600) return;
        if (warning) lastWarning = time;
        for (Notice notice : notices)
            if (notice.title.equals(title) && notice.detail.equals(detail)) return;
        if (first) notices.removeIf(notice -> notice.kind == kind || (!warning && notice.warning));
        if (notices.size() == 3) notices.removeLast();
        Notice next = new Notice(kind, title, detail, warning);
        if (first) notices.addFirst(next); else notices.addLast(next);
    }

    void pulse(EffectKind kind, Bounds source, boolean activation) {
        Bounds bounds = source == null ? player : source;
        if (bounds == null) return;
        if (pulses.size() == 8) pulses.remove(0);
        pulses.add(new Pulse(kind, bounds, activation));
    }

    String notice() {
        return notices.isEmpty() ? null : notices.peek().title + " - " + notices.peek().detail;
    }

    /** A blocked pickup remains in the world; only the explanation is added here. */
    void checkFullInventory(View view, Bounds bounds, boolean available) {
        if (!available || view.slots.contains(null)) return;
        for (DropView drop : view.drops) {
            Bounds b = drop.bounds;
            if (drop.item.activationMode == ActivationMode.MANUAL
                && bounds.x <= b.x + b.width && bounds.x + bounds.width >= b.x
                && bounds.y <= b.y + b.height && bounds.y + bounds.height >= b.y) {
                action(drop.item.effectKind, "Inventory full", "Use 1-" + view.slots.size() + " to free a slot", true);
                return;
            }
        }
    }

    void drawNotice(Graphics target, int width, int height, Font font) {
        if (notices.isEmpty()) return;
        Notice notice = notices.peek();
        Graphics g = target.create();
        try {
            int left = 9, top = height - 89, panelWidth = width - 18;
            g.clipRect(left, top, panelWidth, 41);
            g.setColor(new Color(8, 15, 26, 235));
            g.fillRect(left, top, panelWidth, 41);
            Color accent = notice.warning ? new Color(255, 184, 83) : ItemSystem.colorOf(notice.kind);
            g.setColor(accent);
            g.fillRect(left, top, 2, 41);
            ItemArtwork.drawIcon(g, notice.kind, left + 8, top + 9, 22);
            g.setFont(font);
            g.setColor(new Color(239, 246, 255));
            drawFitted(g, notice.title, left + 39, top + 15, panelWidth - 45);
            g.setColor(notice.warning ? accent : new Color(168, 191, 216));
            drawFitted(g, notice.detail, left + 39, top + 30, panelWidth - 45);
            g.setColor(accent);
            g.fillRect(left + 3, top + 39, (int) ((panelWidth - 4) * notice.remaining / NOTICE_TIME), 2);
        } finally {
            g.dispose();
        }
    }

    private static void drawFitted(Graphics g, String text, int x, int y, int width) {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(text) > width) {
            while (!text.isEmpty() && fm.stringWidth(text + "...") > width)
                text = text.substring(0, text.length() - 1);
            text += "...";
        }
        g.drawString(text, x, y);
    }

    /** Pickup sparks stay at the pickup; activation bursts originate around the player. */
    void drawPulses(Graphics target) {
        Graphics g = target.create();
        try {
            for (Pulse pulse : pulses) {
                double progress = pulse.age / (double) PULSE_TIME;
                int radius = (int) (9 + progress * (pulse.activation ? 26 : 14));
                Color color = ItemSystem.colorOf(pulse.kind);
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(),
                    (int) (200 * (1 - progress))));
                int count = pulse.activation ? 8 : 4;
                for (int i = 0; i < count; i++) {
                    double angle = i * Math.PI * 2 / count;
                    int x = (int) (pulse.x + Math.cos(angle) * radius);
                    int y = (int) (pulse.y + Math.sin(angle) * radius);
                    g.fillRect(x - 1, y - 1, 3, 3);
                    if (pulse.kind == EffectKind.FREEZE) {
                        g.fillRect(x - 3, y, 7, 1);
                        g.fillRect(x, y - 3, 1, 7);
                    }
                }
            }
        } finally {
            g.dispose();
        }
    }
}
