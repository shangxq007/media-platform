package com.example.platform.render.app.timeline;

import com.example.platform.render.app.TimelinePatchService;
import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.app.TimelineSnapshotService.SnapshotInfo;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.TenantContext;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain version control for Internal Timeline: revision chain per project with snapshot blobs.
 */
@Service
public class TimelineRevisionService {

    private static final Logger log = LoggerFactory.getLogger(TimelineRevisionService.class);

    private final TimelineRevisionRepository revisionRepository;
    private final TimelineSnapshotService snapshotService;
    private final TimelineContentHasher contentHasher;
    private final TimelineRevisionDiffService diffService;
    private final InternalTimelineToEditorConverter editorConverter;
    private final TimelineConversionService timelineConversionService;
    private final TimelinePatchService timelinePatchService;

    public TimelineRevisionService(
            TimelineRevisionRepository revisionRepository,
            TimelineSnapshotService snapshotService,
            TimelineContentHasher contentHasher,
            TimelineRevisionDiffService diffService,
            InternalTimelineToEditorConverter editorConverter,
            TimelineConversionService timelineConversionService,
            TimelinePatchService timelinePatchService) {
        this.revisionRepository = revisionRepository;
        this.snapshotService = snapshotService;
        this.contentHasher = contentHasher;
        this.diffService = diffService;
        this.editorConverter = editorConverter;
        this.timelineConversionService = timelineConversionService;
        this.timelinePatchService = timelinePatchService;
    }

    @Transactional
    public RevisionInfo recordRevision(
            String projectId,
            String tenantId,
            String snapshotId,
            String internalTimelineJson,
            String source,
            String authorUserId,
            String editSessionId,
            String message) {
        return recordRevision(
                projectId,
                tenantId,
                snapshotId,
                internalTimelineJson,
                source,
                authorUserId,
                editSessionId,
                message,
                null);
    }

    @Transactional
    public RevisionInfo recordRevision(
            String projectId,
            String tenantId,
            String snapshotId,
            String internalTimelineJson,
            String source,
            String authorUserId,
            String editSessionId,
            String message,
            List<TimelinePatchService.PatchOperation> patchOperations) {
        String effectiveTenant = tenantId != null ? tenantId : TenantContext.get();
        String contentHash = contentHasher.hashInternalTimeline(internalTimelineJson);
        int internalRevision = parseInternalRevision(internalTimelineJson);

        Optional<TimelineRevisionRepository.RevisionRow> head =
                revisionRepository.findHeadByProject(projectId);
        if (head.isPresent() && contentHash.equals(head.get().contentHash())) {
            log.debug("Skipping duplicate timeline revision for project={}", projectId);
            return toInfo(head.get());
        }

        String parentPayload = head.flatMap(h -> snapshotService.findPayload(h.snapshotId())).orElse(null);
        String changeSummaryJson = diffService.summarizeJson(parentPayload, internalTimelineJson);
        String patchOpsJson = TimelinePatchOpsJson.toJson(patchOperations);

        int revisionNumber = revisionRepository.nextRevisionNumber(projectId);
        String revisionId = Ids.newId("trev");
        TimelineRevisionRepository.RevisionRow row = new TimelineRevisionRepository.RevisionRow(
                revisionId,
                projectId,
                effectiveTenant,
                head.map(TimelineRevisionRepository.RevisionRow::id).orElse(null),
                revisionNumber,
                snapshotId,
                internalRevision,
                contentHash,
                "internal-1.0",
                source != null ? source : "sync",
                authorUserId,
                editSessionId,
                message,
                changeSummaryJson,
                patchOpsJson,
                null,
                OffsetDateTime.now());
        revisionRepository.insert(row);
        log.info("Recorded timeline revision id={} project={} rev={}", revisionId, projectId, revisionNumber);
        return toInfo(row);
    }

    public Optional<RevisionInfo> findHead(String projectId) {
        return revisionRepository.findHeadByProject(projectId).map(TimelineRevisionService::toInfo);
    }

    public Optional<RevisionInfo> findById(String revisionId) {
        return revisionRepository.findById(revisionId).map(TimelineRevisionService::toInfo);
    }

