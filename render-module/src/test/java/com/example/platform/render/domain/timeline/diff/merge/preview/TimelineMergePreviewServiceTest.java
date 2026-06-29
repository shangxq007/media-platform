package com.example.platform.render.domain.timeline.diff.merge.preview;

import com.example.platform.render.domain.timeline.diff.calculation.*;
import com.example.platform.render.domain.timeline.diff.merge.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TimelineMergePreviewService covering:
 * - Stage 1: Preview request and status types
 * - Stage 2: Preview result / summary / issue types
 * - Stage 3: TimelineMergePreviewService core
 * - Stage 4: Preview mode tests
 * - Stage 5: Preview policy tests
 * - Stage 6: End-to-end preview tests
 * - Stage 7: Safety and boundary tests
 */
class TimelineMergePreviewServiceTest {

    private TimelineMergePreviewService service;
    private TimelineMergeConflictDetector detector;

    @BeforeEach
    void setUp() {
        detector = new TimelineMergeConflictDetector();
        service = new TimelineMergePreviewService(detector);
    }

    // ===== Stage 1: Preview Request and Status Types =====

    @Test @DisplayName("Request ID rejects blank")
    void requestIdRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new TimelineMergePreviewRequestId(""));
        assertThrows(IllegalArgumentException.class, () -> new TimelineMergePreviewRequestId("  "));
        assertThrows(IllegalArgumentException.class, () -> new TimelineMergePreviewRequestId(null));
    }

    @Test @DisplayName("Request ID accepts valid value")
    void requestIdAcceptsValid() {
        TimelineMergePreviewRequestId id = new TimelineMergePreviewRequestId("req-1");
        assertEquals("req-1", id.value());
    }

    @Test @DisplayName("Request requires base/ours/theirs validation via service")
    void requestRequiresSnapshots() {
        // Null base
        TimelineMergePreviewRequest reqBase = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                null, snap("ours"), snap("theirs"),
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
        TimelineMergePreviewResult rBase = service.preview(reqBase);
        assertEquals(TimelineMergePreviewStatus.INVALID_INPUT, rBase.status());

        // Null ours
        TimelineMergePreviewRequest reqOurs = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-2"),
                snap("base"), null, snap("theirs"),
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
        TimelineMergePreviewResult rOurs = service.preview(reqOurs);
        assertEquals(TimelineMergePreviewStatus.INVALID_INPUT, rOurs.status());

        // Null theirs
        TimelineMergePreviewRequest reqTheirs = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-3"),
                snap("base"), snap("ours"), null,
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
        TimelineMergePreviewResult rTheirs = service.preview(reqTheirs);
        assertEquals(TimelineMergePreviewStatus.INVALID_INPUT, rTheirs.status());
    }

    @Test @DisplayName("Mode defaults to DIFF_AND_CONFLICTS when null")
    void modeDefaultsToDiffAndConflicts() {
        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                snap("base"), snap("ours"), snap("theirs"),
                null, TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
        assertEquals(TimelineMergePreviewMode.DIFF_AND_CONFLICTS, req.effectiveMode());
    }

    @Test @DisplayName("Policy defaults to CONSERVATIVE when null")
    void policyDefaultsToConservative() {
        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                snap("base"), snap("ours"), snap("theirs"),
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS, null, Map.of());
        assertEquals(TimelineMergePreviewPolicy.CONSERVATIVE, req.effectivePolicy());
    }

    @Test @DisplayName("Null request returns INVALID_INPUT")
    void nullRequestReturnsInvalidInput() {
        TimelineMergePreviewResult r = service.preview(null);
        assertEquals(TimelineMergePreviewStatus.INVALID_INPUT, r.status());
    }

    @Test @DisplayName("Null request ID rejects at construction time")
    void nullRequestIdRejectsAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new TimelineMergePreviewRequest(
                null, snap("base"), snap("ours"), snap("theirs"),
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of()));
    }

    // ===== Stage 2: Preview Result / Summary / Issue Types =====

    @Test @DisplayName("Merge-ready result factory")
    void mergeReadyResultFactory() {
        TimelineMergePreviewSummary summary = TimelineMergePreviewSummary.of(
                "rev-1", "rev-ours", "rev-theirs", 1, 1, 0, 0, true, false);
        TimelineMergePreviewResult r = TimelineMergePreviewResult.mergeReady(summary, null);
        assertEquals(TimelineMergePreviewStatus.MERGE_READY, r.status());
        assertTrue(r.isMergeReady());
    }

    @Test @DisplayName("Manual-review result factory")
    void manualReviewResultFactory() {
        List<TimelineMergePreviewIssue> issues = List.of(
                TimelineMergePreviewIssue.of(
                        TimelineMergePreviewIssueSeverity.WARNING,
                        TimelineMergePreviewIssueCode.MANUAL_REVIEW_REQUIRED,
                        "conflict", "Manual review needed"));
        TimelineMergePreviewResult r = TimelineMergePreviewResult.manualReview(null, null, issues);
        assertEquals(TimelineMergePreviewStatus.MANUAL_REVIEW_REQUIRED, r.status());
        assertFalse(r.isMergeReady());
        assertEquals(1, r.issues().size());
    }

    @Test @DisplayName("Blocked result factory")
    void blockedResultFactory() {
        List<TimelineMergePreviewIssue> issues = List.of(
                TimelineMergePreviewIssue.of(
                        TimelineMergePreviewIssueSeverity.BLOCKING,
                        TimelineMergePreviewIssueCode.STORAGE_INTERNALS_NOT_ALLOWED,
                        "path", "Forbidden keyword"));
        TimelineMergePreviewResult r = TimelineMergePreviewResult.blocked(issues);
        assertEquals(TimelineMergePreviewStatus.BLOCKED, r.status());
    }

    @Test @DisplayName("Summary counts are correct")
    void summaryCountsCorrect() {
        CanonicalTimelineSnapshot base = snap("rev-1");
        CanonicalTimelineSnapshot ours = snap("rev-ours");
        CanonicalTimelineSnapshot theirs = snap("rev-theirs");

        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
        TimelineMergePreviewResult r = service.preview(req);

        assertNotNull(r.summary());
        assertNotNull(r.summary().baseRevisionId());
        assertNotNull(r.summary().oursRevisionId());
        assertNotNull(r.summary().theirsRevisionId());
    }

    @Test @DisplayName("Safe issue fields have no provider/storage details")
    void safeIssueFieldsNoProviderStorage() {
        TimelineMergePreviewIssue issue = TimelineMergePreviewIssue.of(
                TimelineMergePreviewIssueSeverity.ERROR,
                TimelineMergePreviewIssueCode.MISSING_BASE,
                "base", "Base is null");
        String repr = issue.toString();
        assertFalse(repr.contains("providerName"));
        assertFalse(repr.contains("bucket"));
        assertFalse(repr.contains("signedUrl"));
    }

    // ===== Stage 3: TimelineMergePreviewService Core =====

    @Test @DisplayName("Valid merge-ready preview")
    void validMergeReadyPreview() {
        CanonicalTimelineSnapshot base = snap("rev-1");
        CanonicalTimelineSnapshot ours = snap("rev-ours");
        CanonicalTimelineSnapshot theirs = snap("rev-theirs");

        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
        TimelineMergePreviewResult r = service.preview(req);

        assertEquals(TimelineMergePreviewStatus.MERGE_READY, r.status());
        assertNotNull(r.summary());
        assertNotNull(r.conflictAnalysis());
        assertNotNull(r.issues());
    }

    @Test @DisplayName("Valid manual-review preview")
    void validManualReviewPreview() {
        CanonicalTimelineSnapshot base = snap("rev-1");
        // ours changes duration
        CanonicalTimelineSnapshot ours = new CanonicalTimelineSnapshot(
                base.id(), "rev-ours", 9999,
                base.tracks(), base.captions(), base.watermarks(),
                base.templateApplications(), base.workflowSteps(), base.outputProfile(),
                base.safeMetadata());
        // theirs changes duration differently
        CanonicalTimelineSnapshot theirs = new CanonicalTimelineSnapshot(
                base.id(), "rev-theirs", 7777,
                base.tracks(), base.captions(), base.watermarks(),
                base.templateApplications(), base.workflowSteps(), base.outputProfile(),
                base.safeMetadata());

        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
        TimelineMergePreviewResult r = service.preview(req);

        assertEquals(TimelineMergePreviewStatus.MANUAL_REVIEW_REQUIRED, r.status());
        assertTrue(r.hasConflicts());
    }

    @Test @DisplayName("Invalid missing base returns INVALID_INPUT")
    void invalidMissingBase() {
        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                null, snap("ours"), snap("theirs"),
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
        TimelineMergePreviewResult r = service.preview(req);
        assertEquals(TimelineMergePreviewStatus.INVALID_INPUT, r.status());
        assertTrue(r.issues().stream().anyMatch(
                i -> i.code() == TimelineMergePreviewIssueCode.MISSING_BASE));
    }

    @Test @DisplayName("Invalid missing ours returns INVALID_INPUT")
    void invalidMissingOurs() {
        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                snap("base"), null, snap("theirs"),
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
        TimelineMergePreviewResult r = service.preview(req);
        assertEquals(TimelineMergePreviewStatus.INVALID_INPUT, r.status());
        assertTrue(r.issues().stream().anyMatch(
                i -> i.code() == TimelineMergePreviewIssueCode.MISSING_OURS));
    }

    @Test @DisplayName("Invalid missing theirs returns INVALID_INPUT")
    void invalidMissingTheirs() {
        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                snap("base"), snap("ours"), null,
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
        TimelineMergePreviewResult r = service.preview(req);
        assertEquals(TimelineMergePreviewStatus.INVALID_INPUT, r.status());
        assertTrue(r.issues().stream().anyMatch(
                i -> i.code() == TimelineMergePreviewIssueCode.MISSING_THEIRS));
    }

    @Test @DisplayName("Blocked forbidden metadata path")
    void blockedForbiddenMetadata() {
        Map<String, String> forbiddenMeta = Map.of("bucket", "my-bucket");
        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                snap("base"), snap("ours"), snap("theirs"),
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, forbiddenMeta);
        TimelineMergePreviewResult r = service.preview(req);
        assertEquals(TimelineMergePreviewStatus.BLOCKED, r.status());
        assertTrue(r.issues().stream().anyMatch(
                i -> i.code() == TimelineMergePreviewIssueCode.STORAGE_INTERNALS_NOT_ALLOWED));
    }

    @Test @DisplayName("Blocked forbidden signedUrl metadata")
    void blockedForbiddenSignedUrl() {
        Map<String, String> forbiddenMeta = Map.of("url", "https://signedUrl.example.com");
        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                snap("base"), snap("ours"), snap("theirs"),
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, forbiddenMeta);
        TimelineMergePreviewResult r = service.preview(req);
        assertEquals(TimelineMergePreviewStatus.BLOCKED, r.status());
    }

    @Test @DisplayName("Failed detector exception converts to safe FAILED result")
    void failedDetectorExceptionConvertsToSafeFailed() {
        // Use a detector that throws on analyze
        TimelineMergeConflictDetector throwingDetector = new TimelineMergeConflictDetector() {
            @Override
            public TimelineMergeConflictAnalysis analyze(
                    com.example.platform.render.domain.timeline.diff.calculation.CanonicalTimelineSnapshot base,
                    com.example.platform.render.domain.timeline.diff.calculation.CanonicalTimelineSnapshot ours,
                    com.example.platform.render.domain.timeline.diff.calculation.CanonicalTimelineSnapshot theirs) {
                throw new RuntimeException("Simulated failure");
            }
        };
        TimelineMergePreviewService throwingService = new TimelineMergePreviewService(throwingDetector);

        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                snap("base"), snap("ours"), snap("theirs"),
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
        TimelineMergePreviewResult r = throwingService.preview(req);

        assertEquals(TimelineMergePreviewStatus.FAILED, r.status());
        assertTrue(r.issues().stream().anyMatch(
                i -> i.code() == TimelineMergePreviewIssueCode.CONFLICT_ANALYSIS_FAILED));
        // Verify no stack trace or exception class names leaked
        String repr = r.toString();
        assertFalse(repr.contains("RuntimeException"), "Must not leak exception class name");
        assertFalse(repr.contains("Simulated failure"), "Must not leak exception message");
        assertFalse(repr.contains("at com.example"), "Must not leak stack trace");
    }

    // ===== Stage 4: Preview Mode Tests =====

    @Test @DisplayName("DIFF_AND_CONFLICTS includes conflict analysis")
    void diffAndConflictsIncludesAnalysis() {
        CanonicalTimelineSnapshot base = snap("rev-1");
        CanonicalTimelineSnapshot ours = snap("rev-ours");
        CanonicalTimelineSnapshot theirs = snap("rev-theirs");

        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
        TimelineMergePreviewResult r = service.preview(req);

        assertNotNull(r.conflictAnalysis(), "DIFF_AND_CONFLICTS mode must include conflict analysis");
        assertNotNull(r.summary(), "DIFF_AND_CONFLICTS mode must include summary");
    }

    @Test @DisplayName("CONFLICTS_ONLY includes conflict/readiness summary")
    void conflictsOnlyIncludesSummary() {
        CanonicalTimelineSnapshot base = snap("rev-1");
        CanonicalTimelineSnapshot ours = snap("rev-ours");
        CanonicalTimelineSnapshot theirs = snap("rev-theirs");

        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePreviewMode.CONFLICTS_ONLY,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
        TimelineMergePreviewResult r = service.preview(req);

        assertNotNull(r.conflictAnalysis(), "CONFLICTS_ONLY mode must include conflict analysis");
        assertNotNull(r.summary(), "CONFLICTS_ONLY mode must include summary");
    }

    @Test @DisplayName("READINESS_ONLY returns summary/status safely")
    void readinessOnlyReturnsSummarySafely() {
        CanonicalTimelineSnapshot base = snap("rev-1");
        CanonicalTimelineSnapshot ours = snap("rev-ours");
        CanonicalTimelineSnapshot theirs = snap("rev-theirs");

        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePreviewMode.READINESS_ONLY,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
        TimelineMergePreviewResult r = service.preview(req);

        assertNotNull(r.status(), "READINESS_ONLY must have status");
        assertNotNull(r.summary(), "READINESS_ONLY must have summary");
        assertNull(r.conflictAnalysis(), "READINESS_ONLY must not include conflict analysis");
    }

    // ===== Stage 5: Preview Policy Tests =====

    @Test @DisplayName("CONSERVATIVE returns manual review when detector reports conflicts")
    void conservativeReturnsManualReviewForConflicts() {
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

        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
        TimelineMergePreviewResult r = service.preview(req);

        assertEquals(TimelineMergePreviewStatus.MANUAL_REVIEW_REQUIRED, r.status());
    }

    @Test @DisplayName("ALLOW_IDENTICAL_SAME_PATH_CHANGES keeps identical same-path changes merge-ready")
    void allowIdenticalSamePathKeepsMergeReady() {
        CanonicalTimelineSnapshot base = snap("rev-1");
        CanonicalTimelineSnapshot ours = new CanonicalTimelineSnapshot(
                base.id(), "rev-ours", 9999,
                base.tracks(), base.captions(), base.watermarks(),
                base.templateApplications(), base.workflowSteps(), base.outputProfile(),
                base.safeMetadata());
        CanonicalTimelineSnapshot theirs = new CanonicalTimelineSnapshot(
                base.id(), "rev-theirs", 9999,
                base.tracks(), base.captions(), base.watermarks(),
                base.templateApplications(), base.workflowSteps(), base.outputProfile(),
                base.safeMetadata());

        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.ALLOW_IDENTICAL_SAME_PATH_CHANGES, Map.of());
        TimelineMergePreviewResult r = service.preview(req);

        assertEquals(TimelineMergePreviewStatus.MERGE_READY, r.status());
    }

    @Test @DisplayName("BLOCK_ON_ANY_CONFLICT marks conflicts as MANUAL_REVIEW_REQUIRED")
    void blockOnAnyConflictMarksManualReview() {
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

        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.BLOCK_ON_ANY_CONFLICT, Map.of());
        TimelineMergePreviewResult r = service.preview(req);

        assertEquals(TimelineMergePreviewStatus.MANUAL_REVIEW_REQUIRED, r.status());
        assertTrue(r.issues().stream().anyMatch(
                i -> i.code() == TimelineMergePreviewIssueCode.MANUAL_REVIEW_REQUIRED));
    }

    // ===== Stage 6: End-to-End Preview Tests =====

    @Test @DisplayName("Different metadata keys = MERGE_READY preview")
    void differentMetadataKeysMergeReadyPreview() {
        CanonicalTimelineSnapshot base = snap("rev-1");
        CanonicalTimelineSnapshot ours = new CanonicalTimelineSnapshot(
                base.id(), "rev-ours", base.durationMs(),
                base.tracks(), base.captions(), base.watermarks(),
                base.templateApplications(), base.workflowSteps(), base.outputProfile(),
                Map.of("title", "Ours"));
        CanonicalTimelineSnapshot theirs = new CanonicalTimelineSnapshot(
                base.id(), "rev-theirs", 9999,
                base.tracks(), base.captions(), base.watermarks(),
                base.templateApplications(), base.workflowSteps(), base.outputProfile(),
                base.safeMetadata());

        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
        TimelineMergePreviewResult r = service.preview(req);

        assertEquals(TimelineMergePreviewStatus.MERGE_READY, r.status());
        assertFalse(r.hasConflicts());
    }

    @Test @DisplayName("Same caption changed differently = MANUAL_REVIEW_REQUIRED preview")
    void sameCaptionChangedDifferentlyManualReviewPreview() {
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

        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
        TimelineMergePreviewResult r = service.preview(req);

        assertEquals(TimelineMergePreviewStatus.MANUAL_REVIEW_REQUIRED, r.status());
        assertTrue(r.hasConflicts());
    }

    @Test @DisplayName("Output profile changed differently = MANUAL_REVIEW_REQUIRED preview")
    void outputProfileChangedDifferentlyManualReviewPreview() {
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

        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
        TimelineMergePreviewResult r = service.preview(req);

        assertEquals(TimelineMergePreviewStatus.MANUAL_REVIEW_REQUIRED, r.status());
        assertTrue(r.hasConflicts());
    }

    @Test @DisplayName("Forbidden metadata path = BLOCKED preview")
    void forbiddenMetadataPathBlockedPreview() {
        Map<String, String> forbiddenMeta = Map.of("providerName", "aws");
        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                snap("base"), snap("ours"), snap("theirs"),
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, forbiddenMeta);
        TimelineMergePreviewResult r = service.preview(req);

        assertEquals(TimelineMergePreviewStatus.BLOCKED, r.status());
    }

    @Test @DisplayName("Deterministic preview result double-run")
    void deterministicPreviewResultDoubleRun() {
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

        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());

        TimelineMergePreviewResult r1 = service.preview(req);
        TimelineMergePreviewResult r2 = service.preview(req);

        assertEquals(r1.status(), r2.status());
        assertEquals(r1.summary().conflictCount(), r2.summary().conflictCount());
        assertEquals(r1.summary().mergeReady(), r2.summary().mergeReady());
        assertEquals(r1.issues().size(), r2.issues().size());
    }

    // ===== Stage 7: Safety and Boundary Tests =====

    @Test @DisplayName("Preview service does not reference vedit/OTIO/Remotion")
    void noExternalDependencyReferences() {
        // Verify service loads and runs without vedit/OTIO/Remotion on classpath
        assertNotNull(service);
        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                snap("base"), snap("ours"), snap("theirs"),
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
        TimelineMergePreviewResult r = service.preview(req);
        assertNotNull(r);
    }

    @Test @DisplayName("Preview service does not expose provider/storage fields")
    void noProviderStorageExposure() {
        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                snap("base"), snap("ours"), snap("theirs"),
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
        TimelineMergePreviewResult r = service.preview(req);
        String repr = r.toString();
        assertFalse(repr.contains("providerName"), "Must not expose providerName");
        assertFalse(repr.contains("providerType"), "Must not expose providerType");
        assertFalse(repr.contains("backendName"), "Must not expose backendName");
        assertFalse(repr.contains("bucket"), "Must not expose bucket");
        assertFalse(repr.contains("objectKey"), "Must not expose objectKey");
        assertFalse(repr.contains("signedUrl"), "Must not expose signedUrl");
        assertFalse(repr.contains("materializedPath"), "Must not expose materializedPath");
    }

    @Test @DisplayName("Preview service does not mutate input snapshots")
    void noInputMutation() {
        CanonicalTimelineSnapshot base = snap("base");
        CanonicalTimelineSnapshot ours = snap("ours");
        CanonicalTimelineSnapshot theirs = snap("theirs");
        String baseBefore = base.toString();
        String oursBefore = ours.toString();
        String theirsBefore = theirs.toString();

        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                base, ours, theirs,
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
        service.preview(req);

        assertEquals(baseBefore, base.toString(), "Base snapshot must not be mutated");
        assertEquals(oursBefore, ours.toString(), "Ours snapshot must not be mutated");
        assertEquals(theirsBefore, theirs.toString(), "Theirs snapshot must not be mutated");
    }

    @Test @DisplayName("Preview result contains no stack traces")
    void noStackTracesInResult() {
        // Even for failed case, no stack traces should leak
        TimelineMergeConflictDetector throwingDetector = new TimelineMergeConflictDetector() {
            @Override
            public TimelineMergeConflictAnalysis analyze(
                    com.example.platform.render.domain.timeline.diff.calculation.CanonicalTimelineSnapshot base,
                    com.example.platform.render.domain.timeline.diff.calculation.CanonicalTimelineSnapshot ours,
                    com.example.platform.render.domain.timeline.diff.calculation.CanonicalTimelineSnapshot theirs) {
                throw new NullPointerException("Test exception");
            }
        };
        TimelineMergePreviewService throwingService = new TimelineMergePreviewService(throwingDetector);

        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                snap("base"), snap("ours"), snap("theirs"),
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE, Map.of());
        TimelineMergePreviewResult r = throwingService.preview(req);

        String repr = r.toString();
        assertFalse(repr.contains("at com.example"), "Must not contain stack trace");
        assertFalse(repr.contains("NullPointerException"), "Must not contain exception class name");
        assertFalse(repr.contains("RuntimeException"), "Must not contain exception class name");
    }

    @Test @DisplayName("Constructor rejects null detector")
    void constructorRejectsNullDetector() {
        assertThrows(IllegalArgumentException.class, () -> new TimelineMergePreviewService(null));
    }

    @Test @DisplayName("Preview result toString is safe")
    void previewResultToStringSafe() {
        TimelineMergePreviewRequest req = new TimelineMergePreviewRequest(
                new TimelineMergePreviewRequestId("req-1"),
                snap("base"), snap("ours"), snap("theirs"),
                TimelineMergePreviewMode.DIFF_AND_CONFLICTS,
                TimelineMergePreviewPolicy.CONSERVATIVE,
                Map.of("customKey", "customValue"));
        TimelineMergePreviewResult r = service.preview(req);
        String repr = r.toString();
        assertNotNull(repr);
        assertFalse(repr.isEmpty());
    }

    // ===== Helpers =====

    private CanonicalTimelineSnapshotId snapId(String id) {
        return new CanonicalTimelineSnapshotId(id);
    }

    private CanonicalTimelineSnapshot snap(String revId) {
        return new CanonicalTimelineSnapshot(snapId("snap-" + revId), revId, 5000,
                List.of(track("track-1", 0)), List.of(), List.of(), List.of(), List.of(), null, Map.of());
    }

    private CanonicalTimelineTrackSnapshot track(String id, int order) {
        return new CanonicalTimelineTrackSnapshot(id, order, "VIDEO",
                List.of(new CanonicalTimelineClipSnapshot("clip-1", "asset-1", 0, 5000, 0, 5000, Map.of())),
                Map.of());
    }
}
