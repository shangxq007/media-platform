package com.example.platform.ingest.preflight.policy;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.ingest.contract.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportOnlyPreflightPolicyEvaluatorTest {

    private final ReportOnlyPreflightPolicyEvaluator evaluator = new ReportOnlyPreflightPolicyEvaluator();

    @Test
    void testAcceptCleanReport() {
        var input = new PreflightPolicyEvaluationInput(
            PreflightPolicyMode.REPORT_ONLY, PreflightPolicyProfile.PREVIEW_SAFE,
            null, List.of(), List.of(), MediaCategory.VIDEO,
            null, null, true, true
        );
        var result = evaluator.evaluateReportOnly(input);

        assertEquals(PreflightPolicyDecision.ACCEPT, result.decision());
        assertTrue(result.reportOnly());
        assertTrue(result.isAccepted());
        assertFalse(result.isRejecting());
    }

    @Test
    void testWarningMapping() {
        var input = new PreflightPolicyEvaluationInput(
            PreflightPolicyMode.REPORT_ONLY, PreflightPolicyProfile.PREVIEW_SAFE,
            null, List.of(IngestWarningCode.DECLARED_CONTENT_TYPE_MISMATCH), List.of(),
            MediaCategory.VIDEO, null, null, true, true
        );
        var result = evaluator.evaluateReportOnly(input);

        assertEquals(PreflightPolicyDecision.ACCEPT_WITH_WARNINGS, result.decision());
        assertFalse(result.findings().isEmpty());
        assertTrue(result.reportOnly());
    }

    @Test
    void testRejectCandidateMapping() {
        var input = new PreflightPolicyEvaluationInput(
            PreflightPolicyMode.REPORT_ONLY, PreflightPolicyProfile.PREVIEW_SAFE,
            null, List.of(), List.of(IngestRejectionReasonCode.CONTENT_TYPE_UNSUPPORTED),
            MediaCategory.UNKNOWN, null, null, true, true
        );
        var result = evaluator.evaluateReportOnly(input);

        assertEquals(PreflightPolicyDecision.REJECT_CANDIDATE, result.decision());
        assertTrue(result.reportOnly());
        assertFalse(result.isRejecting()); // REJECT_CANDIDATE is not REJECT
    }

    @Test
    void testMediaDurationRule() {
        var media = new SafeMediaSummary(
            5 * 60 * 60 * 1000L, "mp4", null, true, true, false,
            1, 1, 0, "h264", "aac", 1920, 1080, null, 44100, 2, 0, MediaProbeStatus.SUCCESS
        );
        var input = new PreflightPolicyEvaluationInput(
            PreflightPolicyMode.REPORT_ONLY, PreflightPolicyProfile.PREVIEW_SAFE,
            null, List.of(), List.of(), MediaCategory.VIDEO, null, media, true, true
        );
        var result = evaluator.evaluateReportOnly(input);

        assertEquals(PreflightPolicyDecision.REJECT_CANDIDATE, result.decision());
        assertTrue(result.reportOnly());
    }

    @Test
    void testNoRejectEmitted() {
        var input = new PreflightPolicyEvaluationInput(
            PreflightPolicyMode.REPORT_ONLY, PreflightPolicyProfile.PREVIEW_SAFE,
            null, List.of(IngestWarningCode.UNKNOWN_CONTENT_TYPE),
            List.of(IngestRejectionReasonCode.FILE_EMPTY),
            MediaCategory.UNKNOWN, null, null, true, true
        );
        var result = evaluator.evaluateReportOnly(input);

        assertNotEquals(PreflightPolicyDecision.REJECT, result.decision());
        assertTrue(result.reportOnly());
    }

    @Test
    void testFailOpenOnError() {
        var result = evaluator.evaluateReportOnly((PreflightPolicyEvaluationInput) null);

        assertEquals(PreflightPolicyDecision.ERROR_FAIL_OPEN, result.decision());
        assertTrue(result.failOpen());
        assertTrue(result.reportOnly());
    }
}
