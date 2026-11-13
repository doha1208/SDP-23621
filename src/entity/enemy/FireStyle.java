package entity.enemy;

import java.util.List;

/** How an enemy shoots. Bullet speed and fan angle come from its stats. */
public enum FireStyle {

	/** Does not shoot. */
	NONE {
		@Override
		public void fire(final Enemy shooter, final double targetX,
				final double targetY, final List<EnemyBullet> out) {
			// Never shoots.
		}
	},

	/** One bullet in the direction the enemy is flying. */
	FORWARD {
		@Override
		public void fire(final Enemy shooter, final double targetX,
				final double targetY, final List<EnemyBullet> out) {
			shoot(shooter, shooter.getHeadingX(), shooter.getHeadingY(), out);
		}
	},

	/** One bullet at the player. */
	AIMED {
		@Override
		public void fire(final Enemy shooter, final double targetX,
				final double targetY, final List<EnemyBullet> out) {
			double angle = angleTo(shooter, targetX, targetY);
			shoot(shooter, Math.cos(angle), Math.sin(angle), out);
		}
	},

	/** Three bullets in a fan, the middle one at the player. */
	AIMED_FAN3 {
		@Override
		public void fire(final Enemy shooter, final double targetX,
				final double targetY, final List<EnemyBullet> out) {
			double angle = angleTo(shooter, targetX, targetY);
			double spread = Math.toRadians(shooter.getStats().spreadDegrees);
			for (int i = -1; i <= 1; i++)
				shoot(shooter, Math.cos(angle + i * spread),
						Math.sin(angle + i * spread), out);
		}
	};

	/**
	 * Fires this style's bullets.
	 *
	 * @param shooter Enemy that shoots.
	 * @param targetX Player center x.
	 * @param targetY Player center y.
	 * @param out     List the new bullets are added to.
	 */
	public abstract void fire(Enemy shooter, double targetX, double targetY,
			List<EnemyBullet> out);

	/** @return Angle from the shooter's center to the target, in radians. */
	private static double angleTo(final Enemy shooter, final double targetX,
			final double targetY) {
		double dx = targetX - shooter.getCenterX();
		double dy = targetY - shooter.getCenterY();
		if (dx == 0 && dy == 0)
			return Math.PI / 2;
		return Math.atan2(dy, dx);
	}

	/** Adds one bullet leaving the shooter's edge in the given direction. */
	private static void shoot(final Enemy shooter, final double directionX,
			final double directionY, final List<EnemyBullet> out) {
		double edge = Math.max(shooter.getWidth(), shooter.getHeight()) / 2.0;
		double speed = shooter.getStats().bulletSpeed;
		out.add(new EnemyBullet(shooter.getCenterX() + directionX * edge,
				shooter.getCenterY() + directionY * edge,
				directionX * speed, directionY * speed));
	}
}
