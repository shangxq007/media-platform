package com.example.platform.ingest.preflight.policy.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class IngestPreflightPolicyConfigValidatorTest {

    private final IngestPreflightPolicyConfigValidator validator = new IngestPreflightPolicyConfigValidator();

    @Test
    void testDefaultConfigValid() {
        var config = new IngestPreflightPolicyProperties();
        assertTrue(validator.isValid(config));
        assertTrue(validator.validate(config).isEmpty());
    }

    @Test
    void testDefaultValues() {
        var config = new IngestPreflightPolicyProperties();
        assertFalse(config.isEnabled());
        assertEquals("report_only", config.getMode());
        assertEquals("preview_safe", config.getProfile());
        assertTrue(config.isFailOpen());
        assertTrue(config.isIncludeWarningFindings());
        assertTrue(config.isIncludeMediaTechnicalFindings());
        assertTrue(config.isIncludeRejectCandidates());
        assertEquals(50, config.getMaxFindings());
        assertTrue(config.isLogResult());
    }

    @Test
    void testEnforceModeRejected() {
        var config = new IngestPreflightPolicyProperties();
        config.setMode("enforce");
        assertFalse(validator.isValid(config));
        assertTrue(validator.validate(config).stream().anyMatch(e -> e.contains("report_only")));
    }

    @Test
    void testFailOpenFalseRejected() {
        var config = new IngestPreflightPolicyProperties();
        config.setFailOpen(false);
        assertFalse(validator.isValid(config));
        assertTrue(validator.validate(config).stream().anyMatch(e -> e.contains("fail-open")));
    }

    @Test
    void testMaxFindingsBounds() {
        var config = new IngestPreflightPolicyProperties();
        config.setMaxFindings(0);
        assertFalse(validator.isValid(config));

        config.setMaxFindings(1001);
        assertFalse(validator.isValid(config));

        config.setMaxFindings(50);
        assertTrue(validator.isValid(config));
    }

    @Test
    void testInvalidProfileRejected() {
        var config = new IngestPreflightPolicyProperties();
        config.setProfile("strict");
        assertFalse(validator.isValid(config));
    }
}