    /**
     * Load Internal Timeline JSON for a revision's snapshot (for patch path index resolution on the client).
     */
    public Optional<RevisionSnapshotPayload> getRevisionSnapshotPayload(String projectId, String revisionId) {
        return revisionRepository.findById(revisionId).flatMap(row -> {
            if (!projectId.equals(row.projectId())) {
                return Optional.empty();
            }
            return snapshotService
                    .findPayload(row.snapshotId())
                    .map(payload -> {
                        String internal = payload;
                        try {
                            if (!InternalTimelineJson.isInternalTimeline(InternalTimelineJson.parse(payload))) {
                                internal = timelineConversionService.ensureInternalTimelineJson(payload);
                            }
                        } catch (Exception e) {
                            log.warn("Revision snapshot not internal, id={}", revisionId);
                        }
                        return new RevisionSnapshotPayload(
                                row.snapshotId(), internal, row.schemaVersion() != null ? row.schemaVersion() : "internal-1.0");
                    });
        });
    }

    public List<RevisionInfo> listHistory(String projectId, int limit) {
        return listHistory(projectId, null, limit);
    }

    public List<RevisionInfo> listHistory(String projectId, String editSessionId, int limit) {
        return listHistory(projectId, editSessionId, null, null, limit);
    }

    public List<RevisionInfo> listHistory(
            String projectId, String editSessionId, String authorUserId, String source, int limit) {
        return revisionRepository
                .listByProject(projectId, editSessionId, authorUserId, source, limit)
                .stream()
                .map(TimelineRevisionService::toInfo)
                .toList();
    }

    @Transactional
    public Optional<RevisionInfo> updateAnnotation(
            String projectId, String revisionId, String message, List<String> labels) {
        String trimmed = message == null ? "" : message.trim();
        if (trimmed.length() > 512) {
            trimmed = trimmed.substring(0, 512);
        }
        String labelsJson = TimelineRevisionLabelsJson.toJson(TimelineRevisionLabelsJson.normalize(labels));
        if (!revisionRepository.updateAnnotation(
                revisionId, projectId, trimmed.isEmpty() ? null : trimmed, labelsJson)) {
            return Optional.empty();
        }
        return findById(revisionId).filter(r -> projectId.equals(r.projectId()));
    }

    public RevisionFacets listFacets(String projectId) {
        List<String> sources = revisionRepository.listDistinctSources(projectId);
        List<AuthorFacet> authors = revisionRepository.listAuthorFacets(projectId, 30).stream()
                .map(a -> new AuthorFacet(a.authorUserId(), a.revisionCount()))
                .toList();
        return new RevisionFacets(sources, authors);
    }

    public List<EditSessionInfo> listEditSessions(String projectId, int limit) {
        return revisionRepository.listEditSessions(projectId, limit).stream()
                .map(r -> new EditSessionInfo(
                        r.editSessionId(),
                        r.lastAt() != null ? r.lastAt().toString() : null,
                        r.revisionCount()))
                .toList();
    }

    @Transactional
    public Optional<RevisionInfo> backfillHeadFromLatestSnapshot(String projectId, String tenantId) {
        if (revisionRepository.findHeadByProject(projectId).isPresent()) {
            return Optional.empty();
        }
        Optional<SnapshotInfo> latest = snapshotService.findLatestByProject(projectId);
        if (latest.isEmpty()) {
            return Optional.empty();
        }
        String payload = latest.get().payloadJson();
        String internal = payload;
        try {
            if (!InternalTimelineJson.isInternalTimeline(InternalTimelineJson.parse(payload))) {
                internal = timelineConversionService.ensureInternalTimelineJson(payload);
            }
        } catch (Exception e) {
            log.warn("Backfill skipped: cannot parse snapshot for project={}", projectId);
            return Optional.empty();
        }
        String snapId = latest.get().id();
        RevisionInfo info = recordRevision(
                projectId,
                tenantId,
                snapId,
                internal,
                "backfill",
                null,
                null,
                "Auto backfill from latest snapshot");
        log.info("Backfilled timeline revision head for project={} rev={}", projectId, info.revisionNumber());
        return Optional.of(info);
    }

