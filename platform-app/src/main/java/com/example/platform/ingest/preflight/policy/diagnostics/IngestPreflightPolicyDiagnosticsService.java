package com.example.platform.ingest.preflight.policy.diagnostics;

import com.example.platform.ingest.preflight.policy.config.IngestPreflightPolicyConfigValidator;
import com.example.platform.ingest.preflight.policy.config.IngestPreflightPolicyProperties;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Internal read-only diagnostics for report-only ingest preflight policy evaluator.
 * No persistence. No upload decisions. No raw metadata exposure.
 */
@Service
public class IngestPreflightPolicyDiagnosticsService {

    private final IngestPreflightPolicyProperties properties;
    private final IngestPreflightPolicyConfigValidator validator;

    public IngestPreflightPolicyDiagnosticsService(IngestPreflightPolicyProperties properties,
                                                    IngestPreflightPolicyConfigValidator validator) {
        this.properties = properties;
        this.validator = validator;
    }

    public IngestPreflightPolicyDiagnosticsResponse getDiagnostics() {
        var validationErrors = validator.validate(properties);

        return new IngestPreflightPolicyDiagnosticsResponse(
            "READ_ONLY",
            true,  // reportOnlyEvaluatorImplemented
            true,  // hookIntegrationImplemented
            true,  // configBindingImplemented
            true,  // reportOnlyMode
            true,  // failOpenRequired
            false, // enforceModeEnabled
            false, // uploadRejectionImplemented
            false, // runtimePolicyGateImplemented
            false, // policyEvaluationPersistenceImplemented
            false, // preflightReportPersistenceImplemented
            false, // publicUploadResponseChanged
            false, // rawMetadataExposureAllowed
            false, // ocrEnabled
            false, // fullTextExtractionEnabled
            getConfigDiagnostics(validationErrors),
            getDecisionSemantics(),
            Instant.now()
        );
    }

    public IngestPreflightPolicyConfigDiagnostics getConfigDiagnostics() {
        return getConfigDiagnostics(validator.validate(properties));
    }

    public IngestPreflightPolicyDecisionSemanticsDiagnostics getDecisionSemantics() {
        return new IngestPreflightPolicyDecisionSemanticsDiagnostics(
            "non-blocking",
            "non-blocking",
            "diagnostic-only, non-blocking",
            "not-emitted-by-report-only-evaluator",
            "non-blocking, upload continues"
        );
    }

    private IngestPreflightPolicyConfigDiagnostics getConfigDiagnostics(java.util.List<String> validationErrors) {
        return new IngestPreflightPolicyConfigDiagnostics(
            properties.isEnabled(),
            properties.getMode(),
            properties.getProfile(),
            properties.isFailOpen(),
            properties.getMaxFindings(),
            properties.isLogResult(),
            properties.isIncludeWarningFindings(),
            properties.isIncludeMediaTechnicalFindings(),
            properties.isIncludeRejectCandidates(),
            validationErrors.isEmpty() ? "VALID" : "INVALID",
            (int) validationErrors.stream().filter(e -> e.contains("must")).count(),
            (int) validationErrors.stream().filter(e -> !e.contains("must")).count()
        );
    }
}
