package com.example.platform.ingest.preflight.policy.diagnostics;

public record IngestPreflightPolicyConfigDiagnostics(
    boolean enabled,
    String mode,
    String profile,
    boolean failOpen,
    int maxFindings,
    boolean logResult,
    boolean includeWarningFindings,
    boolean includeMediaTechnicalFindings,
    boolean includeRejectCandidates,
    String validationStatus,
    int validationErrorCount,
    int validationWarningCount
) {}