    public CompareResult compareRevisions(String projectId, String fromRevisionId, String toRevisionId) {
        TimelineRevisionRepository.RevisionRow from = revisionRepository
                .findById(fromRevisionId)
                .orElseThrow(() -> new IllegalArgumentException("Revision not found: " + fromRevisionId));
        TimelineRevisionRepository.RevisionRow to = revisionRepository
                .findById(toRevisionId)
                .orElseThrow(() -> new IllegalArgumentException("Revision not found: " + toRevisionId));
        if (!projectId.equals(from.projectId()) || !projectId.equals(to.projectId())) {
            throw new IllegalArgumentException("Revisions must belong to project: " + projectId);
        }
        String fromPayload = snapshotService
                .findPayload(from.snapshotId())
                .orElseThrow(() -> new IllegalArgumentException("Snapshot missing: " + from.snapshotId()));
        String toPayload = snapshotService
                .findPayload(to.snapshotId())
                .orElseThrow(() -> new IllegalArgumentException("Snapshot missing: " + to.snapshotId()));
        TimelineRevisionDiffService.DetailedCompare detailed = diffService.compare(fromPayload, toPayload);
        List<PatchPathItem> patchPaths = toPatchPaths(TimelinePatchOpsJson.fromJson(to.patchOpsJson()));
        return new CompareResult(
                toInfo(from),
                toInfo(to),
                detailed.summary(),
                detailed.entities(),
                patchPaths);
    }

