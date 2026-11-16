package screen;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import engine.DrawManager.SpriteType;
import entity.Bullet;
import entity.BulletPool;
import entity.EnemyShip;
import entity.Entity;
import entity.Ship;
import entity.boss.BossBalance;
import entity.boss.BossFight;
import entity.boss.BossHit;
import entity.boss.BossHud;
import entity.boss.RaiderBoss;
import entity.enemy.EnemyBullet;
import entity.enemy.EnemyManager;
import item.ItemAPI;
import item.ItemAPI.*;
import item.ItemManager;

/** Self-contained practice; never awards scores, currency or achievements. */
public final class TutorialScreen extends Screen {
    private enum Step { MOVE, SHOOT, DEFEAT, PICKUP, USE_ITEM, BOSS, COMPLETE }
    private static final int ARENA_TOP = 220;
    /** Remember completion/skip only until this Java process exits. */
    private static boolean tutorialPassed;
    private Step step = Step.MOVE;
    private Ship ship;
    private EnemyShip enemy;
    private final Set<Bullet> bullets = new HashSet<Bullet>();
    private ItemManager itemManager;
    private ItemAPI items;
    private EnemyManager enemies;
    private BossFight boss;
    private boolean enterWasDown, skipWasDown, itemKeyWasDown;
    private long lastTickNanos;
    private String notice;
    private int noticeFrames;
    private int hitGrace;

    public TutorialScreen(final int width, final int height, final int fps) {
        super(width, height, fps);
    }

    @Override
    public void initialize() {
        cleanup();
        ship = new Ship(width / 2 - 13, height - 65);
        step = Step.MOVE;
        enemy = null;
        boss = null;
        enemies = new EnemyManager(width, height, ARENA_TOP);
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
        enterWasDown = inputManager.isKeyDown(KeyEvent.VK_ENTER);
        skipWasDown = inputManager.isKeyDown(KeyEvent.VK_S);
        itemKeyWasDown = inputManager.isKeyDown(KeyEvent.VK_1);
        lastTickNanos = System.nanoTime();
        notice = null;
        noticeFrames = hitGrace = 0;
        returnCode = 1;
        inputDelay.reset();
    }

    private void cleanup() {
        if (itemManager != null) itemManager.endLevel();
        if (boss != null) boss.stop();
        if (enemies != null) enemies.clearBullets();
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
        boolean skip = inputManager.isKeyDown(KeyEvent.VK_S);
        boolean use = inputManager.isKeyDown(KeyEvent.VK_1);
        if (inputManager.isKeyDown(KeyEvent.VK_ESCAPE) || (skip && !skipWasDown)) {
            returnCode = inputManager.isKeyDown(KeyEvent.VK_ESCAPE) ? 1 : 2;
            isRunning = false;
            return;
        }
        long now = System.nanoTime();
        long delta = Math.min(100, Math.max(0, (now - lastTickNanos) / 1000000));
        lastTickNanos = now;
        if (inputDelay.checkFinished()) {
            if (step == Step.COMPLETE) {
                if (enter && !enterWasDown) {
                    returnCode = 2;
                    isRunning = false;
                }
            } else {
                updatePractice(delta, use && !itemKeyWasDown);
            }
        }
        enterWasDown = enter;
        skipWasDown = skip;
        itemKeyWasDown = use;
        draw();
    }

    private void updatePractice(final long delta, final boolean usePressed) {
        if (noticeFrames > 0) noticeFrames--;
        if (hitGrace > 0) hitGrace--;
        int oldX = ship.getPositionX();
        boolean left = inputManager.isKeyDown(KeyEvent.VK_LEFT) || inputManager.isKeyDown(KeyEvent.VK_A);
        boolean right = inputManager.isKeyDown(KeyEvent.VK_RIGHT) || inputManager.isKeyDown(KeyEvent.VK_D);
        if (left && !right && oldX - ship.getSpeed() >= 1) ship.moveLeft();
        else if (right && !left && oldX + ship.getWidth() + ship.getSpeed() < width) ship.moveRight();
        if (step == Step.MOVE && ship.getPositionX() != oldX) {
            step = Step.SHOOT;
            enemy = new EnemyShip(width / 2 - 12, height / 2, SpriteType.EnemyShipA1);
        }
        if ((step == Step.SHOOT || step == Step.DEFEAT || step == Step.BOSS)
                && inputManager.isKeyDown(KeyEvent.VK_SPACE) && ship.shoot(bullets)) {
            if (step == Step.SHOOT) step = Step.DEFEAT;
        }
        ship.update();
        if (enemy != null) enemy.update();
        itemManager.update(delta, new PlayerSnapshot(new Bounds(ship.getPositionX(),
                ship.getPositionY(), ship.getWidth(), ship.getHeight()), true, true));
        if (step == Step.BOSS) updateBoss();
        updateBullets();
        if (step == Step.PICKUP) {
            if (items.getView().slots.get(0) != null) step = Step.USE_ITEM;
            else if (items.getView().drops.isEmpty()) spawnShield(width / 2.0, height / 2.0);
        } else if (step == Step.USE_ITEM && usePressed && items.useSlot(0) == UseResult.USED) {
            startBoss();
        }
        items.drainEvents(); // No persistent listeners or achievement callbacks.
    }

