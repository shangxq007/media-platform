package com.example.platform.ingest.preflight.persistence.contract;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SafePreflightPersistenceContractValidator {

    public List<String> validate(SafePreflightPersistenceContractProperties config) {
        List<String> errors = new ArrayList<>();

        if (config.getMode() != SafePreflightPersistenceMode.DISABLED
            && config.getMode() != SafePreflightPersistenceMode.DEV_PREVIEW_EPHEMERAL_ONLY) {
            errors.add("mode must be DISABLED or DEV_PREVIEW_EPHEMERAL_ONLY");
        }

        if (config.getAccessScope() != SafePreflightPersistenceAccessScope.DEV_ONLY) {
            errors.add("accessScope must be DEV_ONLY");
        }

        if (config.getRetentionDays() < 1 || config.getRetentionDays() > 7) {
            errors.add("retentionDays must be 1-7");
        }

        if (!config.isFailOpen()) {
            errors.add("failOpen must be true");
        }

        if (config.isPublicResponseEnabled()) {
            errors.add("publicResponseEnabled must be false");
        }

        if (config.isAllowRawMetadata()) {
            errors.add("allowRawMetadata must be false");
        }

        if (config.isAllowLocalPath()) {
            errors.add("allowLocalPath must be false");
        }

        if (config.isAllowStorageInternals()) {
            errors.add("allowStorageInternals must be false");
        }

        if (config.isAllowSignedUrl()) {
            errors.add("allowSignedUrl must be false");
        }

        if (config.isAllowCredentials()) {
            errors.add("allowCredentials must be false");
        }

        return errors;
    }

    public boolean isValid(SafePreflightPersistenceContractProperties config) {
        return validate(config).isEmpty();
    }
}
