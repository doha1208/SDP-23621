package entity;

import java.awt.Color;
import java.util.Set;

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
	/** Sideways speed added per step between spread lanes, in px per frame (Team CS). */
	private static final int SPREAD_STEP = 1;

	/**
	 * Types of player ships. Each type has its own movement speed, shooting
	 * interval, bullet speed, bullets per shot, bullet size and sprite.
	 */
	public enum ShipType {
		/** Standard ship. */
		STANDARD(2, 750, 6, 1, Bullet.DEFAULT_WIDTH, Bullet.DEFAULT_HEIGHT,
				SpriteType.Ship),
		/** Ship with a high movement speed. */
		FAST_MOVE(4, 750, 6, 1, Bullet.DEFAULT_WIDTH, Bullet.DEFAULT_HEIGHT,
				SpriteType.ShipFastMove),
		/** Ship that shoots a bullet from each of its two barrels. */
		TWO_WAY(1, 1000, 6, 2, Bullet.DEFAULT_WIDTH, Bullet.DEFAULT_HEIGHT,
				SpriteType.ShipTwoWay),
		/** Ship with a high rate of fire. */
		FAST_ATTACK(2, 375, 6, 1, Bullet.DEFAULT_WIDTH, Bullet.DEFAULT_HEIGHT,
				SpriteType.ShipFastAttack),
		/** Ship with a large projectile (9x15 sprite pixels, scaled by 2). */
		BIG_BULLET(2, 1000, 6, 1, 9 * 2, 15 * 2, SpriteType.ShipBigBullet);

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
		 * @param idleSprite
		 *            Sprite of the ship while it is not destroyed.
		 */
		ShipType(final int speed, final int shootingInterval,
				 final int bulletSpeed, final int bulletsPerShot,
				 final int bulletWidth, final int bulletHeight,
				 final SpriteType idleSprite) {
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

	/** Colour of the ship when not blinking; changed by skins (GoG). */
	private Color skinColor = BASE_COLOR;

	/** Minimum time between shots. */
	private Cooldown shootingCooldown;
	/** Time spent inactive between hits. */
	private Cooldown destructionCooldown;
	/** Extra shots per second from items, added to the base rate (Team CS). */
	private double itemFireRateBonus;
	/** Extra bullet speed from items, added to the base speed (Team CS). */
	private double itemBulletSpeedBonus;
	/** Extra movement speed from items, added to the base speed (Team CS). */
	private int itemMovementSpeedBonus;
	/** Extra bullet lanes per shot from the spread item (Team CS). */
	private int itemSpreadCount;
	/** Extra enemies each bullet passes through, from the pierce item (Team CS). */
	private int itemPierceCount;

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
	 * Shoots upwards: each of the type's barrels fires one bullet with the
	 * type's bullet speed and size. With the spread item every lane of the fan
	 * fires the ship's own shot, and the pierce item lets each bullet pass
	 * through extra enemies (Team CS).
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
			for (int lane : spreadLanes(1 + this.itemSpreadCount)) {
				final int speedX = lane * SPREAD_STEP;
				for (int barrel = 0; barrel < barrels; barrel++) {
					// Barrels sit symmetrically around the center: one at
					// the center, two at -8/+8, three at -16/0/+16, ...
					final int x = centerX
							+ (2 * barrel - (barrels - 1)) * BARREL_OFFSET;
					addBullet(bullets, BulletPool.getCustomBullet(x,
							positionY, bulletSpeed, this.type.bulletWidth,
							this.type.bulletHeight), speedX);
				}
			}
			return true;
		}
		return false;
	}

	/** Gives a new bullet its lane and the pierce item's extra hits (Team CS). */
	private void addBullet(final Set<Bullet> bullets, final Bullet bullet,
						   final int speedX) {
		bullet.setSpeedX(speedX);
		bullet.setPierce(this.itemPierceCount);
		bullets.add(bullet);
	}

	/**
	 * Sideways steps of each lane in a spread fan, symmetric around the
	 * center (Team CS): 1 lane {0}, 2 {-1, 1}, 3 {-1, 0, 1}, 4 {-2, -1, 1, 2},
	 * 5 {-2, -1, 0, 1, 2}.
	 *
	 * @param lanes
	 *            Number of lanes, at least 1.
	 * @return Step of each lane, from left to right.
	 */
	static int[] spreadLanes(final int lanes) {
		final int count = Math.max(1, lanes);
		final int[] steps = new int[count];
		final int half = count / 2;
		int i = 0;
		for (int step = -half; step <= half; step++) {
			if (step == 0 && count % 2 == 0)
				continue;
			steps[i++] = step;
		}
		return steps;
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
