package entity.boss;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import engine.DrawManager;
import entity.Entity;
import entity.enemy.EnemyType;
import entity.enemy.SpawnRequest;

/**
 * Boss 2: a mid-sized, fast ship with less health than the fortress.
 *
 * Patterns:
 * <ol>
 * <li>Patrol (default): roams left and right, fires down now and then,
 * and summons enemy formations (normal, shooter, bat).</li>
 * <li>Charge: a warning lane, then it charges down spraying bullets to
 * both sides, leaves at the bottom and comes back in from the top.</li>
 * <li>Drones: for a few seconds homing drones come in from every side;
 * they chase the player for a while, then fly straight on and leave.</li>
 * </ol>
 * After each patrol it uses charge or drones, never the same one twice in a
 * row. Touching its body hurts the player.
 */
public final class RaiderBoss extends AbstractBoss {

	/** What the raider is doing. */
	private enum State { ENTER, PATROL, CHARGE_WARNING, CHARGE, RETURN, DRONES }

	/** Body width. */
	static final int WIDTH = 100;
	/** Body height. */
	static final int HEIGHT = 60;
	/** Gap between the HUD line and the body at rest, below the health bar. */
	private static final int HOME_GAP = 36;
	/** Distance of the guns from the body sides. */
	private static final int GUN_INSET = 20;
	/** Enemy types it can summon, with their balance keys. */
	private static final EnemyType[] SUMMON_TYPES = {
			EnemyType.NORMAL, EnemyType.SHOOTER, EnemyType.BAT };
	/** Distance kept from the screen sides while roaming. */
	private static final int SIDE_MARGIN = 8;
	/** Chance per frame to turn around while roaming. */
	private static final double TURN_CHANCE = 0.006;
	/** Drones never appear closer than this to the player. */
	private static final double DRONE_SAFE_DISTANCE = 150;
	/** Bullet angle off horizontal while charging, radians. */
	private static final double SPRAY_ANGLE = 0.35;

	private static final Color BODY = new Color(240, 150, 120);
	private static final Color BODY_DARK = new Color(200, 105, 85);
	private static final Color COCKPIT = new Color(20, 90, 130);
	private static final Color GUN = new Color(150, 150, 160);

	private final int maxHealth;
	private int health;
	private int flash;
	private double x;
	private double y;
	private final double homeY;
	private int direction = 1;

	private State state = State.ENTER;
	private State lastSpecial;
	private int stateFrames;
	private int fireTimer;
	private int summonTimer;
	private int spawnTimer;
	private final List<HomingDrone> drones = new ArrayList<HomingDrone>();

	/**
	 * @param balance Boss balance values.
	 * @param level   Stage number, for the stage multipliers.
	 * @param random  Random source.
	 * @param width   Screen width.
	 * @param height  Screen height.
	 * @param top     Top of the play area.
	 */
	public RaiderBoss(final BossBalance balance, final int level,
			final Random random, final int width, final int height,
			final int top) {
		super(balance, level, random, width, height, top);
		this.maxHealth = balance.count("raider.health");
		this.health = this.maxHealth;
		this.x = (width - WIDTH) / 2.0;
		this.y = top - HEIGHT;
		this.homeY = top + HOME_GAP;
		this.summonTimer = balance.frames("raider.patrol.summonGap");
	}

	/* ---------------- Frame logic ---------------- */

	@Override
	protected void tickParts(final double playerX, final double playerY) {
		if (this.flash > 0)
			this.flash--;
		Iterator<HomingDrone> it = this.drones.iterator();
		while (it.hasNext()) {
			HomingDrone drone = it.next();
			drone.update(playerX, playerY);
			if (drone.isGone(this.width, this.height, this.top))
				it.remove();
		}
	}

