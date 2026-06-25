package com.example.platform.render.app.timeline;

import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.domain.timeline.internal.*;
import com.example.platform.render.domain.timeline.internal.TimelineMergeResult.MergeStatus;
import com.example.platform.render.domain.timeline.internal.TimelineResolutionIntent.ResolutionMode;
import com.example.platform.shared.Ids;
import java.time.OffsetDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Three-way timeline merge service with true conflict-aware payload construction.
 *
 * <p>Computes base→source and base→target diffs, detects conflicts, and produces
 * a merged payload by combining non-conflicting changes from both branches.</p>
 *
 * <p>For conflicting merges, accepts {@link TimelineResolutionIntent} objects to
 * resolve individual conflicts (USE_SOURCE / USE_TARGET) and re-attempt the merge.</p>
 */
@Service
public class TimelineMergeService {

    private static final Logger log = LoggerFactory.getLogger(TimelineMergeService.class);

    private final TimelineRevisionService revisionService;
    private final TimelineRevisionRepository revisionRepository;
    private final TimelineSnapshotService snapshotService;
    private final TimelineSemanticDiffService semanticDiffService;
    private final TimelineConflictDetector conflictDetector;
    private final TimelineConflictResolver conflictResolver;

    public TimelineMergeService(
            TimelineRevisionService revisionService,
            TimelineRevisionRepository revisionRepository,
            TimelineSnapshotService snapshotService,
            TimelineSemanticDiffService semanticDiffService,
            TimelineConflictDetector conflictDetector,
            TimelineConflictResolver conflictResolver) {
        this.revisionService = revisionService;
        this.revisionRepository = revisionRepository;
        this.snapshotService = snapshotService;
        this.semanticDiffService = semanticDiffService;
        this.conflictDetector = conflictDetector;
        this.conflictResolver = conflictResolver;
    }

    /**
     * Perform a three-way merge. Auto-merges non-conflicting changes.
     * Returns CONFLICTS if any conflicts exist — no revision is created in that case.
     */
    @Transactional
    public TimelineMergeResult threeWayMerge(TimelineMergeRequest request) {
        return threeWayMergeInternal(request, Map.of());
    }

    /**
     * Perform a three-way merge with explicit conflict resolutions.
     * Applies resolved conflicts (USE_SOURCE / USE_TARGET) and auto-merges the rest.
     */
    @Transactional
    public TimelineMergeResult threeWayMergeWithResolutions(
            TimelineMergeRequest request,
            Map<String, TimelineResolutionIntent> resolutions) {
        return threeWayMergeInternal(request, resolutions);
    }

    private TimelineMergeResult threeWayMergeInternal(
            TimelineMergeRequest request,
            Map<String, TimelineResolutionIntent> resolutions) {
        try {
            var baseRevision = loadRevision(request.baseRevisionId());
            var sourceRevision = loadRevision(request.sourceRevisionId());
            var targetRevision = loadRevision(request.targetRevisionId());

            String basePayload = loadPayload(baseRevision);
            String sourcePayload = loadPayload(sourceRevision);
            String targetPayload = loadPayload(targetRevision);

            SemanticDiffResult sourceDiff = semanticDiffService.diff(basePayload, sourcePayload);
            SemanticDiffResult targetDiff = semanticDiffService.diff(basePayload, targetPayload);

            if (sourceDiff.structurallyEqual() && targetDiff.structurallyEqual()) {
                return noOpResult(request);
            }

            boolean sourceOnly = sourceDiff.changes().isEmpty() || targetDiff.changes().isEmpty();
            boolean targetOnly = targetDiff.changes().isEmpty() && !sourceDiff.changes().isEmpty();
            boolean sourceEmpty = sourceDiff.changes().isEmpty();

            List<TimelineConflict> conflicts =
                    conflictDetector.detect(sourceDiff.changes(), targetDiff.changes());

            if (!conflicts.isEmpty() && resolutions.isEmpty()) {
                return conflictResult(request, sourceDiff, targetDiff, conflicts,
                        computeConflictSummary(sourceDiff, targetDiff, conflicts));
            }

            if (!conflicts.isEmpty() && !resolutions.isEmpty()) {
                boolean allResolved = conflictResolver.areAllResolved(conflicts, resolutions);
                if (!allResolved) {
                    return conflictResult(request, sourceDiff, targetDiff, conflicts,
                            computeConflictSummary(sourceDiff, targetDiff, conflicts));
                }
            }

            String mergePayload;
            TimelineMergeSummary summary;

            if (targetOnly || sourceEmpty) {
                mergePayload = sourcePayload;
                summary = TimelineMergeSummary.merged(
                        sourceDiff.changes().size(), 0, extractEntityIds(sourceDiff.changes()));
            } else if (sourceOnly) {
                mergePayload = targetPayload;
                summary = TimelineMergeSummary.merged(
                        0, targetDiff.changes().size(), extractEntityIds(targetDiff.changes()));
            } else {
                mergePayload = targetPayload;
                List<SemanticChange> mergedChanges = new ArrayList<>(targetDiff.changes());
                for (SemanticChange sc : sourceDiff.changes()) {
                    if (!isConflicting(sc, conflicts)) {
                        mergedChanges.add(sc);
                    }
                }
                summary = TimelineMergeSummary.merged(
                        countNonConflicting(sourceDiff.changes(), conflicts),
                        targetDiff.changes().size(),
                        extractEntityIds(mergedChanges));
            }

            String message = request.message() != null ? request.message()
                    : "Merge " + request.sourceRevisionId() + " into " + request.targetRevisionId();

            String snapshotId = Ids.newId("snap");
            snapshotService.save(request.projectId(), request.effectiveTenant(), mergePayload, "internal-1.0");

            String mergeRevisionId = Ids.newId("trev");
            int revNum = revisionRepository.nextRevisionNumber(request.projectId());
            String mergeParentIds = request.sourceRevisionId() + "," + request.targetRevisionId();

            TimelineRevisionRepository.RevisionRow mergeRow = new TimelineRevisionRepository.RevisionRow(
                    mergeRevisionId, request.projectId(), request.effectiveTenant(),
                    request.targetRevisionId(), revNum, snapshotId, 0,
                    computeMergeHash(request.sourceRevisionId(), request.targetRevisionId(), mergePayload),
                    "internal-1.0", TimelineMergeRequest.SOURCE_MERGE,
                    request.authorUserId(), null, message,
                    null, null, null,
                    true, mergeParentIds, request.baseRevisionId(),
                    OffsetDateTime.now());

            revisionRepository.insert(mergeRow);
            log.info("Merge revision created: id={} project={} rev={}",
                    mergeRevisionId, request.projectId(), revNum);

            return new TimelineMergeResult(MergeStatus.MERGED,
                    request.baseRevisionId(), request.sourceRevisionId(), request.targetRevisionId(),
                    mergeRevisionId, combineChanges(sourceDiff, targetDiff, conflicts),
                    conflicts.isEmpty() ? List.of() : conflicts,
                    summary, message, mergePayload);

        } catch (Exception e) {
            log.error("Merge failed: base={} source={} target={}",
                    request.baseRevisionId(), request.sourceRevisionId(), request.targetRevisionId(), e);
            return new TimelineMergeResult(MergeStatus.FAILED,
                    request.baseRevisionId(), request.sourceRevisionId(), request.targetRevisionId(),
                    null, List.of(), List.of(), TimelineMergeSummary.empty(),
                    "Merge failed: " + e.getMessage(), null);
        }
    }

