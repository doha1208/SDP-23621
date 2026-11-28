package entity;

import java.awt.Color;
import java.awt.Point;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import engine.Cooldown;
import engine.Core;
import engine.DrawManager.SpriteType;

/**
 * Implements a ship, to be controlled by the player.
 *
 * @author <a href="mailto:RobertoIA1987@gmail.com">Roberto Izquierdo Amo</a>
 *
 */
public class Ship extends Entity {

	/** Half the distance in px between two neighbouring barrels. */
	private static final int BARREL_OFFSET = 8;

	/**
	 * Types of player ships. Each type has its own movement speed, shooting
	 * interval, bullet speed, bullets per shot, bullet size and sprite.
	 */
	public enum ShipType {
		/** Standard ship. */
		STANDARD(2, 250, 6, 1, Bullet.DEFAULT_WIDTH, Bullet.DEFAULT_HEIGHT,
				false, SpriteType.Ship),
		/** Ship with a high movement speed. */
		FAST_MOVE(4, 250, 6, 1, Bullet.DEFAULT_WIDTH, Bullet.DEFAULT_HEIGHT,
				false, SpriteType.ShipFastMove),
		/** Ship that shoots a bullet from each of its two barrels. */
		TWO_WAY(1, 333, 6, 2, Bullet.DEFAULT_WIDTH, Bullet.DEFAULT_HEIGHT,
				false, SpriteType.ShipTwoWay),
		/** Ship with a high rate of fire. */
		FAST_ATTACK(2, 125, 6, 1, Bullet.DEFAULT_WIDTH, Bullet.DEFAULT_HEIGHT,
				false, SpriteType.ShipFastAttack),
		/**
		 * Ship with a large projectile (9x15 sprite pixels, scaled by 2) that
		 * goes on through every enemy it overlaps.
		 */
		BIG_BULLET(2, 333, 6, 1, 9 * 2, 15 * 2, true,
				SpriteType.ShipBigBullet);

		/** Movement of the ship for each unit of time. */
		private final int speed;
		/** Time between shots, in milliseconds. */
		private final int shootingInterval;
		/** Upward speed of the ship's bullets, in px per frame. */
		private final int bulletSpeed;
		/** Bullets fired per shot, one from each barrel. */
		private final int bulletsPerShot;
		/** Size of each bullet in px. */
		private final int bulletWidth;
		private final int bulletHeight;
		/** Whether the bullets go on through every enemy they overlap. */
		private final boolean piercing;
		/** Sprite of the ship while it is not destroyed. */
		private final SpriteType idleSprite;

		/**
		 * Constructor, establishes the properties of the ship type. The
		 * numbers are defaults; res/ship-stats.properties can override them.
		 *
		 * @param speed
		 *            Movement of the ship for each unit of time.
		 * @param shootingInterval
		 *            Time between shots, in milliseconds.
		 * @param bulletSpeed
		 *            Upward speed of the bullets, in px per frame.
		 * @param bulletsPerShot
		 *            Bullets fired per shot.
		 * @param bulletWidth
		 *            Width of each bullet in px.
		 * @param bulletHeight
		 *            Height of each bullet in px.
		 * @param piercing
		 *            True if the bullets go on through every enemy they
		 *            overlap.
		 * @param idleSprite
		 *            Sprite of the ship while it is not destroyed.
		 */
		ShipType(final int speed, final int shootingInterval,
				 final int bulletSpeed, final int bulletsPerShot,
				 final int bulletWidth, final int bulletHeight,
				 final boolean piercing, final SpriteType idleSprite) {
			// res/ship-stats.properties can override every number.
			this.speed = ShipStats.get(name(), "speed", speed);
			this.shootingInterval = ShipStats.get(name(), "shootingInterval",
					shootingInterval);
			this.bulletSpeed = ShipStats.get(name(), "bulletSpeed", bulletSpeed);
			this.bulletsPerShot = ShipStats.get(name(), "bulletsPerShot",
					bulletsPerShot);
			this.bulletWidth = ShipStats.get(name(), "bulletWidth", bulletWidth);
			this.bulletHeight = ShipStats.get(name(), "bulletHeight",
					bulletHeight);
			this.piercing = ShipStats.getFlag(name(), "piercing", piercing);
			this.idleSprite = idleSprite;
		}

		/**
		 * Read-only stats, so the Shop and Customize screens can show them
		 * without keeping their own copy (GoG - Currency System).
		 *
		 * @return Movement of the ship for each unit of time.
		 */
		public int getSpeed() {
			return this.speed;
		}

