package engine;

import java.awt.event.KeyEvent;

/**
 * Gameplay keys that more than one screen needs to agree on (Team
 * Entities): the item slot keys and the dash key. Change a key here and
 * the game screen, the tutorial and the item hints all follow.
 */
public final class ControlKeys {

	/**
	 * Item slot keys in slot order. Three slots by default; the fourth key
	 * is for the Extra Slots equipment, and the rest only matter if the
	 * slot count is raised in item-balance.properties.
	 */
	private static final int[] ITEM_SLOT_KEYS = { KeyEvent.VK_Z,
			KeyEvent.VK_X, KeyEvent.VK_C, KeyEvent.VK_V, KeyEvent.VK_B,
			KeyEvent.VK_N, KeyEvent.VK_M };
	/** Names of the item slot keys, as shown to the player. */
	private static final String[] ITEM_SLOT_LABELS = { "Z", "X", "C", "V",
			"B", "N", "M" };

	/** Dash dodge key. */
	public static final int DASH = KeyEvent.VK_CONTROL;
	/** Name of the dash key, as shown to the player. */
	public static final String DASH_LABEL = "Ctrl";

	/** Not instantiable. */
	private ControlKeys() {
	}

	/** @return How many item slots have a key. */
	public static int itemSlotCount() {
		return ITEM_SLOT_KEYS.length;
	}

	/**
	 * @param slot
	 *            Item slot, 0 for the first.
	 * @return Key code that uses the slot.
	 */
	public static int itemSlotKey(final int slot) {
		return ITEM_SLOT_KEYS[slot];
	}

	/**
	 * @param slot
	 *            Item slot, 0 for the first.
	 * @return Name of the key that uses the slot, or the slot number if
	 *         the slot has no key.
	 */
	public static String itemSlotLabel(final int slot) {
		return slot >= 0 && slot < ITEM_SLOT_LABELS.length
				? ITEM_SLOT_LABELS[slot] : Integer.toString(slot + 1);
	}
}
