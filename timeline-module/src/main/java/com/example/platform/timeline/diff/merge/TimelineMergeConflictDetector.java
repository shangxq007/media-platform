package com.example.platform.timeline.diff.merge;

import com.example.platform.timeline.diff.TimelineChangeOperation;
import com.example.platform.timeline.diff.TimelineChangeType;
import com.example.platform.timeline.diff.TimelineDiff;
import com.example.platform.timeline.diff.calculation.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Three-way merge conflict detector. Pure in-memory, side-effect free.
 * Uses CanonicalTimelineDiffCalculator to compute diffs, then compares
 * operations by path to detect divergent changes.
 *
 * <p>Does not merge, resolve conflicts, apply patches, or call services.
 * Internal domain service. Provider-neutral, storage-neutral.</p>
 */
public class TimelineMergeConflictDetector {

    private static final Set<String> FORBIDDEN_PATH_KEYWORDS = Set.of(
            "bucket", "objectKey", "signedUrl", "rootPath", "relativePath",
            "materializedPath", "providerName", "providerType", "backendName",
            "executionEnvironment", "command", "process");

    private final CanonicalTimelineDiffCalculator diffCalculator;

    public TimelineMergeConflictDetector() {
        this.diffCalculator = new CanonicalTimelineDiffCalculator();
    }

    public TimelineMergeConflictDetector(CanonicalTimelineDiffCalculator diffCalculator) {
        this.diffCalculator = diffCalculator != null
                ? diffCalculator : new CanonicalTimelineDiffCalculator();
    }

    /**
     * Analyze three-way merge conflict between base, ours, and theirs snapshots.
     */
    public TimelineMergeConflictAnalysis analyze(
            CanonicalTimelineSnapshot base,
            CanonicalTimelineSnapshot ours,
            CanonicalTimelineSnapshot theirs) {

        // Validate inputs
        List<TimelineMergeConflictIssue> inputIssues = validateInputs(base, ours, theirs);
        if (!inputIssues.isEmpty()) {
            return new TimelineMergeConflictAnalysis(
                    new TimelineMergeConflictAnalysisId("analysis-invalid"),
                    base != null ? base.revisionId() : null,
                    ours != null ? ours.revisionId() : null,
                    theirs != null ? theirs.revisionId() : null,
                    null, null, List.of(),
                    TimelineMergeReadiness.invalidInput(inputIssues),
                    TimelineMergeConflictSummary.of(0, 0, 0, 0, 0),
                    Map.of());
        }

        // Compute diffs
        CanonicalTimelineDiffCalculationResult oursResult = diffCalculator.calculate(base, ours);
        CanonicalTimelineDiffCalculationResult theirsResult = diffCalculator.calculate(base, theirs);

        if (!oursResult.successful() || !theirsResult.successful()) {
            List<TimelineMergeConflictIssue> diffIssues = new ArrayList<>();
            if (!oursResult.successful()) {
                diffIssues.add(issue(TimelineMergeConflictIssueSeverity.BLOCKING,
                        TimelineMergeConflictIssueCode.UNSUPPORTED_CHANGE_TYPE,
                        "oursDiff", "Failed to compute ours diff: " + oursResult.warnings()));
            }
            if (!theirsResult.successful()) {
                diffIssues.add(issue(TimelineMergeConflictIssueSeverity.BLOCKING,
                        TimelineMergeConflictIssueCode.UNSUPPORTED_CHANGE_TYPE,
                        "theirsDiff", "Failed to compute theirs diff: " + theirsResult.warnings()));
            }
            return new TimelineMergeConflictAnalysis(
                    new TimelineMergeConflictAnalysisId("analysis-diff-failed"),
                    base.revisionId(), ours.revisionId(), theirs.revisionId(),
                    null, null, List.of(),
                    TimelineMergeReadiness.blocked(diffIssues),
                    TimelineMergeConflictSummary.of(0, 0, 0, 0, 0),
                    Map.of());
        }

        TimelineDiff oursDiff = oursResult.diff();
        TimelineDiff theirsDiff = theirsResult.diff();

        // Check for forbidden path keywords in operations
        List<TimelineMergeConflictIssue> forbiddenIssues = new ArrayList<>();
        checkForbiddenPaths(oursDiff.operations(), "ours", forbiddenIssues);
        checkForbiddenPaths(theirsDiff.operations(), "theirs", forbiddenIssues);
        if (!forbiddenIssues.isEmpty()) {
            return new TimelineMergeConflictAnalysis(
                    new TimelineMergeConflictAnalysisId("analysis-blocked"),
                    base.revisionId(), ours.revisionId(), theirs.revisionId(),
                    oursDiff, theirsDiff, List.of(),
                    TimelineMergeReadiness.blocked(forbiddenIssues),
                    TimelineMergeConflictSummary.of(
                            oursDiff.operations().size(), theirsDiff.operations().size(),
                            0, forbiddenIssues.size(), 0),
                    Map.of());
        }

        // Compare operations
        List<TimelineConflict> conflicts = new ArrayList<>();
        List<TimelineMergeConflictIssue> issues = new ArrayList<>();
        compareOperations(oursDiff, theirsDiff, conflicts, issues);

        // Determine readiness
        boolean hasBlocking = conflicts.stream().anyMatch(TimelineConflict::resolutionRequired);

        TimelineMergeReadiness readiness;
        if (hasBlocking || !conflicts.isEmpty()) {
            readiness = TimelineMergeReadiness.manualReview(issues);
        } else {
            readiness = TimelineMergeReadiness.ready();
        }

        // Sort conflicts deterministically: resolution-required first,
        // then conflict type ordinal, conflict id, message
        conflicts.sort(Comparator
                .comparingInt((TimelineConflict c) -> c.resolutionRequired() ? 0 : 1)
                .thenComparing(c -> c.conflictType().ordinal())
                .thenComparing(c -> c.conflictId())
                .thenComparing(c -> c.message() != null ? c.message() : ""));

        int blockingCount = (int) conflicts.stream()
                .filter(TimelineConflict::resolutionRequired).count();
        int manualReviewCount = conflicts.size() - blockingCount;

        TimelineMergeConflictSummary summary = TimelineMergeConflictSummary.of(
                oursDiff.operations().size(), theirsDiff.operations().size(),
                conflicts.size(), blockingCount, manualReviewCount);

        return new TimelineMergeConflictAnalysis(
                new TimelineMergeConflictAnalysisId(
                        "analysis-" + base.revisionId() + "-" + ours.revisionId() + "-" + theirs.revisionId()),
                base.revisionId(), ours.revisionId(), theirs.revisionId(),
                oursDiff, theirsDiff, conflicts, readiness, summary, Map.of());
    }