		/** @return Time between shots, in milliseconds. */
		public int getShootingInterval() {
			return this.shootingInterval;
		}

		/** @return Upward speed of the bullets, in px per frame. */
		public int getBulletSpeed() {
			return this.bulletSpeed;
		}

		/** @return Bullets fired per shot. */
		public int getBulletsPerShot() {
			return this.bulletsPerShot;
		}

		/** @return Width of each bullet in px. */
		public int getBulletWidth() {
			return this.bulletWidth;
		}

		/** @return Height of each bullet in px. */
		public int getBulletHeight() {
			return this.bulletHeight;
		}

		/** @return True if the bullets go on through every enemy they overlap. */
		public boolean isPiercing() {
			return this.piercing;
		}

		/** @return Sprite of the ship while it is not destroyed. */
		public SpriteType getIdleSprite() {
			return this.idleSprite;
		}
	}

	/** Type of this ship. */
	private final ShipType type;
	/** Sprite drawn while the ship is not destroyed: the type's own, or a shape skin's. */
	private SpriteType idleSprite;

	/**
	 * AUTHORED BY: VFX TEAM (effection)
	 *
	 * Blink settings for low health.
	 */
	private static final int BLINK_INTERVAL = 200;
	private static final Color BASE_COLOR = Color.GREEN;
	private static final Color BLINK_COLOR = new Color(255, 60, 60);
	/** Frames the ship is shown, then hidden, while invincible (Team Entities). */
	private static final int INVINCIBLE_BLINK_FRAMES = 6;

	/** Colour of the ship when not blinking; changed by skins (GoG). */
	private Color skinColor = BASE_COLOR;

	/** Minimum time between shots. */
	private Cooldown shootingCooldown;
	/** Time spent inactive between hits. */
	private Cooldown destructionCooldown;
	/**
	 * Gameplay frames left in which the ship cannot be hit, after losing a
	 * life (Team Entities). The ship blinks but keeps moving and shooting.
	 */
	private int invincibleFrames;
	/** Extra shots per second from items, added to the base rate (Team CS). */
	private double itemFireRateBonus;
	/** Extra bullet speed from items, added to the base speed (Team CS). */
	private double itemBulletSpeedBonus;
	/** Extra movement speed from items, added to the base speed (Team CS). */
	private int itemMovementSpeedBonus;
	/** Spread item stacks, 0 to 6; see shoot (Team CS / Team Entities). */
	private int itemSpreadCount;
	/** Enemy centers for the spread item's aimed shot (Team Entities). */
	private Supplier<List<Point>> aimTargets;
	/** Spread item (Team Entities): px between side-by-side front shots. */
	private int spreadFrontGap = 14;
	/** Spread item: sideways px per frame of the first diagonal pair. */
	private int spreadDiagonal = 3;
	/** Spread item: sideways px per frame of the wide diagonal pair. */
	private int spreadWideDiagonal = 6;
	/** Extra enemies each bullet passes through, from the pierce item (Team CS). */
	private int itemPierceCount;
	/** Bullets of the last shot that fly at enemies, without the spread back shot (Team Entities). */
	private int lastForwardShots;

	/**
	 * AUTHORED BY: VFX TEAM (effection)
	 *
	 * Blink state for low health.
	 */
	private Cooldown blinkCooldown;
	private boolean blinking;
	private boolean blinkOn;

	/**
	 * Constructor, establishes the ship's properties.
	 *
	 * @param positionX
	 *            Initial position of the ship in the X axis.
	 * @param positionY
	 *            Initial position of the ship in the Y axis.
	 */
	public Ship(final int positionX, final int positionY) {
		this(positionX, positionY, ShipType.STANDARD);
	}

	/**
	 * Constructor, establishes the ship's properties.
	 *
	 * @param positionX
	 *            Initial position of the ship in the X axis.
	 * @param positionY
	 *            Initial position of the ship in the Y axis.
	 * @param type
	 *            Type of the ship, which defines its speed and sprite.
	 */
	public Ship(final int positionX, final int positionY, final ShipType type) {
		super(positionX, positionY, 13 * 2, 8 * 2, Color.GREEN);

		this.type = type;
		this.idleSprite = type.idleSprite;
		this.spriteType = this.idleSprite;
		this.shootingCooldown = Core.getCooldown(type.shootingInterval);
		this.destructionCooldown = Core.getCooldown(1000);

		/**
		 * AUTHORED BY: VFX TEAM (effection)
		 */
		this.blinkCooldown = Core.getCooldown(BLINK_INTERVAL);
	}

