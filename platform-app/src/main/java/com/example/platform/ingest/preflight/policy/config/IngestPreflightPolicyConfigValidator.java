package com.example.platform.ingest.preflight.policy.config;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Validates ingest preflight policy configuration.
 * No remote calls. No enforcement activation.
 */
@Component
public class IngestPreflightPolicyConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(IngestPreflightPolicyConfigValidator.class);

    public List<String> validate(IngestPreflightPolicyProperties config) {
        List<String> errors = new ArrayList<>();

        // Mode must be report_only
        if (!"report_only".equals(config.getMode())) {
            errors.add("mode must be 'report_only', got: " + config.getMode());
        }

        // Fail-open must be true
        if (!config.isFailOpen()) {
            errors.add("fail-open must be true; false is not allowed in current runtime");
        }

        // Max findings must be bounded
        if (config.getMaxFindings() < 1 || config.getMaxFindings() > 1000) {
            errors.add("max-findings must be between 1 and 1000");
        }

        // Profile must be safe
        String profile = config.getProfile();
        if (profile != null && !profile.equals("preview_safe") && !profile.equals("permissive")) {
            errors.add("profile must be 'preview_safe' or 'permissive', got: " + profile);
        }

        return errors;
    }

    public boolean isValid(IngestPreflightPolicyProperties config) {
        return validate(config).isEmpty();
    }
}