    // --- Comparison logic ---

    private void compareOperations(
            TimelineDiff oursDiff, TimelineDiff theirsDiff,
            List<TimelineConflict> conflicts, List<TimelineMergeConflictIssue> issues) {

        Map<String, List<TimelineChangeOperation>> oursByPath = groupByPath(oursDiff.operations());
        Map<String, List<TimelineChangeOperation>> theirsByPath = groupByPath(theirsDiff.operations());

        Set<String> allPaths = new LinkedHashSet<>();
        allPaths.addAll(oursByPath.keySet());
        allPaths.addAll(theirsByPath.keySet());

        for (String path : allPaths) {
            List<TimelineChangeOperation> oursOps = oursByPath.getOrDefault(path, List.of());
            List<TimelineChangeOperation> theirsOps = theirsByPath.getOrDefault(path, List.of());

            if (!oursOps.isEmpty() && !theirsOps.isEmpty()) {
                // Both sides touched this path
                compareSamePath(path, oursOps, theirsOps, conflicts, issues);
            } else if (!oursOps.isEmpty()) {
                // Only ours touched this path — check if theirs removed the target
                checkRemovalVsModification(path, oursOps, theirsByPath, conflicts, issues, TimelineMergeSide.OURS);
            } else {
                // Only theirs touched this path — check if ours removed the target
                checkRemovalVsModification(path, theirsOps, oursByPath, conflicts, issues, TimelineMergeSide.THEIRS);
            }
        }
    }

    private void compareSamePath(String path,
                                  List<TimelineChangeOperation> oursOps,
                                  List<TimelineChangeOperation> theirsOps,
                                  List<TimelineConflict> conflicts,
                                  List<TimelineMergeConflictIssue> issues) {
        // Check if identical change
        if (areIdenticalChanges(oursOps, theirsOps)) {
            // Same change to same path — merge ready (no conflict)
            return;
        }

        // Divergent change to same path
        TimelineChangeType oursType = oursOps.get(0).type();
        TimelineChangeType theirsType = theirsOps.get(0).type();
        TimelineConflictType conflictType = mapToConflictType(oursType, path);
        TimelineMergeConflictIssueCode issueCode = mapToIssueCode(oursType, path);

        String message = "Divergent change on path: " + path
                + " (ours=" + oursType + ", theirs=" + theirsType + ")";

        conflicts.add(new TimelineConflict(
                "conflict-" + path.hashCode(),
                entityRefFor(path),
                conflictType,
                null,
                null,
                true,
                message));

        issues.add(issue(TimelineMergeConflictIssueSeverity.BLOCKING,
                issueCode, path, message));
    }