	/**
	 * Moves the ship speed uni ts right, or until the right screen border is
	 * reached.
	 */
	public final void moveRight() {
		this.positionX += getSpeed();
	}

	/**
	 * Moves the ship speed units left, or until the left screen border is
	 * reached.
	 */
	public final void moveLeft() {
		this.positionX -= getSpeed();
	}

	/**
	 * Moves the ship speed units up, stopping at the given height.
	 *
	 * @param topLimit
	 *            Highest y the top of the ship may reach.
	 */
	public final void moveUp(final int topLimit) {
		this.positionY = Math.max(topLimit, this.positionY - getSpeed());
	}

	/**
	 * Moves the ship speed units down, stopping at the given height.
	 *
	 * @param bottomLimit
	 *            Lowest y the top of the ship may reach.
	 */
	public final void moveDown(final int bottomLimit) {
		this.positionY = Math.min(bottomLimit, this.positionY + getSpeed());
	}

	/**
	 * Shoots upwards: each of the type's barrels fires one bullet with the
	 * type's bullet speed and size, and the pierce item lets each bullet pass
	 * through extra enemies (Team CS). Spread item stacks (Team Entities):
	 * 1 two front shots side by side, 2 a diagonal pair, 3 three front
	 * shots, 4 a shot backwards, 5 a shot aimed at the nearest enemy,
	 * 6 a wider diagonal pair. Every barrel fires the front shots; the other
	 * shots leave once from the center.
	 *
	 * @param bullets
	 *            List of bullets on screen, to add the new bullet.
	 * @return Checks if the bullet was shot correctly.
	 */
	public final boolean shoot(final Set<Bullet> bullets) {
		if (this.shootingCooldown.checkFinished()) {
			this.shootingCooldown.reset();
			final int centerX = positionX + this.width / 2;
			final int bulletSpeed = -(this.type.bulletSpeed
					+ (int) Math.round(this.itemBulletSpeedBonus));
			final int barrels = this.type.bulletsPerShot;
			final int spread = this.itemSpreadCount;
			final int columns = spread >= 3 ? 3 : spread >= 1 ? 2 : 1;
			// Every bullet below except the back shot (stack 4) flies at enemies.
			this.lastForwardShots = columns * barrels + (spread >= 2 ? 2 : 0)
					+ (spread >= 5 ? 1 : 0) + (spread >= 6 ? 2 : 0);
			for (int column = 0; column < columns; column++) {
				final int columnX = centerX
						+ (2 * column - (columns - 1)) * this.spreadFrontGap / 2;
				for (int barrel = 0; barrel < barrels; barrel++) {
					// Barrels sit symmetrically around the center: one at
					// the center, two at -8/+8, three at -16/0/+16, ...
					final int x = columnX
							+ (2 * barrel - (barrels - 1)) * BARREL_OFFSET;
					addBullet(bullets, x, positionY, bulletSpeed, 0);
				}
			}
			if (spread >= 2) {
				addBullet(bullets, centerX, positionY, bulletSpeed, -this.spreadDiagonal);
				addBullet(bullets, centerX, positionY, bulletSpeed, this.spreadDiagonal);
			}
			if (spread >= 4)
				addBullet(bullets, centerX, positionY + this.height,
						-bulletSpeed, 0);
			if (spread >= 5)
				addAimedBullet(bullets, centerX, bulletSpeed);
			if (spread >= 6) {
				addBullet(bullets, centerX, positionY, bulletSpeed,
						-this.spreadWideDiagonal);
				addBullet(bullets, centerX, positionY, bulletSpeed,
						this.spreadWideDiagonal);
			}
			return true;
		}
		return false;
	}

	/**
	 * Fires one player bullet with the type's size, the pierce item's extra
	 * hits and the ship's piercing (Team CS / Team Entities).
	 */
	private void addBullet(final Set<Bullet> bullets, final int x, final int y,
						   final int speedY, final int speedX) {
		final Bullet bullet = BulletPool.getCustomBullet(x, y, speedY,
				this.type.bulletWidth, this.type.bulletHeight);
		bullet.setSpeedX(speedX);
		bullet.setPierce(this.itemPierceCount);
		bullet.setPiercing(this.type.piercing);
		bullet.setPlayerShot(true);
		bullets.add(bullet);
	}

