package entity.boss;

import java.awt.Color;
import java.util.List;

import engine.DrawManager;
import entity.Entity;
import entity.enemy.EnemyBullet;

/**
 * A bomb that flies to a target spot and bursts there into a ring of
 * bullets. Touching it hurts the player and removes it without a burst.
 */
final class BossBomb {

	/** Size on screen. */
	static final int SIZE = 10;

	private double x;
	private double y;
	private final double targetX;
	private final double targetY;
	private final double speed;
	private final int bullets;
	private final double bulletSpeed;
	/** Ring rotation, so bursts do not all line up. */
	private final double ringOffset;
	private boolean done;
	private int age;

	/**
	 * @param x           Start center x.
	 * @param y           Start center y.
	 * @param targetX     Burst center x.
	 * @param targetY     Burst center y.
	 * @param speed       Pixels per frame.
	 * @param bullets     Bullets in the burst.
	 * @param bulletSpeed Speed of the burst bullets.
	 * @param ringOffset  Rotation of the burst ring, radians.
	 */
	BossBomb(final double x, final double y, final double targetX,
			final double targetY, final double speed, final int bullets,
			final double bulletSpeed, final double ringOffset) {
		this.x = x;
		this.y = y;
		this.targetX = targetX;
		this.targetY = targetY;
		this.speed = speed;
		this.bullets = bullets;
		this.bulletSpeed = bulletSpeed;
		this.ringOffset = ringOffset;
	}

	/**
	 * Flies one frame; bursts on arrival.
	 *
	 * @param out Where burst bullets go.
	 */
	void update(final List<EnemyBullet> out) {
		if (this.done)
			return;
		this.age++;
		double dx = this.targetX - this.x;
		double dy = this.targetY - this.y;
		double distance = Math.hypot(dx, dy);
		if (distance <= this.speed) {
			this.x = this.targetX;
			this.y = this.targetY;
			for (int i = 0; i < this.bullets; i++) {
				double angle = this.ringOffset + 2 * Math.PI * i / this.bullets;
				out.add(new EnemyBullet(this.x, this.y,
						Math.cos(angle) * this.bulletSpeed,
						Math.sin(angle) * this.bulletSpeed));
			}
			this.done = true;
			return;
		}
		this.x += dx / distance * this.speed;
		this.y += dy / distance * this.speed;
	}

	/** @return True if it touches the ship; the bomb is then gone. */
	boolean hurts(final Entity ship) {
		if (this.done || !BossHazard.overlaps(this.x - SIZE / 2.0,
				this.y - SIZE / 2.0, SIZE, SIZE, ship))
			return false;
		this.done = true;
		return true;
	}

	boolean isDone() {
		return this.done;
	}

	double getX() {
		return this.x;
	}

	double getY() {
		return this.y;
	}

	void draw(final DrawManager drawManager) {
		if (this.done)
			return;
		// Target marker, so the burst spot can be read in advance.
		int tx = (int) Math.round(this.targetX);
		int ty = (int) Math.round(this.targetY);
		Color marker = new Color(255, 200, 60, 110);
		drawManager.fillBox(tx - 8, ty, 17, 1, marker);
		drawManager.fillBox(tx, ty - 8, 1, 17, marker);
		boolean bright = (this.age / 4) % 2 == 0;
		int ix = (int) Math.round(this.x) - SIZE / 2;
		int iy = (int) Math.round(this.y) - SIZE / 2;
		drawManager.fillBox(ix, iy, SIZE, SIZE,
				bright ? new Color(255, 70, 50) : new Color(200, 30, 30));
		drawManager.fillBox(ix + 3, iy + 3, SIZE - 6, SIZE - 6, Color.YELLOW);
	}
}
