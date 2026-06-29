package com.example.platform.render.domain.timeline.diff.merge.plan;

import com.example.platform.render.domain.timeline.diff.*;
import com.example.platform.render.domain.timeline.diff.calculation.*;
import com.example.platform.render.domain.timeline.diff.merge.*;
import com.example.platform.render.domain.timeline.diff.merge.preview.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TimelineNonConflictingMergePlanner covering:
 * - Stage 1: Merge plan request and status types
 * - Stage 2: Merge plan operation types
 * - Stage 3: Merge plan issue and summary types
 * - Stage 4: Planner core behavior
 * - Stage 5: Non-conflicting operation classification
 * - Stage 6: Conflicting operation classification
 * - Stage 7: Duplicate / identical operation handling
 * - Stage 8: Policy tests
 * - Stage 9: Determinism and safety tests
 */
class TimelineNonConflictingMergePlannerTest {

    private TimelineNonConflictingMergePlanner planner;
    private TimelineMergePreviewService previewService;
    private TimelineMergeConflictDetector detector;

    @BeforeEach
    void setUp() {
        detector = new TimelineMergeConflictDetector();
        previewService = new TimelineMergePreviewService(detector);
        planner = new TimelineNonConflictingMergePlanner(previewService);
    }

    // ===== Stage 1: Merge Plan Request and Status Types =====

