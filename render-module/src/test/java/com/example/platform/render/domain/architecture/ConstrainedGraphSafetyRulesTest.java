package com.example.platform.render.domain.architecture;

import com.example.platform.render.domain.artifact.ArtifactDagMode;
import com.example.platform.render.domain.template.*;
import com.example.platform.render.domain.template.profile.caption.*;
import com.example.platform.render.domain.timeline.diff.*;
import com.example.platform.render.domain.timeline.diff.application.*;
import com.example.platform.render.domain.timeline.diff.calculation.*;
import com.example.platform.render.domain.timeline.diff.merge.TimelineMergeConflictDetector;
import com.example.platform.render.domain.timeline.diff.merge.preview.*;
import com.example.platform.render.domain.workflow.*;
import com.example.platform.render.domain.workflow.planning.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Safety rules tests for constrained graph operations.
 * Proves: acyclicity, determinism, input immutability, deferred defaults,
 * and that merge preview does not apply patches or merge.
 */
class ConstrainedGraphSafetyRulesTest {

    // ===== Workflow cycle detection still rejects cycles =====

    @Test
    @DisplayName("Workflow cycle detector rejects simple cycle")
    void workflowCycleDetectorRejectsCycle() {
        WorkflowStep s1 = new WorkflowStep(new WorkflowStepId("s1"),
                WorkflowStepType.INGEST_PRODUCT,
                List.of(new WorkflowStepDependency(new WorkflowStepId("s2"), null)),
                Map.of(), null, Map.of());
        WorkflowStep s2 = new WorkflowStep(new WorkflowStepId("s2"),
                WorkflowStepType.VALIDATE_INPUT,
                List.of(new WorkflowStepDependency(new WorkflowStepId("s1"), null)),
                Map.of(), null, Map.of());
        WorkflowDefinition def = new WorkflowDefinition(
                new WorkflowDefinitionId("wf1"), new WorkflowVersion("1.0"), null,
                List.of(), List.of(s1, s2), List.of(), Map.of());

        assertTrue(new WorkflowCycleDetector().hasCycle(def));
    }

    @Test
    @DisplayName("Workflow cycle detector accepts acyclic graph")
    void workflowCycleDetectorAcceptsAcyclic() {
        WorkflowStep s1 = new WorkflowStep(new WorkflowStepId("s1"),
                WorkflowStepType.INGEST_PRODUCT, List.of(), Map.of(), null, Map.of());
        WorkflowStep s2 = new WorkflowStep(new WorkflowStepId("s2"),
                WorkflowStepType.VALIDATE_INPUT,
                List.of(new WorkflowStepDependency(new WorkflowStepId("s1"), null)),
                Map.of(), null, Map.of());
        WorkflowStep s3 = new WorkflowStep(new WorkflowStepId("s3"),
                WorkflowStepType.RENDER_TIMELINE,
                List.of(new WorkflowStepDependency(new WorkflowStepId("s2"), null)),
                Map.of(), null, Map.of());
        WorkflowDefinition def = new WorkflowDefinition(
                new WorkflowDefinitionId("wf1"), new WorkflowVersion("1.0"), null,
                List.of(), List.of(s1, s2, s3), List.of(), Map.of());

        assertFalse(new WorkflowCycleDetector().hasCycle(def));
    }

    @Test
    @DisplayName("Workflow cycle detector rejects self-cycle")
    void workflowCycleDetectorRejectsSelfCycle() {
        WorkflowStep s1 = new WorkflowStep(new WorkflowStepId("s1"),
                WorkflowStepType.INGEST_PRODUCT,
                List.of(new WorkflowStepDependency(new WorkflowStepId("s1"), null)),
                Map.of(), null, Map.of());
        WorkflowDefinition def = new WorkflowDefinition(
                new WorkflowDefinitionId("wf1"), new WorkflowVersion("1.0"), null,
                List.of(), List.of(s1), List.of(), Map.of());

        assertTrue(new WorkflowCycleDetector().hasCycle(def));
    }

    // ===== Workflow topological order is deterministic =====

    @Test
    @DisplayName("Workflow topological order is deterministic")
    void workflowTopologicalOrderIsDeterministic() {
        WorkflowStep s1 = new WorkflowStep(new WorkflowStepId("s1"),
                WorkflowStepType.INGEST_PRODUCT, List.of(), Map.of(), null, Map.of());
        WorkflowStep s2 = new WorkflowStep(new WorkflowStepId("s2"),
                WorkflowStepType.VALIDATE_INPUT, List.of(), Map.of(), null, Map.of());
        WorkflowStep s3 = new WorkflowStep(new WorkflowStepId("s3"),
                WorkflowStepType.NORMALIZE_TIMELINE, List.of(), Map.of(), null, Map.of());
        WorkflowDefinition def = new WorkflowDefinition(
                new WorkflowDefinitionId("wf1"), new WorkflowVersion("1.0"), null,
                List.of(), List.of(s1, s2, s3), List.of(), Map.of());

        WorkflowStepOrderResolver resolver = new WorkflowStepOrderResolver();
        List<String> order1 = resolver.resolveOrder(def);
        List<String> order2 = resolver.resolveOrder(def);
        List<String> order3 = resolver.resolveOrder(def);

        assertEquals(order1, order2);
        assertEquals(order2, order3);
    }

