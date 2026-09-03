package org.eu.net.pool.hexic.hexcompat;

import com.mojang.serialization.Codec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Compatibility support for Hexic's configurable signed/int-sized item stacks.
 *
 * <p>The original 2.1.0 implementation read these values from any
 * {@code config/*.properties} file and changed both persistent and packet
 * serialization. Minecraft 1.21 moved ItemStack persistence to codecs, so the
 * old byte-NBT redirects cannot be carried forward directly.</p>
 */
public final class StackCountCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("hexic");
    private static final String MIN_KEY = "hexic.min_stack_size";
    private static final String MAX_KEY = "hexic.max_stack_size";
    private static volatile Range range;

    private StackCountCompat() {
    }

    public static int min() {
        return range().min();
    }

    public static int max() {
        return range().max();
    }

    public static int clamp(int count) {
        Range current = range();
        return Math.max(current.min(), Math.min(current.max(), count));
    }

    /**
     * Codec used in place of vanilla's {@code intRange(1, 99)} count codec.
     * The xmap clamps on both read and write, matching the old mixin's
     * load/save behavior instead of rejecting a whole containing object.
     */
    public static Codec<Integer> codec() {
        return Codec.INT.xmap(StackCountCompat::clamp, StackCountCompat::clamp);
    }

    public static void resetForProbe() {
        range = null;
    }

    private static Range range() {
        Range result = range;
        if (result == null) {
            synchronized (StackCountCompat.class) {
                result = range;
                if (result == null) {
                    result = loadRange();
                    range = result;
                }
            }
        }
        return result;
    }

    private static Range loadRange() {
        Properties discovered = new Properties();
        Path config = Path.of("config");
        if (Files.isDirectory(config)) {
            try (Stream<Path> paths = Files.list(config)) {
                paths.filter(path -> path.getFileName().toString().endsWith(".properties"))
                    .sorted()
                    .forEach(path -> loadProperties(path, discovered));
            } catch (IOException exception) {
                LOGGER.warn("Failed to enumerate Hexic property files in {}", config, exception);
            }
        }

        int min = readInt(MIN_KEY, discovered, 0);
        int max = readInt(MAX_KEY, discovered, Integer.MAX_VALUE);
        if (min > max) {
            LOGGER.warn(
                "Ignoring invalid Hexic stack range {}..{}; using default 0..{}",
                min,
                max,
                Integer.MAX_VALUE
            );
            return new Range(0, Integer.MAX_VALUE);
        }
        return new Range(min, max);
    }

    private static void loadProperties(Path path, Properties target) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Properties file = new Properties();
            file.load(reader);
            file.forEach(target::putIfAbsent);
        } catch (IOException exception) {
            LOGGER.warn("Failed to read Hexic properties from {}", path, exception);
        }
    }

    private static int readInt(String key, Properties discovered, int fallback) {
        String raw = System.getProperty(key);
        if (raw == null) {
            raw = discovered.getProperty(key);
            if (raw != null) {
                System.setProperty(key, raw);
            }
        }
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            LOGGER.warn("Ignoring invalid integer value for {}: {}", key, raw);
            return fallback;
        }
    }

    private record Range(int min, int max) {
    }
}
