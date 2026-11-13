package preview;

import engine.DashVisualEffect;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Isolated art preview; all movement here is a mock, never gameplay logic. */
@SuppressWarnings("serial")
public final class DashPreview extends JPanel {
    private static final int W = 448, H = 260, Y = 142;
    private static final double DURATION = 180, DISTANCE = 110;
    private final boolean[][] sprite;
    private final DashVisualEffect effect = new DashVisualEffect();
    private double now, began = -1000, from = 130, x = 130, nextAuto = 700;
    private int direction = 1, motionDirection = 1;
    private boolean auto, paused, slow, effects = true;
    private long last = System.nanoTime();
    private Timer timer;

    public DashPreview() throws Exception {
        List<String> rows = Files.readAllLines(Paths.get("res/preview/dash-standard.sprite"));
        if (rows.size() != 8) throw new IllegalArgumentException("Expected eight sprite rows");
        sprite = new boolean[13][8];
        for (int row = 0; row < 8; row++) {
            if (!rows.get(row).matches("[.#]{13}")) throw new IllegalArgumentException("Invalid sprite row");
            for (int col = 0; col < 13; col++) sprite[col][row] = rows.get(row).charAt(col) == '#';
        }
        setPreferredSize(new Dimension(W * 2, H * 2)); setBackground(Color.BLACK);
        bind("SPACE", "dash", () -> dash());
        bind("LEFT", "left", () -> { if (!dashing()) direction = -1; });
        bind("RIGHT", "right", () -> { if (!dashing()) direction = 1; });
        bind("A", "auto", () -> { auto = !auto; nextAuto = now + 400; });
        bind("S", "slow", () -> slow = !slow);
        bind("P", "pause", () -> paused = !paused);
        bind("E", "effects", () -> effects = !effects);
        bind("R", "reset", () -> reset());
        bind("ESCAPE", "close", () -> SwingUtilities.getWindowAncestor(this).dispose());
        effect.update(0, x, Y, false, direction, Color.WHITE);
    }
    private void bind(String key, String name, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, new AbstractAction() {
            public void actionPerformed(ActionEvent e) { action.run(); repaint(); }
        });
    }
    private boolean dashing() { return now - began < DURATION; }
    private void dash() {
        if (paused || dashing()) return;
        // Keep every dash inside the viewport, including repeated key presses.
        if (x + direction * DISTANCE < 40 || x + direction * DISTANCE > W - 66)
            direction = -direction;
        from = x; began = now; motionDirection = direction;
        effect.update(now, x, Y, true, direction, Color.WHITE);
    }
    private void reset() {
        effect.reset(); now = 0; began = -1000; x = from = 130;
        direction = 1; nextAuto = 700; paused = false; auto = false;
        effect.update(now, x, Y, false, direction, Color.WHITE);
    }
    private void advance(double dt) {
        if (paused) return;
        now += dt;
        double t = Math.max(0, Math.min(1, (now - began) / DURATION));
        if (began >= 0) x = from + motionDirection * DISTANCE * (1 - (1 - t) * (1 - t));
        effect.update(now, x, Y, dashing(), direction, Color.WHITE);
        if (auto && now >= nextAuto && !dashing()) { dash(); nextAuto = now + 850; }
    }
    private void start() {
        timer = new Timer(16, e -> {
            long current = System.nanoTime();
            double dt = Math.min(50, (current - last) / 1_000_000.0);
            last = current; advance(dt * (slow ? 0.25 : 1)); repaint();
        });
        timer.start();
    }
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.scale(getWidth() / (double) W, getHeight() / (double) H);
        render(g); g.dispose();
    }
    private void render(Graphics2D g) {
        g.setColor(Color.BLACK); g.fillRect(0, 0, W, H);
        // Fixed stars keep attention on the effect rather than background motion.
        g.setColor(new Color(65, 65, 65));
        for (int i = 0; i < 38; i++) g.fillRect((i * 97 + 17) % W, 72 + (i * 47) % 118, 1, 1);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14)); g.setColor(Color.WHITE);
        g.drawString("STANDARD / DASH THRUST STUDY", 18, 25);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        g.drawString("Visual prototype | no dodge rules or invulnerability", 18, 44);
        g.setColor(new Color(100, 100, 100)); g.drawLine(18, 188, W - 18, 188);
        if (effects) effect.draw(g, sprite);
        DashVisualEffect.drawShip(g, sprite, (int) Math.round(x), Y, Color.WHITE);
        String state = paused ? "PAUSED" : dashing() ? "DASH" : effect.getGhostCount() > 0 ? "FADE" : "IDLE";
        g.setColor(Color.WHITE);
        g.drawString(state + " | " + (direction > 0 ? "RIGHT" : "LEFT") + " | "
                + (slow ? "0.25x" : "1x") + " | FX " + (effects ? "ON" : "OFF")
                + " | LOOP " + (auto ? "ON" : "OFF"), 18, 208);
        g.drawString("Space dash | Left/Right direction | A loop | S slow", 18, 229);
        g.drawString("P pause | E compare FX | R reset | Esc close", 18, 245);
    }
    private void snapshot(String path) throws Exception {
        BufferedImage image = new BufferedImage(W * 2, H * 6, BufferedImage.TYPE_INT_RGB);
        for (int row = 0; row < 3; row++) {
            reset();
            if (row > 0) { dash(); for (int t = 0; t < (row == 1 ? 100 : 240); t += 10) advance(10); }
            Graphics2D g = image.createGraphics(); g.translate(0, row * H * 2); g.scale(2, 2); render(g); g.dispose();
        }
        ImageIO.write(image, "png", Paths.get(path).toFile());
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("--snapshot")) {
            new DashPreview().snapshot(args[1]); return;
        }
        SwingUtilities.invokeAndWait(() -> {
            try {
                DashPreview panel = new DashPreview();
                JFrame frame = new JFrame("Dash Motion Preview - Standard");
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); frame.setContentPane(panel);
                frame.pack(); frame.setMinimumSize(frame.getSize()); frame.setLocationRelativeTo(null);
                frame.addWindowListener(new WindowAdapter() {
                    public void windowClosed(WindowEvent e) { panel.timer.stop(); }
                });
                frame.setVisible(true); panel.start();
                if (args.length == 1 && args[0].equals("--smoke")) {
                    panel.getActionMap().get("dash").actionPerformed(null);
                    Timer close = new Timer(1400, e -> { frame.dispose(); System.out.println("DashPreview smoke passed"); });
                    close.setRepeats(false); close.start();
                }
            } catch (Exception e) { throw new IllegalStateException("Cannot start dash preview", e); }
        });
    }
}