	/**
	 * Spread item stack 5 (Team Entities): one bullet at the bullet speed
	 * towards the nearest enemy, or straight up when there is none.
	 */
	private void addAimedBullet(final Set<Bullet> bullets, final int centerX,
								final int bulletSpeed) {
		final Point target = nearestTarget(centerX, positionY);
		int speedX = 0;
		int speedY = bulletSpeed;
		if (target != null) {
			final double dx = target.x - centerX;
			final double dy = target.y - positionY;
			final double length = Math.hypot(dx, dy);
			if (length > 0) {
				final double speed = Math.abs(bulletSpeed);
				speedX = (int) Math.round(dx / length * speed);
				speedY = (int) Math.round(dy / length * speed);
				if (speedX == 0 && speedY == 0)
					speedY = bulletSpeed;
			}
		}
		addBullet(bullets, centerX, positionY, speedY, speedX);
	}

	/** Nearest of the aim targets to the given point, or null. */
	private Point nearestTarget(final int x, final int y) {
		if (this.aimTargets == null)
			return null;
		final List<Point> targets = this.aimTargets.get();
		Point nearest = null;
		double best = Double.MAX_VALUE;
		if (targets != null)
			for (Point target : targets) {
				final double distance = target.distanceSq(x, y);
				if (distance < best) {
					best = distance;
					nearest = target;
				}
			}
		return nearest;
	}

	/**
	 * Where the spread item's aimed shot can go (Team Entities): centers of
	 * the enemies that can be hit right now. Null means no aimed target.
	 *
	 * @param aimTargets
	 *            Supplier of enemy centers, read at each shot.
	 */
	public final void setAimTargets(final Supplier<List<Point>> aimTargets) {
		this.aimTargets = aimTargets;
	}

	/**
	 * Sets the spread and pierce item stacks used by the next shots (Team CS
	 * - Item System). With both at 0 the ship shoots exactly as before.
	 *
	 * @param spreadCount
	 *            Extra bullet lanes per shot.
	 * @param pierceCount
	 *            Extra enemies each bullet passes through.
	 */
	public final void setWeaponMods(final int spreadCount,
									final int pierceCount) {
		this.itemSpreadCount = Math.max(0, spreadCount);
		this.itemPierceCount = Math.max(0, pierceCount);
	}

	/**
	 * Sets the spread item's shot geometry (Team Entities), from the item
	 * balance file.
	 *
	 * @param frontGap
	 *            Px between side-by-side front shots.
	 * @param diagonal
	 *            Sideways px per frame of the first diagonal pair.
	 * @param wideDiagonal
	 *            Sideways px per frame of the wide diagonal pair.
	 */
	public final void setSpreadPattern(final int frontGap, final int diagonal,
									   final int wideDiagonal) {
		this.spreadFrontGap = Math.max(0, frontGap);
		this.spreadDiagonal = Math.max(0, diagonal);
		this.spreadWideDiagonal = Math.max(0, wideDiagonal);
	}

	/**
	 * Sets the bonuses items add on top of the base fire rate and bullet
	 * speed (Team CS - Item System). The base values stay unchanged; with
	 * both bonuses at 0 the ship shoots exactly as before.
	 *
	 * @param fireRateBonus
	 *            Extra shots per second.
	 * @param bulletSpeedBonus
	 *            Extra bullet speed in pixels per frame.
	 */
	public final void setItemBonuses(final double fireRateBonus,
									 final double bulletSpeedBonus) {
		setItemBonuses(fireRateBonus, bulletSpeedBonus, 0);
	}

	/**
	 * Same as {@link #setItemBonuses(double, double)}, plus extra movement
	 * speed (e.g. the boost item), added on top of the base speed and rounded
	 * to whole pixels per frame.
	 *
	 * @param fireRateBonus
	 *            Extra shots per second.
	 * @param bulletSpeedBonus
	 *            Extra bullet speed in pixels per frame.
	 * @param movementSpeedBonus
	 *            Extra movement speed in pixels per frame.
	 */
	public final void setItemBonuses(final double fireRateBonus,
									 final double bulletSpeedBonus, final double movementSpeedBonus) {
		this.itemMovementSpeedBonus = (int) Math.round(Math.max(0,
				movementSpeedBonus));
		this.itemBulletSpeedBonus = Math.max(0, bulletSpeedBonus);
		double bonus = Math.max(0, fireRateBonus);
		if (bonus != this.itemFireRateBonus) {
			this.itemFireRateBonus = bonus;
			// (base shots per second) + (item bonus) -> new interval.
			this.shootingCooldown = Core.getCooldown((int) Math.round(
					1000.0 / (1000.0 / this.type.shootingInterval + bonus)));
		}
	}