    private void spawnShield(final double x, final double y) {
        items.onEnemyDefeated(DropSource.REGULAR_ENEMY, x, y);
    }

    private void startBoss() {
        Properties settings = new Properties();
        settings.setProperty("raider.health", "10");
        settings.setProperty("raider.speed", "0.8");
        settings.setProperty("raider.patrol.fireGap", "1.2");
        settings.setProperty("raider.patrol.bulletSpeed", "1.5");
        settings.setProperty("raider.patrol.summonGap", "3600");
        settings.setProperty("raider.charge.warning", "2");
        settings.setProperty("raider.charge.speed", "3");
        settings.setProperty("raider.charge.fireGap", "0.5");
        settings.setProperty("raider.charge.bulletSpeed", "1.5");
        settings.setProperty("raider.drones.speed", "1.2");
        boss = new RaiderBoss(BossBalance.fromProperties(settings, "tutorial"),
                1, new Random(0), width, height, ARENA_TOP);
        step = Step.BOSS;
        showNotice("Shield on! Practice safely.");
    }

    private void updateBoss() {
        double x = ship.getPositionX() + ship.getWidth() / 2.0;
        double y = ship.getPositionY() + ship.getHeight() / 2.0;
        boss.update(enemies, x, y, items.getModifiers().enemyMovementBlocked);
        enemies.updateEnemies(false, x, y);
        enemies.updateEffects();
        boolean hit = boss.hurts(ship);
        Iterator<EnemyBullet> it = enemies.getBullets().iterator();
        while (it.hasNext()) {
            if (overlaps(it.next(), ship)) {
                it.remove();
                hit = true;
            }
        }
        if (hit && hitGrace == 0) {
            showNotice(items.tryBlockHit() ? "Shield blocked the hit!" : "Hit! No damage in practice.");
            hitGrace = 60;
        }
        if (boss.isFinished()) {
            step = Step.COMPLETE;
            enemies.clearBullets();
            BulletPool.recycle(bullets);
            bullets.clear();
        }
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
            } else if (step == Step.BOSS) {
                BossHit result = boss.hit(bullet);
                hit = result != null;
            }
            if (hit || bullet.getPositionY() + bullet.getHeight() < ARENA_TOP
                    || step == Step.PICKUP || step == Step.USE_ITEM) {
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
        case MOVE: instruction = "1/6  Move: A/D or LEFT/RIGHT"; detail = "Move sideways to begin."; break;
        case SHOOT: instruction = "2/6  Hold SPACE to fire"; detail = "Holding SPACE fires repeatedly."; break;
        case DEFEAT: instruction = "3/6  Defeat the practice enemy"; detail = "Move below the target and fire."; break;
        case PICKUP: instruction = "4/6  Collect the Shield"; detail = "Touch the falling S item."; break;
        case USE_ITEM: instruction = "5/6  Press 1 to use Shield"; detail = "Some other items work on pickup."; break;
        case BOSS: instruction = "6/6  Defeat the practice boss"; detail = "Dodge and fire. You cannot die."; break;
        default: instruction = "Tutorial complete!"; detail = "Press ENTER to start Stage 1"; break;
        }
        drawManager.drawCenteredRegularString(this, instruction, 120);
        drawManager.drawCenteredRegularString(this, detail, 145);
        if (noticeFrames > 0) drawManager.drawCenteredRegularString(this, notice, 170);
        else if (step == Step.COMPLETE)
            drawManager.drawCenteredRegularString(this, "P: pause | 1-3: item slots", 170);
        drawItems();
        if (boss != null && !boss.isFinished()) {
            boss.draw(drawManager);
            BossHud.drawHealthBar(drawManager, this, boss, 205);
            enemies.draw(drawManager);
        }
        drawManager.drawEntity(ship, ship.getPositionX(), ship.getPositionY());
        if (enemy != null) drawManager.drawEntity(enemy, enemy.getPositionX(), enemy.getPositionY());
        for (Bullet bullet : bullets)
            drawManager.drawEntity(bullet, bullet.getPositionX(), bullet.getPositionY());
        drawManager.drawKeyHints(this, "S: skip to Stage 1 | ESC: menu");
        drawManager.completeDrawing(this);
    }

    /** Tutorial presentation only; pickup, inventory and effects use the real item implementation. */
    private void drawItems() {
        View view = items.getView();
        for (int i = 0; i < view.slots.size(); i++) {
            int x = 12 + i * 75;
            boolean occupied = view.slots.get(i) != null;
            drawManager.drawBox(x, 76, 65, 22, occupied ? Color.CYAN : Color.GRAY);
            drawManager.drawRegularString((i + 1) + (occupied ? " SH" : " --"), x + 4, 93,
                    occupied ? Color.CYAN : Color.GRAY);
        }
        for (DropView drop : view.drops) {
            int x = (int) drop.bounds.x, y = (int) drop.bounds.y;
            drawManager.fillBox(x, y, (int) drop.bounds.width, (int) drop.bounds.height, Color.CYAN);
            drawManager.drawRegularString("S", x + 2, y + 15, Color.BLACK);
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
