package com.example.platform.ingest.preflight.persistence.contract;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SafePreflightPersistenceContractValidatorTest {

    private final SafePreflightPersistenceContractValidator validator = new SafePreflightPersistenceContractValidator();

    @Test
    void testDefaultConfigValid() {
        var config = new SafePreflightPersistenceContractProperties();
        assertTrue(validator.isValid(config));
    }

    @Test
    void testDefaultValues() {
        var config = new SafePreflightPersistenceContractProperties();
        assertEquals(SafePreflightPersistenceMode.DISABLED, config.getMode());
        assertEquals(SafePreflightPersistenceAccessScope.DEV_ONLY, config.getAccessScope());
        assertEquals(7, config.getRetentionDays());
        assertTrue(config.isFailOpen());
        assertFalse(config.isPublicResponseEnabled());
        assertFalse(config.isAllowRawMetadata());
        assertFalse(config.isAllowLocalPath());
        assertFalse(config.isAllowStorageInternals());
        assertFalse(config.isAllowSignedUrl());
        assertFalse(config.isAllowCredentials());
    }

    @Test
    void testRetentionOverSevenDays() {
        var config = new SafePreflightPersistenceContractProperties();
        config.setRetentionDays(8);
        assertFalse(validator.isValid(config));
    }

    @Test
    void testPublicResponseEnabled() {
        var config = new SafePreflightPersistenceContractProperties();
        config.setPublicResponseEnabled(true);
        assertFalse(validator.isValid(config));
    }

    @Test
    void testRawMetadataAllowed() {
        var config = new SafePreflightPersistenceContractProperties();
        config.setAllowRawMetadata(true);
        assertFalse(validator.isValid(config));
    }

    @Test
    void testFailOpenFalse() {
        var config = new SafePreflightPersistenceContractProperties();
        config.setFailOpen(false);
        assertFalse(validator.isValid(config));
    }
}
