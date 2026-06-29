package com.example.platform.render.domain.artifact;

import com.example.platform.render.domain.timeline.diff.ArtifactDAGImpact;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Artifact DAG mode vocabulary and boundary policy.
 * Proves: default mode, disabled behavior, dry-run non-blocking, required not default.
 */
class ArtifactDagModeTest {

    // --- ArtifactDagMode ---

    @Test
    @DisplayName("Default mode is DISABLED")
    void defaultModeIsDisabled() {
        assertEquals(ArtifactDagMode.DISABLED, ArtifactDagMode.defaultMode());
    }

    @Test
    @DisplayName("DISABLED does not allow analysis")
    void disabledDoesNotAllowAnalysis() {
        assertFalse(ArtifactDagMode.DISABLED.allowsAnalysis());
    }

    @Test
    @DisplayName("DRY_RUN allows analysis")
    void dryRunAllowsAnalysis() {
        assertTrue(ArtifactDagMode.DRY_RUN.allowsAnalysis());
    }

    @Test
    @DisplayName("EXPERIMENTAL allows analysis")
    void experimentalAllowsAnalysis() {
        assertTrue(ArtifactDagMode.EXPERIMENTAL.allowsAnalysis());
    }

    @Test
    @DisplayName("REQUIRED allows analysis")
    void requiredAllowsAnalysis() {
        assertTrue(ArtifactDagMode.REQUIRED.allowsAnalysis());
    }

    @Test
    @DisplayName("DISABLED cannot affect render decisions")
    void disabledCannotAffectRenderDecisions() {
        assertFalse(ArtifactDagMode.DISABLED.canAffectRenderDecisions());
    }

    @Test
    @DisplayName("DRY_RUN cannot affect render decisions")
    void dryRunCannotAffectRenderDecisions() {
        assertFalse(ArtifactDagMode.DRY_RUN.canAffectRenderDecisions());
    }

    @Test
    @DisplayName("EXPERIMENTAL cannot affect render decisions")
    void experimentalCannotAffectRenderDecisions() {
        assertFalse(ArtifactDagMode.EXPERIMENTAL.canAffectRenderDecisions());
    }

    @Test
    @DisplayName("REQUIRED can affect render decisions")
    void requiredCanAffectRenderDecisions() {
        assertTrue(ArtifactDagMode.REQUIRED.canAffectRenderDecisions());
    }

    @Test
    @DisplayName("REQUIRED is not default")
    void requiredIsNotDefault() {
        assertNotEquals(ArtifactDagMode.REQUIRED, ArtifactDagMode.defaultMode());
    }

    @Test
    @DisplayName("EXPERIMENTAL is not default")
    void experimentalIsNotDefault() {
        assertNotEquals(ArtifactDagMode.EXPERIMENTAL, ArtifactDagMode.defaultMode());
    }

    @Test
    @DisplayName("DRY_RUN is not default")
    void dryRunIsNotDefault() {
        assertNotEquals(ArtifactDagMode.DRY_RUN, ArtifactDagMode.defaultMode());
    }

    // --- ArtifactDagEvaluationStatus ---

    @Test
    @DisplayName("SKIPPED_DISABLED was not computed")
    void skippedDisabledWasNotComputed() {
        assertFalse(ArtifactDagEvaluationStatus.SKIPPED_DISABLED.wasComputed());
    }

    @Test
    @DisplayName("DRY_RUN_COMPLETED was computed")
    void dryRunCompletedWasComputed() {
        assertTrue(ArtifactDagEvaluationStatus.DRY_RUN_COMPLETED.wasComputed());
    }

    @Test
    @DisplayName("EXPERIMENTAL_COMPLETED was computed")
    void experimentalCompletedWasComputed() {
        assertTrue(ArtifactDagEvaluationStatus.EXPERIMENTAL_COMPLETED.wasComputed());
    }

    @Test
    @DisplayName("NOT_COMPUTED was not computed")
    void notComputedWasNotComputed() {
        assertFalse(ArtifactDagEvaluationStatus.NOT_COMPUTED.wasComputed());
    }

    @Test
    @DisplayName("FAILED_NON_BLOCKING is non-blocking failure")
    void failedNonBlockingIsNonBlocking() {
        assertTrue(ArtifactDagEvaluationStatus.FAILED_NON_BLOCKING.isNonBlockingFailure());
    }

    @Test
    @DisplayName("SKIPPED_DISABLED is not non-blocking failure")
    void skippedDisabledIsNotNonBlocking() {
        assertFalse(ArtifactDagEvaluationStatus.SKIPPED_DISABLED.isNonBlockingFailure());
    }

    // --- ArtifactDagEvaluationResult ---

    @Test
    @DisplayName("Disabled result has SKIPPED_DISABLED status")
    void disabledResultHasSkippedStatus() {
        ArtifactDagEvaluationResult result = ArtifactDagEvaluationResult.disabled();
        assertEquals(ArtifactDagMode.DISABLED, result.mode());
        assertEquals(ArtifactDagEvaluationStatus.SKIPPED_DISABLED, result.status());
        assertNull(result.artifactGraph());
        assertNull(result.impact());
        assertTrue(result.wasSkipped());
        assertFalse(result.wasComputed());
        assertFalse(result.canAffectRenderDecisions());
    }

    @Test
    @DisplayName("Not-computed result has NOT_COMPUTED status")
    void notComputedResultHasNotComputedStatus() {
        ArtifactDagEvaluationResult result = ArtifactDagEvaluationResult.notComputed(ArtifactDagMode.DRY_RUN);
        assertEquals(ArtifactDagMode.DRY_RUN, result.mode());
        assertEquals(ArtifactDagEvaluationStatus.NOT_COMPUTED, result.status());
        assertFalse(result.wasComputed());
    }