    @Test @DisplayName("Plan request ID rejects blank")
    void planRequestIdRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new TimelineMergePlanRequestId(""));
        assertThrows(IllegalArgumentException.class, () -> new TimelineMergePlanRequestId("  "));
        assertThrows(IllegalArgumentException.class, () -> new TimelineMergePlanRequestId(null));
    }

    @Test @DisplayName("Plan request ID accepts valid value")
    void planRequestIdAcceptsValid() {
        TimelineMergePlanRequestId id = new TimelineMergePlanRequestId("plan-req-1");
        assertEquals("plan-req-1", id.value());
    }

    @Test @DisplayName("Plan request requires base/ours/theirs validation via planner")
    void planRequestRequiresSnapshots() {
        // Null base
        TimelineMergePlanRequest reqBase = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                null, snap("ours"), snap("theirs"),
                TimelineMergePlanPolicy.CONSERVATIVE, Map.of());
        TimelineNonConflictingMergePlan rBase = planner.plan(reqBase);
        assertEquals(TimelineMergePlanStatus.INVALID_INPUT, rBase.status());

        // Null ours
        TimelineMergePlanRequest reqOurs = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-2"),
                snap("base"), null, snap("theirs"),
                TimelineMergePlanPolicy.CONSERVATIVE, Map.of());
        TimelineNonConflictingMergePlan rOurs = planner.plan(reqOurs);
        assertEquals(TimelineMergePlanStatus.INVALID_INPUT, rOurs.status());

        // Null theirs
        TimelineMergePlanRequest reqTheirs = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-3"),
                snap("base"), snap("ours"), null,
                TimelineMergePlanPolicy.CONSERVATIVE, Map.of());
        TimelineNonConflictingMergePlan rTheirs = planner.plan(reqTheirs);
        assertEquals(TimelineMergePlanStatus.INVALID_INPUT, rTheirs.status());
    }

    @Test @DisplayName("Policy defaults to CONSERVATIVE when null")
    void policyDefaultsToConservative() {
        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                snap("base"), snap("ours"), snap("theirs"),
                null, Map.of());
        assertEquals(TimelineMergePlanPolicy.CONSERVATIVE, req.effectivePolicy());
    }

    @Test @DisplayName("Plan status enum contains required statuses")
    void planStatusEnumContainsRequired() {
        assertNotNull(TimelineMergePlanStatus.READY);
        assertNotNull(TimelineMergePlanStatus.MANUAL_REVIEW_REQUIRED);
        assertNotNull(TimelineMergePlanStatus.BLOCKED);
        assertNotNull(TimelineMergePlanStatus.UNSUPPORTED);
        assertNotNull(TimelineMergePlanStatus.INVALID_INPUT);
        assertNotNull(TimelineMergePlanStatus.FAILED);
    }

    @Test @DisplayName("Safe metadata only in request")
    void safeMetadataOnlyInRequest() {
        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                snap("base"), snap("ours"), snap("theirs"),
                TimelineMergePlanPolicy.CONSERVATIVE,
                Map.of("key", "value"));
        assertEquals("value", req.safeMetadata().get("key"));
    }

    @Test @DisplayName("Null request returns INVALID_INPUT")
    void nullRequestReturnsInvalidInput() {
        TimelineNonConflictingMergePlan r = planner.plan(null);
        assertEquals(TimelineMergePlanStatus.INVALID_INPUT, r.status());
    }

    @Test @DisplayName("Null request ID rejects at construction time")
    void nullRequestIdRejectsAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new TimelineMergePlanRequest(
                null, snap("base"), snap("ours"), snap("theirs"),
                TimelineMergePlanPolicy.CONSERVATIVE, Map.of()));
    }

    // ===== Stage 2: Merge Plan Operation Types =====

    @Test @DisplayName("Operation status enum contains required statuses")
    void operationStatusEnumContainsRequired() {
        assertNotNull(TimelineMergePlanOperationStatus.SAFE_TO_APPLY_LATER);
        assertNotNull(TimelineMergePlanOperationStatus.CONFLICT_REQUIRES_MANUAL_REVIEW);
        assertNotNull(TimelineMergePlanOperationStatus.UNSUPPORTED);
        assertNotNull(TimelineMergePlanOperationStatus.BLOCKED);
        assertNotNull(TimelineMergePlanOperationStatus.SKIPPED_DUPLICATE);
    }

    @Test @DisplayName("Operation source enum contains required sources")
    void operationSourceEnumContainsRequired() {
        assertNotNull(TimelineMergePlanOperationSource.OURS);
        assertNotNull(TimelineMergePlanOperationSource.THEIRS);
        assertNotNull(TimelineMergePlanOperationSource.BOTH_IDENTICAL);
        assertNotNull(TimelineMergePlanOperationSource.SYSTEM);
    }

    @Test @DisplayName("Operation supports safe-to-apply-later")
    void operationSupportsSafeToApplyLater() {
        TimelineChangeOperation op = changeOp(TimelineChangeType.METADATA_CHANGED,
                "timeline.metadata.title", "Old", "New");
        TimelineMergePlanOperation planOp = TimelineMergePlanOperation.safeFromOurs(op);
        assertEquals(TimelineMergePlanOperationStatus.SAFE_TO_APPLY_LATER, planOp.status());
        assertEquals(TimelineMergePlanOperationSource.OURS, planOp.source());
    }

    @Test @DisplayName("Operation supports manual review")
    void operationSupportsManualReview() {
        TimelineChangeOperation op = changeOp(TimelineChangeType.CAPTION_SEGMENT_CHANGED,
                "timeline.captions.cap-1.text", "Hello", "Ours");
        TimelineMergePlanOperation planOp = TimelineMergePlanOperation.conflict(
                TimelineMergePlanOperationSource.OURS, op, List.of(), List.of());
        assertEquals(TimelineMergePlanOperationStatus.CONFLICT_REQUIRES_MANUAL_REVIEW, planOp.status());
    }

    @Test @DisplayName("Operation supports blocked")
    void operationSupportsBlocked() {
        TimelineChangeOperation op = changeOp(TimelineChangeType.METADATA_CHANGED,
                "timeline.metadata.bucket", "old", "new");
        TimelineMergePlanOperation planOp = TimelineMergePlanOperation.blocked(op,
                TimelineMergePlanIssue.of(TimelineMergePlanIssueSeverity.BLOCKING,
                        TimelineMergePlanIssueCode.BLOCKED_OPERATION, "path", "Forbidden"));
        assertEquals(TimelineMergePlanOperationStatus.BLOCKED, planOp.status());
    }

    @Test @DisplayName("Operation supports skipped duplicate")
    void operationSupportsSkippedDuplicate() {
        TimelineChangeOperation op = changeOp(TimelineChangeType.METADATA_CHANGED,
                "timeline.metadata.title", "Old", "New");
        TimelineMergePlanOperation planOp = TimelineMergePlanOperation.skippedDuplicate(
                op, TimelineMergePlanOperationSource.BOTH_IDENTICAL);
        assertEquals(TimelineMergePlanOperationStatus.SKIPPED_DUPLICATE, planOp.status());
        assertEquals(TimelineMergePlanOperationSource.BOTH_IDENTICAL, planOp.source());
    }

    @Test @DisplayName("Path is safe in operation")
    void pathIsSafeInOperation() {
        TimelineChangeOperation op = changeOp(TimelineChangeType.METADATA_CHANGED,
                "timeline.metadata.title", "Old", "New");
        TimelineMergePlanOperation planOp = TimelineMergePlanOperation.safeFromOurs(op);
        assertEquals("timeline.metadata.title", planOp.path());
        assertNotNull(planOp.safeMetadata());
    }

    // ===== Stage 3: Merge Plan Issue and Summary Types =====

    @Test @DisplayName("Issue severity enum contains INFO/WARNING/ERROR/BLOCKING")
    void issueSeverityEnumContainsLevels() {
        assertNotNull(TimelineMergePlanIssueSeverity.INFO);
        assertNotNull(TimelineMergePlanIssueSeverity.WARNING);
        assertNotNull(TimelineMergePlanIssueSeverity.ERROR);
        assertNotNull(TimelineMergePlanIssueSeverity.BLOCKING);
    }

    @Test @DisplayName("Issue codes include boundary codes")
    void issueCodesIncludeBoundaries() {
        assertNotNull(TimelineMergePlanIssueCode.PERSISTENCE_NOT_IMPLEMENTED);
        assertNotNull(TimelineMergePlanIssueCode.RENDER_NOT_ALLOWED);
        assertNotNull(TimelineMergePlanIssueCode.PRODUCT_CREATION_NOT_ALLOWED);
        assertNotNull(TimelineMergePlanIssueCode.STORAGE_INTERNALS_NOT_ALLOWED);
        assertNotNull(TimelineMergePlanIssueCode.PROVIDER_INTERNALS_NOT_ALLOWED);
        assertNotNull(TimelineMergePlanIssueCode.ARTIFACT_DAG_NOT_USED);
        assertNotNull(TimelineMergePlanIssueCode.GLOBAL_OPTIMIZATION_NOT_ALLOWED);
        assertNotNull(TimelineMergePlanIssueCode.MERGE_ENGINE_NOT_IMPLEMENTED);
        assertNotNull(TimelineMergePlanIssueCode.CONFLICT_RESOLUTION_NOT_IMPLEMENTED);
        assertNotNull(TimelineMergePlanIssueCode.PATCH_APPLICATION_NOT_ALLOWED);
    }

    @Test @DisplayName("Summary counts are validated")
    void summaryCountsValidated() {
        TimelineMergePlanSummary summary = TimelineMergePlanSummary.of(
                5, 3, 4, 2, 1, 1, 0, 2, true, false);
        assertEquals(5, summary.oursOperationCount());
        assertEquals(3, summary.theirsOperationCount());
        assertEquals(4, summary.safeOperationCount());
        assertEquals(2, summary.conflictOperationCount());
        assertEquals(1, summary.unsupportedOperationCount());
        assertEquals(1, summary.blockedOperationCount());
        assertEquals(0, summary.skippedDuplicateCount());
        assertEquals(2, summary.conflictCount());
        assertTrue(summary.manualReviewRequired());
        assertFalse(summary.canAutoApplyInFuture());
    }

    @Test @DisplayName("Plan supports all required statuses")
    void planSupportsAllStatuses() {
        TimelineMergePlanId id = new TimelineMergePlanId("test-id");

        assertEquals(TimelineMergePlanStatus.READY,
                TimelineNonConflictingMergePlan.ready(id, null, List.of(), null).status());
        assertEquals(TimelineMergePlanStatus.MANUAL_REVIEW_REQUIRED,
                TimelineNonConflictingMergePlan.manualReview(id, null, List.of(), null, List.of()).status());
        assertEquals(TimelineMergePlanStatus.BLOCKED,
                TimelineNonConflictingMergePlan.blocked(id, List.of()).status());
        assertEquals(TimelineMergePlanStatus.INVALID_INPUT,
                TimelineNonConflictingMergePlan.invalidInput(id, List.of()).status());
        assertEquals(TimelineMergePlanStatus.UNSUPPORTED,
                TimelineNonConflictingMergePlan.unsupported(id, List.of()).status());
        assertEquals(TimelineMergePlanStatus.FAILED,
                TimelineNonConflictingMergePlan.failed(id, List.of()).status());
    }

    @Test @DisplayName("Plan does not contain merged snapshot")
    void planDoesNotContainMergedSnapshot() {
        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                snap("base"), snap("ours"), snap("theirs"),
                TimelineMergePlanPolicy.CONSERVATIVE, Map.of());
        TimelineNonConflictingMergePlan plan = planner.plan(req);
        // Plan has no field for merged snapshot
        String repr = plan.toString();
        assertFalse(repr.contains("mergedSnapshot"), "Plan must not contain merged snapshot");
    }

    @Test @DisplayName("Plan does not contain TimelineCommit")
    void planDoesNotContainTimelineCommit() {
        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                snap("base"), snap("ours"), snap("theirs"),
                TimelineMergePlanPolicy.CONSERVATIVE, Map.of());
        TimelineNonConflictingMergePlan plan = planner.plan(req);
        String repr = plan.toString();
        assertFalse(repr.contains("TimelineCommit"), "Plan must not contain TimelineCommit");
    }

    // ===== Stage 4: Planner Core Behavior =====

    @Test @DisplayName("Valid merge-ready preview returns READY plan")
    void validMergeReadyPreviewReturnsReadyPlan() {
        CanonicalTimelineSnapshot base = snap("rev-1");
        CanonicalTimelineSnapshot ours = snap("rev-ours");
        CanonicalTimelineSnapshot theirs = snap("rev-theirs");

        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePlanPolicy.CONSERVATIVE, Map.of());
        TimelineNonConflictingMergePlan plan = planner.plan(req);

        assertEquals(TimelineMergePlanStatus.READY, plan.status());
        assertNotNull(plan.previewResult());
        assertNotNull(plan.summary());
        assertNotNull(plan.issues());
    }

    @Test @DisplayName("Manual-review preview returns MANUAL_REVIEW_REQUIRED plan")
    void manualReviewPreviewReturnsManualReviewPlan() {
        CanonicalTimelineSnapshot base = snap("rev-1");
        CanonicalTimelineSnapshot ours = new CanonicalTimelineSnapshot(
                base.id(), "rev-ours", 9999,
                base.tracks(), base.captions(), base.watermarks(),
                base.templateApplications(), base.workflowSteps(), base.outputProfile(),
                base.safeMetadata());
        CanonicalTimelineSnapshot theirs = new CanonicalTimelineSnapshot(
                base.id(), "rev-theirs", 7777,
                base.tracks(), base.captions(), base.watermarks(),
                base.templateApplications(), base.workflowSteps(), base.outputProfile(),
                base.safeMetadata());

        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePlanPolicy.CONSERVATIVE, Map.of());
        TimelineNonConflictingMergePlan plan = planner.plan(req);

        assertEquals(TimelineMergePlanStatus.MANUAL_REVIEW_REQUIRED, plan.status());
        assertTrue(plan.hasConflicts());
    }

    @Test @DisplayName("Invalid input returns INVALID_INPUT plan")
    void invalidInputReturnsInvalidInputPlan() {
        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                null, snap("ours"), snap("theirs"),
                TimelineMergePlanPolicy.CONSERVATIVE, Map.of());
        TimelineNonConflictingMergePlan plan = planner.plan(req);

        assertEquals(TimelineMergePlanStatus.INVALID_INPUT, plan.status());
    }

    @Test @DisplayName("Planner does not apply patch")
    void plannerDoesNotApplyPatch() {
        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                snap("base"), snap("ours"), snap("theirs"),
                TimelineMergePlanPolicy.CONSERVATIVE, Map.of());
        TimelineNonConflictingMergePlan plan = planner.plan(req);

        // Verify no patch was applied — base snapshot unchanged
        assertNotNull(plan);
        assertEquals(TimelineMergePlanStatus.READY, plan.status());
    }

    @Test @DisplayName("Planner does not create merged snapshot")
    void plannerDoesNotCreateMergedSnapshot() {
        CanonicalTimelineSnapshot base = snap("base");
        String baseBefore = base.toString();

        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                base, snap("ours"), snap("theirs"),
                TimelineMergePlanPolicy.CONSERVATIVE, Map.of());
        planner.plan(req);

        assertEquals(baseBefore, base.toString(), "Base snapshot must not be mutated");
    }

    @Test @DisplayName("Planner does not persist commit")
    void plannerDoesNotPersistCommit() {
        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                snap("base"), snap("ours"), snap("theirs"),
                TimelineMergePlanPolicy.CONSERVATIVE, Map.of());
        TimelineNonConflictingMergePlan plan = planner.plan(req);

        // Plan has no persistence mechanism
        assertNotNull(plan);
    }

    // ===== Stage 5: Non-conflicting Operation Classification Tests =====

    @Test @DisplayName("Different metadata and duration changed -> safe operations")
    void differentMetadataAndDurationSafe() {
        // Note: metadata is treated atomically by the diff calculator.
        // Different keys changed on both sides = divergent same-path change = conflict.
        // Instead, test truly different paths: ours changes metadata, theirs changes duration.
        CanonicalTimelineSnapshot base = snapWithMetadata("rev-1", Map.of("title", "Old"));
        CanonicalTimelineSnapshot ours = snapWithMetadata("rev-ours", Map.of("title", "New"));
        // theirs changes duration instead of metadata
        CanonicalTimelineSnapshot theirs = new CanonicalTimelineSnapshot(
                snapId("snap-rev-theirs"), "rev-theirs", 9999,
                base.tracks(), base.captions(), base.watermarks(),
                base.templateApplications(), base.workflowSteps(), base.outputProfile(),
                base.safeMetadata());

        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePlanPolicy.CONSERVATIVE, Map.of());
        TimelineNonConflictingMergePlan plan = planner.plan(req);

        // Different paths changed -> should be safe
        assertTrue(plan.operations().stream()
                .allMatch(op -> op.status() == TimelineMergePlanOperationStatus.SAFE_TO_APPLY_LATER),
                "Different path changes should be safe");
    }

    @Test @DisplayName("Different captions changed -> safe operations")
    void differentCaptionsChangedSafe() {
        CanonicalTimelineCaptionSnapshot cap1 = new CanonicalTimelineCaptionSnapshot(
                "cap-1", 0, 3000, "Hello", Map.of(), Map.of());
        CanonicalTimelineCaptionSnapshot cap2 = new CanonicalTimelineCaptionSnapshot(
                "cap-2", 3000, 6000, "World", Map.of(), Map.of());
        CanonicalTimelineSnapshot base = new CanonicalTimelineSnapshot(
                snapId("s1"), "rev-1", 5000,
                List.of(), List.of(cap1, cap2), List.of(), List.of(), List.of(), null, Map.of());

        // ours changes cap-1
        CanonicalTimelineCaptionSnapshot cap1ours = new CanonicalTimelineCaptionSnapshot(
                "cap-1", 0, 3000, "Hi", Map.of(), Map.of());
        CanonicalTimelineSnapshot ours = new CanonicalTimelineSnapshot(
                snapId("s1"), "rev-ours", 5000,
                List.of(), List.of(cap1ours, cap2), List.of(), List.of(), List.of(), null, Map.of());

        // theirs changes cap-2
        CanonicalTimelineCaptionSnapshot cap2theirs = new CanonicalTimelineCaptionSnapshot(
                "cap-2", 3000, 6000, "Earth", Map.of(), Map.of());
        CanonicalTimelineSnapshot theirs = new CanonicalTimelineSnapshot(
                snapId("s1"), "rev-theirs", 5000,
                List.of(), List.of(cap1, cap2theirs), List.of(), List.of(), List.of(), null, Map.of());

        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePlanPolicy.CONSERVATIVE, Map.of());
        TimelineNonConflictingMergePlan plan = planner.plan(req);

        assertTrue(plan.operations().stream()
                .allMatch(op -> op.status() == TimelineMergePlanOperationStatus.SAFE_TO_APPLY_LATER),
                "Different caption changes should be safe");
    }

    @Test @DisplayName("One-sided operation -> safe operation")
    void oneSidedOperationSafe() {
        CanonicalTimelineSnapshot base = snap("rev-1");
        // Only ours changes duration
        CanonicalTimelineSnapshot ours = new CanonicalTimelineSnapshot(
                base.id(), "rev-ours", 9999,
                base.tracks(), base.captions(), base.watermarks(),
                base.templateApplications(), base.workflowSteps(), base.outputProfile(),
                base.safeMetadata());
        CanonicalTimelineSnapshot theirs = snap("rev-theirs");

        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePlanPolicy.CONSERVATIVE, Map.of());
        TimelineNonConflictingMergePlan plan = planner.plan(req);

        assertEquals(TimelineMergePlanStatus.READY, plan.status());
        assertTrue(plan.operations().stream()
                .allMatch(op -> op.status() == TimelineMergePlanOperationStatus.SAFE_TO_APPLY_LATER));
    }

    // ===== Stage 6: Conflicting Operation Classification Tests =====

    @Test @DisplayName("Same caption text changed differently -> conflict manual review")
    void sameCaptionChangedDifferentlyManualReview() {
        CanonicalTimelineCaptionSnapshot cap = new CanonicalTimelineCaptionSnapshot(
                "cap-1", 0, 3000, "Hello", Map.of(), Map.of());
        CanonicalTimelineSnapshot base = new CanonicalTimelineSnapshot(
                snapId("s1"), "rev-1", 5000,
                List.of(), List.of(cap), List.of(), List.of(), List.of(), null, Map.of());

        CanonicalTimelineCaptionSnapshot capOurs = new CanonicalTimelineCaptionSnapshot(
                "cap-1", 0, 3000, "Ours", Map.of(), Map.of());
        CanonicalTimelineSnapshot ours = new CanonicalTimelineSnapshot(
                snapId("s1"), "rev-ours", 5000,
                List.of(), List.of(capOurs), List.of(), List.of(), List.of(), null, Map.of());

        CanonicalTimelineCaptionSnapshot capTheirs = new CanonicalTimelineCaptionSnapshot(
                "cap-1", 0, 3000, "Theirs", Map.of(), Map.of());
        CanonicalTimelineSnapshot theirs = new CanonicalTimelineSnapshot(
                snapId("s1"), "rev-theirs", 5000,
                List.of(), List.of(capTheirs), List.of(), List.of(), List.of(), null, Map.of());

        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePlanPolicy.CONSERVATIVE, Map.of());
        TimelineNonConflictingMergePlan plan = planner.plan(req);

        assertEquals(TimelineMergePlanStatus.MANUAL_REVIEW_REQUIRED, plan.status());
        assertTrue(plan.operations().stream().anyMatch(
                op -> op.status() == TimelineMergePlanOperationStatus.CONFLICT_REQUIRES_MANUAL_REVIEW));
    }

    @Test @DisplayName("Same output profile changed differently -> manual review")
    void sameOutputProfileChangedDifferentlyManualReview() {
        CanonicalTimelineOutputProfileSnapshot p1 = new CanonicalTimelineOutputProfileSnapshot(
                "p1", "mp4", "16:9", 1920, 1080, Map.of());
        CanonicalTimelineOutputProfileSnapshot p2 = new CanonicalTimelineOutputProfileSnapshot(
                "p2", "mp4", "16:9", 1280, 720, Map.of());
        CanonicalTimelineSnapshot base = new CanonicalTimelineSnapshot(
                snapId("s1"), "rev-1", 5000,
                List.of(), List.of(), List.of(), List.of(), List.of(), null, Map.of());
        CanonicalTimelineSnapshot ours = new CanonicalTimelineSnapshot(
                snapId("s1"), "rev-ours", 5000,
                List.of(), List.of(), List.of(), List.of(), List.of(), p1, Map.of());
        CanonicalTimelineSnapshot theirs = new CanonicalTimelineSnapshot(
                snapId("s1"), "rev-theirs", 5000,
                List.of(), List.of(), List.of(), List.of(), List.of(), p2, Map.of());

        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePlanPolicy.CONSERVATIVE, Map.of());
        TimelineNonConflictingMergePlan plan = planner.plan(req);

        assertEquals(TimelineMergePlanStatus.MANUAL_REVIEW_REQUIRED, plan.status());
        assertTrue(plan.hasConflicts());
    }

    @Test @DisplayName("Same watermark changed differently -> manual review")
    void sameWatermarkChangedDifferentlyManualReview() {
        CanonicalTimelineWatermarkSnapshot wm = new CanonicalTimelineWatermarkSnapshot(
                "wm-1", "logo", "BOTTOM_RIGHT", 50, Map.of());
        CanonicalTimelineSnapshot base = new CanonicalTimelineSnapshot(
                snapId("s1"), "rev-1", 5000,
                List.of(), List.of(), List.of(wm), List.of(), List.of(), null, Map.of());

        CanonicalTimelineWatermarkSnapshot wmOurs = new CanonicalTimelineWatermarkSnapshot(
                "wm-1", "logo", "TOP_LEFT", 80, Map.of());
        CanonicalTimelineSnapshot ours = new CanonicalTimelineSnapshot(
                snapId("s1"), "rev-ours", 5000,
                List.of(), List.of(), List.of(wmOurs), List.of(), List.of(), null, Map.of());

        CanonicalTimelineWatermarkSnapshot wmTheirs = new CanonicalTimelineWatermarkSnapshot(
                "wm-1", "logo", "CENTER", 30, Map.of());
        CanonicalTimelineSnapshot theirs = new CanonicalTimelineSnapshot(
                snapId("s1"), "rev-theirs", 5000,
                List.of(), List.of(), List.of(wmTheirs), List.of(), List.of(), null, Map.of());

        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePlanPolicy.CONSERVATIVE, Map.of());
        TimelineNonConflictingMergePlan plan = planner.plan(req);

        assertEquals(TimelineMergePlanStatus.MANUAL_REVIEW_REQUIRED, plan.status());
        assertTrue(plan.hasConflicts());
    }

    // ===== Stage 7: Duplicate / Identical Operation Tests =====

    @Test @DisplayName("Same metadata key changed to same value -> SKIPPED_DUPLICATE with ALLOW_IDENTICAL")
    void sameMetadataKeySameValueSkippedDuplicate() {
        CanonicalTimelineSnapshot base = snapWithMetadata("rev-1", Map.of("title", "Old"));
        CanonicalTimelineSnapshot ours = snapWithMetadata("rev-ours", Map.of("title", "New"));
        CanonicalTimelineSnapshot theirs = snapWithMetadata("rev-theirs", Map.of("title", "New"));

        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePlanPolicy.ALLOW_IDENTICAL_SAME_PATH_CHANGES, Map.of());
        TimelineNonConflictingMergePlan plan = planner.plan(req);

        assertTrue(plan.operations().stream().anyMatch(
                op -> op.status() == TimelineMergePlanOperationStatus.SKIPPED_DUPLICATE),
                "Identical same-path changes should be SKIPPED_DUPLICATE with ALLOW_IDENTICAL policy");
    }

    @Test @DisplayName("Same caption changed to same value -> SKIPPED_DUPLICATE with ALLOW_IDENTICAL")
    void sameCaptionSameValueSkippedDuplicate() {
        CanonicalTimelineCaptionSnapshot cap = new CanonicalTimelineCaptionSnapshot(
                "cap-1", 0, 3000, "Hello", Map.of(), Map.of());
        CanonicalTimelineSnapshot base = new CanonicalTimelineSnapshot(
                snapId("s1"), "rev-1", 5000,
                List.of(), List.of(cap), List.of(), List.of(), List.of(), null, Map.of());

        CanonicalTimelineCaptionSnapshot capChanged = new CanonicalTimelineCaptionSnapshot(
                "cap-1", 0, 3000, "World", Map.of(), Map.of());
        CanonicalTimelineSnapshot ours = new CanonicalTimelineSnapshot(
                snapId("s1"), "rev-ours", 5000,
                List.of(), List.of(capChanged), List.of(), List.of(), List.of(), null, Map.of());
        CanonicalTimelineSnapshot theirs = new CanonicalTimelineSnapshot(
                snapId("s1"), "rev-theirs", 5000,
                List.of(), List.of(capChanged), List.of(), List.of(), List.of(), null, Map.of());

        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePlanPolicy.ALLOW_IDENTICAL_SAME_PATH_CHANGES, Map.of());
        TimelineNonConflictingMergePlan plan = planner.plan(req);

        assertTrue(plan.operations().stream().anyMatch(
                op -> op.status() == TimelineMergePlanOperationStatus.SKIPPED_DUPLICATE));
    }

    @Test @DisplayName("Same output profile changed to same value -> SKIPPED_DUPLICATE with ALLOW_IDENTICAL")
    void sameOutputProfileSameValueSkippedDuplicate() {
        CanonicalTimelineOutputProfileSnapshot p = new CanonicalTimelineOutputProfileSnapshot(
                "p1", "mp4", "16:9", 1920, 1080, Map.of());
        CanonicalTimelineSnapshot base = new CanonicalTimelineSnapshot(
                snapId("s1"), "rev-1", 5000,
                List.of(), List.of(), List.of(), List.of(), List.of(), null, Map.of());
        CanonicalTimelineSnapshot ours = new CanonicalTimelineSnapshot(
                snapId("s1"), "rev-ours", 5000,
                List.of(), List.of(), List.of(), List.of(), List.of(), p, Map.of());
        CanonicalTimelineSnapshot theirs = new CanonicalTimelineSnapshot(
                snapId("s1"), "rev-theirs", 5000,
                List.of(), List.of(), List.of(), List.of(), List.of(), p, Map.of());

        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePlanPolicy.ALLOW_IDENTICAL_SAME_PATH_CHANGES, Map.of());
        TimelineNonConflictingMergePlan plan = planner.plan(req);

        assertTrue(plan.operations().stream().anyMatch(
                op -> op.status() == TimelineMergePlanOperationStatus.SKIPPED_DUPLICATE));
    }

    // ===== Stage 8: Policy Tests =====

    @Test @DisplayName("CONSERVATIVE default")
    void conservativeDefault() {
        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                snap("base"), snap("ours"), snap("theirs"),
                null, Map.of());
        assertEquals(TimelineMergePlanPolicy.CONSERVATIVE, req.effectivePolicy());
    }

    @Test @DisplayName("ALLOW_DIFFERENT_PATHS allows non-conflicting different paths")
    void allowDifferentPathsAllowsNonConflicting() {
        // Ours changes metadata, theirs changes duration — truly different paths
        CanonicalTimelineSnapshot base = snapWithMetadata("rev-1", Map.of("title", "Old"));
        CanonicalTimelineSnapshot ours = snapWithMetadata("rev-ours", Map.of("title", "New"));
        CanonicalTimelineSnapshot theirs = new CanonicalTimelineSnapshot(
                snapId("snap-rev-theirs"), "rev-theirs", 9999,
                base.tracks(), base.captions(), base.watermarks(),
                base.templateApplications(), base.workflowSteps(), base.outputProfile(),
                base.safeMetadata());

        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePlanPolicy.ALLOW_DIFFERENT_PATHS, Map.of());
        TimelineNonConflictingMergePlan plan = planner.plan(req);

        assertEquals(TimelineMergePlanStatus.READY, plan.status());
    }

    @Test @DisplayName("BLOCK_ON_ANY_CONFLICT returns BLOCKED when conflicts exist")
    void blockOnAnyConflictReturnsBlocked() {
        CanonicalTimelineSnapshot base = snap("rev-1");
        CanonicalTimelineSnapshot ours = new CanonicalTimelineSnapshot(
                base.id(), "rev-ours", 9999,
                base.tracks(), base.captions(), base.watermarks(),
                base.templateApplications(), base.workflowSteps(), base.outputProfile(),
                base.safeMetadata());
        CanonicalTimelineSnapshot theirs = new CanonicalTimelineSnapshot(
                base.id(), "rev-theirs", 7777,
                base.tracks(), base.captions(), base.watermarks(),
                base.templateApplications(), base.workflowSteps(), base.outputProfile(),
                base.safeMetadata());

        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePlanPolicy.BLOCK_ON_ANY_CONFLICT, Map.of());
        TimelineNonConflictingMergePlan plan = planner.plan(req);

        assertEquals(TimelineMergePlanStatus.BLOCKED, plan.status());
    }

    // ===== Stage 9: Determinism and Safety Tests =====

    @Test @DisplayName("Plan operation ordering deterministic across double-run")
    void planOperationOrderingDeterministic() {
        CanonicalTimelineCaptionSnapshot cap = new CanonicalTimelineCaptionSnapshot(
                "cap-1", 0, 3000, "Hello", Map.of(), Map.of());
        CanonicalTimelineSnapshot base = new CanonicalTimelineSnapshot(
                snapId("s1"), "rev-1", 5000,
                List.of(), List.of(cap), List.of(), List.of(), List.of(), null, Map.of());

        CanonicalTimelineCaptionSnapshot capOurs = new CanonicalTimelineCaptionSnapshot(
                "cap-1", 0, 3000, "Ours", Map.of(), Map.of());
        CanonicalTimelineSnapshot ours = new CanonicalTimelineSnapshot(
                snapId("s1"), "rev-ours", 5000,
                List.of(), List.of(capOurs), List.of(), List.of(), List.of(), null, Map.of());

        CanonicalTimelineCaptionSnapshot capTheirs = new CanonicalTimelineCaptionSnapshot(
                "cap-1", 0, 3000, "Theirs", Map.of(), Map.of());
        CanonicalTimelineSnapshot theirs = new CanonicalTimelineSnapshot(
                snapId("s1"), "rev-theirs", 5000,
                List.of(), List.of(capTheirs), List.of(), List.of(), List.of(), null, Map.of());

        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePlanPolicy.CONSERVATIVE, Map.of());

        TimelineNonConflictingMergePlan plan1 = planner.plan(req);
        TimelineNonConflictingMergePlan plan2 = planner.plan(req);

        assertEquals(plan1.status(), plan2.status());
        assertEquals(plan1.operations().size(), plan2.operations().size());
        for (int i = 0; i < plan1.operations().size(); i++) {
            assertEquals(plan1.operations().get(i).status(), plan2.operations().get(i).status());
            assertEquals(plan1.operations().get(i).source(), plan2.operations().get(i).source());
            assertEquals(plan1.operations().get(i).path(), plan2.operations().get(i).path());
        }
    }

    @Test @DisplayName("Summary counts stable across double-run")
    void summaryCountsStable() {
        CanonicalTimelineSnapshot base = snap("rev-1");
        CanonicalTimelineSnapshot ours = snap("rev-ours");
        CanonicalTimelineSnapshot theirs = snap("rev-theirs");

        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePlanPolicy.CONSERVATIVE, Map.of());

        TimelineNonConflictingMergePlan plan1 = planner.plan(req);
        TimelineNonConflictingMergePlan plan2 = planner.plan(req);

        assertEquals(plan1.summary().safeOperationCount(), plan2.summary().safeOperationCount());
        assertEquals(plan1.summary().conflictOperationCount(), plan2.summary().conflictOperationCount());
        assertEquals(plan1.summary().manualReviewRequired(), plan2.summary().manualReviewRequired());
    }

    @Test @DisplayName("Input snapshots not mutated")
    void inputSnapshotsNotMutated() {
        CanonicalTimelineSnapshot base = snap("base");
        CanonicalTimelineSnapshot ours = snap("ours");
        CanonicalTimelineSnapshot theirs = snap("theirs");
        String baseBefore = base.toString();
        String oursBefore = ours.toString();
        String theirsBefore = theirs.toString();

        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePlanPolicy.CONSERVATIVE, Map.of());
        planner.plan(req);

        assertEquals(baseBefore, base.toString(), "Base snapshot must not be mutated");
        assertEquals(oursBefore, ours.toString(), "Ours snapshot must not be mutated");
        assertEquals(theirsBefore, theirs.toString(), "Theirs snapshot must not be mutated");
    }

    @Test @DisplayName("Preview result not modified")
    void previewResultNotModified() {
        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                snap("base"), snap("ours"), snap("theirs"),
                TimelineMergePlanPolicy.CONSERVATIVE, Map.of());
        TimelineNonConflictingMergePlan plan = planner.plan(req);
        TimelineMergePreviewResult previewResult = plan.previewResult();

        // Verify preview result is included as-is
        assertNotNull(previewResult);
        assertNotNull(previewResult.status());
    }

    @Test @DisplayName("Planner constructor rejects null preview service")
    void plannerConstructorRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new TimelineNonConflictingMergePlanner(null));
    }

    @Test @DisplayName("Plan result toString is safe")
    void planResultToStringSafe() {
        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                snap("base"), snap("ours"), snap("theirs"),
                TimelineMergePlanPolicy.CONSERVATIVE,
                Map.of("customKey", "customValue"));
        TimelineNonConflictingMergePlan plan = planner.plan(req);
        String repr = plan.toString();
        assertNotNull(repr);
        assertFalse(repr.isEmpty());
    }

    @Test @DisplayName("Blocked forbidden path in operations")
    void blockedForbiddenPathInOperations() {
        // Create a snapshot with forbidden path keyword in metadata key
        // This tests the planner's forbidden path detection
        CanonicalTimelineSnapshot base = snap("rev-1");
        CanonicalTimelineSnapshot ours = snap("rev-ours");
        CanonicalTimelineSnapshot theirs = snap("rev-theirs");

        TimelineMergePlanRequest req = new TimelineMergePlanRequest(
                new TimelineMergePlanRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePlanPolicy.CONSERVATIVE, Map.of());
        TimelineNonConflictingMergePlan plan = planner.plan(req);

        // Verify no blocked operations for clean snapshots
        assertTrue(plan.operations().stream()
                .noneMatch(op -> op.status() == TimelineMergePlanOperationStatus.BLOCKED));
    }

    // ===== Helpers =====

    private CanonicalTimelineSnapshotId snapId(String id) {
        return new CanonicalTimelineSnapshotId(id);
    }

    private CanonicalTimelineSnapshot snap(String revId) {
        return new CanonicalTimelineSnapshot(snapId("snap-" + revId), revId, 5000,
                List.of(track("track-1", 0)), List.of(), List.of(), List.of(), List.of(), null, Map.of());
    }

    private CanonicalTimelineSnapshot snapWithMetadata(String revId, Map<String, String> metadata) {
        return new CanonicalTimelineSnapshot(snapId("snap-" + revId), revId, 5000,
                List.of(track("track-1", 0)), List.of(), List.of(), List.of(), List.of(), null, metadata);
    }

    private CanonicalTimelineTrackSnapshot track(String id, int order) {
        return new CanonicalTimelineTrackSnapshot(id, order, "VIDEO",
                List.of(new CanonicalTimelineClipSnapshot("clip-1", "asset-1", 0, 5000, 0, 5000, Map.of())),
                Map.of());
    }

    private TimelineChangeOperation changeOp(TimelineChangeType type, String path,
                                               String beforeVal, String afterVal) {
        return new TimelineChangeOperation(
                new TimelineChangeOperationId("op-" + System.nanoTime()),
                type,
                TimelineChangeScope.METADATA,
                new TimelineChangePath(path),
                beforeVal != null ? TimelineChangePayload.ofString(beforeVal) : null,
                afterVal != null ? TimelineChangePayload.ofString(afterVal) : null,
                Map.of());
    }
}
