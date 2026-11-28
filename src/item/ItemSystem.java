package item;

import java.awt.Color;
import java.awt.Graphics;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import engine.ControlKeys;
import engine.Core;
import entity.Entity;
import entity.Ship;
import item.ItemAPI.*;

/**
 * Bridge between the game screen and the item system. The goal is to leave only one-line calls on the game side.
 * All translation (building LevelRules/LifePort/PlayerSnapshot, frame timing, bonus math) happens here.
 * Shop, HUD and effects teams use the ItemAPI from api().
 *
 * One per run. GameScreen is recreated every level, so this class keeps the run-wide instance.
 * Level 1 always starts a new run, so forLevel(1) creates a new instance.
 * Call only from the thread that owns game state.
 */
public final class ItemSystem {
    /** Drops bounce below the item HUD row (y 42 to 60), so they are never hidden under it (Team Entities). */
    private static final double PLAY_AREA_TOP = 60;

    private static ItemSystem current;

    /** Balance values of this run (res/item-balance.properties). Read once when the run starts. */
    private final ItemBalance balance;
    /** Highest lives the life item can reach this run (raised by the Max Life equipped item). */
    private final int maxLives;
    private final ItemManager manager;
    private final ItemAPI api;
    private GameHooks hooks;
    private int levelNumber;
    private long lastUpdateNanos = -1;
    /** Shared game logger, like the other systems use. */
    private final Logger logger = Core.getLogger();
    private final ItemFeedback feedback = new ItemFeedback();
    /** True while a slot item is being used, so its effect start shows no pickup notice. */
    private boolean usingSlot;

    /** Lets the game screen hand over access to lives/score. The item system does not own lives or score. */
    public interface GameHooks {
        int getLives();
        void addLife();
        void addScore(int points);
    }

    private ItemSystem(Random random) {
        balance = ItemBalance.load();
        catalog = new ItemDefinitions(balance); // pick up balance changes for the equipment calls too
        ItemInfo equipped = equippedItem();
        int capacity = balance.inventoryCapacity;
        if (equipped != null && equipped.effectKind == EffectKind.EXTRA_SLOTS)
            capacity += equipped.magnitude.intValue();
        maxLives = balance.maxLives + (equipped != null && equipped.effectKind == EffectKind.MAX_LIFE
            ? equipped.magnitude.intValue() : 0);
        manager = new ItemManager(capacity, balance, random);
        api = new ItemAPI(manager);
        manager.setEventListener(this::onEvent);
        if (equipped != null) manager.equip(equipped);
        logger.info("Item system started a new run (" + capacity + " item slots, max lives " + maxLives + ", equipped: "
            + (equipped == null ? "none" : equipped.displayName) + ").");
    }

    /** Run instance for the level number. Starts a new run at level 1 or when there is no run. */
    public static ItemSystem forLevel(int level) {
        if (level <= 1 || current == null) current = new ItemSystem(new Random());
        return current;
    }

    /** The current run. May be null outside a run (menus, etc.). */
    public static ItemSystem current() { return current; }

    // --- Equipped items: owned and equipped outside a run, at most one equipped, used from the next run on. ---
    // Ownership is saved next to the game, like the coin balance. How an item becomes owned
    // (e.g. a shop purchase) belongs to the caller, which then calls grantEquipment.

    private static final String EQUIPMENT_FILE = "equipment";
    private static Set<String> ownedEquipment;
    private static String equippedId;
    /** Item catalog for the equipment calls outside a run. Loaded on first use, refreshed when a run starts. */
    private static ItemDefinitions catalog;

    private static ItemDefinitions catalog() {
        if (catalog == null) catalog = new ItemDefinitions(ItemBalance.load());
        return catalog;
    }

