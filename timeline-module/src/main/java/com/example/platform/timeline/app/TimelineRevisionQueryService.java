package com.example.platform.timeline.app;

import com.example.platform.timeline.adapter.TimelineRevisionRepository;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CFRH-I2 ownership-scoped timeline revision query authority.
 *
 * <p>Replaces the query behaviors of the retired legacy timeline revision service.
 * Every production read carries explicit projectId + tenantId and ownership
 * participates in the persistence query — no ambient-global lookup, no
 * load-then-check ownership. This service is a non-authoritative projection /
 * application read authority: it never creates revisions and never mutates
 * canonical semantic state (updateAnnotation is a bounded metadata command
 * that does not touch the semantic digest).
 */
@Service
public class TimelineRevisionQueryService {

    private static final Logger log = LoggerFactory.getLogger(TimelineRevisionQueryService.class);

    private final TimelineRevisionRepository revisionRepository;
    private final TimelineSnapshotService snapshotService;
    private final TimelineRevisionDiffService diffService;
    private final TimelinePayloadCodec payloadCodec;

    public TimelineRevisionQueryService(
            TimelineRevisionRepository revisionRepository,
            TimelineSnapshotService snapshotService,
            TimelineRevisionDiffService diffService,
            TimelinePayloadCodec payloadCodec) {
        this.revisionRepository = revisionRepository;
        this.snapshotService = snapshotService;
        this.diffService = diffService;
        this.payloadCodec = payloadCodec;
    }

    public Optional<RevisionInfo> findHead(String projectId, String tenantId) {
        return revisionRepository.findOwnedHead(projectId, tenantId).map(TimelineRevisionQueryService::toInfo);
    }

    public Optional<RevisionInfo> findById(String projectId, String tenantId, String revisionId) {
        return revisionRepository.findOwnedById(revisionId, projectId, tenantId)
                .map(TimelineRevisionQueryService::toInfo);
    }

    /**
     * Load Internal Timeline JSON for a revision's snapshot (for patch path index resolution on the client).
     */
    public Optional<RevisionSnapshotPayload> getRevisionSnapshotPayload(
            String projectId, String tenantId, String revisionId) {
        return revisionRepository.findOwnedById(revisionId, projectId, tenantId).flatMap(row ->
                snapshotService
                        .findOwnedById(row.projectId(), row.tenantId(), row.snapshotId())
                        .map(TimelineSnapshotService.SnapshotInfo::payloadJson)
                        .map(payload -> {
                            String internal = payload;
                            try {
                                if (!InternalTimelineJson.isInternalTimeline(InternalTimelineJson.parse(payload))) {
                                    internal = payloadCodec.ensureInternalTimelineJson(payload);
                                }
                            } catch (Exception e) {
                                log.warn("Revision snapshot not internal, id={}", revisionId);
                            }
                            return new RevisionSnapshotPayload(
                                    row.snapshotId(), internal,
                                    row.schemaVersion() != null ? row.schemaVersion() : "internal-1.0");
                        }));
    }

    public List<RevisionInfo> listHistory(String projectId, String tenantId, int limit) {
        return listHistory(projectId, tenantId, null, null, null, limit);
    }

    public List<RevisionInfo> listHistory(
            String projectId, String tenantId, String editSessionId, String authorUserId, String source, int limit) {
        return revisionRepository
                .listOwnedByProject(projectId, tenantId, editSessionId, authorUserId, source, limit)
                .stream()
                .map(TimelineRevisionQueryService::toInfo)
                .toList();
    }

    @Transactional
    public Optional<RevisionInfo> updateAnnotation(
            String projectId, String tenantId, String revisionId, String message, List<String> labels) {
        String trimmed = message == null ? "" : message.trim();
        if (trimmed.length() > 512) {
            trimmed = trimmed.substring(0, 512);
        }
        String labelsJson = TimelineRevisionLabelsJson.toJson(TimelineRevisionLabelsJson.normalize(labels));
        if (!revisionRepository.updateOwnedAnnotation(
                revisionId, projectId, tenantId, trimmed.isEmpty() ? null : trimmed, labelsJson)) {
            return Optional.empty();
        }
        return revisionRepository.findOwnedById(revisionId, projectId, tenantId)
                .map(TimelineRevisionQueryService::toInfo);
    }

    public RevisionFacets listFacets(String projectId, String tenantId) {
        List<String> sources = revisionRepository.listOwnedDistinctSources(projectId, tenantId);
        List<AuthorFacet> authors = revisionRepository.listOwnedAuthorFacets(projectId, tenantId, 30).stream()
                .map(a -> new AuthorFacet(a.authorUserId(), a.revisionCount()))
                .toList();
        return new RevisionFacets(sources, authors);
    }

    public List<EditSessionInfo> listEditSessions(String projectId, String tenantId, int limit) {
        return revisionRepository.listOwnedEditSessions(projectId, tenantId, limit).stream()
                .map(r -> new EditSessionInfo(
                        r.editSessionId(),
                        r.lastAt() != null ? r.lastAt().toString() : null,
                        r.revisionCount()))
                .toList();
    }

    public Optional<RevisionDetail> getDetail(String projectId, String tenantId, String revisionId) {
        return revisionRepository.findOwnedById(revisionId, projectId, tenantId).map(row -> {
            RevisionInfo info = toInfo(row);
            TimelineRevisionDiffService.ChangeSummary summary = parseSummary(row.changeSummaryJson());
            String parentSummary = null;
            if (row.parentRevisionId() != null) {
                parentSummary = revisionRepository
                        .findOwnedById(row.parentRevisionId(), projectId, tenantId)
                        .map(TimelineRevisionRepository.RevisionRow::changeSummaryJson)
                        .orElse(null);
            }
            return new RevisionDetail(info, summary, parentSummary);
        });
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
                row.isMerge(),
                row.mergeParentRevisionIds(),
                row.mergeBaseRevisionId(),
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
            boolean isMerge,
            String mergeParentRevisionIds,
            String mergeBaseRevisionId,
            String createdAt) {}

    public record RevisionFacets(List<String> sources, List<AuthorFacet> authors) {}

    public record AuthorFacet(String authorUserId, int revisionCount) {}

    public record EditSessionInfo(String editSessionId, String lastAt, int revisionCount) {}

    public record RevisionDetail(
            RevisionInfo revision,
            TimelineRevisionDiffService.ChangeSummary changeSummary,
            String parentChangeSummaryJson) {}

    public record RevisionSnapshotPayload(String snapshotId, String internalTimelineJson, String schemaVersion) {}
}