    private void checkRemovalVsModification(String path,
                                             List<TimelineChangeOperation> modOps,
                                             Map<String, List<TimelineChangeOperation>> otherByPath,
                                             List<TimelineConflict> conflicts,
                                             List<TimelineMergeConflictIssue> issues,
                                             TimelineMergeSide modSide) {
        // Check if the parent entity was removed by the other side
        String parentPath = extractParentPath(path);
        if (parentPath != null) {
            List<TimelineChangeOperation> parentOps = otherByPath.getOrDefault(parentPath, List.of());
            boolean parentRemoved = parentOps.stream()
                    .anyMatch(op -> op.type() == TimelineChangeType.TRACK_REMOVED
                            || op.type() == TimelineChangeType.CLIP_REMOVED);
            if (parentRemoved) {
                String message = "Target removed on " + (modSide == TimelineMergeSide.OURS ? "theirs" : "ours")
                        + " but modified on " + (modSide == TimelineMergeSide.OURS ? "ours" : "theirs")
                        + ": " + path;

                conflicts.add(new TimelineConflict(
                        "conflict-removal-" + path.hashCode(),
                        entityRefFor(path),
                        TimelineConflictType.UNKNOWN,
                        null,
                        null,
                        true,
                        message));

                issues.add(issue(TimelineMergeConflictIssueSeverity.BLOCKING,
                        TimelineMergeConflictIssueCode.TARGET_REMOVED_AND_MODIFIED, path, message));
            }
        }
    }

    // --- Helpers ---

    private boolean areIdenticalChanges(List<TimelineChangeOperation> oursOps,
                                         List<TimelineChangeOperation> theirsOps) {
        if (oursOps.size() != theirsOps.size()) return false;
        // Compare by type and after-value
        for (int i = 0; i < oursOps.size(); i++) {
            TimelineChangeOperation oo = oursOps.get(i);
            TimelineChangeOperation to = theirsOps.get(i);
            if (oo.type() != to.type()) return false;
            String oursAfter = oo.afterValue() != null ? oo.afterValue().stringValue() : null;
            String theirsAfter = to.afterValue() != null ? to.afterValue().stringValue() : null;
            if (!Objects.equals(oursAfter, theirsAfter)) return false;
        }
        return true;
    }

    private Map<String, List<TimelineChangeOperation>> groupByPath(List<TimelineChangeOperation> ops) {
        Map<String, List<TimelineChangeOperation>> map = new LinkedHashMap<>();
        if (ops != null) {
            for (TimelineChangeOperation op : ops) {
                map.computeIfAbsent(op.path().value(), k -> new ArrayList<>()).add(op);
            }
        }
        return map;
    }

    private String extractParentPath(String path) {
        // e.g. "timeline.tracks.track-1.clips.clip-1.start" -> "timeline.tracks.track-1.clips.clip-1"
        // or "timeline.captions.cap-1.text" -> "timeline.captions.cap-1"
        int lastDot = path.lastIndexOf('.');
        if (lastDot > 0) {
            String candidate = path.substring(0, lastDot);
            // Only return parent if it looks like a sub-entity path
            if (candidate.contains(".") && !candidate.endsWith("clips") && !candidate.endsWith("tracks")) {
                return candidate;
            }
        }
        return null;
    }

    private TimelineConflictType mapToConflictType(TimelineChangeType changeType, String path) {
        return switch (changeType) {
            case TRACK_REORDERED -> TimelineConflictType.TRACK_STRUCTURE_CONFLICT;
            case CLIP_MOVED, CLIP_TRIMMED -> TimelineConflictType.CLIP_RANGE_CONFLICT;
            case CLIP_REMOVED -> TimelineConflictType.CLIP_REMOVED_AND_MODIFIED;
            case ASSET_BINDING_CHANGED -> TimelineConflictType.CLIP_RANGE_CONFLICT;
            case CAPTION_SEGMENT_CHANGED, TEXT_STYLE_CHANGED, WATERMARK_CHANGED,
                    TEXT_ELEMENT_CHANGED, TEMPLATE_PARAMETER_CHANGED,
                    TEMPLATE_PROFILE_CHANGED -> TimelineConflictType.EFFECT_CONFLICT;
            case COMPOSITE_CHILD_TEMPLATE_CHANGED, WORKFLOW_APPLY_TEMPLATE_STEP_CHANGED ->
                    TimelineConflictType.TRACK_STRUCTURE_CONFLICT;
            case OUTPUT_PROFILE_CHANGED, METADATA_CHANGED, TIMELINE_DURATION_CHANGED,
                    AUDIO_MIX_CHANGED, BRAND_STYLE_CHANGED -> TimelineConflictType.METADATA_CONFLICT;
            default -> TimelineConflictType.UNKNOWN;
        };
    }

