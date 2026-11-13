import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;

import engine.*;
import engine.soundEngine.SoundManager;
import entity.*;
import entity.boss.*;
import entity.enemy.*;
import item.ItemAPI.*;
import item.ItemSystem;
import level.*;
import screen.GameScreen;
import screen.Screen;
import screen.ScoreScreen;
import screen.Starfield;

/** Headless, muted integration tests. Compile into an isolated save directory. */
public final class TimedRunTest {
    private static int checks;
    private static final long SECOND = 1_000_000_000L;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        while (type != null) {
            try {
                Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object get(Object target, String name) throws Exception {
        return field(target.getClass(), name).get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        field(target.getClass(), name).set(target, value);
    }

    private static Object call(Object target, String name, Class<?>[] types,
            Object... args) throws Exception {
        Class<?> type = target instanceof Class ? (Class<?>) target : target.getClass();
        Method method = type.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target instanceof Class ? null : target, args);
    }

    private static void call(Object target, String name) throws Exception {
        call(target, name, new Class<?>[0]);
    }

    private static void progress(GameScreen game, long now) throws Exception {
        call(game, "updateRunProgress", new Class<?>[]{long.class}, now);
    }

    private static GameScreen game() throws Exception {
        GameScreen game = new GameScreen(new GameState(1, 0, 3, 0, 0, 0),
                new GameSettings(5, 2, 60, 2000), false, 448, 520, 60, true);
        game.initialize();
        return game;
    }

    private static void active(GameScreen game) throws Exception {
        set(game, "inputDelay", Core.getCooldown(0));
    }

    private static void clockChecks() {
        TimedRun run = new TimedRun();
        check(run.label().equals("BOSS IN 2:00"), "Initial timer");
        run.update(0, false, 3, false);
        run.update(600 * SECOND, false, 3, false);
        check(run.remainingSeconds() == 120, "Countdown excluded");
        run.update(601 * SECOND, true, 3, false);
        run.update(661 * SECOND, true, 3, false);
        check(run.remainingSeconds() == 60, "Active elapsed time");
        run.update(661 * SECOND, false, 3, false);
        run.update(1661 * SECOND, false, 3, false);
        run.update(1662 * SECOND, true, 3, false);
        check(run.remainingSeconds() == 60, "Pause and resume exclude suspended time");
        run.update(1722 * SECOND - 1, true, 3, false);
        check(run.getPhase() == TimedRun.Phase.COMBAT && run.remainingSeconds() == 1,
                "One nanosecond before deadline");
        run.update(1722 * SECOND, true, 3, false);
        check(run.getPhase() == TimedRun.Phase.BOSS, "Exact deadline starts boss");
        run.update(2000 * SECOND, true, 3, false);
        check(run.getPhase() == TimedRun.Phase.BOSS, "Time alone never clears boss");
        run.update(2001 * SECOND, true, 3, true);
        check(run.getPhase() == TimedRun.Phase.WON, "Boss defeat clears run");
        run.quit(); run.update(2002 * SECOND, true, 0, false);
        check(run.getPhase() == TimedRun.Phase.WON, "Terminal result cannot change");
        for (boolean boss : new boolean[]{false, true}) {
            TimedRun death = new TimedRun();
            death.update(0, true, 1, false);
            if (boss) death.update(120 * SECOND, true, 1, false);
            death.update(120 * SECOND, true, 0, true);
            check(death.getPhase() == TimedRun.Phase.LOST, "Death wins boundary/defeat tie");
        }
        TimedRun quit = new TimedRun(); quit.quit(); quit.update(0, true, 3, true);
        check(quit.getPhase() == TimedRun.Phase.QUIT, "Quit is terminal");
        check(new TimedRun().remainingSeconds() == 120, "New run resets timer");
    }

    @SuppressWarnings("unchecked")
    private static void integrationChecks() throws Exception {
        GameScreen game = game();
        ItemSystem items = (ItemSystem) get(game, "items");
        call(game, "updateState");
        check(((TimedRun)get(game, "timedRun")).remainingSeconds() == 120,
                "Screen countdown does not consume combat time");
        active(game);
        items.update((Entity)get(game, "ship"), true);
        RandomDirector normal = (RandomDirector)get(game, "levelDirector");
        set(normal, "frame", 7200);
        check(!normal.isFinished(), "Frame rate cannot stop timed spawns before wall-clock deadline");
        set(normal, "frame", 0);
        check(items.api().tryGrant(new GrantRequest("shield", "shield",
                GrantSource.REWARD, GrantTiming.NOW)).isSuccess(), "Store shield");
        check(items.api().tryGrant(new GrantRequest("rapid", "rapid_fire",
                GrantSource.REWARD, GrantTiming.NOW)).isSuccess(), "Apply run-wide bonus");
        check(items.api().tryGrant(new GrantRequest("freeze", "freeze",
                GrantSource.REWARD, GrantTiming.NOW)).isSuccess(), "Store freeze");
        items.useSlot(1);
        check(items.enemiesFrozen(), "Timed effect active");
        long freezeBefore = items.api().getView().effects.stream()
                .filter(e -> e.item.itemId.equals("freeze")).findFirst().get().remainingMillis;
        set(items, "lastUpdateNanos", System.nanoTime() - 600 * SECOND);
        set(game, "paused", true);
        set(game, "pauseStartedAt", System.currentTimeMillis() - 600000);
        call(game, "updateState"); call(game, "resumeGameplay");
        items.update((Entity)get(game, "ship"), true);
        check(items.enemiesFrozen(), "Pause keeps effect active");
        check(items.api().getView().effects.stream()
                .filter(e -> e.item.itemId.equals("freeze")).findFirst().get().remainingMillis
                == freezeBefore, "Pause duration not applied on resume");
        active(game);
        set(game, "score", 123); set(game, "lives", 2); set(game, "pendingDiamonds", 7);
        EnemyManager enemies = (EnemyManager)get(game, "enemyManager");
        enemies.spawn(SpawnRequest.of(EnemyType.NORMAL), 200, 450);
        enemies.getBullets().add(new EnemyBullet(200, 300, 0, 2));
        Set<Bullet> bullets = (Set<Bullet>)get(game, "bullets");
        bullets.add(new Bullet(200, 300, -4));
        progress(game, 0); progress(game, 120 * SECOND);
        check(get(game, "boss") != null, "Deadline creates real boss");
        check(!((Boolean)get(game, "levelFinished")), "Transition is not a clear");
        check(game.getGameState().getLivesRemaining() == 2
                && game.getGameState().getScore() == 123, "Life and score preserved");
        check(get(game, "items") == items && items.api().getView().slots.get(0) != null
                && items.fireRateBonus() > 0 && items.enemiesFrozen(), "Items and effects preserved");
        check(((EnemyManager)get(game, "enemyManager")).isEmpty()
                && ((EnemyManager)get(game, "enemyManager")).getBullets().isEmpty()
                && bullets.isEmpty(), "Old enemies/bullets removed");
        check(game.getGameState().getPendingDiamonds() == 7
                && game.getGameState().getLevelsCompletedRun() == 0, "Transition gives no clear rewards");
        Object boss = get(game, "boss"); progress(game, 121 * SECOND);
        check(get(game, "boss") == boss, "Boss spawned once");

        int directorFrame = ((RandomDirector)get(game, "levelDirector")).getFrame();
        call(game, "updateState");
        check(((RandomDirector)get(game, "levelDirector")).getFrame() == directorFrame,
                "Normal director stops during boss fight");
        set(game, "boss", new DefeatedBoss());
        call(game, "updateState");
        check((Boolean)get(game, "levelFinished"), "Boss defeat finalizes run");
        GameState cleared = game.getGameState();
        call(game, "hitPlayer"); call(game, "updateState");
        check(game.getGameState().getLivesRemaining() == cleared.getLivesRemaining(),
                "No post-clear damage");
        check(game.getGameState().getPendingDiamonds() == cleared.getPendingDiamonds()
                && game.getGameState().getLevelsCompletedRun() == 1, "Clear rewards once");
        GameState settled = (GameState)call(Core.class, "settlePendingDiamonds",
                new Class<?>[]{GameState.class}, cleared);
        int banked = DiamondManager.getInstance().getDiamonds();
        call(Core.class, "settlePendingDiamonds", new Class<?>[]{GameState.class}, settled);
        check(DiamondManager.getInstance().getDiamonds() == banked
                && settled.getPendingDiamonds() == 0, "Settlement is idempotent");

        Map<GameEvents.Type, List<GameEvents.Listener>> listeners =
                (Map<GameEvents.Type, List<GameEvents.Listener>>)field(GameEvents.class, "LISTENERS").get(null);
        int listenerCount = listeners.get(GameEvents.Type.PLAYER_HIT).size();
        call(game, "restartLevel");
        check(listeners.get(GameEvents.Type.PLAYER_HIT).size() == listenerCount,
                "Restart does not leak event listeners");
        check(get(game, "items") != items && ((ItemSystem)get(game, "items")).fireRateBonus() == 0
                && ((ItemSystem)get(game, "items")).api().getView().slots.stream().allMatch(i -> i == null),
                "Restart resets inventory and effects");
        check(get(game, "boss") == null && ((TimedRun)get(game, "timedRun")).remainingSeconds() == 120
                && game.getGameState().getLivesRemaining() == 3 && game.getGameState().getScore() == 0,
                "Restart returns to fresh combat");
        check(game.getGameState().getPendingDiamonds() == 0
                && game.getGameState().getLevelsCompletedRun() == 0, "Restart resets run counters");

        active(game); progress(game, 0); set(game, "lives", 0); progress(game, 120 * SECOND);
        call(game, "updateState");
        check(get(game, "boss") == null && (Boolean)get(game, "gameOverActive"),
                "Fatal deadline does not spawn boss");
        call(game, "restartLevel"); active(game); progress(game, 0); progress(game, 120 * SECOND);
        set(game, "boss", new DefeatedBoss()); set(game, "lives", 0);
        call(game, "updateState");
        check(((TimedRun)get(game, "timedRun")).getPhase() == TimedRun.Phase.LOST
                && game.getGameState().getLevelsCompletedRun() == 0, "Simultaneous boss death is a loss");
        call(game, "restartLevel"); set(game, "pendingDiamonds", 5);
        Object quit = Class.forName("screen.GameScreen$PauseOption").getEnumConstants()[2];
        set(game, "pauseSelection", quit); call(game, "confirmPauseSelection");
        check(game.didQuit() && game.getGameState().getPendingDiamonds() == 0
                && !(Boolean)field(Screen.class, "isRunning").get(game), "Quit discards pending and exits");
        check(game.getGameState().getLivesRemaining() == 3, "Quit is not fabricated as death");
        GameState lost = new GameState(1, 25, 0, 0, 0, 9, 0, 0);
        int beforeLoss = DiamondManager.getInstance().getDiamonds();
        GameState lossResult = (GameState)call(Core.class, "settlePendingDiamonds",
                new Class<?>[]{GameState.class}, lost);
        check(lossResult.getPendingDiamonds() == 0
                && DiamondManager.getInstance().getDiamonds() == beforeLoss,
                "Death forfeits pending diamonds");
    }

    private static final class DefeatedBoss implements BossFight {
        public void update(EnemyManager enemies, double x, double y, boolean frozen) { }
        public BossHit hit(Entity bullet) { return null; }
        public boolean hurts(Entity ship) { return false; }
        public void draw(DrawManager drawManager) { }
        public void stop() { }
        public boolean isDefeated() { return true; }
        public boolean isFinished() { return false; }
        public int getHealth() { return 0; }
        public int getMaxHealth() { return 100; }
        public String getName() { return "test boss"; }
    }

    private static void render(Screen screen, String directory, String name) throws Exception {
        call(screen, "draw");
        BufferedImage image = (BufferedImage)field(DrawManager.class, "backBuffer").get(null);
        check(image.getWidth() == 448 && image.getHeight() == 520, "Render size: " + name);
        check(field(DrawManager.class, "frame").get(null) == null, "No desktop window: " + name);
        if (directory != null) {
            File folder = new File(directory);
            if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException(directory);
            ImageIO.write(image, "png", new File(folder, name + ".png"));
        }
    }

    @SuppressWarnings("unchecked")
    private static void mergedGameplayChecks(String directory) throws Exception {
        String previousOrder = System.getProperty(BossRoster.ORDER_PROPERTY);
        boolean[] keys = (boolean[])field(InputManager.class, "keys").get(null);
        try {
            for (String kind : new String[]{"fortress", "raider"}) {
                System.setProperty(BossRoster.ORDER_PROPERTY, kind);
                BossRoster.newRun();
                GameScreen game = game();
                active(game);
                Ship ship = (Ship)get(game, "ship");
                int startX = ship.getPositionX();
                keys[KeyEvent.VK_RIGHT] = true;
                call(game, "update");
                keys[KeyEvent.VK_RIGHT] = false;
                check(ship.getPositionX() > startX, "Movement through game update: " + kind);
                Starfield stars = (Starfield)get(game, "starfield");
                float[] before = ((float[])get(stars, "y")).clone();
                call(game, "update");
                check(!Arrays.equals(before, (float[])get(stars, "y")), "Merged starfield moves");
                if (kind.equals("fortress")) {
                    for (int i = 0; i < 240; i++) call(game, "updateState");
                    render(game, directory, "timed-run-combat");
                    set(game, "pauseKeyCooldown", Core.getCooldown(0));
                    keys[KeyEvent.VK_P] = true;
                    call(game, "update");
                    keys[KeyEvent.VK_P] = false;
                    before = ((float[])get(stars, "y")).clone();
                    call(game, "update");
                    check((Boolean)get(game, "paused")
                            && Arrays.equals(before, (float[])get(stars, "y")), "Pause holds starfield");
                    render(game, directory, "timed-run-pause");
                    call(game, "resumeGameplay");
                }
                progress(game, 0); progress(game, 120 * SECOND);
                BossFight boss = (BossFight)get(game, "boss");
                check(kind.equals("fortress") ? boss instanceof FortressBoss : boss instanceof RaiderBoss,
                        "Real selected boss: " + kind);
                for (int i = 0; i < 90; i++) call(game, "update");
                render(game, directory, "timed-run-" + kind);
                int scoreBefore = game.getGameState().getScore();
                int killsBefore = game.getGameState().getShipsDestroyed();
                int health = boss.getHealth();
                Set<Bullet> bullets = (Set<Bullet>)get(game, "bullets");
                for (int i = 0; i < health + 1 && !boss.isDefeated(); i++) {
                    bullets.add(new Bullet(0, 40, -4, 448, 170));
                    call(game, "manageCollisions");
                }
                check(boss.isDefeated(), "Real collision path defeats " + kind);
                check(game.getGameState().getShipsDestroyed() == killsBefore + 1,
                        "Only the boss defeat counts as a kill: " + kind);
                call(game, "update");
                check((Boolean)get(game, "levelFinished")
                        && ((TimedRun)get(game, "timedRun")).getPhase() == TimedRun.Phase.WON,
                        "Real boss defeat clears run: " + kind);
                check(game.getGameState().getScore() > scoreBefore, "Boss gives score: " + kind);
                int finalScore = game.getGameState().getScore();
                call(game, "manageCollisions"); call(game, "update");
                check(game.getGameState().getScore() == finalScore, "No repeated boss reward");
                ScoreScreen results = new ScoreScreen(448, 520, 60, game.getGameState(), true);
                results.initialize();
                set(results, "inputDelay", Core.getCooldown(0));
                render(results, directory, "timed-run-clear-" + kind);
                call(game, "restartLevel");
                check(get(game, "boss") == null && ((TimedRun)get(game, "timedRun")).remainingSeconds() == 120,
                        "Real boss clear restarts to combat: " + kind);
                set(game, "lives", 0); active(game);
                call(game, "update");
                check((Boolean)get(game, "gameOverActive"), "Real death enters game over: " + kind);
                render(game, directory, "timed-run-loss-" + kind);
            }
        } finally {
            Arrays.fill(keys, false);
            if (previousOrder == null) System.clearProperty(BossRoster.ORDER_PROPERTY);
            else System.setProperty(BossRoster.ORDER_PROPERTY, previousOrder);
            BossRoster.newRun();
        }
    }

    public static void main(String[] args) throws Exception {
        check(java.awt.GraphicsEnvironment.isHeadless(), "Require -Djava.awt.headless=true");
        if (System.getProperty(LevelDirectors.SEED_PROPERTY) == null)
            System.setProperty(LevelDirectors.SEED_PROPERTY, "1");
        Core.getLogger().setUseParentHandlers(false);
        SoundManager.getInstance().setMuted(true);
        clockChecks();
        integrationChecks();
        mergedGameplayChecks(args.length == 0 ? null : args[0]);
        System.out.println("TimedRunTest: " + checks + " checks passed");
    }
}