    /** All equipped-type items, in catalog order. */
    public static List<ItemInfo> equipmentItems() {
        List<ItemInfo> equipment = new ArrayList<ItemInfo>();
        for (ItemInfo item : catalog().all())
            if (item.activationMode == ActivationMode.EQUIPPED) equipment.add(item);
        return Collections.unmodifiableList(equipment);
    }

    public static boolean isOwned(String itemId) {
        loadEquipment();
        return ownedEquipment.contains(itemId);
    }

    /** The equipped item, or null when nothing is equipped. */
    public static ItemInfo equippedItem() {
        loadEquipment();
        return equippedId == null ? null : equipment(equippedId);
    }

    /**
     * Makes the player own an equipped item, e.g. after a shop purchase. Returns true when the item
     * is owned afterwards (also when it already was); false for an unknown or non-equipped item, or
     * when the ownership could not be saved (nothing changes then, so the caller can undo its side).
     */
    public static boolean grantEquipment(String itemId) {
        ItemInfo item = equipment(itemId);
        if (item == null) return false;
        loadEquipment();
        if (ownedEquipment.contains(itemId)) return true;
        ownedEquipment.add(itemId);
        if (!saveEquipment()) {
            ownedEquipment.remove(itemId);
            return false;
        }
        Core.getLogger().info("Equipment owned: " + item.displayName + ".");
        return true;
    }

    /** Equips an owned item, replacing the equipped one. Takes effect when the next run starts. */
    public static boolean equip(String itemId) {
        ItemInfo item = equipment(itemId);
        if (item == null || !isOwned(itemId)) return false;
        String previous = equippedId;
        equippedId = itemId;
        if (!saveEquipment()) {
            equippedId = previous;
            return false;
        }
        Core.getLogger().info("Equipment equipped: " + item.displayName + ".");
        return true;
    }

    /** Takes off the equipped item. Takes effect when the next run starts. */
    public static void unequip() {
        loadEquipment();
        if (equippedId == null) return;
        String previous = equippedId;
        equippedId = null;
        if (saveEquipment()) Core.getLogger().info("Equipment unequipped.");
        else equippedId = previous;
    }

    private static ItemInfo equipment(String itemId) {
        if (itemId == null) return null;
        ItemInfo item = catalog().find(itemId);
        return item != null && item.activationMode == ActivationMode.EQUIPPED ? item : null;
    }

