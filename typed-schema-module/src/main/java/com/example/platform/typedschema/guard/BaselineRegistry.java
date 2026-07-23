package com.example.platform.typedschema.guard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Manages the baseline counts of untyped jOOQ DSL calls.
 *
 * <p>The baseline records the known counts at a specific point in time.
 * The guard enforces that new code does not increase these counts.</p>
 *
 * <p>Format: Java properties file with keys:</p>
 * <ul>
 *   <li>{@code production.raw} — count of untyped calls in production sources</li>
 *   <li>{@code test.raw} — count of untyped calls in test sources</li>
 * </ul>
 */
public final class BaselineRegistry {

    private BaselineRegistry() {
        // utility class
    }

    /**
     * Load baseline from file.
     *
     * @param baselineFile path to the baseline properties file
     * @return baseline values; defaults if file doesn't exist
     * @throws IOException if the file cannot be read
     */
    public static Baseline load(Path baselineFile) throws IOException {
        Properties props = new Properties();
        if (Files.exists(baselineFile)) {
            try (var in = Files.newInputStream(baselineFile)) {
                props.load(in);
            }
        }
        return new Baseline(
            Integer.parseInt(props.getProperty("production.raw", "3042")),
            Integer.parseInt(props.getProperty("test.raw", "259"))
        );
    }

    /**
     * Save baseline to file.
     *
     * @param baselineFile path to the baseline properties file
     * @param baseline     the baseline values to persist
     * @throws IOException if the file cannot be written
     */
    public static void save(Path baselineFile, Baseline baseline) throws IOException {
        Properties props = new Properties();
        props.setProperty("production.raw", String.valueOf(baseline.productionRaw()));
        props.setProperty("test.raw", String.valueOf(baseline.testRaw()));
        try (var out = Files.newOutputStream(baselineFile)) {
            props.store(out, "jOOQ untyped identifier baseline");
        }
    }

    /**
     * Baseline counts for untyped jOOQ DSL calls.
     *
     * @param productionRaw count of untyped calls in production sources
     * @param testRaw       count of untyped calls in test sources
     */
    public record Baseline(int productionRaw, int testRaw) {
    }
}