	/**
	 * Paints the ship in a skin colour (GoG - Currency System). The
	 * low-health blink still flashes red and returns to this colour.
	 *
	 * @param color
	 *            Skin colour; null goes back to the standard green.
	 */
	public final void setSkinColor(final Color color) {
		this.skinColor = color == null ? BASE_COLOR : color;
		if (!this.blinkOn)
			setColor(this.skinColor);
	}

	/**
	 * Draws the ship with another shape (A1 - ship skins). Only the sprite
	 * changes: hitbox, speed and shooting stay those of the ship type.
	 *
	 * @param sprite
	 *            Sprite to use while the ship is not destroyed; null goes
	 *            back to the ship type's own sprite.
	 */
	public final void setShapeSprite(final SpriteType sprite) {
		this.idleSprite = sprite == null ? this.type.idleSprite : sprite;
		if (!isDestroyed())
			this.spriteType = this.idleSprite;
	}

	/** Equipped shape even while the ship is temporarily destroyed. */
	public final SpriteType getIdleSprite() {
		return this.idleSprite;
	}

	/**
	 * Updates status of the ship.
	 */
	public final void update() {
		if (this.invincibleFrames > 0)
			this.invincibleFrames--;
		if (!this.destructionCooldown.checkFinished())
			this.spriteType = SpriteType.ShipDestroyed;
		else
			this.spriteType = this.idleSprite;

		/**
		 * AUTHORED BY: VFX TEAM (effection)
		 *
		 * Toggle color each BLINK_INTERVAL ms.
		 */
		if (this.blinking && this.blinkCooldown.checkFinished()) {
			this.blinkOn = !this.blinkOn;
			setColor(this.blinkOn ? BLINK_COLOR : this.skinColor);
			this.blinkCooldown.reset();
		}
	}

	/**
	 * AUTHORED BY: VFX TEAM (effection)
	 *
	 * Turns low-health blinking on or off.
	 *
	 * @param blinking
	 *            True to start blinking.
	 */
	public final void setBlinking(final boolean blinking) {
		if (this.blinking == blinking)
			return;
		this.blinking = blinking;
		this.blinkOn = false;
		setColor(this.skinColor);
		this.blinkCooldown.reset();
	}

	/**
	 * Switches the ship to its destroyed state.
	 */
	public final void destroy() {
		this.destructionCooldown.reset();
	}

	/**
	 * Makes the ship unhittable for a while after losing a life (Team
	 * Entities). It blinks but can still move and shoot. Counted in
	 * gameplay frames, so pausing pauses it too.
	 *
	 * @param frames
	 *            Gameplay frames the ship stays unhittable.
	 */
	public final void startInvincibility(final int frames) {
		this.invincibleFrames = Math.max(this.invincibleFrames, frames);
	}

	/** @return True while the ship cannot be hit after losing a life. */
	public final boolean isInvincible() {
		return this.invincibleFrames > 0;
	}

	/**
	 * @return Bullets of the last shot that fly at enemies (Team Entities):
	 *         all of them except the spread back shot, which almost never
	 *         hits. Used for accuracy.
	 */
	public final int getLastForwardShots() {
		return this.lastForwardShots;
	}

	/**
	 * @return True on the frames an invincible ship is hidden, which makes
	 *         it blink.
	 */
	public final boolean isBlinkHidden() {
		return this.invincibleFrames > 0
				&& (this.invincibleFrames / INVINCIBLE_BLINK_FRAMES) % 2 == 1;
	}

	/**
	 * Checks if the ship is destroyed.
	 *
	 * @return True if the ship is currently destroyed.
	 */
	public final boolean isDestroyed() {
		return !this.destructionCooldown.checkFinished();
	}

	/**
	 * Getter for the ship's speed.
	 *
	 * @return Speed of the ship, including any item bonus.
	 */
	public final int getSpeed() {
		return this.type.speed + this.itemMovementSpeedBonus;
	}

	/**
	 * Base movement speed without item bonuses (Team CS - Item System).
	 *
	 * @return Base speed in pixels per frame.
	 */
	public final int getBaseSpeed() {
		return this.type.speed;
	}

	/**
	 * Base fire rate without item bonuses (Team CS - Item System).
	 *
	 * @return Base shots per second.
	 */
	public final double getBaseFireRate() {
		return 1000.0 / this.type.shootingInterval;
	}

	/**
	 * Getter for the ship's type.
	 *
	 * @return Type of the ship.
	 */
	public final ShipType getType() {
		return this.type;
	}
}