    /** Reads owned/equipped items once. A missing or broken file means nothing is owned. */
    private static void loadEquipment() {
        if (ownedEquipment != null) return;
        ownedEquipment = new LinkedHashSet<String>();
        equippedId = null;
        Properties saved = new Properties();
        try {
            File file = equipmentFile();
            if (!file.exists()) return;
            try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                saved.load(reader);
            }
        } catch (IOException | IllegalArgumentException error) {
            Core.getLogger().warning("Couldn't load equipment, starting with none.");
            return;
        }
        for (String id : saved.getProperty("owned", "").split(","))
            if (equipment(id.trim()) != null) ownedEquipment.add(id.trim());
        String equipped = saved.getProperty("equipped", "").trim();
        equippedId = ownedEquipment.contains(equipped) ? equipped : null;
    }

    /**
     * Writes owned/equipped items. Returns false (and logs) when the file cannot be written.
     * Writes a temporary file first and then replaces the save, like the coin balance, so a crash
     * while saving never leaves a half-written file that would read as "nothing owned".
     */
    private static boolean saveEquipment() {
        Properties saved = new Properties();
        saved.setProperty("owned", String.join(",", ownedEquipment));
        saved.setProperty("equipped", equippedId == null ? "" : equippedId);
        try {
            Path file = equipmentFile().toPath();
            Path temp = file.resolveSibling(EQUIPMENT_FILE + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                saved.store(writer, "Item System equipment (owned, equipped)");
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException error) {
            Core.getLogger().warning("Couldn't save equipment: " + error.getMessage());
            return false;
        }
    }

    /** Next to the game files, like the coin balance and player profile. */
    private static File equipmentFile() throws IOException {
        String path = ItemSystem.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        return new File(new File(URLDecoder.decode(path, "UTF-8")).getParent(), EQUIPMENT_FILE);
    }

    /** General entry point for the shop, HUD and effects teams. */
    public ItemAPI api() { return api; }

    /**
     * Starts a level. Drops leave the play area only when fully below the screen bottom.
     * Ends the previous level first if it was not ended.
     */
    public void beginLevel(int level, int screenWidth, int screenHeight, Entity ship, GameHooks gameHooks) {
        hooks = ItemAPI.required(gameHooks, "hooks");
        ItemAPI.required(ship, "ship");
        endLevel();
        levelNumber = level;
        // LevelRules still takes the old fall speed and ground lifetime (public API); bouncing drops
        // ignore both, so the drop speed and bounce time stand in for them (Team Entities).
        LevelRules rules = new LevelRules("level-" + level, 0, screenWidth, PLAY_AREA_TOP, screenHeight,
            balance.bounceSpeed, balance.dropWidth, balance.dropHeight, balance.bounceMillis,
            dropRules());
        manager.beginLevel(rules, lifePort);
        manager.setUpgradeFloor(balance.upgradeFloor(level)); // upgrade catch-up (Team Entities)
        // Spread shot geometry from the balance file; it does not change during a level (Team Entities).
        if (ship instanceof Ship)
            ((Ship) ship).setSpreadPattern(balance.spreadFrontGap, balance.spreadDiagonal,
                balance.spreadWideDiagonal);
        lastUpdateNanos = -1;
        feedback.clear();
        logger.info("Item system started level " + level + ".");
    }

    /**
     * Part of the stage played so far, 0 to 1 (Team Entities). Upgrades come more often once a stage is
     * half over and the player owns fewer than that stage's floor (drop.upgrade.floor.stageN).
     * Below 0, or never called (boss stages, timed run), means no catch-up.
     */
    public void setStageProgress(double progress) {
        if (isLevelActive()) manager.setStageProgress(progress);
    }

    /** Exclude pauses/countdowns from effect and drop timers. */
	public void pauseClock() { lastUpdateNanos = -1; }

    /** Called only during active gameplay; pauseClock excludes suspended time. */
    public void update(Entity ship, boolean shipAvailable) { update(ship, shipAvailable, false); }

    /**
     * After a level is cleared: every drop still on the field flies to the ship and is picked up,
     * so the level ends tidy (Team Entities). Call every frame until hasDrops() is false.
     */
    public void gatherDrops(Entity ship) { update(ship, true, true); }

    /** True while item drops are still on the field. */
    public boolean hasDrops() { return isLevelActive() && manager.hasDrops(); }

    private void update(Entity ship, boolean shipAvailable, boolean gather) {
        // Spread/pierce reach the ship here, so the shared GameScreen needs no extra call.
        if (ship instanceof Ship) ((Ship) ship).setWeaponMods(spreadCount(), pierceCount());
        if (!isLevelActive()) return;
        long now = System.nanoTime(), delta = 0;
        if (lastUpdateNanos < 0) lastUpdateNanos = now;
        else {
            delta = (now - lastUpdateNanos) / 1_000_000L;
            lastUpdateNanos += delta * 1_000_000L; // Carries the sub-millisecond remainder to the next frame.
        }
        Bounds bounds = new Bounds(ship.getPositionX(), ship.getPositionY(),
            Math.max(1, ship.getWidth()), Math.max(1, ship.getHeight()));
        feedback.advance(delta, bounds);
        manager.update(delta, new PlayerSnapshot(bounds, shipAvailable, shipAvailable), gather);
        feedback.checkFullInventory(api.getView(), bounds, shipAvailable);
    }

    /** Called when an enemy is defeated. Rolls a drop at the enemy's center. */
    public void onEnemyDefeated(Entity enemy, boolean special) {
        if (!isLevelActive()) return;
        api.onEnemyDefeated(special ? DropSource.SPECIAL_ENEMY : DropSource.REGULAR_ENEMY,
            enemy.getPositionX() + enemy.getWidth() / 2.0,
            enemy.getPositionY() + enemy.getHeight() / 2.0);
    }

    /** Called when the player is hit, before taking a life. true means the shield blocked it, so no damage. */
    public boolean tryBlockHit() { return api.tryBlockHit(); }

    /** Uses the item in an inventory slot. Tells the player when the item is already active. */
    public UseResult useSlot(int slot) {
        List<ItemInfo> slots = api.getView().slots;
        ItemInfo item = slot >= 0 && slot < slots.size() ? slots.get(slot) : null;
        usingSlot = true;
        UseResult result;
        try {
            result = api.useSlot(slot);
        } finally {
            usingSlot = false;
        }
        if (result != UseResult.USED) {
            String reason;
            switch (result) {
                case EMPTY_SLOT: reason = "Slot " + ControlKeys.itemSlotLabel(slot) + " is empty"; break;
                case EFFECT_ALREADY_ACTIVE: reason = "This effect is already active"; break;
                case INVALID_SLOT: reason = "Choose an existing item slot"; break;
                case PLAYER_UNAVAILABLE: reason = "Wait until your ship is ready"; break;
                case LEVEL_NOT_ACTIVE: reason = "Start a level to use items"; break;
                default: reason = "The effect cannot be applied right now"; break;
            }
            feedback.action(item == null ? EffectKind.EXTRA_SLOTS : item.effectKind,
                "Item not used", reason, true);
        }
        return result;
    }

    /** Permanent fire rate bonus, in extra shots per second, not counting rapid fire (see fireRateBonus(double)). */
    public double fireRateBonus() { return api.getModifiers().fireRateBonus; }

    /** Rapid fire's share of the base rate plus the temporary boost's extra shots per second. */
    public double fireRateBonus(double baseFireRate) {
        return api.getModifiers().fireRateBonus(baseFireRate);
    }

    /** Extra ship speed to add to the unchanged base speed. */
    public double movementSpeedBonus(double baseSpeed) {
        return api.getModifiers().movementSpeedBonus(baseSpeed);
    }

    /** Extra whole points to add to an earned score. */
    public int scoreBonus(int basePoints) { return api.getModifiers().scoreBonus(basePoints); }

    /** Earned base points with the active score boost applied once. */
    public int scoreFor(int basePoints) { return basePoints + scoreBonus(basePoints); }

    /** Item bullet speed bonus: extra pixels per frame, added on top of the base bullet speed. */
    public double bulletSpeedBonus() { return api.getModifiers().bulletSpeedBonus; }

    /** Extra bullet lanes per shot from spread stacks. */
    public int spreadCount() { return api.getModifiers().spreadCount; }

    /** Extra enemies one bullet passes through, from pierce stacks. */
    public int pierceCount() { return api.getModifiers().pierceCount; }

    /** The item equipped for this run, or null. Fixed when the run starts. */
    public ItemInfo equippedThisRun() {
        for (EffectView effect : api.getView().effects)
            if (effect.item.activationMode == ActivationMode.EQUIPPED) return effect.item;
        return null;
    }

    /** true means enemies must not move (freeze). */
    public boolean enemiesFrozen() { return api.getModifiers().enemyMovementBlocked; }

    /** The running shield effect, or null when the ship has no shield. */
    public EffectView shield() {
        if (!isLevelActive()) return null;
        for (EffectView effect : api.getView().effects)
            if (effect.item.effectKind == EffectKind.SHIELD) return effect;
        return null;
    }

    /** Pixel icons use the same visual language as the inventory and active effects. */
    public void drawDrops(Graphics graphics) {
        for (DropView drop : api.getView().drops)
            ItemArtwork.drawIcon(graphics, drop.item.effectKind,
                (int) drop.bounds.x, (int) drop.bounds.y,
                (int) Math.min(drop.bounds.width, drop.bounds.height));
    }

    /** Paints short bursts and temporary boost thrust without consuming the event queue. */
    public void drawFeedback(Graphics graphics, Entity ship) {
        feedback.drawPulses(graphics);
        for (EffectView effect : api.getView().effects) {
            if (effect.item.effectKind != EffectKind.BOOST) continue;
            Graphics g = graphics.create();
            try {
                g.setColor(new Color(120, 255, 165, 150));
                int x = ship.getPositionX() + ship.getWidth() / 2;
                int y = ship.getPositionY() + ship.getHeight() + 2;
                for (int i = 0; i < 3; i++)
                    g.fillRect(x - 5 + i * 4, y + (int) (feedback.time() / 80 + i) % 3, 2, 5);
            } finally { g.dispose(); }
        }
    }

    /** Compact, two-line explanation above the player. Time freezes while gameplay is paused. */
    public void drawNotice(Graphics graphics, int width, int height, java.awt.Font font) {
        feedback.drawNotice(graphics, width, height, font);
    }

    /** Ends the level. Does nothing if no level is active. */
    public void endLevel() {
        if (!isLevelActive()) return;
        manager.endLevel();
        feedback.clear();
        logger.info("Item system ended level " + levelNumber + ".");
    }

    /** True when picked-up items start at once instead of going to a slot (Team Entities). */
    public boolean autoUse() { return balance.autoUse; }

    /** Message about the last item action for the screen, or null when there is nothing recent. */
    public String getNotice() {
        return feedback.notice();
    }

    /** Events for sound/effects. Cleared once drained. */
    public List<ItemEvent> drainEvents() { return api.drainEvents(); }

    public int getLevelNumber() { return levelNumber; }

    private boolean isLevelActive() { return hooks != null && manager.isLevelActive(); }

    /** Life item: +1 life below the cap, a score bonus at the cap. The item is consumed either way. */
    private final LifePort lifePort = new LifePort() {
        public boolean canAddLife() { return true; }
        public boolean tryAddLife() {
            if (hooks.getLives() < maxLives) {
                hooks.addLife();
                logger.info("Life item: +1 life, now " + hooks.getLives() + ".");
                feedback.show(EffectKind.LIFE, "Life +1", "Extra life applied", false);
            } else {
                int points = scoreFor(balance.lifeCapBonusScore);
                hooks.addScore(points);
                logger.info("Life item at max lives: +" + points + " score.");
                feedback.show(EffectKind.LIFE, "Max lives: +" + points, "Life converted to bonus points", false);
            }
            return true;
        }
    };

    /** Logs every item event and turns the ones the player cares about into a notice. */
    private void onEvent(ItemEvent event) {
        ItemInfo item = api.getItemInfo(event.itemId);
        String name = item == null ? event.itemId : item.displayName;
        switch (event.type) {
            case ITEM_SPAWNED:
                logger.info("Item dropped: " + name + position(event.bounds) + ".");
                break;
            case ITEM_COLLECTED:
                feedback.pulse(item.effectKind, event.bounds, false);
                if (event.slotIndex != null) {
                    int key = event.slotIndex + 1;
                    logger.info("Item collected: " + name + " stored in slot " + key + ".");
                    feedback.show(item.effectKind, name + " collected",
                        "Press " + ControlKeys.itemSlotLabel(event.slotIndex) + " / " + ItemArtwork.describe(item, null), false);
                } else {
                    logger.info("Item collected: " + name + ".");
                    // An auto-use pickup counts as using the item (Team Entities).
                    if (item.activationMode == ActivationMode.MANUAL) Core.getAchievementManager().recordItemUsed();
                }
                break;
            case ITEM_USED:
                logger.info("Item used: " + name + " from slot " + (event.slotIndex + 1) + ".");
                Core.getAchievementManager().recordItemUsed();
                if (item != null)
                    feedback.action(item.effectKind, name + " active",
                        ItemArtwork.describe(item, effect(event.effectId)), false);
                break;
            case EFFECT_STARTED: {
                Integer stacks = stacksOf(event.effectId);
                String label = name + (stacks != null ? " x" + stacks : "");
                logger.info("Item effect started: " + label + ".");
                feedback.pulse(item.effectKind, null, true);
                if (!usingSlot)
                    feedback.show(item.effectKind, name + " applied",
                        ItemArtwork.describe(item, effect(event.effectId)), false);
                break;
            }
            case EFFECT_ENDED:
                logger.info("Item effect ended: " + name + " (" + event.endReason + ").");
                if (event.endReason == EffectEndReason.EXPIRED && feedback.notice() == null)
                    feedback.show(item.effectKind, name + " ended", "Effect duration finished", false);
                break;
            case SHIELD_BLOCKED:
                logger.info("Item effect: shield blocked a hit.");
                feedback.action(EffectKind.SHIELD, "Shield blocked a hit", "No life lost", false);
                feedback.pulse(EffectKind.SHIELD, null, true);
                break;
            case ITEM_EXPIRED:
                logger.info("Item drop disappeared: " + name + ".");
                break;
            case ITEM_CONVERTED: {
                ItemInfo magnet = equippedThisRun();
                int points = magnet == null ? 0 : magnet.magnitude.intValue();
                hooks.addScore(points);
                logger.info("Magnet: " + name + " could not be kept, +" + points + " score.");
                feedback.show(item.effectKind, name + " +" + points, "Magnet converted the pickup", false);
                break;
            }
            case ITEM_GRANTED:
                if (event.slotIndex != null)
                    feedback.show(item.effectKind, name + " received",
                        "Press " + ControlKeys.itemSlotLabel(event.slotIndex) + " / " + ItemArtwork.describe(item, null), false);
                break;
            default:
                logger.info("Item event " + event.type + ": " + name + ".");
                break;
        }
    }

    private EffectView effect(Long id) {
        if (id != null)
            for (EffectView effect : api.getView().effects)
                if (effect.effectId == id) return effect;
        return null;
    }

    private Integer stacksOf(Long effectId) {
        if (effectId == null) return null;
        for (EffectView effect : api.getView().effects)
            if (effect.effectId == effectId) return effect.stacks;
        return null;
    }

    private static String position(Bounds bounds) {
        return bounds == null ? "" : " at (" + (int) bounds.x + ", " + (int) bounds.y + ")";
    }

    private Map<DropSource, DropRule> dropRules() {
        Map<String, Double> weights = balance.dropWeights;
        Map<String, Double> decay = new java.util.LinkedHashMap<String, Double>();
        decay.put("spread", balance.spreadDropDecay);
        decay.put("bullet_power", balance.bulletPowerDropDecay);
        Map<DropSource, DropRule> rules = new EnumMap<DropSource, DropRule>(DropSource.class);
        rules.put(DropSource.REGULAR_ENEMY, new DropRule(balance.regularDropProbability, weights, decay));
        rules.put(DropSource.SPECIAL_ENEMY, new DropRule(balance.specialDropProbability, weights, decay));
        return rules;
    }

    /** Color that marks an item kind, shared by the drops and the HUD. */
    public static Color colorOf(EffectKind kind) {
        switch (kind) {
            case LIFE: return Color.RED;
            case SHIELD: return Color.CYAN;
            case SPREAD: return new Color(0, 160, 255);
            case BULLET_POWER: return new Color(255, 120, 60);
            case FREEZE: return new Color(150, 150, 255);
            case BOOST: return new Color(100, 255, 100);
            case SCORE_BOOST: return new Color(255, 100, 200);
            default: return Color.WHITE;
        }
    }
}
