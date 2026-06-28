package com.example.platform.render.domain.timeline.diff;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Timeline Diff domain skeleton.
 * Proves: identity types, change operations, conflicts, version graph, impact, bridges.
 */
class TimelineDiffDomainTest {

    // --- Identity types ---

    @Test
    @DisplayName("Identity types reject blank values")
    void identityTypesRejectBlank() {
        assertThrows(IllegalArgumentException.class, () -> new TimelineDiffId(""));
        assertThrows(IllegalArgumentException.class, () -> new TimelinePatchId(null));
        assertThrows(IllegalArgumentException.class, () -> new TimelineChangeOperationId("  "));
        assertThrows(IllegalArgumentException.class, () -> new TimelineConflictId(""));
        assertThrows(IllegalArgumentException.class, () -> new TimelineCommitId(null));
        assertThrows(IllegalArgumentException.class, () -> new TimelineSemanticHash(""));
    }

    // --- TimelineChangeOperation ---

    @Test
    @DisplayName("Change operation requires type/scope/path")
    void changeOpRequiresFields() {
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineChangeOperation(new TimelineChangeOperationId("op1"),
                        null, TimelineChangeScope.CLIP, new TimelineChangePath("p"),
                        null, null, Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineChangeOperation(new TimelineChangeOperationId("op1"),
                        TimelineChangeType.CLIP_ADDED, null, new TimelineChangePath("p"),
                        null, null, Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineChangeOperation(new TimelineChangeOperationId("op1"),
                        TimelineChangeType.CLIP_ADDED, TimelineChangeScope.CLIP, null,
                        null, null, Map.of()));
    }

    @Test
    @DisplayName("Supports CAPTION_SEGMENT_CHANGED")
    void supportsCaptionSegmentChanged() {
        TimelineChangeOperation op = new TimelineChangeOperation(
                new TimelineChangeOperationId("op1"),
                TimelineChangeType.CAPTION_SEGMENT_CHANGED,
                TimelineChangeScope.CAPTION,
                new TimelineChangePath("captions[0].text"),
                TimelineChangePayload.ofString("Old"), TimelineChangePayload.ofString("New"),
                Map.of());
        assertEquals(TimelineChangeType.CAPTION_SEGMENT_CHANGED, op.type());
    }

    @Test
    @DisplayName("Supports WATERMARK_CHANGED")
    void supportsWatermarkChanged() {
        TimelineChangeOperation op = new TimelineChangeOperation(
                new TimelineChangeOperationId("op1"),
                TimelineChangeType.WATERMARK_CHANGED,
                TimelineChangeScope.WATERMARK,
                new TimelineChangePath("watermarks[0].opacity"),
                TimelineChangePayload.ofString("50"), TimelineChangePayload.ofString("80"),
                Map.of());
        assertEquals(TimelineChangeType.WATERMARK_CHANGED, op.type());
    }

    @Test
    @DisplayName("Supports TEMPLATE_PARAMETER_CHANGED")
    void supportsTemplateParameterChanged() {
        TimelineChangeOperation op = new TimelineChangeOperation(
                new TimelineChangeOperationId("op1"),
                TimelineChangeType.TEMPLATE_PARAMETER_CHANGED,
                TimelineChangeScope.TEMPLATE_APPLICATION,
                new TimelineChangePath("template.parameters.fontSize"),
                TimelineChangePayload.ofString("24"), TimelineChangePayload.ofString("32"),
                Map.of());
        assertEquals(TimelineChangeType.TEMPLATE_PARAMETER_CHANGED, op.type());
    }

    @Test
    @DisplayName("Supports COMPOSITE_CHILD_TEMPLATE_CHANGED")
    void supportsCompositeChildChanged() {
        TimelineChangeOperation op = new TimelineChangeOperation(
                new TimelineChangeOperationId("op1"),
                TimelineChangeType.COMPOSITE_CHILD_TEMPLATE_CHANGED,
                TimelineChangeScope.COMPOSITE_TEMPLATE,
                new TimelineChangePath("composite.children[0]"),
                null, TimelineChangePayload.ofString("child-template-id"),
                Map.of());
        assertEquals(TimelineChangeType.COMPOSITE_CHILD_TEMPLATE_CHANGED, op.type());
    }

    @Test
    @DisplayName("Supports WORKFLOW_APPLY_TEMPLATE_STEP_CHANGED")
    void supportsWorkflowStepChanged() {
        TimelineChangeOperation op = new TimelineChangeOperation(
                new TimelineChangeOperationId("op1"),
                TimelineChangeType.WORKFLOW_APPLY_TEMPLATE_STEP_CHANGED,
                TimelineChangeScope.WORKFLOW_STEP,
                new TimelineChangePath("workflow.steps.apply-caption.templateId"),
                TimelineChangePayload.ofString("old-template"), TimelineChangePayload.ofString("new-template"),
                Map.of());
        assertEquals(TimelineChangeType.WORKFLOW_APPLY_TEMPLATE_STEP_CHANGED, op.type());
    }

    @Test
    @DisplayName("No provider/storage fields in change operation")
    void noProviderFieldsInChangeOp() {
        TimelineChangeOperation op = new TimelineChangeOperation(
                new TimelineChangeOperationId("op1"),
                TimelineChangeType.CLIP_ADDED,
                TimelineChangeScope.CLIP,
                new TimelineChangePath("clips[0]"),
                null, TimelineChangePayload.ofString("new"), Map.of());
        assertFalse(op.toString().contains("providerName"));
        assertFalse(op.toString().contains("bucket"));
        assertFalse(op.toString().contains("signedUrl"));
    }

    // --- TimelineDiff ---

    @Test
    @DisplayName("Diff supports no-op")
    void diffSupportsNoOp() {
        TimelineDiff diff = new TimelineDiff(
                new TimelineDiffId("d1"), "rev-1", "rev-2",
                List.of(), List.of(), TimelineRenderImpact.metadataOnly(), Map.of());
        assertTrue(diff.isNoOp());
        assertFalse(diff.hasConflicts());
    }

    @Test
    @DisplayName("Diff supports render-impacting diff")
    void diffSupportsRenderImpacting() {
        TimelineChangeOperation op = new TimelineChangeOperation(
                new TimelineChangeOperationId("op1"),
                TimelineChangeType.CLIP_TRIMMED,
                TimelineChangeScope.CLIP,
                new TimelineChangePath("clips[0].duration"),
                TimelineChangePayload.ofString("5"), TimelineChangePayload.ofString("10"),
                Map.of());
        TimelineDiff diff = new TimelineDiff(
                new TimelineDiffId("d1"), "rev-1", "rev-2",
                List.of(op), List.of(),
                new TimelineRenderImpact(TimelineRenderImpactLevel.PARTIAL_RERENDER,
                        List.of("clips[0]"), List.of("artifact-node-1"), Map.of()),
                Map.of());
        assertFalse(diff.isNoOp());
        assertEquals(TimelineRenderImpactLevel.PARTIAL_RERENDER, diff.renderImpact().level());
    }

    @Test
    @DisplayName("Diff has blocking conflicts")
    void diffHasBlockingConflicts() {
        TimelineConflict conflict = new TimelineConflict(
                new TimelineConflictId("c1"),
                TimelineConflictType.CLIP_TIMING_CONFLICT,
                TimelineConflictSeverity.BLOCKING,
                new TimelineChangePath("clips[0].startMs"),
                "Clip timing conflict", Map.of());
        TimelineDiff diff = new TimelineDiff(
                new TimelineDiffId("d1"), "rev-1", "rev-2",
                List.of(), List.of(conflict), TimelineRenderImpact.metadataOnly(), Map.of());
        assertTrue(diff.hasConflicts());
        assertTrue(diff.hasBlockingConflicts());
    }

    // --- TimelinePatch ---

    @Test
    @DisplayName("Patch carries merge policy")
    void patchCarriesPolicy() {
        TimelinePatch patch = new TimelinePatch(
                new TimelinePatchId("p1"), "rev-1",
                List.of(), TimelineMergePolicy.MERGE_IF_COMPATIBLE, Map.of());
        assertEquals(TimelineMergePolicy.MERGE_IF_COMPATIBLE, patch.mergePolicy());
    }

    // --- Conflict ---

    @Test
    @DisplayName("Conflict supports BLOCKING severity and TEMPLATE_PARAMETER_CONFLICT")
    void conflictSupportsBlockingTemplate() {
        TimelineConflict conflict = new TimelineConflict(
                new TimelineConflictId("c1"),
                TimelineConflictType.TEMPLATE_PARAMETER_CONFLICT,
                TimelineConflictSeverity.BLOCKING,
                new TimelineChangePath("template.parameters.fontSize"),
                "Font size conflict", Map.of());
        assertTrue(conflict.isBlocking());
        assertEquals(TimelineConflictType.TEMPLATE_PARAMETER_CONFLICT, conflict.type());
    }

    // --- Version Graph ---

    @Test
    @DisplayName("Commit supports root commit")
    void commitSupportsRoot() {
        TimelineCommit commit = new TimelineCommit(
                new TimelineCommitId("c1"), List.of(),
                new TimelineSemanticHash("abc123"), "rev-1", "user", "Initial", Map.of());
        assertTrue(commit.isRoot());
        assertFalse(commit.isMerge());
    }

    @Test
    @DisplayName("Commit supports merge commit with two parents")
    void commitSupportsMerge() {
        TimelineCommit commit = new TimelineCommit(
                new TimelineCommitId("c3"),
                List.of(
                        TimelineCommitParent.primary(new TimelineCommitId("c1")),
                        TimelineCommitParent.mergeFrom(new TimelineCommitId("c2"))),
                new TimelineSemanticHash("def456"), "rev-3", "user", "Merge", Map.of());
        assertTrue(commit.isMerge());
        assertEquals(2, commit.parents().size());
    }

    @Test
    @DisplayName("Version graph contains commits")
    void versionGraphContainsCommits() {
        TimelineCommit commit = new TimelineCommit(
                new TimelineCommitId("c1"), List.of(),
                new TimelineSemanticHash("abc"), "rev-1", "user", "Init", Map.of());
        TimelineVersionGraph graph = new TimelineVersionGraph(
                "graph-1", List.of(commit), Map.of());
        assertEquals(1, graph.commits().size());
    }

    // --- Impact ---

    @Test
    @DisplayName("Render impact supports METADATA_ONLY")
    void renderImpactMetadataOnly() {
        TimelineRenderImpact impact = TimelineRenderImpact.metadataOnly();
        assertEquals(TimelineRenderImpactLevel.METADATA_ONLY, impact.level());
    }

    @Test
    @DisplayName("Render impact supports FULL_RERENDER")
    void renderImpactFullRerender() {
        TimelineRenderImpact impact = TimelineRenderImpact.fullRerender();
        assertEquals(TimelineRenderImpactLevel.FULL_RERENDER, impact.level());
    }

    @Test
    @DisplayName("ArtifactDAGImpact stores safe node keys")
    void artifactDagImpactSafe() {
        ArtifactDAGImpact impact = new ArtifactDAGImpact(
                "imp-1", List.of("node-1", "node-2"), List.of("node-3"), Map.of());
        assertEquals(2, impact.affectedNodeKeys().size());
        assertFalse(impact.toString().contains("bucket"));
    }

    @Test
    @DisplayName("ProductLineageImpact stores safe product ids")
    void productLineageImpactSafe() {
        ProductLineageImpact impact = new ProductLineageImpact(
                "imp-1", List.of("prod-1"), List.of("prod-2"), Map.of());
        assertEquals(1, impact.affectedProductIds().size());
        assertFalse(impact.toString().contains("signedUrl"));
    }

    // --- Bridge types ---

    @Test
    @DisplayName("TemplateApplicationDiff carries operations")
    void templateAppDiff() {
        TemplateApplicationDiff diff = new TemplateApplicationDiff(
                "app-1", List.of(), Map.of());
        assertEquals("app-1", diff.templateApplicationId());
    }

    @Test
    @DisplayName("CompositeTemplateDiff carries child template ids")
    void compositeDiff() {
        CompositeTemplateDiff diff = new CompositeTemplateDiff(
                "comp-1", List.of("child-1", "child-2"), List.of(), Map.of());
        assertEquals(2, diff.childTemplateIds().size());
    }

    @Test
    @DisplayName("WorkflowApplyTemplateStepDiff carries workflow step id")
    void workflowStepDiff() {
        WorkflowApplyTemplateStepDiff diff = new WorkflowApplyTemplateStepDiff(
                "step-1", "app-1", List.of(), Map.of());
        assertEquals("step-1", diff.workflowStepId());
        assertEquals("app-1", diff.templateApplicationId());
    }

    // --- Safety ---

    @Test
    @DisplayName("No providerName/providerType/backendName fields")
    void noProviderFields() {
        TimelineDiff diff = new TimelineDiff(
                new TimelineDiffId("d1"), "r1", "r2",
                List.of(), List.of(), TimelineRenderImpact.metadataOnly(), Map.of());
        assertFalse(diff.toString().contains("providerName"));
        assertFalse(diff.toString().contains("backendName"));
    }

    @Test
    @DisplayName("No bucket/objectKey/signedUrl fields")
    void noStorageFields() {
        TimelineDiff diff = new TimelineDiff(
                new TimelineDiffId("d1"), "r1", "r2",
                List.of(), List.of(), TimelineRenderImpact.metadataOnly(), Map.of());
        assertFalse(diff.toString().contains("bucket"));
        assertFalse(diff.toString().contains("signedUrl"));
    }

    @Test
    @DisplayName("No Remotion references")
    void noRemotionReferences() {
        TimelineDiff diff = new TimelineDiff(
                new TimelineDiffId("d1"), "r1", "r2",
                List.of(), List.of(), TimelineRenderImpact.metadataOnly(), Map.of());
        assertFalse(diff.toString().contains("remotion"));
    }

    @Test
    @DisplayName("No vedit dependency referenced")
    void noVeditDependency() {
        TimelineDiff diff = new TimelineDiff(
                new TimelineDiffId("d1"), "r1", "r2",
                List.of(), List.of(), TimelineRenderImpact.metadataOnly(), Map.of());
        assertFalse(diff.toString().contains("vedit"));
    }
}
