package entity.enemy;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple timetable of spawn commands per level. This is a stand-in so
 * normal stages are playable until the level designer system replaces it
 * through {@link LevelDirector}. Time counts active frames only, so pausing
 * or the start countdown does not use it up.
 */
public final class WaveScript implements LevelDirector {

	/** Frames per second the game runs at. */
	private static final int FPS = 60;

	private static final EnemyType N = EnemyType.NORMAL;
	private static final EnemyType S = EnemyType.SHOOTER;
	private static final EnemyType B = EnemyType.BAT;
	private static final EnemyType A = EnemyType.ASSASSIN;
	private static final EnemyType T = EnemyType.TANK;

	/** Spawn frames, in order. */
	private final List<Integer> frames = new ArrayList<Integer>();
	/** Spawn commands, matching {@link #frames}. */
	private final List<SpawnRequest> requests = new ArrayList<SpawnRequest>();
	/** Index of the next command to send. */
	private int next;
	/** Active frames so far. */
	private int frame;

	/** @return A script that spawns nothing. */
	public static WaveScript empty() {
		return new WaveScript();
	}

	/**
	 * Adds a spawn command; commands may be added in any order.
	 *
	 * @param seconds Time from the level start, in seconds.
	 * @param request What to spawn.
	 * @return This script.
	 */
	public WaveScript at(final double seconds, final SpawnRequest request) {
		int when = (int) Math.round(seconds * FPS);
		int index = 0;
		while (index < this.frames.size() && this.frames.get(index) <= when)
			index++;
		this.frames.add(index, when);
		this.requests.add(index, request);
		return this;
	}

	/**
	 * Adds a formation of a type on a random allowed pattern.
	 *
	 * @param seconds Time from the level start, in seconds.
	 * @param type    Enemy type.
	 * @return This script.
	 */
	public WaveScript at(final double seconds, final EnemyType type) {
		return at(seconds, SpawnRequest.of(type));
	}

	@Override
	public void update(final EnemyManager manager, final double playerX,
			final double playerY) {
		this.frame++;
		while (this.next < this.frames.size()
				&& this.frames.get(this.next) <= this.frame) {
			manager.spawn(this.requests.get(this.next), playerX, playerY);
			this.next++;
		}
	}

	@Override
	public boolean isFinished() {
		return this.next >= this.frames.size();
	}

	/** @return Number of spawn commands in the script. */
	public int size() {
		return this.frames.size();
	}

	/**
	 * Temporary timetable for each normal stage. Stages 5 and 10 are boss
	 * stages and do not use it.
	 *
	 * @param level Level number.
	 * @return The level's script; later levels reuse the last one.
	 */
	public static WaveScript forLevel(final int level) {
		switch (level) {
		case 1:
			return build(1, N, 4, N, 7, N, 10, N, 13, N, 16, N, 19, N, 22, N);
		case 2:
			return build(1, N, 4, N, 7, S, 10, N, 13, S, 16, N, 19, S, 22, N,
					25, S);
		case 3:
			return build(1, N, 4, B, 7, S, 9, N, 12, B, 15, S, 17, N, 20, B,
					23, S, 26, B);
		case 4:
			return build(1, N, 3, S, 6, A, 8, B, 10, N, 12, A, 14, S, 16, A,
					18, B, 20, N, 22, A, 24, S, 26, A, 28, B);
		case 6:
			return build(1, N, 3, S, 5, T, 8, B, 10, A, 12, N, 14, S, 16, A,
					18, T, 21, B, 23, A, 25, N, 27, S, 29, A, 31, B);
		case 7:
			return build(1, N, 2, S, 4, B, 6, A, 7, N, 9, T, 11, S, 12, A,
					14, B, 16, N, 17, A, 19, S, 21, T, 23, B, 24, A, 26, N,
					28, S, 29, A, 31, B);
		case 8:
			return build(1, N, 2, B, 3, S, 5, A, 6, T, 8, N, 9, B, 10, A,
					12, S, 13, T, 15, A, 16, N, 17, B, 19, S, 20, A, 22, T,
					23, N, 24, B, 26, A, 27, S, 29, T, 30, B, 31, A, 33, N);
		default:
			return build(1, N, 2, S, 3, B, 4, A, 5, T, 7, N, 8, S, 9, A,
					10, B, 11, T, 13, A, 14, N, 15, S, 16, B, 17, A, 19, T,
					20, N, 21, S, 22, A, 23, B, 25, T, 26, A, 27, N, 28, S,
					29, B, 30, A, 32, T, 33, B, 34, A);
		}
	}

	/** Builds a script from alternating seconds and enemy types. */
	private static WaveScript build(final Object... secondsAndTypes) {
		WaveScript script = new WaveScript();
		for (int i = 0; i < secondsAndTypes.length; i += 2)
			script.at(((Number) secondsAndTypes[i]).doubleValue(),
					(EnemyType) secondsAndTypes[i + 1]);
		return script;
	}
}
