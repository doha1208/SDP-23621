package engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Player ship movement values, read from res/ship.properties so they can be
 * tuned without recompiling.
 *
 * File lookup order: -Dinvaders.ship=&lt;path&gt; &gt; res/ship.properties
 * &gt; ship.properties on the classpath. A missing file, missing key or
 * invalid value falls back to the default and logs a warning, so a typo
 * never stops the game. Read again each time a level starts.
 */
public final class ShipConfig {

	/** System property that overrides the config file location. */
	static final String PATH_PROPERTY = "invaders.ship";
	/** Name of the config file. */
	static final String FILE_NAME = "ship.properties";
	/** Key of the top limit. */
	static final String TOP_LIMIT_KEY = "move.topLimit";
	/** Default top limit: the middle of the screen. */
	public static final double DEFAULT_TOP_LIMIT = 0.5d;

	/** Not instantiable. */
	private ShipConfig() {
	}

	/**
	 * @return How high the ship may fly, as a fraction of the screen height
	 *         from the top (0.0 to 1.0).
	 */
	public static double topLimit() {
		String raw = load().getProperty(TOP_LIMIT_KEY);
		if (raw == null)
			return DEFAULT_TOP_LIMIT;
		try {
			double value = Double.parseDouble(raw.trim());
			if (value >= 0.0d && value <= 1.0d)
				return value;
		} catch (NumberFormatException e) {
			// Falls through to the warning below.
		}
		Core.getLogger().warning("Invalid ship value " + TOP_LIMIT_KEY + "="
				+ raw + ", using " + DEFAULT_TOP_LIMIT + ".");
		return DEFAULT_TOP_LIMIT;
	}

	/**
	 * Finds and reads the config file. Never throws.
	 *
	 * @return Loaded settings, empty if no file could be read.
	 */
	private static Properties load() {
		Properties settings = new Properties();
		String configured = System.getProperty(PATH_PROPERTY);
		Path path = Paths.get(configured != null ? configured
				: "res/" + FILE_NAME);
		Logger logger = Core.getLogger();
		try {
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path,
						StandardCharsets.UTF_8)) {
					settings.load(reader);
				}
				return settings;
			}
			InputStream stream = ShipConfig.class.getClassLoader()
					.getResourceAsStream(FILE_NAME);
			if (stream != null) {
				try (Reader reader = new InputStreamReader(stream,
						StandardCharsets.UTF_8)) {
					settings.load(reader);
				}
				return settings;
			}
			logger.info("No ship config found, using defaults.");
		} catch (IOException | IllegalArgumentException e) {
			logger.warning("Couldn't read ship config, using defaults: "
					+ e.getMessage());
			settings.clear();
		}
		return settings;
	}
}