	@Override
	protected void act(final double playerX, final double playerY) {
		switch (this.state) {
		case ENTER:
		case RETURN:
			this.y += speed("raider.charge.returnSpeed");
			if (this.y >= this.homeY) {
				this.y = this.homeY;
				startPatrol();
			}
			break;
		case PATROL:
			roam(1);
			if (--this.fireTimer <= 0) {
				double speed = bulletSpeed("raider.patrol.bulletSpeed");
				fire(this.x + GUN_INSET, this.y + HEIGHT, Math.PI / 2, speed);
				fire(this.x + WIDTH - GUN_INSET, this.y + HEIGHT, Math.PI / 2, speed);
				this.fireTimer = gapFrames("raider.patrol.fireGap");
			}
			if (--this.summonTimer <= 0) {
				this.enemies.spawn(SpawnRequest.of(summonType()), playerX,
						playerY);
				this.summonTimer = this.balance.frames("raider.patrol.summonGap");
			}
			if (--this.stateFrames <= 0)
				startSpecial();
			break;
		case CHARGE_WARNING:
			if (--this.stateFrames <= 0) {
				this.state = State.CHARGE;
				this.fireTimer = 0;
			}
			break;
		case CHARGE:
			this.y += speed("raider.charge.speed");
			if (--this.fireTimer <= 0) {
				double speed = bulletSpeed("raider.charge.bulletSpeed");
				double sideY = this.y + HEIGHT / 2.0;
				fire(this.x, sideY, Math.PI - SPRAY_ANGLE, speed);
				fire(this.x + WIDTH, sideY, SPRAY_ANGLE, speed);
				this.fireTimer = gapFrames("raider.charge.fireGap");
			}
			if (this.y > this.height) {
				this.y = this.top - HEIGHT - 10;
				this.state = State.RETURN;
			}
			break;
		default: // DRONES
			roam(0.5);
			if (--this.spawnTimer <= 0) {
				spawnDrone(playerX, playerY);
				this.spawnTimer = gapFrames("raider.drones.spawnGap");
			}
			if (--this.stateFrames <= 0)
				startPatrol();
			break;
		}
	}

	private void startPatrol() {
		this.state = State.PATROL;
		this.stateFrames = this.balance.frames("raider.patrol.time");
		this.fireTimer = gapFrames("raider.patrol.fireGap");
	}

	private void startSpecial() {
		State next = this.lastSpecial == State.CHARGE_WARNING ? State.DRONES
				: this.lastSpecial == State.DRONES ? State.CHARGE_WARNING
				: this.random.nextBoolean() ? State.CHARGE_WARNING : State.DRONES;
		this.lastSpecial = next;
		this.state = next;
		if (next == State.CHARGE_WARNING) {
			int warning = this.balance.frames("raider.charge.warning");
			this.stateFrames = warning;
			double laneTop = this.y + HEIGHT;
			this.hazards.add(new BossHazard(this.x, laneTop, WIDTH,
					this.height - laneTop, warning, 0, BODY));
		} else {
			this.stateFrames = this.balance.frames("raider.drones.time");
			this.spawnTimer = 0;
		}
	}

	/** @return A summon type drawn by the raider.patrol.summon.* weights. */
	private EnemyType summonType() {
		double total = 0;
		for (EnemyType type : SUMMON_TYPES)
			total += summonWeight(type);
		if (total <= 0)
			return EnemyType.NORMAL;
		double roll = this.random.nextDouble() * total;
		for (EnemyType type : SUMMON_TYPES) {
			roll -= summonWeight(type);
			if (roll < 0)
				return type;
		}
		return EnemyType.NORMAL;
	}

	private double summonWeight(final EnemyType type) {
		return this.balance.get("raider.patrol.summon." + type.getBalanceKey());
	}

	/** Moves left and right, turning at the sides and now and then. */
	private void roam(final double speedFactor) {
		this.x += this.direction * speed("raider.speed") * speedFactor;
		if (this.x <= SIDE_MARGIN) {
			this.x = SIDE_MARGIN;
			this.direction = 1;
		} else if (this.x >= this.width - WIDTH - SIDE_MARGIN) {
			this.x = this.width - WIDTH - SIDE_MARGIN;
			this.direction = -1;
		} else if (this.random.nextDouble() < TURN_CHANCE) {
			this.direction = -this.direction;
		}
	}

	private void spawnDrone(final double playerX, final double playerY) {
		double sx = 0;
		double sy = 0;
		for (int attempt = 0; attempt < 12; attempt++) {
			int side = this.random.nextInt(4);
			double alongX = 10 + this.random.nextDouble() * (this.width - 20);
			double alongY = this.top + 20 + this.random.nextDouble()
					* (this.height - this.top - 60);
			if (side == 0) {
				sx = -HomingDrone.SIZE;
				sy = alongY;
			} else if (side == 1) {
				sx = this.width + HomingDrone.SIZE;
				sy = alongY;
			} else if (side == 2) {
				sx = alongX;
				sy = this.top + HomingDrone.SIZE;
			} else {
				sx = alongX;
				sy = this.height + HomingDrone.SIZE;
			}
			if (Math.hypot(sx - playerX, sy - playerY) >= DRONE_SAFE_DISTANCE)
				break;
		}
		this.drones.add(new HomingDrone(sx, sy,
				Math.atan2(playerY - sy, playerX - sx),
				speed("raider.drones.speed"),
				Math.toRadians(this.balance.get("raider.drones.turnDegrees")),
				this.balance.frames("raider.drones.chaseTime"),
				this.balance.count("raider.drones.health")));
	}

	/* ---------------- Hits ---------------- */

