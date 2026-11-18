package entity;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Per-ship stats read from res/ship-stats.properties, so ship balance can be
 * tuned without recompiling. Keys are {@code <ship>.<stat>}, for example
 * {@code fast_move.speed=4}. A missing file or key keeps the value written in
 * {@link Ship.ShipType}; a value that is not a whole number above 0 is logged
 * and ignored, so a typo cannot stop the game from starting.
 */
final class ShipStats {

	/** File name, looked up in res/ and then on the classpath. */
	static final String FILE_NAME = "ship-stats.properties";

	/**
	 * Same logger as Core.getLogger(), looked up by name: ShipType is built
	 * while other classes are still loading, so it must not touch Core.
	 */
	private static final Logger LOGGER = Logger.getLogger("Core");

	/** Settings read once, when the first ship type is built. */
	private static final Properties SETTINGS = load();

	private ShipStats() {
	}

	/**
	 * Value of one stat for one ship.
	 *
	 * @param ship
	 *            Ship type name, e.g. FAST_MOVE.
	 * @param stat
	 *            Stat name, e.g. speed.
	 * @param fallback
	 *            Built-in value.
	 * @return The configured value, or the fallback.
	 */
	static int get(final String ship, final String stat, final int fallback) {
		return positive(SETTINGS, ship.toLowerCase(Locale.ROOT) + "." + stat,
				fallback);
	}

	/**
	 * Reads a whole number above 0.
	 *
	 * @param settings
	 *            Loaded settings.
	 * @param key
	 *            Property key.
	 * @param fallback
	 *            Value used when the key is missing or invalid.
	 * @return The parsed value, or the fallback.
	 */
	static int positive(final Properties settings, final String key,
			final int fallback) {
		String value = settings.getProperty(key);
		if (value == null || value.trim().isEmpty())
			return fallback;
		try {
			int parsed = Integer.parseInt(value.trim());
			if (parsed > 0)
				return parsed;
		} catch (NumberFormatException e) {
			// Reported below together with values <= 0.
		}
		LOGGER.warning("Ignoring " + key + "=" + value.trim() + " in "
				+ FILE_NAME + ": expected a whole number above 0, using "
				+ fallback + ".");
		return fallback;
	}

	private static Properties load() {
		Properties settings = new Properties();
		Path path = Paths.get("res", FILE_NAME);
		try {
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path,
						StandardCharsets.UTF_8)) {
					settings.load(reader);
				}
			} else {
				InputStream stream = ShipStats.class.getClassLoader()
						.getResourceAsStream(FILE_NAME);
				if (stream != null) {
					try (Reader reader = new InputStreamReader(stream,
							StandardCharsets.UTF_8)) {
						settings.load(reader);
					}
				}
			}
		} catch (IOException | IllegalArgumentException e) {
			LOGGER.warning("Cannot read " + path.toAbsolutePath()
					+ ", using the built-in ship stats: " + e.getMessage());
			settings.clear();
		}
		return settings;
	}
}
