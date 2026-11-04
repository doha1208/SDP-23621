package entity.boss;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import engine.DrawManager;
import entity.Entity;

/**
 * Boss 1: a ship wider than the screen across the top, with three cores
 * (left, center, right). Only the cores take damage; the hull absorbs
 * bullets. It is beaten when all three cores are destroyed, and a
 * destroyed core's attack stops.
 *
 * Patterns, picked at random without repeating the last one:
 * <ol>
 * <li>Fan: the side cores fire aimed 5-way fans, several volleys.</li>
 * <li>Bombs: bombs fly to random spots and burst into rings.</li>
 * <li>Laser: the center core glows, then fires a wide laser down the
 * middle (needs the center core).</li>
 * <li>Sweep: crossfire flies in from the sides while a warning column
 * follows the player; it stops, then an attack rises from below. If it
 * reaches a core, the fortress is stunned for a while.</li>
 * </ol>
 */
public final class FortressBoss extends AbstractBoss {

	/** Attack patterns. */
	private enum Pattern { FAN, BOMB, LASER, SWEEP }

	/** Steps of the sweep pattern. */
	private enum Sweep { TRACK, LOCK, RISE, HOLD }

	/** Hull extends this far past each screen side. */
	private static final int HULL_OVERHANG = 30;
	/** Hull top, below the HUD line. */
	private static final int HULL_TOP = 2;
	/** Hull height. */
	private static final int HULL_HEIGHT = 56;
	/** Frames of one sway back and forth. */
	private static final int SWAY_PERIOD = 300;
	/** Pause before the first attack. */
	private static final int FIRST_REST = 90;
	/** Largest step of the sweep column toward the player per frame. */
	private static final double TRACK_STEP = 5;
	/** Frames the risen attack stays before it ends. */
	private static final int HOLD_FRAMES = 12;

	private static final Color HULL = new Color(240, 150, 120);
	private static final Color HULL_DARK = new Color(200, 110, 90);
	private static final Color HULL_LIGHT = new Color(255, 190, 160);
	private static final Color CORE = new Color(20, 90, 130);
	private static final Color CORE_LENS = new Color(70, 160, 200);
	private static final Color LASER = new Color(255, 40, 40);

	/** One weak point. */
	private static final class Core {
		/** Center x without sway. */
		final double centerX;
		/** Top edge. */
		final double y;
		final double width;
		final double height;
		final boolean side;
		final int maxHealth;
		int health;
		int flash;

		Core(final double centerX, final double y, final double width,
				final double height, final boolean side, final int health) {
			this.centerX = centerX;
			this.y = y;
			this.width = width;
			this.height = height;
			this.side = side;
			this.maxHealth = health;
			this.health = health;
		}

		boolean alive() {
			return this.health > 0;
		}
	}

	private final Core left;
	private final Core center;
	private final Core right;
	private final Core[] cores;
	private final List<BossBomb> bombs = new ArrayList<BossBomb>();

	private Pattern current;
	private Pattern last;
	private int restLeft = FIRST_REST;
	private int timer;
	private int countLeft;
	private int swayFrame;
	private double sway;

	private BossHazard laser;
	private BossHazard column;
	private Sweep sweep;
	private double columnX;
	private double columnFront;
	private int crossfireTimer;
	private boolean crossfireFromLeft;

	/**
	 * @param balance Boss balance values.
	 * @param level   Stage number, for the stage multipliers.
	 * @param random  Random source.
	 * @param width   Screen width.
	 * @param height  Screen height.
	 * @param top     Top of the play area.
	 */
	public FortressBoss(final BossBalance balance, final int level,
			final Random random, final int width, final int height,
			final int top) {
		super(balance, level, random, width, height, top);
		int sideHealth = balance.count("fortress.sideCoreHealth");
		this.left = new Core(width * 0.17, top + 44, 36, 24, true, sideHealth);
		this.right = new Core(width * 0.83, top + 44, 36, 24, true, sideHealth);
		this.center = new Core(width / 2.0, top + 49, 60, 22, false,
				balance.count("fortress.centerCoreHealth"));
		this.cores = new Core[] {this.left, this.center, this.right};
	}

	/* ---------------- Frame logic ---------------- */

	@Override
	protected void tickParts(final double playerX, final double playerY) {
		for (Core core : this.cores)
			if (core.flash > 0)
				core.flash--;
		Iterator<BossBomb> it = this.bombs.iterator();
		while (it.hasNext()) {
			BossBomb bomb = it.next();
			bomb.update(this.enemies.getBullets());
			if (bomb.isDone())
				it.remove();
		}
	}