	@Override
	public BossHit hit(final Entity bullet) {
		if (!isFighting())
			return null;
		for (HomingDrone drone : this.drones) {
			if (!drone.touches(bullet))
				continue;
			if (!drone.takeHit())
				return BossHit.damage(drone.getX(), drone.getY());
			return new BossHit((int) Math.round(this.balance.get(
					"raider.drones.points")), drone.getX(), drone.getY(),
					true, true, false);
		}
		if (!bodyOnScreen() || !BossHazard.overlaps(this.x, this.y, WIDTH,
				HEIGHT, bullet))
			return null;
		this.health--;
		this.flash = FLASH_FRAMES;
		double hitX = bullet.getPositionX() + bullet.getWidth() / 2.0;
		if (this.health > 0)
			return BossHit.damage(hitX, this.y + HEIGHT);
		defeat();
		return new BossHit((int) Math.round(this.balance.get(
				"raider.defeatPoints")), this.x + WIDTH / 2.0,
				this.y + HEIGHT / 2.0, true, true, true);
	}

	@Override
	public boolean hurts(final Entity ship) {
		if (super.hurts(ship))
			return true;
		if (!isFighting())
			return false;
		if (bodyOnScreen() && BossHazard.overlaps(this.x, this.y, WIDTH,
				HEIGHT, ship))
			return true;
		for (HomingDrone drone : this.drones)
			if (drone.touches(ship)) {
				drone.crash();
				return true;
			}
		return false;
	}

	@Override
	protected void clearParts() {
		this.drones.clear();
	}

	private boolean bodyOnScreen() {
		return this.y + HEIGHT > this.top && this.y < this.height;
	}

	/* ---------------- Drawing ---------------- */

	@Override
	public void draw(final DrawManager drawManager) {
		drawHazards(drawManager);
		boolean visible = drawDeath(drawManager, this.x + WIDTH / 2.0,
				this.y + HEIGHT / 2.0, WIDTH / 2.0, HEIGHT / 2.0);
		if (visible && bodyOnScreen())
			drawBody(drawManager);
		if (getDeathFrames() < 0)
			for (HomingDrone drone : this.drones)
				drone.draw(drawManager);
	}

	private void drawBody(final DrawManager drawManager) {
		int shake = this.state == State.CHARGE_WARNING
				? (this.frame % 4 < 2 ? -2 : 2) : 0;
		int bx = (int) Math.round(this.x) + shake;
		int by = (int) Math.round(this.y);
		boolean white = this.flash > 0;
		Color body = white ? Color.WHITE : BODY;
		Color dark = white ? Color.WHITE : BODY_DARK;
		// Wings, fuselage, guns under the wings, cockpit, engine flame.
		box(drawManager, bx, by + 19, WIDTH, 20, dark);
		box(drawManager, bx + 4, by + 39, 12, 4, dark);
		box(drawManager, bx + WIDTH - 16, by + 39, 12, 4, dark);
		box(drawManager, bx + 25, by, WIDTH - 50, HEIGHT, body);
		box(drawManager, bx + WIDTH / 2 - 1, by + 4, 2, HEIGHT - 30, dark);
		box(drawManager, bx + GUN_INSET - 3, by + 39, 6, 17, GUN);
		box(drawManager, bx + WIDTH - GUN_INSET - 3, by + 39, 6, 17, GUN);
		box(drawManager, bx + WIDTH / 2 - 8, by + HEIGHT - 22, 16, 12,
				white ? Color.WHITE : COCKPIT);
		if (this.state == State.CHARGE || (this.frame / 3) % 2 == 0)
			box(drawManager, bx + WIDTH / 2 - 14, by - 3, 28, 5,
					new Color(255, 170, 40));
	}

	/** Fills a box, cut off at the HUD line. */
	private void box(final DrawManager drawManager, final int bx, final int by,
			final int bw, final int bh, final Color color) {
		int y0 = Math.max(by, this.top);
		int y1 = by + bh;
		if (y1 > y0)
			drawManager.fillBox(bx, y0, bw, y1 - y0, color);
	}

	/* ---------------- Info ---------------- */

	@Override
	public int getHealth() {
		return this.health;
	}

	@Override
	public int getMaxHealth() {
		return this.maxHealth;
	}

	@Override
	public String getName() {
		return "RAIDER";
	}

	/** @return Current state name. */
	public String getPatternName() {
		return this.state.name();
	}

	/** @return Drones in flight. */
	public int getDroneCount() {
		return this.drones.size();
	}

	/** @return Body width. */
	public int getBodyWidth() {
		return WIDTH;
	}

	/** @return Body height. */
	public int getBodyHeight() {
		return HEIGHT;
	}

	/** @return Body left edge. */
	public double getX() {
		return this.x;
	}

	/** @return Body top edge. */
	public double getY() {
		return this.y;
	}
}