    /**
     * Dry-run stored RFC6902 ops against the parent revision snapshot (or this revision if no parent).
     */
    public PatchPreviewResult previewPatchReplay(String revisionId) {
        TimelineRevisionRepository.RevisionRow row = revisionRepository
                .findById(revisionId)
                .orElseThrow(() -> new IllegalArgumentException("Revision not found: " + revisionId));
        List<TimelinePatchService.PatchOperation> ops = TimelinePatchOpsJson.fromJson(row.patchOpsJson());
        if (ops.isEmpty()) {
            return PatchPreviewResult.noOps(revisionId);
        }
        String basePayload = resolvePatchBasePayload(row);
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
    public PatchStepsResult previewPatchSteps(String revisionId) {
        TimelineRevisionRepository.RevisionRow row = revisionRepository
                .findById(revisionId)
                .orElseThrow(() -> new IllegalArgumentException("Revision not found: " + revisionId));
        List<TimelinePatchService.PatchOperation> ops = TimelinePatchOpsJson.fromJson(row.patchOpsJson());
        if (ops.isEmpty()) {
            return PatchStepsResult.noOps(revisionId);
        }
        String current = resolvePatchBasePayload(row);
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

    private String resolvePatchBasePayload(TimelineRevisionRepository.RevisionRow row) {
        if (row.parentRevisionId() != null) {
            TimelineRevisionRepository.RevisionRow parent = revisionRepository
                    .findById(row.parentRevisionId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Parent revision not found: " + row.parentRevisionId()));
            return snapshotService
                    .findPayload(parent.snapshotId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Parent snapshot missing: " + parent.snapshotId()));
        }
        return snapshotService
                .findPayload(row.snapshotId())
                .orElseThrow(() -> new IllegalArgumentException("Snapshot missing: " + row.snapshotId()));
    }

    private static List<PatchPathItem> toPatchPaths(List<TimelinePatchService.PatchOperation> ops) {
        return ops.stream().map(o -> new PatchPathItem(o.op(), o.path())).toList();
    }

    @Transactional
    public RevisionInfo recordAiAdoptRevision(
            String projectId,
            String tenantId,
            String internalTimelineJson,
            String editSessionId,
            String proposalId,
            List<TimelinePatchService.PatchOperation> patchOperations) {
        String snapshotId = snapshotService.save(projectId, tenantId, internalTimelineJson, "internal-1.0");
        String message = proposalId != null ? "AI proposal adopted: " + proposalId : "AI edit applied";
        return recordRevision(
                projectId,
                tenantId,
                snapshotId,
                internalTimelineJson,
                "ai-adopt",
                null,
                editSessionId,
                message,
                patchOperations);
    }

    public Optional<RevisionDetail> getDetail(String revisionId) {
        return revisionRepository.findById(revisionId).map(row -> {
            RevisionInfo info = toInfo(row);
            TimelineRevisionDiffService.ChangeSummary summary = parseSummary(row.changeSummaryJson());
            String parentSummary = null;
            if (row.parentRevisionId() != null) {
                parentSummary = revisionRepository
                        .findById(row.parentRevisionId())
                        .map(TimelineRevisionRepository.RevisionRow::changeSummaryJson)
                        .orElse(null);
            }
            return new RevisionDetail(info, summary, parentSummary);
        });
    }

    @Transactional
    public RestoreResult restore(String projectId, String tenantId, String revisionId, String authorUserId) {
        TimelineRevisionRepository.RevisionRow target = revisionRepository
                .findById(revisionId)
                .orElseThrow(() -> new IllegalArgumentException("Revision not found: " + revisionId));
        if (!projectId.equals(target.projectId())) {
            throw new IllegalArgumentException("Revision does not belong to project: " + projectId);
        }
        String payload = snapshotService
                .findPayload(target.snapshotId())
                .orElseThrow(() -> new IllegalArgumentException("Snapshot missing for revision: " + revisionId));

        String newSnapshotId = snapshotService.save(projectId, tenantId, payload, "internal-1.0");
        String message = "Restored from revision #" + target.revisionNumber();
        RevisionInfo newHead = recordRevision(
                projectId,
                tenantId,
                newSnapshotId,
                payload,
                "rollback",
                authorUserId,
                null,
                message);

        String editorJson = editorConverter.toEditorJson(payload);
        return new RestoreResult(newHead, editorJson, payload);
    }

    private static int parseInternalRevision(String internalTimelineJson) {
        try {
            return InternalTimelineJson.revision(InternalTimelineJson.parse(internalTimelineJson));
        } catch (Exception e) {
            return 0;
        }
    }

    private static TimelineRevisionDiffService.ChangeSummary parseSummary(String json) {
        if (json == null || json.isBlank()) {
            return TimelineRevisionDiffService.ChangeSummary.unsupported();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, TimelineRevisionDiffService.ChangeSummary.class);
        } catch (Exception e) {
            return TimelineRevisionDiffService.ChangeSummary.unsupported();
        }
    }

    private static RevisionInfo toInfo(TimelineRevisionRepository.RevisionRow row) {
        return new RevisionInfo(
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
                row.createdAt() != null ? row.createdAt().toString() : null);
    }

    public record RevisionInfo(
            String id,
            String projectId,
            String tenantId,
            String parentRevisionId,
            int revisionNumber,
            String snapshotId,
            int internalRevision,
            String contentHash,
            String schemaVersion,
            String source,
            String authorUserId,
            String editSessionId,
            String message,
            List<String> labels,
            String changeSummaryJson,
            String patchOpsJson,
            String createdAt) {}

    public record RevisionFacets(List<String> sources, List<AuthorFacet> authors) {}

    public record AuthorFacet(String authorUserId, int revisionCount) {}

    public record EditSessionInfo(String editSessionId, String lastAt, int revisionCount) {}

    public record PatchPathItem(String op, String path) {}

    public record CompareResult(
            RevisionInfo fromRevision,
            RevisionInfo toRevision,
            TimelineRevisionDiffService.ChangeSummary summary,
            List<TimelineRevisionDiffService.EntityChange> entityChanges,
            List<PatchPathItem> patchPaths) {}

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

    public record RevisionDetail(
            RevisionInfo revision,
            TimelineRevisionDiffService.ChangeSummary changeSummary,
            String parentChangeSummaryJson) {}

    public record RevisionSnapshotPayload(String snapshotId, String internalTimelineJson, String schemaVersion) {}

    public record RestoreResult(RevisionInfo newRevision, String editorTimelineJson, String internalTimelineJson) {}
}
