package com.example.platform.storage.delivery.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StorageDeliveryProfileConfigValidatorTest {

    private final StorageDeliveryProfileConfigValidator validator = new StorageDeliveryProfileConfigValidator();

    @Test
    void testValidPreviewR2Config() {
        var config = new StorageDeliveryProfileConfigProperties();
        config.setDefaultProfile("preview-r2-signed-url");

        var profileConfig = new StorageDeliveryProfileConfigProperties.ProfileConfig();
        profileConfig.setStatus("PREVIEW_VERIFIED");
        profileConfig.setEnabled(true);
        profileConfig.setRuntimeSelectable(true);
        profileConfig.setProvider("S3_COMPATIBLE");
        profileConfig.setBackend("R2");
        profileConfig.setAccessMode("SIGNED_URL");
        profileConfig.setAccessDescriptorType("SIGNED_URL");

        var caps = new StorageDeliveryProfileConfigProperties.CapabilitiesConfig();
        caps.setWriteArtifact(true);
        caps.setReadArtifact(true);
        caps.setPresignRead(true);
        caps.setSupportsContentMetadata(true);
        profileConfig.setCapabilities(caps);

        var sec = new StorageDeliveryProfileConfigProperties.SecurityConfig();
        sec.setRequireTenantProjectScope(true);
        sec.setUserFacing(true);
        profileConfig.setSecurity(sec);

        config.setProfiles(Map.of("preview-r2-signed-url", profileConfig));

        var issues = validator.validate(config);
        assertTrue(issues.isEmpty(), "Valid config should have no issues: " + issues);
    }

    @Test
    void testMissingDefaultProfile() {
        var config = new StorageDeliveryProfileConfigProperties();
        config.setDefaultProfile(null);

        var issues = validator.validate(config);
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(i -> i.code().equals("DEFAULT_PROFILE_MISSING")));
    }
}