    private TimelineMergeConflictIssueCode mapToIssueCode(TimelineChangeType changeType, String path) {
        return switch (changeType) {
            case TRACK_REORDERED -> TimelineMergeConflictIssueCode.TRACK_ORDER_DIVERGENCE;
            case CLIP_MOVED, CLIP_TRIMMED -> TimelineMergeConflictIssueCode.CLIP_TIMING_OVERLAP;
            case CAPTION_SEGMENT_CHANGED -> TimelineMergeConflictIssueCode.CAPTION_TEXT_DIVERGENCE;
            case TEXT_STYLE_CHANGED -> TimelineMergeConflictIssueCode.TEXT_STYLE_DIVERGENCE;
            case WATERMARK_CHANGED -> TimelineMergeConflictIssueCode.WATERMARK_POSITION_DIVERGENCE;
            case TEXT_ELEMENT_CHANGED -> TimelineMergeConflictIssueCode.TEXT_ELEMENT_DIVERGENCE;
            case TEMPLATE_PARAMETER_CHANGED -> TimelineMergeConflictIssueCode.TEMPLATE_PARAMETER_DIVERGENCE;
            case TEMPLATE_PROFILE_CHANGED -> TimelineMergeConflictIssueCode.TEMPLATE_PROFILE_DIVERGENCE;
            case WORKFLOW_APPLY_TEMPLATE_STEP_CHANGED -> TimelineMergeConflictIssueCode.WORKFLOW_STEP_DIVERGENCE;
            case OUTPUT_PROFILE_CHANGED -> TimelineMergeConflictIssueCode.OUTPUT_PROFILE_DIVERGENCE;
            case METADATA_CHANGED -> TimelineMergeConflictIssueCode.SAME_PATH_DIVERGENT_CHANGE;
            default -> TimelineMergeConflictIssueCode.SAME_PATH_DIVERGENT_CHANGE;
        };
    }

    private List<TimelineMergeConflictIssue> validateInputs(
            CanonicalTimelineSnapshot base,
            CanonicalTimelineSnapshot ours,
            CanonicalTimelineSnapshot theirs) {
        List<TimelineMergeConflictIssue> issues = new ArrayList<>();
        if (base == null) {
            issues.add(issue(TimelineMergeConflictIssueSeverity.BLOCKING,
                    TimelineMergeConflictIssueCode.MISSING_BASE, "base", "Base snapshot must not be null"));
        }
        if (ours == null) {
            issues.add(issue(TimelineMergeConflictIssueSeverity.BLOCKING,
                    TimelineMergeConflictIssueCode.MISSING_OURS, "ours", "Ours snapshot must not be null"));
        }
        if (theirs == null) {
            issues.add(issue(TimelineMergeConflictIssueSeverity.BLOCKING,
                    TimelineMergeConflictIssueCode.MISSING_THEIRS, "theirs", "Theirs snapshot must not be null"));
        }
        return issues;
    }

    private void checkForbiddenPaths(List<TimelineChangeOperation> ops, String side,
                                      List<TimelineMergeConflictIssue> issues) {
        if (ops == null) return;
        for (TimelineChangeOperation op : ops) {
            if (op.path() != null && op.path().value() != null) {
                String path = op.path().value();
                for (String keyword : FORBIDDEN_PATH_KEYWORDS) {
                    if (path.contains(keyword)) {
                        issues.add(issue(TimelineMergeConflictIssueSeverity.BLOCKING,
                                TimelineMergeConflictIssueCode.STORAGE_INTERNALS_NOT_ALLOWED,
                                path, "Path on " + side + " contains forbidden keyword: " + keyword));
                    }
                }
            }
        }
    }

    /** Derive the merge entity reference from a canonical path. */
    private EntityRef entityRefFor(String path) {
        if (path.contains(".clips.")) {
            return new EntityRef(EntityKind.CLIP, path);
        }
        if (path.contains("tracks.") || path.startsWith("timeline.tracks")) {
            return new EntityRef(EntityKind.TRACK, path);
        }
        if (path.contains("captions.")) {
            return new EntityRef(EntityKind.SUBTITLE_TRACK, path);
        }
        if (path.contains("audioMix") || path.contains("audio_mix")) {
            return new EntityRef(EntityKind.AUDIO_MIX, path);
        }
        return new EntityRef(EntityKind.TRACK, path);
    }

    private TimelineMergeConflictIssue issue(
            TimelineMergeConflictIssueSeverity severity,
            TimelineMergeConflictIssueCode code,
            String field, String message) {
        return new TimelineMergeConflictIssue(severity, code, field, message, Map.of());
    }
}
