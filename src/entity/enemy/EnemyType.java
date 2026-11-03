package entity.enemy;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import engine.DrawManager.SpriteType;

/**
 * The enemy catalogue: each type's look, attack and allowed movement
 * patterns in one list. Numbers (health, speed, bullet speed...) are not
 * here; they come from the balance file under the type's key.
 *
 * Sprites reuse the original invaders, told apart by colour. New art can
 * replace them later without changing anything else.
 */
public enum EnemyType {

	/** Flies in formation without shooting. */
	NORMAL("normal", SpriteType.EnemyShipA1, SpriteType.EnemyShipA2, 12, 8,
			Color.WHITE, FireStyle.NONE,
			MovePattern.CURVE, MovePattern.STRAIGHT_DOWN, MovePattern.SIDE_DIAGONAL),

	/** Shoots in the direction it flies. */
	SHOOTER("shooter", SpriteType.EnemyShipB1, SpriteType.EnemyShipB2, 12, 8,
			Color.CYAN, FireStyle.FORWARD,
			MovePattern.STRAIGHT_DOWN, MovePattern.SEMICIRCLE, MovePattern.GENTLE_DIAGONAL),

	/** Shoots at the player. */
	BAT("bat", SpriteType.EnemyShipC1, SpriteType.EnemyShipC2, 12, 8,
			new Color(190, 120, 255), FireStyle.AIMED,
			MovePattern.CURVE, MovePattern.SEMICIRCLE, MovePattern.GENTLE_DIAGONAL),

	/** Flies fast at the player, alone. */
	ASSASSIN("assassin", SpriteType.EnemyShipSpecial, SpriteType.EnemyShipSpecial,
			16, 7, new Color(255, 150, 0), FireStyle.NONE,
			MovePattern.CHARGE),

	/** Slow, big and tough; fires a fan of three at the player. */
	TANK("tank", SpriteType.EnemyShipB1, SpriteType.EnemyShipB2, 12, 8,
			new Color(120, 220, 120), FireStyle.AIMED_FAN3,
			MovePattern.STRAIGHT_DOWN, MovePattern.SIDE_DIAGONAL);

	/** Key prefix in the balance file. */
	private final String balanceKey;
	/** First animation frame. */
	private final SpriteType spriteA;
	/** Second animation frame. */
	private final SpriteType spriteB;
	/** Sprite width in sprite pixels. */
	private final int spriteColumns;
	/** Sprite height in sprite pixels. */
	private final int spriteRows;
	/** Colour the sprite is drawn in. */
	private final Color color;
	/** How it shoots. */
	private final FireStyle fireStyle;
	/** Movement patterns it may use. */
	private final List<MovePattern> patterns;

	EnemyType(final String balanceKey, final SpriteType spriteA,
			final SpriteType spriteB, final int spriteColumns,
			final int spriteRows, final Color color, final FireStyle fireStyle,
			final MovePattern... patterns) {
		this.balanceKey = balanceKey;
		this.spriteA = spriteA;
		this.spriteB = spriteB;
		this.spriteColumns = spriteColumns;
		this.spriteRows = spriteRows;
		this.color = color;
		this.fireStyle = fireStyle;
		this.patterns = Collections.unmodifiableList(Arrays.asList(patterns));
	}

	/** @return Key prefix in the balance file, such as "tank". */
	public String getBalanceKey() {
		return this.balanceKey;
	}

	/** @return First animation frame. */
	public SpriteType getSpriteA() {
		return this.spriteA;
	}

	/** @return Second animation frame. */
	public SpriteType getSpriteB() {
		return this.spriteB;
	}

	/** @return Sprite width in sprite pixels. */
	public int getSpriteColumns() {
		return this.spriteColumns;
	}

	/** @return Sprite height in sprite pixels. */
	public int getSpriteRows() {
		return this.spriteRows;
	}

	/** @return Colour the sprite is drawn in. */
	public Color getColor() {
		return this.color;
	}

	/** @return How it shoots. */
	public FireStyle getFireStyle() {
		return this.fireStyle;
	}

	/** @return Movement patterns it may use. */
	public List<MovePattern> getPatterns() {
		return this.patterns;
	}

	/**
	 * @param pattern Movement pattern.
	 * @return True if this type may fly that pattern.
	 */
	public boolean allows(final MovePattern pattern) {
		return this.patterns.contains(pattern);
	}
}
