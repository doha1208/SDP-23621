package level;

/** One run: 120 active seconds, one boss, then one terminal result. */
public final class TimedRun {

	public static final int COMBAT_SECONDS = 120;

	public enum Phase { COMBAT, BOSS, WON, LOST, QUIT }

	private static final long DURATION = COMBAT_SECONDS * 1_000_000_000L;
	private Phase phase = Phase.COMBAT;
	private long elapsed;
	private long previous;
	private boolean counting;

	/** Call once per frame, including pauses/countdowns. Death wins ties. */
	public void update(final long now, final boolean active, final int lives,
			final boolean bossDefeated) {
		if (isFinished())
			return;
		if (lives <= 0) {
			this.phase = Phase.LOST;
			return;
		}
		if (this.phase == Phase.COMBAT) {
			if (active && this.counting)
				this.elapsed += Math.min(DURATION - this.elapsed,
						Math.max(0, now - this.previous));
			this.previous = now;
			this.counting = active;
			if (this.elapsed >= DURATION)
				this.phase = Phase.BOSS;
		} else if (active && bossDefeated) {
			this.phase = Phase.WON;
		}
	}

	/** Abandoning never becomes a clear or banks pending diamonds. */
	public void quit() {
		if (!isFinished())
			this.phase = Phase.QUIT;
	}

	public Phase getPhase() { return this.phase; }

	public boolean isFinished() {
		return this.phase == Phase.WON || this.phase == Phase.LOST
				|| this.phase == Phase.QUIT;
	}

	/** Round up so the HUD never says zero before the transition. */
	public int remainingSeconds() {
		return (int) ((DURATION - this.elapsed + 999_999_999L) / 1_000_000_000L);
	}

	public String label() {
		if (this.phase != Phase.COMBAT)
			return this.phase == Phase.WON ? "CLEAR" : this.phase.name();
		int remaining = remainingSeconds();
		return String.format(java.util.Locale.ROOT, "BOSS IN %d:%02d",
				remaining / 60, remaining % 60);
	}
}
