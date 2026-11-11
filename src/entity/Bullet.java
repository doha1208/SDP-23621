package entity;

import java.awt.Color;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import engine.DrawManager.SpriteType;

/**
 * Implements a bullet that moves vertically up or down, optionally drifting
 * sideways (used by aimed boss attacks).
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 * 
 */
public class Bullet extends Entity {
	/** Dimensions of an ordinary player or enemy bullet. */
	public static final int DEFAULT_WIDTH = 6;
	public static final int DEFAULT_HEIGHT = 10;

	/**
	 * Speed of the bullet, positive or negative depending on direction -
	 * positive is down.
	 */
	private int speed;
	/** Horizontal speed of the bullet, positive is right. Usually zero. */
	private int speedX;
	/** Extra enemies this bullet may pass through (pierce item, Team CS). */
	private int pierce;
	/** Enemies this bullet has already hit, so it hits each one only once. */
	private final Set<Object> hitTargets = Collections
			.newSetFromMap(new IdentityHashMap<Object, Boolean>());
	/** Whether registerHit counted a new enemy since the last piercesThrough. */
	private boolean hitThisFrame;

	/**
	 * Constructor, establishes the bullet's properties.
	 * 
	 * @param positionX
	 *            Initial position of the bullet in the X axis.
	 * @param positionY
	 *            Initial position of the bullet in the Y axis.
	 * @param speed
	 *            Speed of the bullet, positive or negative depending on
	 *            direction - positive is down.
	 */
    public Bullet(final int positionX, final int positionY, final int speed) {
        this(positionX, positionY, speed, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * Constructor, establishes the bullet's properties with custom size.
     *
     * @param positionX
     *            Initial position of the bullet in the X axis.
     * @param positionY
     *            Initial position of the bullet in the Y axis.
     * @param speed
     *            Speed of the bullet.
     * @param width
     *            Width of the bullet.
     * @param height
     *            Height of the bullet.
     */
    public Bullet(final int positionX, final int positionY, final int speed, final int width, final int height) {
        super(positionX, positionY, width, height, Color.WHITE);

        this.speed = speed;
        setSprite();
    }

	/**
	 * Sets correct sprite for the bullet, based on speed.
	 */
	public final void setSprite() {
		if (speed < 0)
			this.spriteType = SpriteType.Bullet;
		else
			this.spriteType = SpriteType.EnemyBullet;
	}

	/**
	 * Updates the bullet's position.
	 */
	public final void update() {
		this.positionX += this.speedX;
		this.positionY += this.speed;
	}

	/**
	 * Setter of the speed of the bullet.
	 * 
	 * @param speed
	 *            New speed of the bullet.
	 */
	public final void setSpeed(final int speed) {
		this.speed = speed;
	}

	/**
	 * Getter for the speed of the bullet.
	 * 
	 * @return Speed of the bullet.
	 */
	public final int getSpeed() {
		return this.speed;
	}

	/**
	 * Setter of the horizontal speed of the bullet.
	 *
	 * @param speedX
	 *            New horizontal speed of the bullet, positive is right.
	 */
	public final void setSpeedX(final int speedX) {
		this.speedX = speedX;
	}

	/**
	 * Getter for the horizontal speed of the bullet.
	 *
	 * @return Horizontal speed of the bullet.
	 */
	public final int getSpeedX() {
		return this.speedX;
	}

	/**
	 * Sets how many extra enemies the bullet may pass through (Team CS -
	 * pierce item). 0 means it stops at the first enemy, as before.
	 *
	 * @param pierce
	 *            Extra enemies, 0 or more.
	 */
	public final void setPierce(final int pierce) {
		this.pierce = Math.max(0, pierce);
	}

	/** @return Extra enemies the bullet may pass through. */
	public final int getPierce() {
		return this.pierce;
	}

	/**
	 * Records a hit on an enemy (Team CS - pierce item). A piercing bullet
	 * keeps overlapping the enemy it went through for a few frames, so the
	 * same enemy is never hit twice.
	 *
	 * @param target
	 *            Enemy the bullet collides with.
	 * @return True for a new hit; false when this bullet already hit it.
	 */
	public final boolean registerHit(final Object target) {
		if (!this.hitTargets.add(target))
			return false;
		this.hitThisFrame = true;
		return true;
	}

	/**
	 * Decides, once per frame, whether a bullet that collided keeps flying
	 * (Team CS - pierce item). Only enemy hits counted by registerHit can be
	 * pierced; anything else (boss, special ship, meteor, player) still
	 * consumes the bullet.
	 *
	 * @return True when the bullet passes through and must not be removed.
	 */
	public final boolean piercesThrough() {
		final boolean keep = this.hitThisFrame
				&& this.hitTargets.size() <= this.pierce;
		this.hitThisFrame = false;
		return keep;
	}

	/** Forgets pierce state, for a bullet reused from the pool. */
	final void resetHits() {
		this.pierce = 0;
		this.hitTargets.clear();
		this.hitThisFrame = false;
	}
}
