package com.example.platform.timeline.app;

import com.example.platform.timeline.adapter.TimelineRevisionRepository;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.diff.merge.SemanticDiffResult;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * CFRH-I2 ownership-scoped timeline revision diff / patch-preview authority.
 *
 * <p>Replaces the compare/preview behaviors of {@code TimelineRevisionService}
 * (retired). All base reads are ownership-scoped (projectId + tenantId
 * participate in the persistence query) — no ambient-global revision lookup,
 * no load-then-check ownership. Read-only: never creates revisions.
 */
@Service
public class TimelineRevisionDiffQuery {

    private final TimelineRevisionRepository revisionRepository;
    private final TimelineSnapshotService snapshotService;
    private final TimelineContentHasher contentHasher;
    private final TimelineRevisionDiffService diffService;
    private final TimelinePatchService timelinePatchService;
    private final TimelineSemanticDiffService semanticDiffService;

    public TimelineRevisionDiffQuery(
            TimelineRevisionRepository revisionRepository,
            TimelineSnapshotService snapshotService,
            TimelineContentHasher contentHasher,
            TimelineRevisionDiffService diffService,
            TimelinePatchService timelinePatchService,
            TimelineSemanticDiffService semanticDiffService) {
        this.revisionRepository = revisionRepository;
        this.snapshotService = snapshotService;
        this.contentHasher = contentHasher;
        this.diffService = diffService;
        this.timelinePatchService = timelinePatchService;
        this.semanticDiffService = semanticDiffService;
    }

