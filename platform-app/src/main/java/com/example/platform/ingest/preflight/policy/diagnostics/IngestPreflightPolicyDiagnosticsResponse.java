package com.example.platform.ingest.preflight.policy.diagnostics;

import java.time.Instant;

public record IngestPreflightPolicyDiagnosticsResponse(
    String diagnosticsMode,
    boolean reportOnlyEvaluatorImplemented,
    boolean hookIntegrationImplemented,
    boolean configBindingImplemented,
    boolean reportOnlyMode,
    boolean failOpenRequired,
    boolean enforceModeEnabled,
    boolean uploadRejectionImplemented,
    boolean runtimePolicyGateImplemented,
    boolean policyEvaluationPersistenceImplemented,
    boolean preflightReportPersistenceImplemented,
    boolean publicUploadResponseChanged,
    boolean rawMetadataExposureAllowed,
    boolean ocrEnabled,
    boolean fullTextExtractionEnabled,
    IngestPreflightPolicyConfigDiagnostics config,
    IngestPreflightPolicyDecisionSemanticsDiagnostics decisionSemantics,
    Instant generatedAt
) {}
