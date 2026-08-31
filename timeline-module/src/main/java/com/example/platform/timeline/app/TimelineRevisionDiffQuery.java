package com.example.platform.timeline.app;

import com.example.platform.timeline.adapter.TimelineRevisionRepository;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
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
    private final TimelineRevisionDiffService diffService;

    public TimelineRevisionDiffQuery(
            TimelineRevisionRepository revisionRepository,
            TimelineSnapshotService snapshotService,
            TimelineRevisionDiffService diffService) {
        this.revisionRepository = revisionRepository;
        this.snapshotService = snapshotService;
        this.diffService = diffService;
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
        // V2: compare output is derived solely from the requested from->to payloads.
        // Stored target patch metadata is never substituted as compare authority.
        List<PatchPathItem> patchPaths = detailed.entities().stream()
                .map(TimelineRevisionDiffQuery::toActualPairPath)
                .toList();

        return new CompareResult(
                toInfo(from),
                toInfo(to),
                detailed.summary(),
                detailed.entities(),
                patchPaths);
    }

    private static PatchPathItem toActualPairPath(
            TimelineRevisionDiffService.EntityChange change) {
        String op = switch (change.action()) {
            case "added" -> "add";
            case "removed" -> "remove";
            default -> "replace";
        };
        return new PatchPathItem(op, "/" + change.kind() + "s/" + change.entityId());
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
            List<PatchPathItem> patchPaths) {}

}
