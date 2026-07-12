package com.example.platform.ingest.preflight.policy.diagnostics;

public record IngestPreflightPolicyDecisionSemanticsDiagnostics(
    String accept,
    String acceptWithWarnings,
    String rejectCandidate,
    String reject,
    String errorFailOpen
) {}
