package entity.enemy;

import java.awt.Color;

import engine.DrawManager.SpriteType;
import entity.Entity;

/**
 * A bullet fired by an enemy. Kept separate from {@link entity.Bullet},
 * which the player and the bosses use, so enemy shots can fly in any
 * direction without changing the shared bullet class. Moves with sub-pixel
 * precision so aimed shots keep their angle.
 */
public final class EnemyBullet extends Entity {

	/** Width on screen, same as an ordinary bullet. */
	public static final int WIDTH = 6;
	/** Height on screen, same as an ordinary bullet. */
	public static final int HEIGHT = 10;

	/** Exact x of the upper left corner. */
	private double x;
	/** Exact y of the upper left corner. */
	private double y;
	/** Horizontal speed, pixels per frame, positive is right. */
	private final double velocityX;
	/** Vertical speed, pixels per frame, positive is down. */
	private final double velocityY;

	/**
	 * @param centerX   Center x where the bullet appears.
	 * @param centerY   Center y where the bullet appears.
	 * @param velocityX Horizontal speed, pixels per frame.
	 * @param velocityY Vertical speed, pixels per frame.
	 */
	public EnemyBullet(final double centerX, final double centerY,
			final double velocityX, final double velocityY) {
		super((int) Math.round(centerX - WIDTH / 2.0),
				(int) Math.round(centerY - HEIGHT / 2.0), WIDTH, HEIGHT,
				Color.WHITE);
		this.spriteType = SpriteType.EnemyBullet;
		this.x = centerX - WIDTH / 2.0;
		this.y = centerY - HEIGHT / 2.0;
		this.velocityX = velocityX;
		this.velocityY = velocityY;
	}

	/** Moves the bullet one frame. */
	public void update() {
		this.x += this.velocityX;
		this.y += this.velocityY;
		this.positionX = (int) Math.round(this.x);
		this.positionY = (int) Math.round(this.y);
	}

	/**
	 * @param width  Screen width.
	 * @param height Screen height.
	 * @param top    Top of the play area.
	 * @return True once the bullet has left the play area.
	 */
	public boolean isOutside(final int width, final int height, final int top) {
		return this.y + HEIGHT < top || this.y > height
				|| this.x + WIDTH < 0 || this.x > width;
	}

	/** @return Horizontal speed, pixels per frame. */
	public double getVelocityX() {
		return this.velocityX;
	}

	/** @return Vertical speed, pixels per frame. */
	public double getVelocityY() {
		return this.velocityY;
	}
}
