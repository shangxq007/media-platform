package com.example.platform.ingest.preflight.policy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class PreflightPolicyEvaluationResultTest {

    @Test
    void testEnumCompleteness() {
        assertEquals(3, PreflightPolicyMode.values().length);
        assertEquals(6, PreflightPolicyDecision.values().length);
        assertEquals(4, PreflightPolicySeverity.values().length);
        assertEquals(4, PreflightPolicyProfile.values().length);
    }

    @Test
    void testAcceptReportOnly() {
        var result = PreflightPolicyEvaluationResult.acceptReportOnly(PreflightPolicyProfile.PREVIEW_SAFE);
        assertEquals(PreflightPolicyDecision.ACCEPT, result.decision());
        assertTrue(result.isAccepted());
        assertFalse(result.isRejecting());
        assertTrue(result.reportOnly());
    }

    @Test
    void testRejectCandidateReportOnly() {
        var finding = new PreflightPolicyFinding(
            new PreflightPolicyFindingCode("UNSUPPORTED_CODEC"),
            PreflightPolicySeverity.BLOCKER
        );
        var result = PreflightPolicyEvaluationResult.rejectCandidateReportOnly(
            PreflightPolicyProfile.PREVIEW_SAFE, List.of(finding));

        assertEquals(PreflightPolicyDecision.REJECT_CANDIDATE, result.decision());
        assertTrue(result.hasRejectCandidates());
        assertTrue(result.reportOnly());
        assertFalse(result.isRejecting());
    }

    @Test
    void testErrorFailOpen() {
        var result = PreflightPolicyEvaluationResult.errorFailOpen(
            PreflightPolicyProfile.PREVIEW_SAFE, "Test error");

        assertEquals(PreflightPolicyDecision.ERROR_FAIL_OPEN, result.decision());
        assertTrue(result.failOpen());
        assertFalse(result.userSafeMessages().isEmpty());
    }

    @Test
    void testRuleIdValidation() {
        assertDoesNotThrow(() -> new PreflightPolicyRuleId("content-type-mismatch"));
        assertThrows(IllegalArgumentException.class, () -> new PreflightPolicyRuleId(""));
        assertThrows(IllegalArgumentException.class, () -> new PreflightPolicyRuleId("UPPER"));
    }

    @Test
    void testNoSensitiveFields() {
        var result = PreflightPolicyEvaluationResult.acceptReportOnly(PreflightPolicyProfile.PREVIEW_SAFE);
        assertNull(result.durationMs());
        assertNotNull(result.evaluatedAt());
    }
}
