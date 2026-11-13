import engine.DashVisualEffect;
import preview.DashPreview;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.util.Arrays;

/** Headless behavioral checks; no game saves or purchases are loaded. */
public final class DashVisualEffectTest {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static BufferedImage draw(DashVisualEffect effect, boolean[][] sprite) {
        BufferedImage image = new BufferedImage(220, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics(); g.translate(5, 0); g.setColor(Color.MAGENTA);
        effect.draw(g, sprite);
        check(g.getColor().equals(Color.MAGENTA) && g.getTransform().getTranslateX() == 5, "Graphics state preserved");
        g.dispose(); return image;
    }
    private static int[] pixels(BufferedImage image) { return image.getRGB(0, 0, 220, 40, null, 0, 220); }
    private static DashVisualEffect motion(int step) {
        DashVisualEffect effect = new DashVisualEffect();
        effect.update(0, 30, 10, true, 1, Color.CYAN);
        for (int time = step; time <= 120; time += step) effect.update(time, 30 + time, 10, true, 1, Color.CYAN);
        return effect;
    }
    private static Object value(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private static void advance(DashPreview panel, double ms) throws Exception {
        Method m = DashPreview.class.getDeclaredMethod("advance", double.class); m.setAccessible(true); m.invoke(panel, ms);
    }
    public static void main(String[] args) throws Exception {
        boolean[][] sprite = {{true, true}, {true, false}};
        DashVisualEffect effect = motion(10);
        check(effect.getGhostCount() == 3, "History is bounded");
        check(Arrays.equals(pixels(draw(effect, sprite)), pixels(draw(motion(30), sprite))), "Frame-rate independent sampling");
        BufferedImage active = draw(effect, sprite);
        effect.update(120, 150, 10, false, 1, Color.CYAN);
        check(!Arrays.equals(pixels(active), pixels(draw(effect, sprite))), "Thrust stops when dash ends");
        effect.update(240, 150, 10, false, 1, Color.CYAN);
        check(effect.getGhostCount() == 0, "Ghosts expire after dash");
        check(Arrays.stream(pixels(draw(effect, sprite))).allMatch(p -> p == 0), "Idle has no effect pixels");
        effect.update(250, 70, 10, true, -1, Color.WHITE);
        BufferedImage left = draw(effect, new boolean[13][8]);
        check((left.getRGB(103, 17) >>> 24) > 0 && (left.getRGB(65, 17) >>> 24) == 0, "Left dash thrust points right");
        effect.reset(); check(effect.getGhostCount() == 0, "Reset clears history");
        effect.update(0, 70, 10, true, 1, Color.WHITE);
        BufferedImage right = draw(effect, new boolean[13][8]);
        check((right.getRGB(70, 17) >>> 24) > 0 && (right.getRGB(103, 17) >>> 24) == 0, "Right dash thrust points left");
        try { effect.update(-1, 0, 0, false, 1, Color.WHITE); throw new AssertionError("Negative time accepted"); }
        catch (IllegalArgumentException expected) { check(true, "Invalid time rejected"); }
        effect.update(1_000_000, 70, 10, true, 1, Color.WHITE);
        check(effect.getGhostCount() <= 3, "Long time gaps keep bounded history");
        DashPreview panel = new DashPreview(); panel.getActionMap().get("dash").actionPerformed(null);
        advance(panel, 180); double x = (Double) value(panel, "x");
        panel.getActionMap().get("left").actionPerformed(null); advance(panel, 16);
        check((Double) value(panel, "x") == x, "Changing direction at rest does not teleport");
        panel.getActionMap().get("pause").actionPerformed(null); double now = (Double) value(panel, "now"); advance(panel, 50);
        check((Double) value(panel, "now") == now, "Pause freezes simulation");
        panel.getActionMap().get("reset").actionPerformed(null);
        check((Double) value(panel, "x") == 130 && !(Boolean) value(panel, "paused"), "Reset restores preview");
        for (int i = 0; i < 20; i++) { panel.getActionMap().get("dash").actionPerformed(null); advance(panel, 350); }
        check((Double) value(panel, "x") >= 40 && (Double) value(panel, "x") <= 382, "Repeated dashes stay visible");
        System.out.println("DashVisualEffectTest: " + checks + " checks passed");
    }
}