	@Override
	protected void act(final double playerX, final double playerY) {
		this.swayFrame++;
		this.sway = this.balance.get("fortress.sway")
				* Math.sin(this.swayFrame * 2 * Math.PI / SWAY_PERIOD);
		if (this.current == null) {
			if (--this.restLeft <= 0)
				start(choose(), playerX);
			return;
		}
		switch (this.current) {
		case FAN:
			tickFan(playerX, playerY);
			break;
		case BOMB:
			tickBomb();
			break;
		case LASER:
			tickLaser();
			break;
		default:
			tickSweep(playerX);
			break;
		}
	}

	private Pattern choose() {
		List<Pattern> options = new ArrayList<Pattern>();
		if (this.left.alive() || this.right.alive())
			options.add(Pattern.FAN);
		options.add(Pattern.BOMB);
		if (this.center.alive())
			options.add(Pattern.LASER);
		options.add(Pattern.SWEEP);
		if (options.size() > 1)
			options.remove(this.last);
		return options.get(this.random.nextInt(options.size()));
	}

	private void start(final Pattern pattern, final double playerX) {
		this.current = pattern;
		this.timer = 0;
		switch (pattern) {
		case FAN:
			this.countLeft = this.balance.count("fortress.fan.volleys");
			break;
		case BOMB:
			this.countLeft = this.balance.count("fortress.bomb.count");
			break;
		case LASER:
			double w = this.balance.get("fortress.laser.width");
			double y = coreBottom(this.center);
			this.laser = new BossHazard(coreCenterX(this.center) - w / 2, y, w,
					this.height - y, this.balance.frames("fortress.laser.warning"),
					this.balance.frames("fortress.laser.duration"), LASER);
			this.hazards.add(this.laser);
			break;
		default:
			double cw = columnWidth();
			this.columnX = clampColumn(playerX - cw / 2);
			this.column = new BossHazard(this.columnX, hullBottom(), cw,
					this.height - hullBottom(), -1, -1, LASER);
			this.hazards.add(this.column);
			this.sweep = Sweep.TRACK;
			this.timer = this.balance.frames("fortress.sweep.trackTime");
			this.crossfireTimer = 0;
			break;
		}
	}

	private void end() {
		this.last = this.current;
		this.current = null;
		this.restLeft = gapFrames("fortress.restTime");
		if (this.laser != null)
			this.laser.finish();
		if (this.column != null)
			this.column.finish();
		this.laser = null;
		this.column = null;
	}

	@Override
	protected void onStun() {
		end();
		this.restLeft = Math.max(this.restLeft, 1);
	}

	private void tickFan(final double playerX, final double playerY) {
		if (--this.timer > 0)
			return;
		for (Core core : new Core[] {this.left, this.right})
			if (core.alive())
				fireFan(coreCenterX(core), coreBottom(core), playerX, playerY,
						this.balance.count("fortress.fan.bullets"),
						this.balance.get("fortress.fan.spreadDegrees"),
						bulletSpeed("fortress.fan.bulletSpeed"));
		this.countLeft--;
		this.timer = gapFrames("fortress.fan.volleyGap");
		if (this.countLeft <= 0 || !(this.left.alive() || this.right.alive()))
			end();
	}

	private void tickBomb() {
		if (this.countLeft > 0 && --this.timer <= 0) {
			double startX = this.width * (0.25 + 0.5 * this.random.nextDouble())
					+ this.sway;
			double playHeight = this.height - this.top;
			double targetX = 30 + this.random.nextDouble() * (this.width - 60);
			double targetY = this.top + playHeight
					* (0.3 + 0.45 * this.random.nextDouble());
			this.bombs.add(new BossBomb(startX, hullBottom(), targetX, targetY,
					speed("fortress.bomb.speed"),
					this.balance.count("fortress.bomb.bullets"),
					bulletSpeed("fortress.bomb.bulletSpeed"),
					this.random.nextDouble() * 2 * Math.PI));
			this.countLeft--;
			this.timer = gapFrames("fortress.bomb.launchGap");
		}
		if (this.countLeft <= 0 && this.bombs.isEmpty())
			end();
	}

	private void tickLaser() {
		if (this.laser.isDone() || !this.center.alive()) {
			end();
			return;
		}
		double w = this.laser.getWidth();
		double y = coreBottom(this.center);
		this.laser.setBounds(coreCenterX(this.center) - w / 2, y, w,
				this.height - y);
	}

