package screen;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import engine.ControlKeys;
import engine.DrawManager.SpriteType;
import entity.Bullet;
import entity.BulletPool;
import entity.EnemyShip;
import entity.Entity;
import entity.Ship;
import entity.ShipDash;
import item.ItemAPI;
import item.ItemAPI.*;
import item.ItemManager;

/**
 * Self-contained practice; never awards scores, currency or achievements.
 * Teaches the game's keys (see engine.ControlKeys): move, dash, fire,
 * defeat an enemy, and catch an item (it works at once).
 */
public final class TutorialScreen extends Screen {
    private enum Step { MOVE, DASH, SHOOT, DEFEAT, PICKUP, COMPLETE }
    private static final int ARENA_TOP = 220;
    /** Remember completion/skip only until this Java process exits. */
    private static boolean tutorialPassed;
    private Step step = Step.MOVE;
    private Ship ship;
    /** Lowest the ship goes: the row it starts on. */
    private int shipBottomY;
    private ShipDash dash;
    private EnemyShip enemy;
    private final Set<Bullet> bullets = new HashSet<Bullet>();
    private ItemManager itemManager;
    private ItemAPI items;
    private boolean enterWasDown, dashKeyWasDown;
    private long lastTickNanos;
    private String notice;
    private int noticeFrames;

    public TutorialScreen(final int width, final int height, final int fps) {
        super(width, height, fps);
    }

    @Override
    public void initialize() {
        cleanup();
        ship = new Ship(width / 2 - 13, height - 65);
        shipBottomY = ship.getPositionY();
        dash = new ShipDash();
        step = Step.MOVE;
        enemy = null;
        itemManager = new ItemManager(3, new Random(0));
        items = new ItemAPI(itemManager);
        Map<DropSource, DropRule> drops = new EnumMap<DropSource, DropRule>(DropSource.class);
        for (DropSource source : DropSource.values())
            drops.put(source, new DropRule(1, Collections.singletonMap("shield", 1.0)));
        itemManager.beginLevel(new LevelRules("tutorial", 0, width, ARENA_TOP,
                ship.getPositionY() + ship.getHeight(), 100, 18, 18, 30000, drops),
                new LifePort() {
                    public boolean canAddLife() { return false; }
                    public boolean tryAddLife() { return false; }
                });
        // Keys held while entering (e.g. ENTER from the menu) must be
        // released and pressed again.
        enterWasDown = inputManager.isKeyDown(KeyEvent.VK_ENTER);
        dashKeyWasDown = inputManager.isKeyDown(ControlKeys.DASH);
        lastTickNanos = System.nanoTime();
        notice = null;
        noticeFrames = 0;
        returnCode = 1;
        inputDelay.reset();
    }

    private void cleanup() {
        if (itemManager != null) itemManager.endLevel();
        BulletPool.recycle(bullets);
        bullets.clear();
    }

    @Override
    public int run() {
        try {
            if (tutorialPassed) return 2;
            super.run();
            if (returnCode == 2) tutorialPassed = true;
            return returnCode;
        } finally {
            cleanup();
        }
    }

    @Override
    protected void update() {
        boolean enter = inputManager.isKeyDown(KeyEvent.VK_ENTER);
        // ESC: back to the menu. ENTER: start Stage 1, at any step.
        if (inputManager.isKeyDown(KeyEvent.VK_ESCAPE) || (enter && !enterWasDown)) {
            returnCode = inputManager.isKeyDown(KeyEvent.VK_ESCAPE) ? 1 : 2;
            isRunning = false;
            return;
        }
        long now = System.nanoTime();
        long delta = Math.min(100, Math.max(0, (now - lastTickNanos) / 1000000));
        lastTickNanos = now;
        // Practice keeps running after completion, so the player can keep
        // moving and dashing until ENTER.
        if (inputDelay.checkFinished())
            updatePractice(delta);
        enterWasDown = enter;
        draw();
    }

    private void updatePractice(final long delta) {
        if (noticeFrames > 0) noticeFrames--;
        int oldX = ship.getPositionX();
        int oldY = ship.getPositionY();
        boolean left = inputManager.isKeyDown(KeyEvent.VK_LEFT) || inputManager.isKeyDown(KeyEvent.VK_A);
        boolean right = inputManager.isKeyDown(KeyEvent.VK_RIGHT) || inputManager.isKeyDown(KeyEvent.VK_D);
        boolean up = inputManager.isKeyDown(KeyEvent.VK_UP) || inputManager.isKeyDown(KeyEvent.VK_W);
        boolean down = inputManager.isKeyDown(KeyEvent.VK_DOWN) || inputManager.isKeyDown(KeyEvent.VK_S);

        // Dash: a quick sideways move the way the ship last moved.
        dash.face((right ? 1 : 0) - (left ? 1 : 0));
        boolean dashKey = inputManager.isKeyDown(ControlKeys.DASH);
        boolean dashed = dashKey && !dashKeyWasDown && dash.start();
        dashKeyWasDown = dashKey;
        if (dash.isDashing()) {
            int x = ship.getPositionX() + dash.update();
            ship.setPositionX(Math.max(1, Math.min(x, width - 1 - ship.getWidth())));
        } else {
            dash.update(); // recharge
            if (left && !right && oldX - ship.getSpeed() >= 1) ship.moveLeft();
            else if (right && !left && oldX + ship.getWidth() + ship.getSpeed() < width) ship.moveRight();
            if (up && !down) ship.moveUp(ARENA_TOP);
            else if (down && !up) ship.moveDown(shipBottomY);
        }

        if (step == Step.MOVE && (ship.getPositionX() != oldX || ship.getPositionY() != oldY))
            step = Step.DASH;
        else if (step == Step.DASH && dashed) {
            step = Step.SHOOT;
            enemy = new EnemyShip(width / 2 - 12, height / 2, SpriteType.EnemyShipA1);
        }
        if ((step == Step.SHOOT || step == Step.DEFEAT)
                && inputManager.isKeyDown(KeyEvent.VK_SPACE) && ship.shoot(bullets)) {
            if (step == Step.SHOOT) step = Step.DEFEAT;
        }
        ship.update();
        if (enemy != null) enemy.update();
        itemManager.update(delta, new PlayerSnapshot(new Bounds(ship.getPositionX(),
                ship.getPositionY(), ship.getWidth(), ship.getHeight()), true, true));
        updateBullets();
        if (step == Step.PICKUP) {
            // Items work as soon as they are caught. If pickup.autoUse is off the
            // shield lands in a slot instead, so the tutorial uses it right away.
            if (items.getView().slots.get(0) != null) items.useSlot(0);
            if (shieldActive()) {
                step = Step.COMPLETE;
                showNotice("Shield on! It blocks one hit.");
            } else if (items.getView().drops.isEmpty()) spawnShield(width / 2.0, height / 2.0);
        }
        items.drainEvents(); // No persistent listeners or achievement callbacks.
    }

