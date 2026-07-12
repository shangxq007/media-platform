package com.example.platform.ingest.preflight.policy;

import com.example.platform.ingest.contract.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Report-only ingest preflight policy evaluator.
 * Never rejects uploads. Fails open. No persistence.
 */
@Component
public class ReportOnlyPreflightPolicyEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ReportOnlyPreflightPolicyEvaluator.class);

    private static final long MAX_DURATION_MS = 4 * 60 * 60 * 1000; // 4 hours
    private static final int MAX_WIDTH = 3840;
    private static final int MAX_HEIGHT = 2160;

    public PreflightPolicyEvaluationResult evaluateReportOnly(PreflightPolicyEvaluationInput input) {
        try {
            List<PreflightPolicyFinding> findings = new ArrayList<>();

            // Map warning codes to findings
            for (var warning : input.warningCodes()) {
                findings.add(mapWarningToFinding(warning));
            }

            // Map rejection candidates to findings
            for (var rejection : input.rejectionCandidateCodes()) {
                findings.add(mapRejectionToFinding(rejection));
            }

            // Apply media technical rules if media summary present
            if (input.mediaSummary() != null) {
                findings.addAll(evaluateMediaRules(input.mediaSummary()));
            }

            // Determine decision
            PreflightPolicyDecision decision = determineDecision(findings);

            return new PreflightPolicyEvaluationResult(
                PreflightPolicyMode.REPORT_ONLY,
                input.profile() != null ? input.profile() : PreflightPolicyProfile.PREVIEW_SAFE,
                decision,
                true, false,
                findings,
                input.warningCodes(),
                input.rejectionCandidateCodes(),
                buildUserSafeMessages(findings),
                Instant.now(), "v1", null
            );
        } catch (Exception e) {
            log.warn("Policy evaluator failed (fail-open): {}", e.getMessage());
            return PreflightPolicyEvaluationResult.errorFailOpen(
                input.profile() != null ? input.profile() : PreflightPolicyProfile.PREVIEW_SAFE,
                "Policy evaluation failed safely"
            );
        }
    }

    public PreflightPolicyEvaluationResult evaluateReportOnly(SafePreflightReportSummary safeReport) {
        return evaluateReportOnly(PreflightPolicyEvaluationInput.fromSafeReport(safeReport));
    }

    private PreflightPolicyFinding mapWarningToFinding(IngestWarningCode warning) {
        return switch (warning) {
            case DECLARED_CONTENT_TYPE_MISMATCH -> finding("CONTENT_TYPE_MISMATCH", PreflightPolicyRuleId.CONTENT_TYPE_MISMATCH, PreflightPolicySeverity.WARNING, warning);
            case EXTENSION_CONTENT_TYPE_MISMATCH -> finding("CONTENT_TYPE_MISMATCH", PreflightPolicyRuleId.CONTENT_TYPE_MISMATCH, PreflightPolicySeverity.WARNING, warning);
            case UNKNOWN_CONTENT_TYPE -> finding("UNKNOWN_CONTENT_TYPE", PreflightPolicyRuleId.CONTENT_TYPE_MISMATCH, PreflightPolicySeverity.WARNING, warning);
            case UNSUPPORTED_CONTENT_TYPE -> finding("UNSUPPORTED_CONTENT_TYPE", PreflightPolicyRuleId.UNSUPPORTED_CONTENT_TYPE, PreflightPolicySeverity.ERROR, warning);
            case EMPTY_FILE -> finding("EMPTY_FILE", PreflightPolicyRuleId.CONTENT_TYPE_MISMATCH, PreflightPolicySeverity.ERROR, warning);
            case SUSPICIOUS_EXTENSION -> finding("SUSPICIOUS_EXTENSION", PreflightPolicyRuleId.CONTENT_TYPE_MISMATCH, PreflightPolicySeverity.WARNING, warning);
            case MEDIA_TECHNICAL_METADATA_MISSING -> finding("MEDIA_PROBE_FAILED", PreflightPolicyRuleId.MEDIA_PROBE_FAILED, PreflightPolicySeverity.WARNING, warning);
            case DETECTION_LIMIT_REACHED -> finding("DETECTOR_TIMEOUT", PreflightPolicyRuleId.DETECTOR_TIMEOUT, PreflightPolicySeverity.WARNING, warning);
            default -> finding("GENERIC_WARNING", null, PreflightPolicySeverity.WARNING, warning);
        };
    }

    private PreflightPolicyFinding mapRejectionToFinding(IngestRejectionReasonCode rejection) {
        return switch (rejection) {
            case FILE_EMPTY -> finding("EMPTY_FILE", PreflightPolicyRuleId.CONTENT_TYPE_MISMATCH, PreflightPolicySeverity.ERROR, null);
            case FILE_TOO_LARGE -> finding("FILE_TOO_LARGE", null, PreflightPolicySeverity.ERROR, null);
            case CONTENT_TYPE_UNSUPPORTED -> finding("UNSUPPORTED_CONTENT_TYPE", PreflightPolicyRuleId.UNSUPPORTED_CONTENT_TYPE, PreflightPolicySeverity.ERROR, null);
            case MEDIA_DURATION_TOO_LONG -> finding("DURATION_TOO_LONG", PreflightPolicyRuleId.DURATION_TOO_LONG, PreflightPolicySeverity.ERROR, null);
            case MEDIA_CODEC_UNSUPPORTED -> finding("UNSUPPORTED_CODEC", PreflightPolicyRuleId.UNSUPPORTED_VIDEO_CODEC, PreflightPolicySeverity.ERROR, null);
            case MEDIA_RESOLUTION_UNSUPPORTED -> finding("RESOLUTION_TOO_LARGE", PreflightPolicyRuleId.RESOLUTION_TOO_LARGE, PreflightPolicySeverity.ERROR, null);
            case MEDIA_PROBE_FAILED -> finding("MEDIA_PROBE_FAILED", PreflightPolicyRuleId.MEDIA_PROBE_FAILED, PreflightPolicySeverity.WARNING, null);
            default -> finding("GENERIC_REJECTION_CANDIDATE", null, PreflightPolicySeverity.ERROR, null);
        };
    }

    private List<PreflightPolicyFinding> evaluateMediaRules(SafeMediaSummary media) {
        List<PreflightPolicyFinding> findings = new ArrayList<>();

        if (media.durationMs() != null && media.durationMs() > MAX_DURATION_MS) {
            findings.add(finding("DURATION_TOO_LONG", PreflightPolicyRuleId.DURATION_TOO_LONG, PreflightPolicySeverity.ERROR, null));
        }

        if (media.width() != null && media.width() > MAX_WIDTH) {
            findings.add(finding("RESOLUTION_TOO_LARGE", PreflightPolicyRuleId.RESOLUTION_TOO_LARGE, PreflightPolicySeverity.ERROR, null));
        }

        if (media.height() != null && media.height() > MAX_HEIGHT) {
            findings.add(finding("RESOLUTION_TOO_LARGE", PreflightPolicyRuleId.RESOLUTION_TOO_LARGE, PreflightPolicySeverity.ERROR, null));
        }

        if (!media.hasVideo() && !media.hasAudio()) {
            findings.add(finding("NO_MEDIA_STREAM", PreflightPolicyRuleId.NO_VIDEO_STREAM, PreflightPolicySeverity.ERROR, null));
        }

        if (media.probeStatus() == MediaProbeStatus.FAILED) {
            findings.add(finding("MEDIA_PROBE_FAILED", PreflightPolicyRuleId.MEDIA_PROBE_FAILED, PreflightPolicySeverity.WARNING, null));
        }

        return findings;
    }

    private PreflightPolicyDecision determineDecision(List<PreflightPolicyFinding> findings) {
        boolean hasBlocker = findings.stream().anyMatch(f -> f.severity() == PreflightPolicySeverity.BLOCKER);
        boolean hasError = findings.stream().anyMatch(f -> f.severity() == PreflightPolicySeverity.ERROR);
        boolean hasWarning = findings.stream().anyMatch(f -> f.severity() == PreflightPolicySeverity.WARNING);

        if (hasBlocker || hasError) return PreflightPolicyDecision.REJECT_CANDIDATE;
        if (hasWarning) return PreflightPolicyDecision.ACCEPT_WITH_WARNINGS;
        return PreflightPolicyDecision.ACCEPT;
    }

    private List<UserSafePolicyMessage> buildUserSafeMessages(List<PreflightPolicyFinding> findings) {
        List<UserSafePolicyMessage> messages = new ArrayList<>();
        for (var finding : findings) {
            String message = switch (finding.code().value()) {
                case "CONTENT_TYPE_MISMATCH" -> "The uploaded file type does not match its detected content.";
                case "UNSUPPORTED_CONTENT_TYPE" -> "This file type is not supported.";
                case "UNSUPPORTED_CODEC" -> "The media codec may not be supported.";
                case "DURATION_TOO_LONG" -> "The media duration may exceed processing limits.";
                case "RESOLUTION_TOO_LARGE" -> "The video resolution may exceed processing limits.";
                case "MEDIA_PROBE_FAILED" -> "The system could not fully inspect the media file.";
                case "NO_MEDIA_STREAM" -> "No video or audio streams detected.";
                default -> "The file may not be compatible with the current processing pipeline.";
            };
            messages.add(new UserSafePolicyMessage(finding.code().value(), message, finding.severity()));
        }
        return messages;
    }

    private PreflightPolicyFinding finding(String code, PreflightPolicyRuleId ruleId, PreflightPolicySeverity severity, IngestWarningCode warning) {
        return new PreflightPolicyFinding(
            new PreflightPolicyFindingCode(code), ruleId, severity, warning, null, null
        );
    }
}
