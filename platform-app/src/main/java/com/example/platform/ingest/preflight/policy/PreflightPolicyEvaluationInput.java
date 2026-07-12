package com.example.platform.ingest.preflight.policy;

import com.example.platform.ingest.contract.*;
import java.util.List;

public record PreflightPolicyEvaluationInput(
    PreflightPolicyMode mode,
    PreflightPolicyProfile profile,
    SafePreflightReportSummary safeReportSummary,
    List<IngestWarningCode> warningCodes,
    List<IngestRejectionReasonCode> rejectionCandidateCodes,
    MediaCategory mediaCategory,
    ContentTypeSummary contentTypeSummary,
    SafeMediaSummary mediaSummary,
    boolean reportOnly,
    boolean failOpen
) {
    public static PreflightPolicyEvaluationInput fromSafeReport(SafePreflightReportSummary report) {
        return new PreflightPolicyEvaluationInput(
            PreflightPolicyMode.REPORT_ONLY,
            PreflightPolicyProfile.PREVIEW_SAFE,
            report,
            report.warningCodes(),
            report.rejectionCandidateCodes(),
            report.mediaCategory(),
            report.contentTypeSummary(),
            report.mediaSummary(),
            true,
            true
        );
    }
}
