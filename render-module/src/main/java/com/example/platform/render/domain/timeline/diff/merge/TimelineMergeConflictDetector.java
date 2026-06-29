package com.example.platform.render.domain.timeline.diff.merge;

import com.example.platform.render.domain.timeline.diff.*;
import com.example.platform.render.domain.timeline.diff.calculation.*;
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
        boolean hasBlocking = conflicts.stream().anyMatch(c -> c.severity() == TimelineConflictSeverity.BLOCKING);
        boolean hasWarning = conflicts.stream().anyMatch(c -> c.severity() == TimelineConflictSeverity.WARNING);

        TimelineMergeReadiness readiness;
        if (hasBlocking) {
            readiness = TimelineMergeReadiness.manualReview(issues);
        } else if (hasWarning || !conflicts.isEmpty()) {
            readiness = TimelineMergeReadiness.manualReview(issues);
        } else {
            readiness = TimelineMergeReadiness.ready();
        }

        // Sort conflicts deterministically: severity desc, type ordinal, path, issue code
        conflicts.sort(Comparator
                .comparingInt((TimelineConflict c) -> severityOrder(c.severity())).reversed()
                .thenComparing(c -> c.type().ordinal())
                .thenComparing(c -> c.path() != null ? c.path().value() : "")
                .thenComparing(c -> c.message() != null ? c.message() : ""));

        int blockingCount = (int) conflicts.stream()
                .filter(c -> c.severity() == TimelineConflictSeverity.BLOCKING).count();
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
                new TimelineConflictId("conflict-" + path.hashCode()),
                conflictType, TimelineConflictSeverity.BLOCKING,
                new TimelineChangePath(path), message, Map.of()));

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
                        new TimelineConflictId("conflict-removal-" + path.hashCode()),
                        TimelineConflictType.UNKNOWN_CONFLICT,
                        TimelineConflictSeverity.BLOCKING,
                        new TimelineChangePath(path), message, Map.of()));

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
        // e.g. "timeline.tracks.track-1.clips.clip-1.startMs" -> "timeline.tracks.track-1.clips.clip-1"
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
            case TRACK_REORDERED -> TimelineConflictType.TRACK_ORDER_CONFLICT;
            case CLIP_MOVED, CLIP_TRIMMED -> TimelineConflictType.CLIP_TIMING_CONFLICT;
            case ASSET_BINDING_CHANGED -> TimelineConflictType.ASSET_BINDING_CONFLICT;
            case CAPTION_SEGMENT_CHANGED -> TimelineConflictType.CAPTION_TEXT_CONFLICT;
            case TEXT_STYLE_CHANGED -> TimelineConflictType.TEXT_STYLE_CONFLICT;
            case WATERMARK_CHANGED -> TimelineConflictType.WATERMARK_POSITION_CONFLICT;
            case TEMPLATE_PARAMETER_CHANGED -> TimelineConflictType.TEMPLATE_PARAMETER_CONFLICT;
            case TEMPLATE_PROFILE_CHANGED -> TimelineConflictType.TEMPLATE_PARAMETER_CONFLICT;
            case COMPOSITE_CHILD_TEMPLATE_CHANGED -> TimelineConflictType.COMPOSITE_TEMPLATE_CHILD_CONFLICT;
            case WORKFLOW_APPLY_TEMPLATE_STEP_CHANGED -> TimelineConflictType.WORKFLOW_STEP_CONFLICT;
            case OUTPUT_PROFILE_CHANGED -> TimelineConflictType.OUTPUT_PROFILE_CONFLICT;
            case METADATA_CHANGED -> TimelineConflictType.UNKNOWN_CONFLICT;
            default -> TimelineConflictType.UNKNOWN_CONFLICT;
        };
    }

    private TimelineMergeConflictIssueCode mapToIssueCode(TimelineChangeType changeType, String path) {
        return switch (changeType) {
            case TRACK_REORDERED -> TimelineMergeConflictIssueCode.TRACK_ORDER_DIVERGENCE;
            case CLIP_MOVED, CLIP_TRIMMED -> TimelineMergeConflictIssueCode.CLIP_TIMING_OVERLAP;
            case CAPTION_SEGMENT_CHANGED -> TimelineMergeConflictIssueCode.CAPTION_TEXT_DIVERGENCE;
            case TEXT_STYLE_CHANGED -> TimelineMergeConflictIssueCode.TEXT_STYLE_DIVERGENCE;
            case WATERMARK_CHANGED -> TimelineMergeConflictIssueCode.WATERMARK_POSITION_DIVERGENCE;
            case TEMPLATE_PARAMETER_CHANGED -> TimelineMergeConflictIssueCode.TEMPLATE_PARAMETER_DIVERGENCE;
            case TEMPLATE_PROFILE_CHANGED -> TimelineMergeConflictIssueCode.TEMPLATE_PROFILE_DIVERGENCE;
            case WORKFLOW_APPLY_TEMPLATE_STEP_CHANGED -> TimelineMergeConflictIssueCode.WORKFLOW_STEP_DIVERGENCE;
            case OUTPUT_PROFILE_CHANGED -> TimelineMergeConflictIssueCode.OUTPUT_PROFILE_DIVERGENCE;
            case METADATA_CHANGED -> TimelineMergeConflictIssueCode.SAME_PATH_DIVERGENT_CHANGE;
            default -> TimelineMergeConflictIssueCode.SAME_PATH_DIVERGENT_CHANGE;
        };
    }

    private int severityOrder(TimelineConflictSeverity severity) {
        return switch (severity) {
            case BLOCKING -> 4;
            case WARNING -> 2;
            case INFO -> 1;
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

    private TimelineMergeConflictIssue issue(
            TimelineMergeConflictIssueSeverity severity,
            TimelineMergeConflictIssueCode code,
            String field, String message) {
        return new TimelineMergeConflictIssue(severity, code, field, message, Map.of());
    }
}
