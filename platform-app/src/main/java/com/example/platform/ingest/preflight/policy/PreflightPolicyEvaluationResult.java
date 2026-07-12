package com.example.platform.ingest.preflight.policy;

import com.example.platform.ingest.contract.*;
import java.time.Instant;
import java.util.List;

public record PreflightPolicyEvaluationResult(
    PreflightPolicyMode mode,
    PreflightPolicyProfile profile,
    PreflightPolicyDecision decision,
    boolean reportOnly,
    boolean failOpen,
    List<PreflightPolicyFinding> findings,
    List<IngestWarningCode> warningCodes,
    List<IngestRejectionReasonCode> rejectionCandidateCodes,
    List<UserSafePolicyMessage> userSafeMessages,
    Instant evaluatedAt,
    String evaluatorVersion,
    Long durationMs
) {
    public boolean isAccepted() {
        return decision == PreflightPolicyDecision.ACCEPT || decision == PreflightPolicyDecision.ACCEPT_WITH_WARNINGS;
    }

    public boolean hasWarnings() {
        return !warningCodes.isEmpty() || findings.stream().anyMatch(f -> f.severity() == PreflightPolicySeverity.WARNING);
    }

    public boolean hasRejectCandidates() {
        return decision == PreflightPolicyDecision.REJECT_CANDIDATE || decision == PreflightPolicyDecision.REJECT;
    }

    public boolean isRejecting() {
        return decision == PreflightPolicyDecision.REJECT;
    }

    public static PreflightPolicyEvaluationResult acceptReportOnly(PreflightPolicyProfile profile) {
        return new PreflightPolicyEvaluationResult(
            PreflightPolicyMode.REPORT_ONLY, profile, PreflightPolicyDecision.ACCEPT,
            true, false, List.of(), List.of(), List.of(), List.of(),
            Instant.now(), "v1", null
        );
    }

    public static PreflightPolicyEvaluationResult acceptWithWarningsReportOnly(PreflightPolicyProfile profile, List<PreflightPolicyFinding> findings) {
        return new PreflightPolicyEvaluationResult(
            PreflightPolicyMode.REPORT_ONLY, profile, PreflightPolicyDecision.ACCEPT_WITH_WARNINGS,
            true, false, findings, List.of(), List.of(), List.of(),
            Instant.now(), "v1", null
        );
    }

    public static PreflightPolicyEvaluationResult rejectCandidateReportOnly(PreflightPolicyProfile profile, List<PreflightPolicyFinding> findings) {
        return new PreflightPolicyEvaluationResult(
            PreflightPolicyMode.REPORT_ONLY, profile, PreflightPolicyDecision.REJECT_CANDIDATE,
            true, false, findings, List.of(), List.of(), List.of(),
            Instant.now(), "v1", null
        );
    }

    public static PreflightPolicyEvaluationResult errorFailOpen(PreflightPolicyProfile profile, String message) {
        return new PreflightPolicyEvaluationResult(
            PreflightPolicyMode.REPORT_ONLY, profile, PreflightPolicyDecision.ERROR_FAIL_OPEN,
            true, true, List.of(), List.of(), List.of(),
            List.of(new UserSafePolicyMessage("SYSTEM_ERROR", message, PreflightPolicySeverity.ERROR)),
            Instant.now(), "v1", null
        );
    }
}