    @Test
    @DisplayName("Dry-run completed result has DRY_RUN_COMPLETED status")
    void dryRunCompletedResultHasCorrectStatus() {
        ArtifactDAGImpact impact = new ArtifactDAGImpact(
                "imp-1", List.of("node-1"), List.of("node-2"), Map.of());
        ArtifactDagEvaluationResult result = ArtifactDagEvaluationResult.dryRunCompleted(null, impact);
        assertEquals(ArtifactDagMode.DRY_RUN, result.mode());
        assertEquals(ArtifactDagEvaluationStatus.DRY_RUN_COMPLETED, result.status());
        assertTrue(result.wasComputed());
        assertFalse(result.canAffectRenderDecisions());
    }

    @Test
    @DisplayName("Failed non-blocking result has FAILED_NON_BLOCKING status")
    void failedNonBlockingResultHasCorrectStatus() {
        ArtifactDagEvaluationResult result = ArtifactDagEvaluationResult.failedNonBlocking(
                List.of("compilation failed"));
        assertEquals(ArtifactDagEvaluationStatus.FAILED_NON_BLOCKING, result.status());
        assertTrue(result.isNonBlockingFailure());
        assertEquals(1, result.issues().size());
        assertFalse(result.canAffectRenderDecisions());
    }

    @Test
    @DisplayName("Failed non-blocking result accepts null issues")
    void failedNonBlockingAcceptsNullIssues() {
        ArtifactDagEvaluationResult result = ArtifactDagEvaluationResult.failedNonBlocking(null);
        assertTrue(result.issues().isEmpty());
    }

    // --- ArtifactDagBoundaryPolicy ---

    @Test
    @DisplayName("Default policy is DISABLED with no influence")
    void defaultPolicyIsDisabledNoInfluence() {
        ArtifactDagBoundaryPolicy policy = ArtifactDagBoundaryPolicy.defaultPolicy();
        assertEquals(ArtifactDagMode.DISABLED, policy.mode());
        assertFalse(policy.allowProviderBindingInfluence());
        assertFalse(policy.allowRenderExecutionInfluence());
        assertTrue(policy.shouldSkip());
        assertFalse(policy.allowsAnalysis());
        assertFalse(policy.canAffectProviderBinding());
        assertFalse(policy.canAffectRenderExecution());
    }

    @Test
    @DisplayName("DISABLED mode policy should skip")
    void disabledModePolicyShouldSkip() {
        ArtifactDagBoundaryPolicy policy = ArtifactDagBoundaryPolicy.forMode(ArtifactDagMode.DISABLED);
        assertTrue(policy.shouldSkip());
        assertFalse(policy.allowsAnalysis());
        assertFalse(policy.canAffectProviderBinding());
        assertFalse(policy.canAffectRenderExecution());
    }

    @Test
    @DisplayName("DRY_RUN mode policy allows analysis but not decisions")
    void dryRunModePolicyAllowsAnalysisNotDecisions() {
        ArtifactDagBoundaryPolicy policy = ArtifactDagBoundaryPolicy.forMode(ArtifactDagMode.DRY_RUN);
        assertFalse(policy.shouldSkip());
        assertTrue(policy.allowsAnalysis());
        assertFalse(policy.canAffectProviderBinding());
        assertFalse(policy.canAffectRenderExecution());
    }

    @Test
    @DisplayName("EXPERIMENTAL mode policy allows analysis but not decisions")
    void experimentalModePolicyAllowsAnalysisNotDecisions() {
        ArtifactDagBoundaryPolicy policy = ArtifactDagBoundaryPolicy.forMode(ArtifactDagMode.EXPERIMENTAL);
        assertFalse(policy.shouldSkip());
        assertTrue(policy.allowsAnalysis());
        assertFalse(policy.canAffectProviderBinding());
        assertFalse(policy.canAffectRenderExecution());
    }

    @Test
    @DisplayName("REQUIRED mode policy allows analysis and decisions")
    void requiredModePolicyAllowsAnalysisAndDecisions() {
        ArtifactDagBoundaryPolicy policy = ArtifactDagBoundaryPolicy.forMode(ArtifactDagMode.REQUIRED);
        assertFalse(policy.shouldSkip());
        assertTrue(policy.allowsAnalysis());
        assertTrue(policy.canAffectProviderBinding());
        assertTrue(policy.canAffectRenderExecution());
    }

    @Test
    @DisplayName("REQUIRED mode policy is not default")
    void requiredModePolicyIsNotDefault() {
        ArtifactDagBoundaryPolicy policy = ArtifactDagBoundaryPolicy.forMode(ArtifactDagMode.REQUIRED);
        assertNotEquals(ArtifactDagBoundaryPolicy.defaultPolicy(), policy);
    }

    // --- Safety: no provider/storage fields ---

    @Test
    @DisplayName("Evaluation result has no provider/storage fields")
    void evaluationResultNoProviderStorageFields() {
        ArtifactDagEvaluationResult result = ArtifactDagEvaluationResult.disabled();
        String repr = result.toString();
        assertFalse(repr.contains("providerName"));
        assertFalse(repr.contains("bucket"));
        assertFalse(repr.contains("signedUrl"));
        assertFalse(repr.contains("objectKey"));
    }

    @Test
    @DisplayName("Boundary policy has no provider/storage fields")
    void boundaryPolicyNoProviderStorageFields() {
        ArtifactDagBoundaryPolicy policy = ArtifactDagBoundaryPolicy.defaultPolicy();
        String repr = policy.toString();
        assertFalse(repr.contains("providerName"));
        assertFalse(repr.contains("bucket"));
        assertFalse(repr.contains("signedUrl"));
    }
}
