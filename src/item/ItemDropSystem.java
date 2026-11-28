package item;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import item.ItemAPI.*;

/**
 * Owns spawning, bouncing off all four walls, magnet attraction, swept contact and offscreen removal
 * (bouncing movement: Team Entities).
 * No separate files, no Entity subclass, no inventory/effect changes, no game object references.
 * No event queue here. The manager receives spawn/expire/contact results and records them as events.
 */
class ItemDropSystem {
    private final ItemDefinitions definitions;
    private final Random random;
    private final ItemBalance balance;
    private final Map<Long, DroppedItem> items = new LinkedHashMap<Long, DroppedItem>();
    private LevelRules rules;
    private long nextDropId = 1;
    /** Upgrade catch-up state for the current stage (Team Entities). */
    private double stageProgress = -1;
    private int upgradeFloor;
    private int catchUpKills;

    private static class DroppedItem {
        final long id;
        final ItemInfo info;
        double x, y, velocityX, velocityY;
        /** Time spent bouncing; past drop.bounceMillis the drop stops bouncing and leaves (Team Entities). */
        double ageMillis;
        boolean magnetized;

        DroppedItem(long id, ItemInfo info, double x, double y) {
            this.id = id;
            this.info = info;
            this.x = x;
            this.y = y;
        }

        DropView view(LevelRules rules) {
            return new DropView(id, info,
                new Bounds(x, y, rules.pickupWidth, rules.pickupHeight), false, magnetized);
        }
    }

    ItemDropSystem(ItemDefinitions definitions, Random random) {
        this(definitions, ItemBalance.defaults(), random);
    }

    ItemDropSystem(ItemDefinitions definitions, ItemBalance balance, Random random) {
        this.definitions = ItemAPI.required(definitions, "definitions");
        this.balance = ItemAPI.required(balance, "balance");
        this.random = ItemAPI.required(random, "random");
    }

    /** Installs new validated rules and clears the drop list. The dropId counter is never reused. */
    void beginLevel(LevelRules rules) {
        this.rules = ItemAPI.required(rules, "rules");
        items.clear();
        stageProgress = -1;
        upgradeFloor = 0;
        catchUpKills = 0;
    }

    /** Part of the stage played, 0 to 1, or below 0 when unknown (no catch-up) (Team Entities). */
    void setStageProgress(double progress) { stageProgress = progress; }

    /** Upgrades the player should own in this stage; 0 turns catch-up off (Team Entities). */
    void setUpgradeFloor(int floor) { upgradeFloor = Math.max(0, floor); }

    /**
     * Upgrade chance with catch-up (Team Entities): once balance.catchUpCheckAt of the stage has passed
     * and the player owns fewer upgrades than the floor, at least catchUpStart plus catchUpStep for every
     * kill since, and certain from catchUpForceAt. Counts owned stacks, so a missed drop keeps helping.
     */
    private double withCatchUp(double chance, Map<String, Integer> stacks) {
        if (chance <= 0 || stageProgress < balance.catchUpCheckAt || ownedUpgrades(stacks) >= upgradeFloor)
            return chance;
        if (stageProgress >= balance.catchUpForceAt) return 1;
        return Math.max(chance, Math.min(1, balance.catchUpStart + balance.catchUpStep * catchUpKills++));
    }

    private int ownedUpgrades(Map<String, Integer> stacks) {
        int owned = 0;
        if (stacks != null)
            for (Map.Entry<String, Integer> entry : stacks.entrySet())
                if (isUpgrade(entry.getKey()) && entry.getValue() != null) owned += entry.getValue();
        return owned;
    }

