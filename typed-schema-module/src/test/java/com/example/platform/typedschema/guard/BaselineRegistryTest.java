package com.example.platform.typedschema.guard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BaselineRegistry}.
 * Covers load, save, and default values.
 */
class BaselineRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void loadReturnsDefaultsWhenFileMissing() throws IOException {
        Path missing = tempDir.resolve("nonexistent.properties");
        BaselineRegistry.Baseline baseline = BaselineRegistry.load(missing);
        assertThat(baseline.productionRaw()).isEqualTo(3112);
        assertThat(baseline.testRaw()).isEqualTo(259);
    }

    @Test
    void loadReadsFromFile() throws IOException {
        Path file = tempDir.resolve("baseline.properties");
        Properties props = new Properties();
        props.setProperty("production.raw", "100");
        props.setProperty("test.raw", "50");
        try (var out = Files.newOutputStream(file)) {
            props.store(out, "test");
        }
        BaselineRegistry.Baseline baseline = BaselineRegistry.load(file);
        assertThat(baseline.productionRaw()).isEqualTo(100);
        assertThat(baseline.testRaw()).isEqualTo(50);
    }

    @Test
    void saveWritesProperties() throws IOException {
        Path file = tempDir.resolve("out.properties");
        BaselineRegistry.Baseline baseline = new BaselineRegistry.Baseline(200, 75);
        BaselineRegistry.save(file, baseline);
        assertThat(Files.exists(file)).isTrue();
        Properties props = new Properties();
        try (var in = Files.newInputStream(file)) {
            props.load(in);
        }
        assertThat(props.getProperty("production.raw")).isEqualTo("200");
        assertThat(props.getProperty("test.raw")).isEqualTo("75");
    }

    @Test
    void roundTrip() throws IOException {
        Path file = tempDir.resolve("roundtrip.properties");
        BaselineRegistry.Baseline original = new BaselineRegistry.Baseline(500, 123);
        BaselineRegistry.save(file, original);
        BaselineRegistry.Baseline loaded = BaselineRegistry.load(file);
        assertThat(loaded).isEqualTo(original);
    }

    @Test
    void baselineRecordEquality() {
        BaselineRegistry.Baseline a = new BaselineRegistry.Baseline(10, 20);
        BaselineRegistry.Baseline b = new BaselineRegistry.Baseline(10, 20);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void baselineRecordInequality() {
        BaselineRegistry.Baseline a = new BaselineRegistry.Baseline(10, 20);
        BaselineRegistry.Baseline b = new BaselineRegistry.Baseline(10, 21);
        assertThat(a).isNotEqualTo(b);
    }
}
