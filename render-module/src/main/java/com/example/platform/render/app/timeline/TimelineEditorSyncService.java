package com.example.platform.render.app.timeline;

import com.example.platform.timeline.app.InternalTimelineJson;import com.example.platform.timeline.app.TimelineRevisionService;
import com.example.platform.timeline.app.TimelinePatchService;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.adapter.TimelineSnapshotService.SnapshotInfo;
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
    private final TimelineSnapshotService timelineSnapshotService;
    private final TimelineSpecResolver timelineSpecResolver;
    private final TimelineRevisionService timelineRevisionService;

    public TimelineEditorSyncService(
            TimelineConversionService conversionService,
            InternalTimelineToEditorConverter internalToEditorConverter,
            TimelineSnapshotService timelineSnapshotService,
            TimelineSpecResolver timelineSpecResolver,
            TimelineRevisionService timelineRevisionService) {
        this.conversionService = conversionService;
        this.internalToEditorConverter = internalToEditorConverter;
        this.timelineSnapshotService = timelineSnapshotService;
        this.timelineSpecResolver = timelineSpecResolver;
        this.timelineRevisionService = timelineRevisionService;
    }

    /**
     * Editor/legacy JSON -> Internal Timeline 1.0 conversion preview (non-authoring).
     * Legacy revision persistence via TimelineRevisionService was removed in CFRH-I1
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

    public PullResult pullByProject(String projectId) {
        // CFRH-I1: legacy backfill write authority removed (DELETE_OBSOLETE_PRODUCT_BEHAVIOR);
        // no revision is created merely because HEAD is absent.
        Optional<TimelineRevisionService.RevisionInfo> head = timelineRevisionService.findHead(projectId);
        if (head.isPresent()) {
            return pullBySnapshotId(head.get().snapshotId());
        }
        Optional<SnapshotInfo> latest = timelineSnapshotService.findLatestByProject(projectId);
        if (latest.isEmpty()) {
            throw new IllegalArgumentException("No timeline snapshot for project: " + projectId);
        }
        return pullSnapshot(latest.get(), null);
    }

    public PullResult pullBySnapshotId(String snapshotId) {
        Optional<SnapshotInfo> info = timelineSnapshotService.findById(snapshotId);
        if (info.isEmpty()) {
            throw new IllegalArgumentException("Timeline snapshot not found: " + snapshotId);
        }
        return pullSnapshot(info.get(), null);
    }

    private PullResult pullSnapshot(SnapshotInfo info, TimelineRevisionService.RevisionInfo headRevision) {
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
        TimelineRevisionService.RevisionInfo revisionMeta = headRevision;
        if (revisionMeta == null) {
            revisionMeta = timelineRevisionService.findHead(info.projectId()).orElse(null);
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
            TimelineRevisionService.RevisionInfo revision,
            TimelineConversionService.PreviewSummary summary) {}

    public record PullResult(
            String editorTimelineJson,
            String internalTimelineJson,
            String snapshotId,
            String projectId,
            String storedSchemaVersion,
            String editorSchema,
            String resolvedSourceSchema,
            TimelineRevisionService.RevisionInfo headRevision,
            TimelineConversionService.PreviewSummary summary) {}

}
