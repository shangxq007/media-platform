package com.example.platform.typedschema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the jOOQ version authority mechanism.
 * Verifies the 3 key scenarios:
 * 1. Property exists and has expected value
 * 2. Property is NOT the known-bad version 3.19.18
 * 3. Property can be loaded and compared
 */
class JooqVersionAuthorityTest {

    private static final String PROPERTY_FILE = "gradle.properties";
    private static final String VERSION_PROPERTY = "jooq.codegen.version";
    private static final String EXPECTED_VERSION = "3.19.30";
    private static final String KNOWN_BAD_VERSION = "3.19.18";

    @Test
    void versionPropertyExists() throws IOException {
        Properties props = loadProperties();
        assertThat(props.containsKey(VERSION_PROPERTY))
            .as("Property '%s' must exist in %s", VERSION_PROPERTY, PROPERTY_FILE)
            .isTrue();
    }

    @Test
    void versionMatchesExpected() throws IOException {
        Properties props = loadProperties();
        String actual = props.getProperty(VERSION_PROPERTY);
        assertThat(actual)
            .as("jOOQ version must be %s", EXPECTED_VERSION)
            .isEqualTo(EXPECTED_VERSION);
    }

    @Test
    void versionIsNotKnownBad() throws IOException {
        Properties props = loadProperties();
        String actual = props.getProperty(VERSION_PROPERTY);
        assertThat(actual)
            .as("jOOQ version must not be the known-bad %s", KNOWN_BAD_VERSION)
            .isNotEqualTo(KNOWN_BAD_VERSION);
    }

    private Properties loadProperties() throws IOException {
        Properties props = new Properties();
        // Gradle sets project.root.dir for us; fall back to classpath
        String rootDir = System.getProperty("project.root.dir");
        Path propsPath = rootDir != null
            ? Path.of(rootDir, PROPERTY_FILE)
            : Path.of("..", PROPERTY_FILE); // typed-schema-module is one level deep
        try (var in = Files.newInputStream(propsPath)) {
            props.load(in);
        }
        return props;
    }
}