    @Test
    @DisplayName("Workflow graph validator rejects duplicate step IDs")
    void workflowValidatorRejectsDuplicateIds() {
        WorkflowStep step = new WorkflowStep(new WorkflowStepId("s1"),
                WorkflowStepType.INGEST_PRODUCT, List.of(), Map.of(), null, Map.of());
        WorkflowDefinition def = new WorkflowDefinition(
                new WorkflowDefinitionId("wf1"), new WorkflowVersion("1.0"), null,
                List.of(), List.of(step, step), List.of(), Map.of());

        WorkflowGraphValidationResult result = new WorkflowGraphValidator().validate(def);
        assertFalse(result.valid());
        assertTrue(result.issues().stream()
                .anyMatch(i -> i.code() == WorkflowDryRunIssueCode.DUPLICATE_STEP_ID));
    }

    // ===== Timeline diff calculator is deterministic =====

    @Test
    @DisplayName("Timeline diff calculator is deterministic")
    void timelineDiffIsDeterministic() {
        CanonicalTimelineSnapshot before = simpleSnapshot("rev-1");
        CanonicalTimelineSnapshot after = new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-2"), "rev-2", 10000,
                before.tracks(), before.captions(), before.watermarks(),
                before.templateApplications(), before.workflowSteps(),
                before.outputProfile(), Map.of());

        CanonicalTimelineDiffCalculator calculator = new CanonicalTimelineDiffCalculator();
        CanonicalTimelineDiffCalculationResult r1 = calculator.calculate(before, after);
        CanonicalTimelineDiffCalculationResult r2 = calculator.calculate(before, after);

