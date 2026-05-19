package com.example.platform.extension.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionResultTest {

    @Test
    void shouldCreateSuccess() {
        ExtensionResult result = ExtensionResult.success("{\"ok\":true}");

        assertTrue(result.success());
        assertEquals("{\"ok\":true}", result.outputJson());
        assertNull(result.errorCode());
        assertNull(result.errorMessage());
        assertTrue(result.metrics().isEmpty());
    }

    @Test
    void shouldCreateSuccessWithMetrics() {
        ExtensionResult result = ExtensionResult.success("{\"ok\":true}",
                Map.of("durationMs", 100L, "outputSize", 50));

        assertTrue(result.success());
        assertEquals(100L, result.metrics().get("durationMs"));
        assertEquals(50, result.metrics().get("outputSize"));
    }

    @Test
    void shouldCreateFailure() {
        ExtensionResult result = ExtensionResult.failure("ERR-001", "Something went wrong");

        assertFalse(result.success());
        assertEquals("{}", result.outputJson());
        assertEquals("ERR-001", result.errorCode());
        assertEquals("Something went wrong", result.errorMessage());
    }

    @Test
    void shouldAddMetric() {
        ExtensionResult base = ExtensionResult.success("{}");
        ExtensionResult updated = base.withMetric("key", "value");

        assertTrue(updated.success());
        assertFalse(base.metrics().containsKey("key"));
        assertEquals("value", updated.metrics().get("key"));
    }

    @Test
    void shouldDefaultOutputJson() {
        ExtensionResult result = new ExtensionResult(true, null, null, null, null);
        assertEquals("{}", result.outputJson());
    }
}