    public CompareResult compareRevisions(
            String projectId, String tenantId, String fromRevisionId, String toRevisionId) {
        TimelineRevisionRepository.RevisionRow from = revisionRepository
                .findOwnedById(fromRevisionId, projectId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Revision not found: " + fromRevisionId));
        TimelineRevisionRepository.RevisionRow to = revisionRepository
                .findOwnedById(toRevisionId, projectId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Revision not found: " + toRevisionId));
        String fromPayload = snapshotService
                .findOwnedById(projectId, tenantId, from.snapshotId())
                .map(TimelineSnapshotService.SnapshotInfo::payloadJson)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot missing: " + from.snapshotId()));
        String toPayload = snapshotService
                .findOwnedById(projectId, tenantId, to.snapshotId())
                .map(TimelineSnapshotService.SnapshotInfo::payloadJson)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot missing: " + to.snapshotId()));
        TimelineRevisionDiffService.DetailedCompare detailed = diffService.compare(fromPayload, toPayload);
        List<PatchPathItem> patchPaths = toPatchPaths(TimelinePatchOpsJson.fromJson(to.patchOpsJson()));

        // Add semantic diff for effects, text overlays, subtitles
        SemanticDiffResult semanticResult = null;
        try {
            semanticResult = semanticDiffService.diff(fromPayload, toPayload);
        } catch (Exception e) {
            // Semantic diff is best-effort; structural diff is primary
        }

        return new CompareResult(
                toInfo(from),
                toInfo(to),
                detailed.summary(),
                detailed.entities(),
                patchPaths,
                semanticResult);
    }

    /**
     * Dry-run stored RFC6902 ops against the parent revision snapshot (or this revision if no parent).
     */
    public PatchPreviewResult previewPatchReplay(String projectId, String tenantId, String revisionId) {
        TimelineRevisionRepository.RevisionRow row = revisionRepository
                .findOwnedById(revisionId, projectId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Revision not found: " + revisionId));
        List<TimelinePatchService.PatchOperation> ops = TimelinePatchOpsJson.fromJson(row.patchOpsJson());
        if (ops.isEmpty()) {
            return PatchPreviewResult.noOps(revisionId);
        }
        String basePayload = resolvePatchBasePayload(row, projectId, tenantId);
        String hashBefore = contentHasher.hashInternalTimeline(basePayload);
        TimelinePatchService.PatchResult result = timelinePatchService.applyPatch(basePayload, ops);
        String hashAfter = result.success() && result.timelineJson() != null
                ? contentHasher.hashInternalTimeline(result.timelineJson())
                : null;
        return new PatchPreviewResult(
                revisionId,
                true,
                result.success(),
                toPatchPaths(ops),
                result.appliedOps() != null ? result.appliedOps() : List.of(),
                result.errors() != null ? result.errors() : List.of(),
                hashBefore,
                hashAfter,
                row.contentHash());
    }

    /** Apply stored patch ops one at a time (cumulative dry-run). */
    public PatchStepsResult previewPatchSteps(String projectId, String tenantId, String revisionId) {
        TimelineRevisionRepository.RevisionRow row = revisionRepository
                .findOwnedById(revisionId, projectId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Revision not found: " + revisionId));
        List<TimelinePatchService.PatchOperation> ops = TimelinePatchOpsJson.fromJson(row.patchOpsJson());
        if (ops.isEmpty()) {
            return PatchStepsResult.noOps(revisionId);
        }
        String current = resolvePatchBasePayload(row, projectId, tenantId);
        List<PatchStepPreview> steps = new ArrayList<>();
        boolean allOk = true;
        for (int i = 0; i < ops.size(); i++) {
            TimelinePatchService.PatchOperation op = ops.get(i);
            TimelinePatchService.PatchResult stepResult = timelinePatchService.applyPatch(current, List.of(op));
            boolean ok = stepResult.success();
            String hashAfter = ok && stepResult.timelineJson() != null
                    ? contentHasher.hashInternalTimeline(stepResult.timelineJson())
                    : null;
            steps.add(new PatchStepPreview(
                    i,
                    op.op(),
                    op.path(),
                    ok,
                    stepResult.appliedOps() != null ? stepResult.appliedOps() : List.of(),
                    stepResult.errors() != null ? stepResult.errors() : List.of(),
                    hashAfter));
            if (!ok) {
                allOk = false;
                break;
            }
            current = stepResult.timelineJson();
        }
        return new PatchStepsResult(revisionId, true, allOk, steps);
    }

    private String resolvePatchBasePayload(
            TimelineRevisionRepository.RevisionRow row, String projectId, String tenantId) {
        if (row.parentRevisionId() != null) {
            TimelineRevisionRepository.RevisionRow parent = revisionRepository
                    .findOwnedById(row.parentRevisionId(), projectId, tenantId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Parent revision not found: " + row.parentRevisionId()));
            return snapshotService
                    .findOwnedById(projectId, tenantId, parent.snapshotId())
                    .map(TimelineSnapshotService.SnapshotInfo::payloadJson)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Parent snapshot missing: " + parent.snapshotId()));
        }
        return snapshotService
                .findOwnedById(projectId, tenantId, row.snapshotId())
                .map(TimelineSnapshotService.SnapshotInfo::payloadJson)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot missing: " + row.snapshotId()));
    }

    private static List<PatchPathItem> toPatchPaths(List<TimelinePatchService.PatchOperation> ops) {
        return ops.stream().map(o -> new PatchPathItem(o.op(), o.path())).toList();
    }

    private static TimelineRevisionQueryService.RevisionInfo toInfo(TimelineRevisionRepository.RevisionRow row) {
        return new TimelineRevisionQueryService.RevisionInfo(
                row.id(),
                row.projectId(),
                row.tenantId(),
                row.parentRevisionId(),
                row.revisionNumber(),
                row.snapshotId(),
                row.internalRevision(),
                row.contentHash(),
                row.schemaVersion(),
                row.source(),
                row.authorUserId(),
                row.editSessionId(),
                row.message(),
                TimelineRevisionLabelsJson.parse(row.labelsJson()),
                row.changeSummaryJson(),
                row.patchOpsJson(),
                row.isMerge(),
                row.mergeParentRevisionIds(),
                row.mergeBaseRevisionId(),
                row.createdAt() != null ? row.createdAt().toString() : null);
    }

    public record PatchPathItem(String op, String path) {}

    public record CompareResult(
            TimelineRevisionQueryService.RevisionInfo fromRevision,
            TimelineRevisionQueryService.RevisionInfo toRevision,
            TimelineRevisionDiffService.ChangeSummary summary,
            List<TimelineRevisionDiffService.EntityChange> entityChanges,
            List<PatchPathItem> patchPaths,
            SemanticDiffResult semanticDiff) {}

    public record PatchPreviewResult(
            String revisionId,
            boolean hasPatchOps,
            boolean success,
            List<PatchPathItem> patchPaths,
            List<String> appliedOps,
            List<String> errors,
            String contentHashBefore,
            String contentHashAfter,
            String revisionContentHash) {

        static PatchPreviewResult noOps(String revisionId) {
            return new PatchPreviewResult(revisionId, false, true, List.of(), List.of(), List.of(), null, null, null);
        }
    }

    public record PatchStepPreview(
            int stepIndex,
            String op,
            String path,
            boolean success,
            List<String> appliedOps,
            List<String> errors,
            String contentHashAfter) {}

    public record PatchStepsResult(
            String revisionId, boolean hasPatchOps, boolean allStepsSucceeded, List<PatchStepPreview> steps) {

        static PatchStepsResult noOps(String revisionId) {
            return new PatchStepsResult(revisionId, false, true, List.of());
        }
    }
}