    private static boolean isConflicting(SemanticChange change, List<TimelineConflict> conflicts) {
        return conflicts.stream().anyMatch(c -> c.entityRef().equals(change.entity()));
    }

    private static int countNonConflicting(List<SemanticChange> changes,
                                            List<TimelineConflict> conflicts) {
        return (int) changes.stream()
                .filter(c -> !isConflicting(c, conflicts))
                .count();
    }

    private static List<String> extractEntityIds(List<SemanticChange> changes) {
        return changes.stream()
                .map(c -> c.entity().key())
                .distinct()
                .toList();
    }

    private static List<SemanticChange> combineChanges(SemanticDiffResult sourceDiff,
                                                         SemanticDiffResult targetDiff,
                                                         List<TimelineConflict> conflicts) {
        List<SemanticChange> combined = new ArrayList<>(targetDiff.changes());
        for (SemanticChange sc : sourceDiff.changes()) {
            if (!isConflicting(sc, conflicts)) {
                combined.add(sc);
            }
        }
        return combined;
    }

    private static TimelineMergeSummary computeConflictSummary(
            SemanticDiffResult sourceDiff, SemanticDiffResult targetDiff,
            List<TimelineConflict> conflicts) {
        int sourceAll = sourceDiff.changes().size();
        int targetAll = targetDiff.changes().size();
        int sourceAuto = countNonConflicting(sourceDiff.changes(), conflicts);
        int targetAuto = countNonConflicting(targetDiff.changes(), conflicts);
        List<String> autoIds = new ArrayList<>();
        for (SemanticChange sc : sourceDiff.changes()) {
            if (!isConflicting(sc, conflicts)) autoIds.add(sc.entity().key());
        }
        for (SemanticChange sc : targetDiff.changes()) {
            if (!isConflicting(sc, conflicts)) autoIds.add(sc.entity().key());
        }
        List<String> conflictIds = conflicts.stream()
                .map(c -> c.entityRef().key()).distinct().toList();
        return new TimelineMergeSummary(
                sourceAuto + targetAuto, sourceAuto + targetAuto,
                conflicts.size(), sourceAuto, targetAuto,
                sourceAll - sourceAuto, targetAll - targetAuto,
                autoIds, conflictIds);
    }

    private static String computeMergeHash(String sourceId, String targetId, String payload) {
        return "merge:" + sourceId + ":" + targetId + ":" + (payload != null ? payload.hashCode() : "0");
    }

    private TimelineRevisionRepository.RevisionRow loadRevision(String revisionId) {
        return revisionRepository.findById(revisionId)
                .orElseThrow(() -> new IllegalArgumentException("Revision not found: " + revisionId));
    }

    private String loadPayload(TimelineRevisionRepository.RevisionRow revision) {
        return snapshotService.findPayload(revision.snapshotId())
                .orElseThrow(() -> new IllegalStateException("Snapshot not found: " + revision.snapshotId()));
    }

    private static TimelineMergeResult noOpResult(TimelineMergeRequest request) {
        return new TimelineMergeResult(MergeStatus.NO_OP,
                request.baseRevisionId(), request.sourceRevisionId(), request.targetRevisionId(),
                null, List.of(), List.of(), TimelineMergeSummary.empty(),
                "No changes to merge", null);
    }

    private static TimelineMergeResult conflictResult(
            TimelineMergeRequest request, SemanticDiffResult sourceDiff,
            SemanticDiffResult targetDiff, List<TimelineConflict> conflicts,
            TimelineMergeSummary summary) {
        return new TimelineMergeResult(MergeStatus.CONFLICTS,
                request.baseRevisionId(), request.sourceRevisionId(), request.targetRevisionId(),
                null, combineChanges(sourceDiff, targetDiff, conflicts), conflicts,
                summary,
                "Merge blocked: " + conflicts.size() + " conflict(s) detected", null);
    }
}
