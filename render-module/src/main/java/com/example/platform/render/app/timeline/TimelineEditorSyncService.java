package com.example.platform.render.app.timeline;

import com.example.platform.render.app.TimelinePatchService;
import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.app.TimelineSnapshotService.SnapshotInfo;
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

    public PushResult push(String projectId, String tenantId, String timelineJson, boolean persistSnapshot) {
        TimelineConversionService.PreviewResult preview = conversionService.preview(timelineJson);
        String internal = preview.internalTimelineJson();
        String snapshotId = null;
        String storedSchema = "internal-1.0";
        TimelineRevisionService.RevisionInfo revisionInfo = null;
        if (persistSnapshot) {
            snapshotId = timelineSnapshotService.save(projectId, tenantId, internal, storedSchema);
            revisionInfo = timelineRevisionService.recordRevision(
                    projectId, tenantId, snapshotId, internal, "push", null, null, null);
        }
        return new PushResult(
                internal,
                preview.sourceSchema(),
                preview.alreadyInternal(),
                snapshotId,
                revisionInfo,
                preview.summary());
    }

    public PullResult pullByProject(String projectId) {
        timelineRevisionService.backfillHeadFromLatestSnapshot(projectId, null);
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

    public SyncResult sync(String projectId, String tenantId, String editorTimelineJson) {
        return sync(projectId, tenantId, editorTimelineJson, null, null, null);
    }

    public SyncResult sync(
            String projectId,
            String tenantId,
            String editorTimelineJson,
            String authorUserId,
            String editSessionId,
            String message) {
        return sync(projectId, tenantId, editorTimelineJson, authorUserId, editSessionId, message, null, null);
    }

    public SyncResult sync(
            String projectId,
            String tenantId,
            String editorTimelineJson,
            String authorUserId,
            String editSessionId,
            String message,
            String source,
            List<TimelinePatchService.PatchOperation> patchOperations) {
        TimelineConversionService.PreviewResult preview = conversionService.preview(editorTimelineJson);
        String internal = preview.internalTimelineJson();
        String snapshotId = timelineSnapshotService.save(projectId, tenantId, internal, "internal-1.0");
        String effectiveSource = resolveSyncSource(source, editSessionId);
        TimelineRevisionService.RevisionInfo revision = timelineRevisionService.recordRevision(
                projectId,
                tenantId,
                snapshotId,
                internal,
                effectiveSource,
                authorUserId,
                editSessionId,
                message,
                patchOperations);
        String editorJson = internalToEditorConverter.toEditorJson(internal);
        return new SyncResult(
                editorJson,
                internal,
                snapshotId,
                revision,
                preview.sourceSchema(),
                preview.summary());
    }

    private static String resolveSyncSource(String source, String editSessionId) {
        if (source != null && !source.isBlank()) {
            return source;
        }
        if (editSessionId != null && !editSessionId.isBlank()) {
            return "ai-sync";
        }
        return "sync";
    }

    public String saveSnapshotEnsuringInternal(
            String projectId, String tenantId, String timelineJson, String requestedSchemaVersion) {
        String internal = conversionService.ensureInternalTimelineJson(timelineJson);
        String snapshotId = timelineSnapshotService.save(projectId, tenantId, internal, "internal-1.0");
        timelineRevisionService.recordRevision(
                projectId, tenantId, snapshotId, internal, "snapshot", null, null, null);
        return snapshotId;
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

    public record SyncResult(
            String editorTimelineJson,
            String internalTimelineJson,
            String snapshotId,
            TimelineRevisionService.RevisionInfo revision,
            String sourceSchema,
            TimelineConversionService.PreviewSummary summary) {}
}
