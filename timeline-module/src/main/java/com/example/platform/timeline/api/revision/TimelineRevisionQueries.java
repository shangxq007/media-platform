package com.example.platform.timeline.api.revision;
import com.example.platform.timeline.api.revision.TimelineRevisionDiff;
import java.util.List;
import java.util.Optional;
/** Published Timeline owner contract; implemented by the existing single authority. */
public interface TimelineRevisionQueries {
Optional<RevisionInfo> findHead(String projectId, String tenantId);

Optional<RevisionInfo> findById(String projectId, String tenantId, String revisionId);

Optional<RevisionSnapshotPayload> getRevisionSnapshotPayload(
            String projectId, String tenantId, String revisionId);

List<RevisionInfo> listHistory(String projectId, String tenantId, int limit);

List<RevisionInfo> listHistory(
            String projectId, String tenantId, String editSessionId, String authorUserId, String source, int limit);

Optional<RevisionInfo> updateAnnotation(
            String projectId, String tenantId, String revisionId, String message, List<String> labels);

RevisionFacets listFacets(String projectId, String tenantId);

List<EditSessionInfo> listEditSessions(String projectId, String tenantId, int limit);

Optional<RevisionDetail> getDetail(String projectId, String tenantId, String revisionId);

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
            TimelineRevisionDiff.ChangeSummary changeSummary,
            String parentChangeSummaryJson) {}

public record RevisionSnapshotPayload(String snapshotId, String canonicalTimelineJson, String schemaVersion) {}
}