	private void tickSweep(final double playerX) {
		if (this.sweep != Sweep.HOLD && --this.crossfireTimer <= 0) {
			double playHeight = this.height - this.top;
			double y = Math.min(this.height - 12, this.top + playHeight
					* (0.35 + 0.6 * this.random.nextDouble()));
			double speed = bulletSpeed("fortress.sweep.crossfireSpeed");
			if (this.crossfireFromLeft)
				fire(3, y, 0, speed);
			else
				fire(this.width - 3, y, Math.PI, speed);
			this.crossfireFromLeft = !this.crossfireFromLeft;
			this.crossfireTimer = gapFrames("fortress.sweep.crossfireGap");
		}
		double cw = columnWidth();
		switch (this.sweep) {
		case TRACK:
			double wanted = clampColumn(playerX - cw / 2);
			this.columnX += Math.max(-TRACK_STEP, Math.min(TRACK_STEP,
					wanted - this.columnX));
			this.column.setBounds(this.columnX, hullBottom(), cw,
					this.height - hullBottom());
			if (--this.timer <= 0) {
				this.sweep = Sweep.LOCK;
				this.timer = this.balance.frames("fortress.sweep.lockTime");
			}
			break;
		case LOCK:
			if (--this.timer <= 0) {
				this.sweep = Sweep.RISE;
				this.columnFront = this.height;
				this.column.activate(-1);
			}
			break;
		case RISE:
			this.columnFront -= speed("fortress.sweep.riseSpeed");
			for (Core core : this.cores)
				if (core.alive() && columnOverlaps(core)
						&& this.columnFront <= coreBottom(core)) {
					stun(this.balance.frames("fortress.sweep.stunTime"));
					return;
				}
			if (this.columnFront <= hullBottom()) {
				this.columnFront = hullBottom();
				this.sweep = Sweep.HOLD;
				this.timer = HOLD_FRAMES;
			}
			this.column.setBounds(this.columnX, this.columnFront, cw,
					this.height - this.columnFront);
			break;
		default:
			if (--this.timer <= 0)
				end();
			break;
		}
	}

	/* ---------------- Hits ---------------- */

	@Override
	public BossHit hit(final Entity bullet) {
		if (!isFighting())
			return null;
		double bulletX = bullet.getPositionX() + bullet.getWidth() / 2.0;
		for (Core core : this.cores) {
			if (!core.alive() || !BossHazard.overlaps(coreLeft(core), core.y,
					core.width, core.height, bullet))
				continue;
			core.health--;
			core.flash = FLASH_FRAMES;
			if (core.alive())
				return BossHit.damage(bulletX, coreBottom(core));
			int points = (int) Math.round(this.balance.get("fortress.corePoints"));
			boolean beaten = !this.left.alive() && !this.center.alive()
					&& !this.right.alive();
			if (beaten) {
				points += (int) Math.round(this.balance.get("fortress.defeatPoints"));
				defeat();
			}
			return new BossHit(points, coreCenterX(core),
					core.y + core.height / 2, true, true, beaten);
		}
		if (BossHazard.overlaps(-HULL_OVERHANG + this.sway, hullTop(),
				this.width + 2 * HULL_OVERHANG, HULL_HEIGHT, bullet))
			return BossHit.absorbed(bulletX, hullBottom());
		return null;
	}

	@Override
	public boolean hurts(final Entity ship) {
		if (super.hurts(ship))
			return true;
		if (!isFighting())
			return false;
		for (BossBomb bomb : this.bombs)
			if (bomb.hurts(ship))
				return true;
		return false;
	}

	@Override
	protected void clearParts() {
		this.bombs.clear();
		this.laser = null;
		this.column = null;
		this.current = null;
	}

	/* ---------------- Geometry ---------------- */

	private double hullTop() {
		return this.top + HULL_TOP;
	}

	private double hullBottom() {
		return this.top + HULL_TOP + HULL_HEIGHT;
	}

	private double coreCenterX(final Core core) {
		return core.centerX + this.sway;
	}

	private double coreLeft(final Core core) {
		return coreCenterX(core) - core.width / 2;
	}

	private double coreBottom(final Core core) {
		return core.y + core.height;
	}

	private double columnWidth() {
		return this.balance.get("fortress.sweep.columnWidth");
	}

	private double clampColumn(final double x) {
		return Math.max(0, Math.min(this.width - columnWidth(), x));
	}

