package entity;

/**
 * Dash dodge of the player ship (Team CS). A dash sends the ship a short
 * way sideways, fast at first and slowing at the end, and the ship cannot
 * be hit while it lasts. Then the dash has to recharge.
 * <p>
 * Time is counted in game frames, so pausing the game pauses the dash
 * and its recharge too. Call {@link #update} once per gameplay frame.
 */
public final class ShipDash {

	/** Frames a dash lasts, about 180 ms at 60 fps. */
	static final int DURATION_FRAMES = 11;
	/** Pixels a full dash covers. */
	static final int DISTANCE = 110;
	/** Frames from the end of a dash until the next one is ready, 1.5 s. */
	static final int COOLDOWN_FRAMES = 90;

	/** Frames done in the current dash, -1 while not dashing. */
	private int frame = -1;
	/** Pixels covered so far in the current dash. */
	private int covered;
	/** Frames left until the next dash is ready. */
	private int cooldown;
	/** Direction of the next or current dash: -1 left, 1 right. */
	private int direction = 1;

	/**
	 * Remembers which way the ship last moved; a dash started without a
	 * direction key goes that way.
	 *
	 * @param facing
	 *            -1 for left, 1 for right, 0 to keep the last direction.
	 */
	public void face(final int facing) {
		if (facing != 0 && !isDashing())
			this.direction = Integer.signum(facing);
	}

	/**
	 * Starts a dash in the faced direction, if one is ready.
	 *
	 * @return True if the dash started.
	 */
	public boolean start() {
		if (isDashing() || this.cooldown > 0)
			return false;
		this.frame = 0;
		this.covered = 0;
		return true;
	}

	/**
	 * Advances one frame.
	 *
	 * @return Pixels to move the ship this frame: negative is left, 0
	 *         while not dashing.
	 */
	public int update() {
		if (!isDashing()) {
			if (this.cooldown > 0)
				this.cooldown--;
			return 0;
		}
		this.frame++;
		int target = (int) Math.round(DISTANCE
				* easeOut(this.frame / (double) DURATION_FRAMES));
		int step = target - this.covered;
		this.covered = target;
		if (this.frame >= DURATION_FRAMES) {
			this.frame = -1;
			this.cooldown = COOLDOWN_FRAMES;
		}
		return step * this.direction;
	}

	/**
	 * @param t
	 *            Progress of the dash, 0 to 1.
	 * @return Part of the distance covered: fast start, soft stop.
	 */
	static double easeOut(final double t) {
		double left = 1 - t;
		return 1 - left * left * left;
	}

	/** @return True while dashing; the ship cannot be hit then. */
	public boolean isDashing() {
		return this.frame >= 0;
	}

	/** @return -1 if the dash goes left, 1 if it goes right. */
	public int getDirection() {
		return this.direction;
	}

	/** @return How far the next dash has recharged, 0 to 1 (1 = ready). */
	public double getCharge() {
		if (isDashing())
			return 0;
		return 1 - this.cooldown / (double) COOLDOWN_FRAMES;
	}

	/** Stops any dash and makes the next one ready at once. */
	public void reset() {
		this.frame = -1;
		this.covered = 0;
		this.cooldown = 0;
	}
}