    private boolean shieldActive() {
        for (EffectView effect : items.getView().effects)
            if (effect.item.effectKind == EffectKind.SHIELD) return true;
        return false;
    }

    private void spawnShield(final double x, final double y) {
        items.onEnemyDefeated(DropSource.REGULAR_ENEMY, x, y);
    }

    private void updateBullets() {
        Set<Bullet> expired = new HashSet<Bullet>();
        Iterator<Bullet> it = bullets.iterator();
        while (it.hasNext()) {
            Bullet bullet = it.next();
            bullet.update();
            boolean hit = enemy != null && overlaps(bullet, enemy);
            if (hit) {
                spawnShield(enemy.getPositionX() + enemy.getWidth() / 2.0, enemy.getPositionY());
                enemy = null;
                step = Step.PICKUP;
            }
            if (hit || bullet.getPositionY() + bullet.getHeight() < ARENA_TOP
                    || step == Step.PICKUP) {
                it.remove();
                expired.add(bullet);
            }
        }
        BulletPool.recycle(expired);
    }

    private void showNotice(final String text) {
        notice = text;
        noticeFrames = 120;
    }

    private static boolean overlaps(final Entity a, final Entity b) {
        return a.getPositionX() < b.getPositionX() + b.getWidth()
                && a.getPositionX() + a.getWidth() > b.getPositionX()
                && a.getPositionY() < b.getPositionY() + b.getHeight()
                && a.getPositionY() + a.getHeight() > b.getPositionY();
    }

    private void draw() {
        drawManager.initDrawing(this);
        drawManager.drawScreenTitle(this, "TUTORIAL");
        String instruction, detail;
        switch (step) {
        case MOVE: instruction = "1/5  Move: WASD or arrow keys"; detail = "Move in any direction to begin."; break;
        case DASH: instruction = "2/5  Press " + ControlKeys.DASH_LABEL.toUpperCase() + " to dash";
            detail = "Bullets pass through you mid-dash."; break;
        case SHOOT: instruction = "3/5  Hold SPACE to fire"; detail = "Holding SPACE fires repeatedly."; break;
        case DEFEAT: instruction = "4/5  Defeat the practice enemy"; detail = "Move below the target and fire."; break;
        case PICKUP: instruction = "5/5  Catch the Shield"; detail = "Items work as soon as you catch them."; break;
        default: instruction = "Tutorial complete!"; detail = "Press ENTER to start Stage 1"; break;
        }
        drawManager.drawCenteredRegularString(this, instruction, 120);
        drawManager.drawCenteredRegularString(this, detail, 145);
        if (noticeFrames > 0) drawManager.drawCenteredRegularString(this, notice, 170);
        else if (step == Step.COMPLETE)
            drawManager.drawCenteredRegularString(this, "P: pause | "
                    + ControlKeys.DASH_LABEL.toUpperCase() + ": dash", 170);
        drawItems();
        drawManager.drawEntity(ship, ship.getPositionX(), ship.getPositionY());
        if (enemy != null) drawManager.drawEntity(enemy, enemy.getPositionX(), enemy.getPositionY());
        for (Bullet bullet : bullets)
            drawManager.drawEntity(bullet, bullet.getPositionX(), bullet.getPositionY());
        drawManager.drawKeyHints(this, "ENTER: start Stage 1 | ESC: menu");
        drawManager.completeDrawing(this);
    }

    /** Tutorial presentation only; pickup, inventory and effects use the real item implementation. */
    private void drawItems() {
        View view = items.getView();
        for (DropView drop : view.drops) {
            int x = (int) drop.bounds.x, y = (int) drop.bounds.y;
            drawManager.drawItemIcon(drop.item.effectKind, x, y,
                    (int) Math.min(drop.bounds.width, drop.bounds.height));
        }
        for (EffectView effect : view.effects) {
            if (effect.item.effectKind != EffectKind.SHIELD) continue;
            drawManager.drawBox(ship.getPositionX() - 5, ship.getPositionY() - 5,
                    ship.getWidth() + 10, ship.getHeight() + 10, Color.CYAN);
            String seconds = effect.remainingMillis == null ? "" : " " + (effect.remainingMillis + 999) / 1000 + "s";
            drawManager.drawRegularString("Shield" + seconds, width - 140, 93, Color.CYAN);
        }
    }
}
