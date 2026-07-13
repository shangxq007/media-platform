package com.example.platform.ingest.preflight.persistence.diagnostics;

import com.example.platform.ingest.preflight.persistence.contract.SafePreflightPersistenceContractProperties;
import com.example.platform.ingest.preflight.persistence.contract.SafePreflightPersistenceContractValidator;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class SafePreflightPersistenceDiagnosticsService {

    private final SafePreflightPersistenceContractProperties properties;
    private final SafePreflightPersistenceContractValidator validator;

    public SafePreflightPersistenceDiagnosticsService(SafePreflightPersistenceContractProperties properties,
                                                       SafePreflightPersistenceContractValidator validator) {
        this.properties = properties;
        this.validator = validator;
    }

    public SafePreflightPersistenceDiagnosticsResponse getDiagnostics() {
        var errors = validator.validate(properties);

        return new SafePreflightPersistenceDiagnosticsResponse(
            "READ_ONLY",
            properties.getMode(),
            properties.getAccessScope(),
            properties.getRetentionDays(),
            properties.isFailOpen(),
            properties.isPublicResponseEnabled(),
            properties.isAllowRawMetadata(),
            properties.isAllowLocalPath(),
            properties.isAllowStorageInternals(),
            properties.isAllowSignedUrl(),
            properties.isAllowCredentials(),
            false,
            false,
            errors.isEmpty() ? "VALID" : "INVALID",
            Instant.now()
        );
    }
}
