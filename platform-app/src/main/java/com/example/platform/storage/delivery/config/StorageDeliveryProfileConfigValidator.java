package com.example.platform.storage.delivery.config;

import com.example.platform.storage.delivery.contract.*;
import com.example.platform.storage.delivery.validation.StorageDeliveryProfileValidator;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Validates storage delivery profile configuration at startup.
 * No remote calls. No runtime switching.
 */
public class StorageDeliveryProfileConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(StorageDeliveryProfileConfigValidator.class);
    private final StorageDeliveryProfileValidator validator = new StorageDeliveryProfileValidator();

    public List<StorageDeliveryProfileValidationIssue> validate(StorageDeliveryProfileConfigProperties config) {
        List<StorageDeliveryProfileValidationIssue> issues = new ArrayList<>();

        if (config.getDefaultProfile() == null || config.getDefaultProfile().isBlank()) {
            issues.add(new StorageDeliveryProfileValidationIssue(null, "DEFAULT_PROFILE_MISSING", "Default profile is required", StorageDeliveryProfileValidationSeverity.ERROR));
        }

        if (config.getProfiles() == null || config.getProfiles().isEmpty()) {
            issues.add(new StorageDeliveryProfileValidationIssue(null, "PROFILES_MISSING", "At least one profile is required", StorageDeliveryProfileValidationSeverity.ERROR));
            return issues;
        }

        for (var entry : config.getProfiles().entrySet()) {
            var profileId = entry.getKey();
            var profileConfig = entry.getValue();

            try {
                var profile = toProfile(profileId, profileConfig);
                var result = validator.validate(profile);
                issues.addAll(result.errors());
            } catch (Exception e) {
                issues.add(new StorageDeliveryProfileValidationIssue(
                    new StorageDeliveryProfileId(profileId), "PROFILE_CONVERSION_ERROR",
                    "Failed to convert profile: " + e.getMessage(), StorageDeliveryProfileValidationSeverity.ERROR));
            }
        }

        return issues;
    }

    private StorageDeliveryProfile toProfile(String id, StorageDeliveryProfileConfigProperties.ProfileConfig config) {
        return new StorageDeliveryProfile(
            new StorageDeliveryProfileId(id),
            StorageDeliveryProfileStatus.valueOf(config.getStatus()),
            config.isEnabled(),
            config.isRuntimeSelectable(),
            StorageProviderType.valueOf(config.getProvider()),
            StorageBackendType.valueOf(config.getBackend()),
            StorageAccessMode.valueOf(config.getAccessMode()),
            AccessDescriptorContractType.valueOf(config.getAccessDescriptorType()),
            toCapabilities(config.getCapabilities()),
            toSecurityPolicy(config.getSecurity())
        );
    }

    private StorageDeliveryProfileCapabilities toCapabilities(StorageDeliveryProfileConfigProperties.CapabilitiesConfig caps) {
        if (caps == null) return StorageDeliveryProfileCapabilities.noPublicAccess();
        return new StorageDeliveryProfileCapabilities(
            caps.isWriteArtifact(), caps.isReadArtifact(), caps.isPresignRead(),
            caps.isInternalStream(), caps.isExternalBucket(), caps.isExportBundle(),
            caps.isDeleteArtifact(), caps.isSupportsRangeRead(), caps.isSupportsContentMetadata()
        );
    }

    private StorageDeliveryProfileSecurityPolicy toSecurityPolicy(StorageDeliveryProfileConfigProperties.SecurityConfig sec) {
        if (sec == null) return StorageDeliveryProfileSecurityPolicy.internalOnly();
        return new StorageDeliveryProfileSecurityPolicy(
            sec.isExposeStorageReference(), sec.isExposeBucket(), sec.isExposeObjectKey(),
            sec.isExposeLocalPath(), sec.isPersistSignedUrl(), sec.isRequireTenantProjectScope(), sec.isUserFacing()
        );
    }
}