    /**
     * Rolls one kind using the source's probability/weights, registers it, then returns an immutable View.
     * null only for a normal no-drop. p==0/1 consumes no probability roll. A single candidate skips the pick roll too.
     * Weight order is LinkedHashMap insertion order. Centers on cx/cy and clamps to the play area.
     * Regular 0.15 / special 1.0 are demo values; the actual values come from LevelRules.
     * Preventing duplicate defeat notices for the same enemy is the game's job.
     * stacks (itemId → current stack count) lowers stacking items' weights and removes them at max stacks.
     * No drop (null) when every candidate is removed.
     */
    DropView spawn(DropSource source, double cx, double cy, Map<String, Integer> stacks) {
        DropRule rule = checkSpawn(source, cx, cy, stacks);
        if (!roll(rule.probability)) return null;
        return place(rule, null, cx, cy, stacks);
    }

    /**
     * All drops for one defeated enemy (Team Entities). With drop.upgrade.probability set, a regular
     * enemy rolls twice: basic items (life, shield, freeze, boost, score boost) at the rule's probability,
     * and upgrades (rapid fire, bullet speed, spread, pierce) at drop.upgrade.probability times how far
     * their weights have decayed. So one kill may drop up to two items. Otherwise one roll, as spawn().
     */
    List<DropView> spawnAll(DropSource source, double cx, double cy, Map<String, Integer> stacks) {
        DropRule rule = checkSpawn(source, cx, cy, stacks);
        List<DropView> spawned = new ArrayList<DropView>();
        if (balance.upgradeDropProbability == null || source != DropSource.REGULAR_ENEMY) {
            DropView drop = spawn(source, cx, cy, stacks);
            if (drop != null) spawned.add(drop);
            return spawned;
        }
        // Upgrade chance shrinks with the decayed share of the upgrade weights.
        double base = 0, decayed = 0;
        for (Map.Entry<String, Double> entry : rule.weights.entrySet()) {
            if (!isUpgrade(entry.getKey())) continue;
            base += Math.max(0, entry.getValue());
            decayed += effectiveWeight(rule, entry.getKey(), entry.getValue(), stacks);
        }
        double upgradeChance = base <= 0 ? 0 : balance.upgradeDropProbability * decayed / base;
        // A zero decayed weight means every upgrade is maxed: nothing left to catch up on.
        upgradeChance = decayed <= 0 ? 0 : withCatchUp(upgradeChance, stacks);
        if (roll(upgradeChance)) {
            DropView drop = place(rule, Boolean.TRUE, cx, cy, stacks);
            if (drop != null) spawned.add(drop);
        }
        if (roll(rule.probability)) {
            DropView drop = place(rule, Boolean.FALSE, cx, cy, stacks);
            if (drop != null) spawned.add(drop);
        }
        return spawned;
    }

    /** Upgrades stack for the run (EffectKind.isRunUpgrade); every other kind is a basic item. */
    private boolean isUpgrade(String itemId) {
        ItemInfo info = definitions.find(itemId);
        return info != null && info.effectKind.isRunUpgrade();
    }

    /** p==0/1 consumes no random number. */
    private boolean roll(double probability) {
        if (probability <= 0) return false;
        return probability >= 1 || random.nextDouble() < probability;
    }

    private DropRule checkSpawn(DropSource source, double cx, double cy, Map<String, Integer> stacks) {
        requireActive();
        ItemAPI.required(source, "source");
        ItemAPI.finite(cx, "cx");
        ItemAPI.finite(cy, "cy");
        ItemAPI.required(stacks, "stacks");
        DropRule rule = rules.dropRules.get(source);
        if (rule == null) throw new IllegalStateException("missing drop rule: " + source);
        return rule;
    }