	private boolean columnOverlaps(final Core core) {
		return this.columnX < coreLeft(core) + core.width
				&& coreLeft(core) < this.columnX + columnWidth();
	}

	/* ---------------- Drawing ---------------- */

	@Override
	public void draw(final DrawManager drawManager) {
		drawHazards(drawManager);
		boolean visible = drawDeath(drawManager, this.width / 2.0,
				hullTop() + HULL_HEIGHT / 2.0, this.width / 2.0, HULL_HEIGHT / 2.0);
		if (visible)
			drawHull(drawManager);
		if (getDeathFrames() < 0)
			for (BossBomb bomb : this.bombs)
				bomb.draw(drawManager);
	}

	private void drawHull(final DrawManager drawManager) {
		int x = (int) Math.round(-HULL_OVERHANG + this.sway);
		int y = (int) Math.round(hullTop());
		int w = this.width + 2 * HULL_OVERHANG;
		boolean stunned = isStunned();
		drawManager.fillBox(x, y, w, HULL_HEIGHT, stunned ? HULL_DARK : HULL);
		drawManager.fillBox(x, y, w, 4, HULL_LIGHT);
		drawManager.fillBox(x, y + HULL_HEIGHT - 10, w, 10, HULL_DARK);
		for (int panel = x + 24; panel < x + w; panel += 48)
			drawManager.fillBox(panel, y + 6, 2, HULL_HEIGHT - 18, HULL_DARK);
		for (Core core : this.cores)
			drawCore(drawManager, core, stunned);
		if (stunned && (this.frame / 8) % 2 == 0)
			drawManager.drawRegularString("STUN", this.width / 2 - 18, y + 22,
					Color.YELLOW);
	}

	private void drawCore(final DrawManager drawManager, final Core core,
			final boolean stunned) {
		int x = (int) Math.round(coreLeft(core));
		int y = (int) Math.round(core.y);
		int w = (int) core.width;
		int h = (int) core.height;
		if (!core.alive()) {
			drawManager.fillBox(x, y, w, h, new Color(45, 45, 45));
			if ((this.frame / 10) % 3 == 0)
				drawManager.fillBox(x + w / 2 - 3, y + h / 2 - 2, 6, 4,
						new Color(255, 120, 30));
			return;
		}
		Color shell = CORE;
		Color lens = CORE_LENS;
		if (core.flash > 0) {
			shell = Color.WHITE;
			lens = Color.WHITE;
		} else if (stunned) {
			lens = (this.frame / 6) % 2 == 0 ? new Color(150, 230, 255) : CORE_LENS;
		} else if (core == this.center && this.current == Pattern.LASER
				&& this.laser != null
				&& this.laser.getPhase() != BossHazard.Phase.ACTIVE) {
			lens = (this.frame / 4) % 2 == 0 ? new Color(255, 60, 60)
					: new Color(255, 160, 60);
		} else if (core.side && this.current == Pattern.FAN) {
			lens = new Color(255, 170, 70);
		}
		drawManager.fillBox(x, y, w, h, shell);
		drawManager.fillBox(x + 4, y + 4, w - 8, h - 8, lens);
		// Health tick under the core.
		int barWidth = w * core.health / core.maxHealth;
		drawManager.fillBox(x, y + h + 2, barWidth, 2, new Color(255, 80, 80));
	}

	/* ---------------- Info ---------------- */

	@Override
	public int getHealth() {
		return this.left.health + this.center.health + this.right.health;
	}

	@Override
	public int getMaxHealth() {
		return this.left.maxHealth + this.center.maxHealth + this.right.maxHealth;
	}

	@Override
	public String getName() {
		return "FORTRESS";
	}

	/** @return Health of the left, center and right core. */
	public int[] getCoreHealth() {
		return new int[] {this.left.health, this.center.health, this.right.health};
	}

	/**
	 * @param index 0 left, 1 center, 2 right.
	 * @return Hit box of that core: x, y, width, height.
	 */
	public double[] getCoreBox(final int index) {
		Core core = this.cores[index];
		return new double[] {coreLeft(core), core.y, core.width, core.height};
	}

	/** @return Current pattern name, or REST between patterns. */
	public String getPatternName() {
		if (isStunned())
			return "STUNNED";
		return this.current == null ? "REST" : this.current.name();
	}

	/** @return Bombs in flight. */
	public int getBombCount() {
		return this.bombs.size();
	}
}
