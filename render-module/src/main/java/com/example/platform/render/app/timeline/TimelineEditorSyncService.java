package com.example.platform.render.app.timeline;

import com.example.platform.timeline.api.revision.TimelineSnapshotView;
import com.example.platform.timeline.api.revision.TimelineRevisionQueries;
import com.example.platform.timeline.api.serialization.InternalTimelineJson;
import com.example.platform.timeline.api.composition.TimelinePatches;
import com.example.platform.timeline.api.revision.TimelineSnapshotQueries;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Bidirectional sync between editor v2 JSON and Internal Timeline Schema 1.0.
 */
@Service
public class TimelineEditorSyncService {

    private final TimelineConversionService conversionService;
    private final InternalTimelineToEditorConverter internalToEditorConverter;
    private final TimelineSnapshotQueries timelineSnapshotService;
    private final TimelineSpecResolver timelineSpecResolver;
    private final TimelineRevisionQueries timelineRevisionQueryService;

    public TimelineEditorSyncService(
            TimelineConversionService conversionService,
            InternalTimelineToEditorConverter internalToEditorConverter,
            TimelineSnapshotQueries timelineSnapshotService,
            TimelineSpecResolver timelineSpecResolver,
            TimelineRevisionQueries timelineRevisionQueryService) {
        this.conversionService = conversionService;
        this.internalToEditorConverter = internalToEditorConverter;
        this.timelineSnapshotService = timelineSnapshotService;
        this.timelineSpecResolver = timelineSpecResolver;
        this.timelineRevisionQueryService = timelineRevisionQueryService;
    }

    /**
     * Editor/legacy JSON -> Internal Timeline 1.0 conversion preview (non-authoring).
     * Legacy revision persistence via the legacy timeline revision service was removed in CFRH-I1
     * (DELETE_OBSOLETE_PRODUCT_BEHAVIOR); no revision is created by this path.
     */
    public PushResult push(String projectId, String tenantId, String timelineJson) {
        TimelineConversionService.PreviewResult preview = conversionService.preview(timelineJson);
        String internal = preview.internalTimelineJson();
        String storedSchema = "internal-1.0";
        return new PushResult(
                internal,
                preview.sourceSchema(),
                preview.alreadyInternal(),
                null,
                null,
                preview.summary());
    }

    public PullResult pullByProject(String projectId, String tenantId) {
        // CFRH-I1: legacy backfill write authority removed (DELETE_OBSOLETE_PRODUCT_BEHAVIOR);
        // no revision is created merely because HEAD is absent.
        Optional<TimelineRevisionQueries.RevisionInfo> head =
                timelineRevisionQueryService.findHead(projectId, tenantId);
        if (head.isPresent()) {
            return pullBySnapshotId(projectId, tenantId, head.get().snapshotId());
        }
        throw new IllegalArgumentException(
                "No canonical main Timeline revision for project: " + projectId);
    }

    public PullResult pullBySnapshotId(String projectId, String tenantId, String snapshotId) {
        Optional<TimelineSnapshotView> info = timelineSnapshotService.findOwnedById(projectId, tenantId, snapshotId);
        if (info.isEmpty()) {
            throw new IllegalArgumentException("Timeline snapshot not found: " + snapshotId);
        }
        return pullSnapshot(info.get(), null);
    }

    private PullResult pullSnapshot(TimelineSnapshotView info, TimelineRevisionQueries.RevisionInfo headRevision) {
        String payload = info.payloadJson();
        TimelineConversionService.PreviewResult preview = conversionService.preview(payload);
        String internal = preview.internalTimelineJson();
        String editorJson;
        String editorSchema;
        try {
            JsonNode root = InternalTimelineJson.parse(payload);
            String version = root.path("schemaVersion").asText("");
            if (version.startsWith("2") && !InternalTimelineJson.isInternalTimeline(root)) {
                editorJson = payload;
                editorSchema = "editor-" + version;
            } else {
                editorJson = internalToEditorConverter.toEditorJson(internal);
                editorSchema = "editor-2.0.0";
            }
        } catch (Exception e) {
            editorJson = internalToEditorConverter.toEditorJson(internal);
            editorSchema = "editor-2.0.0";
        }
        TimelineRevisionQueries.RevisionInfo revisionMeta = headRevision;
        if (revisionMeta == null) {
            revisionMeta = timelineRevisionQueryService
                    .findHead(info.projectId(), info.tenantId())
                    .orElse(null);
        }
        return new PullResult(
                editorJson,
                internal,
                info.id(),
                info.projectId(),
                info.schemaVersion(),
                editorSchema,
                preview.sourceSchema(),
                revisionMeta,
                preview.summary());
    }

    public record PushResult(
            String internalTimelineJson,
            String sourceSchema,
            boolean alreadyInternal,
            String snapshotId,
            TimelineRevisionQueries.RevisionInfo revision,
            TimelineConversionService.PreviewSummary summary) {}

    public record PullResult(
            String editorTimelineJson,
            String internalTimelineJson,
            String snapshotId,
            String projectId,
            String storedSchemaVersion,
            String editorSchema,
            String resolvedSourceSchema,
            TimelineRevisionQueries.RevisionInfo headRevision,
            TimelineConversionService.PreviewSummary summary) {}

}