    /** Picks a kind by weight (upgrades only, basic only, or any when null) and registers the drop. */
    private DropView place(DropRule rule, Boolean upgrades, double cx, double cy, Map<String, Integer> stacks) {
        Map<String, Double> weights = new LinkedHashMap<String, Double>();
        double total = 0;
        String selected = null;
        for (Map.Entry<String, Double> entry : rule.weights.entrySet()) {
            if (upgrades != null && isUpgrade(entry.getKey()) != upgrades) continue;
            double weight = effectiveWeight(rule, entry.getKey(), entry.getValue(), stacks);
            if (weight > 0) {
                weights.put(entry.getKey(), weight);
                total += weight;
                selected = entry.getKey();
            }
        }
        if (!Double.isFinite(total)) throw new IllegalStateException("invalid drop weights");
        if (weights.isEmpty()) return null; // every remaining candidate is at max stacks
        if (weights.size() > 1) {
            double target = random.nextDouble() * total;
            double cumulative = 0;
            for (Map.Entry<String, Double> entry : weights.entrySet()) {
                cumulative += entry.getValue();
                if (target < cumulative) {
                    selected = entry.getKey();
                    break;
                }
            }
        }
        ItemInfo info = definitions.find(selected);
        if (info == null) throw new IllegalStateException("unknown item: " + selected);
        if (nextDropId == Long.MAX_VALUE) throw new IllegalStateException("drop IDs exhausted");
        double x = Math.max(rules.left, Math.min(rules.right - rules.pickupWidth,
            cx - rules.pickupWidth / 2));
        double y = Math.max(rules.top, Math.min(rules.floorY - rules.pickupHeight,
            cy - rules.pickupHeight / 2));
        DroppedItem item = new DroppedItem(nextDropId++, info, x, y);
        chooseDirection(item);
        items.put(item.id, item);
        return item.view(rules);
    }

    /**
     * Advances game time only (no wall clock). Splits paths at every wall bounce and magnet-range
     * entry, so contact tests follow the actual path.
     * Drops fly in a straight line and bounce off all four walls of the play area for
     * drop.bounceMillis; after that they stop bouncing and are removed once fully outside it.
     * Contact before an offscreen exit wins; the manager then decides pickup/conversion.
     * Uncollectable ordinary drops keep moving and expire on a later update if necessary.
     */
    Frame advance(long delta, PlayerSnapshot player) { return advance(delta, player, false); }

    Frame advance(long delta, PlayerSnapshot player, boolean magnetEquipped) {
        return advance(delta, player, magnetEquipped, false);
    }

    /**
     * gather: the level is over, so every drop flies straight to the player at drop.gatherSpeed,
     * whatever its distance (Team Entities).
     */
    Frame advance(long delta, PlayerSnapshot player, boolean magnetEquipped, boolean gather) {
        requireActive();
        if (delta < 0) throw new IllegalArgumentException("negative delta");
        ItemAPI.required(player, "player");
        List<DropView> expired = new ArrayList<DropView>();
        List<DropView> contacts = new ArrayList<DropView>();
        Iterator<DroppedItem> iterator = items.values().iterator();
        while (iterator.hasNext()) {
            DroppedItem item = iterator.next();
            DropView contact = outside(item) ? null : move(item, delta, player, magnetEquipped, gather);
            if (contact != null) {
                contacts.add(contact);
            } else if (outside(item)) {
                iterator.remove();
                expired.add(item.view(rules));
            }
        }
        return new Frame(expired, contacts);
    }

    /**
     * Random direction with both parts at least about a third of the speed, so a drop never
     * slides along one wall. An axis with no room (area exactly one drop wide/high) gets no speed.
     */
    private void chooseDirection(DroppedItem item) {
        double speed = balance.bounceSpeed * (0.85 + random.nextDouble() * 0.3);
        double angle = Math.toRadians(20 + random.nextDouble() * 50);
        item.velocityX = (random.nextBoolean() ? 1 : -1) * speed * Math.cos(angle);
        item.velocityY = (random.nextBoolean() ? 1 : -1) * speed * Math.sin(angle);
        if (rules.right - rules.pickupWidth <= rules.left) item.velocityX = 0;
        if (rules.floorY - rules.pickupHeight <= rules.top) item.velocityY = 0;
        if (item.velocityX == 0 && item.velocityY == 0) item.velocityY = speed;
        item.ageMillis = 0;
    }

