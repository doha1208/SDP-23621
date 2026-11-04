package entity.boss;

import java.awt.Color;

import engine.DrawManager;
import entity.Entity;

/**
 * A small enemy that turns toward the player for a while, then flies
 * straight on in its last direction until it leaves the screen. It can be
 * shot down, and it is used up when it touches the player.
 */
final class HomingDrone {

	/** Size on screen. */
	static final int SIZE = 12;
	/** Frames it flashes after a hit. */
	private static final int FLASH_FRAMES = 4;

	private double x;
	private double y;
	private double heading;
	private final double speed;
	private final double turnRate;
	private int chaseFrames;
	private int health;
	private int flash;
	private boolean dead;

	/**
	 * @param x           Center x.
	 * @param y           Center y.
	 * @param heading     Start direction, radians.
	 * @param speed       Pixels per frame.
	 * @param turnRate    Largest turn per frame, radians.
	 * @param chaseFrames Frames it keeps turning toward the player.
	 * @param health      Hits it takes.
	 */
	HomingDrone(final double x, final double y, final double heading,
			final double speed, final double turnRate, final int chaseFrames,
			final int health) {
		this.x = x;
		this.y = y;
		this.heading = heading;
		this.speed = speed;
		this.turnRate = turnRate;
		this.chaseFrames = chaseFrames;
		this.health = health;
	}

	/** Moves one frame toward the player while chasing. */
	void update(final double playerX, final double playerY) {
		if (this.dead)
			return;
		if (this.flash > 0)
			this.flash--;
		if (this.chaseFrames > 0) {
			this.chaseFrames--;
			double wanted = Math.atan2(playerY - this.y, playerX - this.x);
			double turn = Math.atan2(Math.sin(wanted - this.heading),
					Math.cos(wanted - this.heading));
			this.heading += Math.max(-this.turnRate, Math.min(this.turnRate, turn));
		}
		this.x += Math.cos(this.heading) * this.speed;
		this.y += Math.sin(this.heading) * this.speed;
	}

	/** @return True once shot down, or gone off screen after chasing. */
	boolean isGone(final int width, final int height, final int top) {
		if (this.dead)
			return true;
		if (this.chaseFrames > 0)
			return false;
		int margin = SIZE * 2;
		return this.x < -margin || this.x > width + margin
				|| this.y < top - margin || this.y > height + margin;
	}

	/** @return True if the entity overlaps this drone. */
	boolean touches(final Entity entity) {
		return !this.dead && BossHazard.overlaps(this.x - SIZE / 2.0,
				this.y - SIZE / 2.0, SIZE, SIZE, entity);
	}

	/** @return True if this hit destroyed it. */
	boolean takeHit() {
		this.health--;
		this.flash = FLASH_FRAMES;
		if (this.health <= 0)
			this.dead = true;
		return this.dead;
	}

	/** Removes it without rewards (it rammed the player). */
	void crash() {
		this.dead = true;
	}

	double getX() {
		return this.x;
	}

	double getY() {
		return this.y;
	}

	boolean isChasing() {
		return this.chaseFrames > 0;
	}

	void draw(final DrawManager drawManager) {
		if (this.dead)
			return;
		int ix = (int) Math.round(this.x) - SIZE / 2;
		int iy = (int) Math.round(this.y) - SIZE / 2;
		drawManager.fillBox(ix, iy, SIZE, SIZE,
				this.flash > 0 ? Color.WHITE : new Color(230, 40, 40));
		drawManager.fillBox(ix + 3, iy + 3, SIZE - 6, SIZE - 6,
				new Color(90, 0, 0));
		// Nose shows where it is heading.
		int nx = (int) Math.round(this.x + Math.cos(this.heading) * (SIZE / 2.0));
		int ny = (int) Math.round(this.y + Math.sin(this.heading) * (SIZE / 2.0));
		drawManager.fillBox(nx - 2, ny - 2, 4, 4, Color.YELLOW);
	}
}