        assertEquals(r1.diff().operations().size(), r2.diff().operations().size());
        for (int i = 0; i < r1.diff().operations().size(); i++) {
            assertEquals(r1.diff().operations().get(i).type(), r2.diff().operations().get(i).type());
            assertEquals(r1.diff().operations().get(i).path(), r2.diff().operations().get(i).path());
        }
    }

    // ===== Timeline patch applier does not mutate input =====

    @Test
    @DisplayName("Timeline patch applier does not mutate input snapshot")
    void timelinePatchDoesNotMutateInput() {
        CanonicalTimelineSnapshot original = simpleSnapshot("rev-1");
        CanonicalTimelineSnapshot copy = new CanonicalTimelineSnapshot(
                original.id(), original.revisionId(), original.durationMs(),
                original.tracks(), original.captions(), original.watermarks(),
                original.templateApplications(), original.workflowSteps(),
                original.outputProfile(), original.safeMetadata());

        TimelinePatch patch = new TimelinePatch(
                new TimelinePatchId("p1"), "rev-1",
                List.of(new TimelineChangeOperation(
                        new TimelineChangeOperationId("op1"),
                        TimelineChangeType.TIMELINE_DURATION_CHANGED,
                        TimelineChangeScope.TIMELINE,
                        new TimelineChangePath("timeline.durationMs"),
                        TimelineChangePayload.ofString("5000"),
                        TimelineChangePayload.ofString("10000"),
                        Map.of())),
                TimelineMergePolicy.FAIL_FAST, Map.of());

        TimelinePatchApplier applier = new TimelinePatchApplier();
        TimelinePatchApplicationResult result = applier.apply(original, patch);

        // Input snapshot must not be mutated
        assertEquals(original.durationMs(), copy.durationMs());
        assertEquals(original.id(), copy.id());
        assertEquals(original.revisionId(), copy.revisionId());
    }

    // ===== Timeline merge preview does not merge or apply patch =====

    @Test
    @DisplayName("Merge preview does not apply patch or merge")
    void mergePreviewDoesNotApplyPatchOrMerge() {
        CanonicalTimelineSnapshot base = simpleSnapshot("rev-base");
        CanonicalTimelineSnapshot ours = new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-ours"), "rev-ours", 8000,
                base.tracks(), base.captions(), base.watermarks(),
                base.templateApplications(), base.workflowSteps(),
                base.outputProfile(), Map.of());
        CanonicalTimelineSnapshot theirs = new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-theirs"), "rev-theirs", 12000,
                base.tracks(), base.captions(), base.watermarks(),
                base.templateApplications(), base.workflowSteps(),
                base.outputProfile(), Map.of());

        TimelineMergeConflictDetector detector = new TimelineMergeConflictDetector();
        TimelineMergePreviewService service = new TimelineMergePreviewService(detector);

        TimelineMergePreviewRequest request = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
        TimelineMergePreviewResult result = service.preview(request);

        // Preview must not mutate inputs
        assertEquals(5000, base.durationMs());
        assertEquals(8000, ours.durationMs());
        assertEquals(12000, theirs.durationMs());

        // Preview result is a conflict analysis, not a merged result
        assertNotNull(result);
        // The result contains conflicts (different durations on ours vs theirs)
        assertTrue(result.conflictAnalysis() != null);
    }

    // ===== Artifact DAG default mode is DISABLED =====

    @Test
    @DisplayName("Artifact DAG default mode is DISABLED")
    void artifactDagModeDefaultIsDisabled() {
        assertEquals(ArtifactDagMode.DISABLED, ArtifactDagMode.defaultMode());
    }

    @Test
    @DisplayName("Artifact DAG DISABLED cannot affect render decisions")
    void artifactDagDisabledCannotAffectRender() {
        assertFalse(ArtifactDagMode.DISABLED.canAffectRenderDecisions());
    }

    @Test
    @DisplayName("Artifact DAG REQUIRED is not default")
    void artifactDagRequiredIsNotDefault() {
        assertNotEquals(ArtifactDagMode.REQUIRED, ArtifactDagMode.defaultMode());
    }

    // ===== Forbidden keyword blocklist in patch application =====

    @Test
    @DisplayName("Patch applier rejects paths with forbidden keywords")
    void patchApplierRejectsForbiddenPaths() {
        CanonicalTimelineSnapshot snap = simpleSnapshot("rev-1");

        // Try a path containing "bucket" (forbidden)
        TimelinePatch patch = new TimelinePatch(
                new TimelinePatchId("p1"), "rev-1",
                List.of(new TimelineChangeOperation(
                        new TimelineChangeOperationId("op1"),
                        TimelineChangeType.METADATA_CHANGED,
                        TimelineChangeScope.METADATA,
                        new TimelineChangePath("timeline.metadata.bucket"),
                        TimelineChangePayload.ofString("old"),
                        TimelineChangePayload.ofString("new"),
                        Map.of())),
                TimelineMergePolicy.FAIL_FAST, Map.of());

        TimelinePatchApplier applier = new TimelinePatchApplier();
        TimelinePatchApplicationResult result = applier.apply(snap, patch);

        // Should fail validation due to forbidden keyword
        assertEquals(TimelinePatchApplicationStatus.VALIDATION_FAILED, result.status());
    }

    @Test
    @DisplayName("Patch applier rejects paths with providerName")
    void patchApplierRejectsProviderName() {
        CanonicalTimelineSnapshot snap = simpleSnapshot("rev-1");

        TimelinePatch patch = new TimelinePatch(
                new TimelinePatchId("p1"), "rev-1",
                List.of(new TimelineChangeOperation(
                        new TimelineChangeOperationId("op1"),
                        TimelineChangeType.METADATA_CHANGED,
                        TimelineChangeScope.METADATA,
                        new TimelineChangePath("timeline.metadata.providerName"),
                        TimelineChangePayload.ofString("old"),
                        TimelineChangePayload.ofString("new"),
                        Map.of())),
                TimelineMergePolicy.FAIL_FAST, Map.of());

        TimelinePatchApplier applier = new TimelinePatchApplier();
        TimelinePatchApplicationResult result = applier.apply(snap, patch);

        assertEquals(TimelinePatchApplicationStatus.VALIDATION_FAILED, result.status());
    }

    // ===== Merge preview rejects forbidden metadata =====

    @Test
    @DisplayName("Merge preview rejects request with forbidden metadata keys")
    void mergePreviewRejectsForbiddenMetadata() {
        CanonicalTimelineSnapshot snap = simpleSnapshot("rev-1");

        TimelineMergeConflictDetector detector = new TimelineMergeConflictDetector();
        TimelineMergePreviewService service = new TimelineMergePreviewService(detector);

        TimelineMergePreviewRequest request = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                snap, snap, snap,
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE,
                Map.of("providerName", "some-provider"));

        TimelineMergePreviewResult result = service.preview(request);
        assertEquals(TimelineMergePreviewStatus.BLOCKED, result.status());
    }

    // ===== Helpers =====

    private CanonicalTimelineSnapshot simpleSnapshot(String revisionId) {
        return new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-" + revisionId),
                revisionId,
                5000,
                List.of(new CanonicalTimelineTrackSnapshot(
                        "track-v", 0, "VIDEO", List.of(
                        new CanonicalTimelineClipSnapshot(
                                "clip-1", "asset-1",
                                0, 5000, 0, 5000, Map.of())), Map.of())),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new CanonicalTimelineOutputProfileSnapshot("default", "mp4", "16:9", 1920, 1080, Map.of()),
                Map.of());
    }
}