    /** Fully outside the play area. Only possible after the bouncing time ends. */
    private boolean outside(DroppedItem item) {
        return item.x + rules.pickupWidth <= rules.left || item.x >= rules.right
            || item.y + rules.pickupHeight <= rules.top || item.y >= rules.floorY;
    }

    /** Milliseconds until a wall along one axis, or infinity when moving away from walls/no room. */
    private static double wallMillis(double position, double velocity, double low, double high) {
        if (velocity == 0 || high <= low) return Double.POSITIVE_INFINITY;
        double wall = velocity > 0 ? high : low;
        return Math.max(0, (wall - position) / velocity * 1000);
    }

    private DropView move(DroppedItem item, long delta, PlayerSnapshot player,
                          boolean magnetEquipped, boolean gather) {
        boolean attract = (magnetEquipped || gather) && player.canPickup;
        double pullSpeed = gather ? Math.max(balance.gatherSpeed, balance.magnetSpeed) : balance.magnetSpeed;
        if (attract && gather) item.magnetized = true;
        double targetX = player.bounds.x + player.bounds.width / 2 - rules.pickupWidth / 2;
        double targetY = player.bounds.y + player.bounds.height / 2 - rules.pickupHeight / 2;
        if (attract && Math.hypot(item.x - targetX, item.y - targetY) <= balance.magnetRange)
            item.magnetized = true;
        DropView contact = player.canPickup ? contactAlong(item, 0, 0, player.bounds) : null;
        double right = rules.right - rules.pickupWidth, bottom = rules.floorY - rules.pickupHeight;
        double remaining = delta;
        while (remaining > 0 && !outside(item)) {
            if (attract && item.magnetized) {
                double dx = targetX - item.x, dy = targetY - item.y;
                double distance = Math.hypot(dx, dy);
                // Stop at the player rather than overshooting and oscillating on long frames.
                double fraction = distance == 0 ? 0
                    : Math.min(1, pullSpeed * (remaining / 1000.0) / distance);
                dx *= fraction;
                dy *= fraction;
                if (contact == null) contact = contactAlong(item, dx, dy, player.bounds);
                item.x += dx;
                item.y += dy;
                break;
            }

            boolean bouncing = item.ageMillis < balance.bounceMillis;
            double wallX = bouncing ? wallMillis(item.x, item.velocityX, rules.left, right) : Double.POSITIVE_INFINITY;
            double wallY = bouncing ? wallMillis(item.y, item.velocityY, rules.top, bottom) : Double.POSITIVE_INFINITY;
            double bounceLeft = bouncing ? balance.bounceMillis - item.ageMillis : Double.POSITIVE_INFINITY;
            double step = Math.min(remaining, Math.min(bounceLeft, Math.min(wallX, wallY)));
            double dx = item.velocityX * (step / 1000.0), dy = item.velocityY * (step / 1000.0);
            double entry = attract && !item.magnetized
                ? rangeEntry(item.x - targetX, item.y - targetY, dx, dy, balance.magnetRange)
                : Double.POSITIVE_INFINITY;
            boolean captured = entry <= 1;
            if (captured) {
                step *= entry;
                dx *= entry;
                dy *= entry;
            }
            if (contact == null && player.canPickup) contact = contactAlong(item, dx, dy, player.bounds);
            item.x += dx;
            item.y += dy;
            remaining = Math.max(0, remaining - step);
            item.ageMillis = step == bounceLeft ? balance.bounceMillis : item.ageMillis + step;
            if (captured) {
                item.magnetized = true;
                // Range entry may coincide with contact at the segment endpoint.
                DropView attractedContact = contactAlong(item, 0, 0, player.bounds);
                if (attractedContact != null) contact = attractedContact;
            } else {
                // Bounce: snap to the wall and turn back along that axis.
                if (step == wallX) {
                    item.x = item.velocityX > 0 ? right : rules.left;
                    item.velocityX = -item.velocityX;
                }
                if (step == wallY) {
                    item.y = item.velocityY > 0 ? bottom : rules.top;
                    item.velocityY = -item.velocityY;
                }
            }
        }
        return contact;
    }

