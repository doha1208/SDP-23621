package engine;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayDeque;

/** Visual-only horizontal dash. The caller owns movement and dodge rules. */
public final class DashVisualEffect {
    public static final int PIXEL = 2;
    public static final int SAMPLE_MS = 30;
    public static final int FADE_MS = 120;
    private final ArrayDeque<Ghost> ghosts = new ArrayDeque<Ghost>();
    private double previousTime = -1, previousX, previousY, nextSample, now, start;
    private double x, y;
    private int direction = 1;
    private boolean active;

    private static final class Ghost {
        final double x, y, time;
        final Color color;
        Ghost(double x, double y, double time, Color color) {
            this.x = x; this.y = y; this.time = time; this.color = color;
        }
    }

    /** Supply monotonic simulation time and actual ship positions, once per update.
     * Pause by freezing simulation time. Reset on teleport, death or screen change. */
    public void update(double timeMs, double shipX, double shipY,
            boolean dashing, int facing, Color color) {
        if (!Double.isFinite(timeMs) || timeMs < 0 || timeMs < previousTime
                || !Double.isFinite(shipX) || !Double.isFinite(shipY)
                || (facing != -1 && facing != 1) || color == null)
            throw new IllegalArgumentException("Invalid dash visual input");
        now = timeMs; x = shipX; y = shipY; direction = facing;
        if (dashing && !active) {
            start = now; nextSample = now + SAMPLE_MS;
            ghosts.clear(); ghosts.add(new Ghost(x, y, now, color));
        } else if (dashing && now > previousTime) {
            // Interpolate samples so spacing does not depend on frame rate.
            // Skip samples that cannot survive the three-ghost history.
            if (nextSample < now - 2 * SAMPLE_MS)
                nextSample += Math.ceil((now - 2 * SAMPLE_MS - nextSample) / SAMPLE_MS) * SAMPLE_MS;
            while (nextSample <= now) {
                double t = (nextSample - previousTime) / (now - previousTime);
                ghosts.add(new Ghost(previousX + (x - previousX) * t,
                        previousY + (y - previousY) * t, nextSample, color));
                nextSample += SAMPLE_MS;
            }
        }
        active = dashing;
        while (!ghosts.isEmpty() && (ghosts.size() > 3
                || now - ghosts.peek().time >= FADE_MS)) ghosts.remove();
        previousTime = now; previousX = x; previousY = y;
    }

    /** Draw before the current ship; sprite columns may have any ship shape. */
    public void draw(Graphics2D graphics, boolean[][] sprite) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            for (Ghost ghost : ghosts) {
                int alpha = (int) Math.round(115 * (1 - (now - ghost.time) / FADE_MS));
                Color c = ghost.color;
                drawShip(g, sprite, (int) Math.round(ghost.x), (int) Math.round(ghost.y),
                        new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
            }
            if (!active) return;
            int width = sprite.length * PIXEL, height = sprite[0].length * PIXEL;
            int edge = (int) Math.round(x) + (direction < 0 ? width : 0);
            int center = (int) Math.round(y) + height / 2;
            int length = now - start < 40 ? 10 : 14 + ((int) ((now - start) / 30) % 2) * 4;
            int left = direction > 0 ? edge - length : edge;
            g.setColor(new Color(170, 190, 200, 150));
            g.fillRect(left, center - 1, length, 2);
            g.fillRect(direction > 0 ? edge - length + 4 : edge, center - 3, length - 4, 6);
            g.setColor(Color.WHITE);
            g.fillRect(direction > 0 ? edge - length + 4 : edge,
                    center - 1, length - 4, 2);
        } finally { g.dispose(); }
    }

    /** Same crisp two-pixel scale as the game's base ship renderer. */
    public static void drawShip(Graphics2D g, boolean[][] sprite, int x, int y, Color color) {
        g.setColor(color);
        for (int col = 0; col < sprite.length; col++)
            for (int row = 0; row < sprite[col].length; row++)
                if (sprite[col][row]) g.fillRect(x + col * PIXEL, y + row * PIXEL, PIXEL, PIXEL);
    }

    public int getGhostCount() { return ghosts.size(); }
    public void reset() { ghosts.clear(); previousTime = -1; active = false; }
}
