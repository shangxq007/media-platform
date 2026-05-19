package com.example.platform.extension.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExtensionResourceLimitsTest {

    @Test
    void shouldHaveCorrectDefaults() {
        assertEquals(4, ExtensionResourceLimits.DEFAULTS.maxConcurrency());
        assertEquals(256, ExtensionResourceLimits.DEFAULTS.maxMemoryMb());
        assertEquals(50, ExtensionResourceLimits.DEFAULTS.maxCpuPercent());
        assertEquals(100, ExtensionResourceLimits.DEFAULTS.maxQueueSize());
    }

    @Test
    void shouldHaveStricterUntrustedLimits() {
        assertTrue(ExtensionResourceLimits.UNTRUSTED.maxConcurrency() < ExtensionResourceLimits.DEFAULTS.maxConcurrency());
        assertTrue(ExtensionResourceLimits.UNTRUSTED.maxMemoryMb() < ExtensionResourceLimits.DEFAULTS.maxMemoryMb());
        assertTrue(ExtensionResourceLimits.UNTRUSTED.maxInputBytes() < ExtensionResourceLimits.DEFAULTS.maxInputBytes());
    }

    @Test
    void shouldHaveHigherTrustedLimits() {
        assertTrue(ExtensionResourceLimits.FULLY_TRUSTED.maxConcurrency() > ExtensionResourceLimits.DEFAULTS.maxConcurrency());
        assertTrue(ExtensionResourceLimits.FULLY_TRUSTED.maxMemoryMb() > ExtensionResourceLimits.DEFAULTS.maxMemoryMb());
    }

    @Test
    void shouldResolveByTrustLevel() {
        assertEquals(ExtensionResourceLimits.FULLY_TRUSTED, ExtensionResourceLimits.forTrustLevel(ExtensionTrustLevel.FULLY_TRUSTED));
        assertEquals(ExtensionResourceLimits.DEFAULTS, ExtensionResourceLimits.forTrustLevel(ExtensionTrustLevel.SEMI_TRUSTED));
        assertEquals(ExtensionResourceLimits.UNTRUSTED, ExtensionResourceLimits.forTrustLevel(ExtensionTrustLevel.UNTRUSTED));
    }

    @Test
    void shouldOverrideWithNonNullValues() {
        ExtensionResourceLimits base = ExtensionResourceLimits.DEFAULTS;
        ExtensionResourceLimits override = new ExtensionResourceLimits(8, 0, 0, 0, 0, 0, 0);
        ExtensionResourceLimits result = base.overrideWith(override);

        assertEquals(8, result.maxConcurrency());
        assertEquals(256, result.maxMemoryMb());
        assertEquals(50, result.maxCpuPercent());
    }

    @Test
    void shouldNotOverrideZeroValues() {
        ExtensionResourceLimits base = ExtensionResourceLimits.DEFAULTS;
        ExtensionResourceLimits override = new ExtensionResourceLimits(0, 512, 0, 0, 0, 0, 0);
        ExtensionResourceLimits result = base.overrideWith(override);

        assertEquals(4, result.maxConcurrency());
        assertEquals(512, result.maxMemoryMb());
    }
}