    /** First entry of a line segment into the center-based magnet circle; infinity means no entry. */
    private static double rangeEntry(double x, double y, double dx, double dy, double radius) {
        double c = x * x + y * y - radius * radius;
        if (c <= 0) return 0;
        double a = dx * dx + dy * dy;
        if (a == 0) return Double.POSITIVE_INFINITY;
        double b = x * dx + y * dy;
        if (b >= 0) return Double.POSITIVE_INFINITY;
        double discriminant = b * b - a * c;
        if (discriminant < 0) return Double.POSITIVE_INFINITY;
        double entry = c / (-b + Math.sqrt(discriminant));
        return entry <= 1 ? entry : Double.POSITIVE_INFINITY;
    }

    /** Sweeps the item's top-left through the player rectangle expanded by the item's size. */
    private DropView contactAlong(DroppedItem item, double dx, double dy, Bounds player) {
        double[] horizontal = axisTimes(item.x, dx, player.x - rules.pickupWidth, player.x + player.width);
        double[] vertical = axisTimes(item.y, dy, player.y - rules.pickupHeight, player.y + player.height);
        double enter = Math.max(0, Math.max(horizontal[0], vertical[0]));
        double exit = Math.min(1, Math.min(horizontal[1], vertical[1]));
        if (enter > exit) return null;
        return new DropView(item.id, item.info,
            new Bounds(item.x + dx * enter, item.y + dy * enter, rules.pickupWidth, rules.pickupHeight),
            false, item.magnetized);
    }

    private static double[] axisTimes(double position, double movement, double low, double high) {
        if (movement == 0) return position >= low && position <= high
            ? new double[]{Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}
            : new double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        double first = (low - position) / movement, last = (high - position) / movement;
        return new double[]{Math.min(first, last), Math.max(first, last)};
    }

    /**
     * Removes one contact the manager picked up successfully. Always completes within a single valid update.
     * An unknown ID is an integration bug, so it throws. Never silently fail to remove after the item was granted.
     */
    void completePickup(long dropId) {
        if (items.remove(dropId) == null)
            throw new IllegalStateException("unknown drop: " + dropId);
    }

    /** Immutable View copies in dropId order. The source drops/list are never exposed. */
    List<DropView> snapshot() {
        List<DropView> result = new ArrayList<DropView>();
        for (DroppedItem item : items.values()) result.add(item.view(rules));
        return ItemAPI.frozen(result, false);
    }

    /** Clears drops and current rules when a stage ends. Creates no expiry events and keeps the IDs. */
    void clear() {
        items.clear();
        rules = null;
    }

    /**
     * Weight after stacking. An item at max stacks never drops. The decay counts the item's own stacks,
     * or with drop.upgradeDecay.shared the stacks of every upgrade together, so the first one or two
     * upgrades come easily and later ones get rare (Team Entities).
     */
    private double effectiveWeight(DropRule rule, String itemId, double weight, Map<String, Integer> stacks) {
        if (weight <= 0) return 0;
        Integer count = stacks.get(itemId);
        ItemInfo info = definitions.find(itemId);
        if (count != null && info != null && info.maxStacks != null && count >= info.maxStacks) return 0;
        int decayCount = count == null ? 0 : count;
        if (balance.sharedUpgradeDecay) {
            decayCount = 0;
            for (int stack : stacks.values()) decayCount += Math.max(0, stack);
        }
        return decayCount <= 0 ? weight : rule.weightFor(itemId, weight, decayCount);
    }

    /** Internal result. A plain value object with no movement/contact logic. */
    static final class Frame {
        final List<DropView> expired, contacts;
        Frame(List<DropView> expired, List<DropView> contacts) {
            this.expired = ItemAPI.frozen(expired, false);
            this.contacts = ItemAPI.frozen(contacts, false);
        }
    }
    private void requireActive() {
        if (rules == null) throw new IllegalStateException("level is not active");
    }
}
