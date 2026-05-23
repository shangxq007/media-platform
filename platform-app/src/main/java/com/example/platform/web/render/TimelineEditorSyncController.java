package com.example.platform.web.render;

import com.example.platform.render.app.timeline.TimelineEditorSyncService;
import com.example.platform.shared.web.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/render/timeline-sync")
@Tag(name = "Timeline Sync", description = "Editor v2 ↔ Internal Timeline 1.0 bidirectional sync")
public class TimelineEditorSyncController {

    private final TimelineEditorSyncService timelineEditorSyncService;

    public TimelineEditorSyncController(TimelineEditorSyncService timelineEditorSyncService) {
        this.timelineEditorSyncService = timelineEditorSyncService;
    }

    @PostMapping("/push")
    @Operation(summary = "推送：编辑器/遗留 JSON → Internal 1.0", description = "可选持久化为 internal-1.0 快照")
    public ResponseEntity<PushResponse> push(@Valid @RequestBody PushRequest request) {
        String tenantId = TenantContext.get();
        var result = timelineEditorSyncService.push(
                request.projectId(), tenantId, request.timelineJson(), request.persistSnapshot());
        return ResponseEntity.ok(toPushResponse(result));
    }

    @PostMapping("/pull")
    @Operation(summary = "拉取：快照/项目最新 → Internal + 编辑器 v2")
    public ResponseEntity<PullResponse> pull(@Valid @RequestBody(required = false) PullRequest request,
                                             @RequestParam(required = false) String projectId,
                                             @RequestParam(required = false) String snapshotId) {
        TimelineEditorSyncService.PullResult result;
        if (request != null && request.snapshotId() != null && !request.snapshotId().isBlank()) {
            result = timelineEditorSyncService.pullBySnapshotId(request.snapshotId());
        } else if (request != null && request.projectId() != null && !request.projectId().isBlank()) {
            result = timelineEditorSyncService.pullByProject(request.projectId());
        } else if (snapshotId != null && !snapshotId.isBlank()) {
            result = timelineEditorSyncService.pullBySnapshotId(snapshotId);
        } else if (projectId != null && !projectId.isBlank()) {
            result = timelineEditorSyncService.pullByProject(projectId);
        } else {
            throw new IllegalArgumentException("projectId or snapshotId is required");
        }
        return ResponseEntity.ok(toPullResponse(result));
    }

    @GetMapping("/latest")
    @Operation(summary = "拉取项目最新快照（编辑器 + Internal）")
    public ResponseEntity<PullResponse> pullLatest(@RequestParam @NotBlank String projectId) {
        var result = timelineEditorSyncService.pullByProject(projectId);
        return ResponseEntity.ok(toPullResponse(result));
    }

    @PostMapping("/sync")
    @Operation(summary = "同步：推送编辑器并返回规范化后的双向视图")
    public ResponseEntity<SyncResponse> sync(
            @Valid @RequestBody SyncRequest request, HttpServletRequest httpRequest) {
        String tenantId = TenantContext.get();
        var result = timelineEditorSyncService.sync(
                request.projectId(),
                tenantId,
                request.timelineJson(),
                resolveAuthorUserId(request.authorUserId(), httpRequest),
                request.editSessionId(),
                request.message(),
                request.source(),
                null);
        return ResponseEntity.status(HttpStatus.CREATED).body(toSyncResponse(result));
    }

    private static String resolveAuthorUserId(String requested, HttpServletRequest httpRequest) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        if (httpRequest == null) {
            return null;
        }
        Object subject = httpRequest.getAttribute("jwt.subject");
        if (subject instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        return null;
    }

    private static PushResponse toPushResponse(TimelineEditorSyncService.PushResult result) {
        var s = result.summary();
        return new PushResponse(
                result.internalTimelineJson(),
                result.sourceSchema(),
                result.alreadyInternal(),
                result.snapshotId(),
                s.sourceTrackOrLayerCount(),
                s.internalTrackOrLayerCount(),
                s.sourceClipCount(),
                s.internalClipCount(),
                s.targetRevision(),
                s.jsonByteDelta());
    }

    private static PullResponse toPullResponse(TimelineEditorSyncService.PullResult result) {
        var s = result.summary();
        var head = result.headRevision();
        return new PullResponse(
                result.editorTimelineJson(),
                result.internalTimelineJson(),
                result.snapshotId(),
                result.projectId(),
                result.storedSchemaVersion(),
                result.editorSchema(),
                result.resolvedSourceSchema(),
                s.sourceTrackOrLayerCount(),
                s.internalTrackOrLayerCount(),
                s.sourceClipCount(),
                s.internalClipCount(),
                s.targetRevision(),
                head != null ? head.id() : null,
                head != null ? head.revisionNumber() : 0,
                head != null ? head.parentRevisionId() : null);
    }

    private static SyncResponse toSyncResponse(TimelineEditorSyncService.SyncResult result) {
        var s = result.summary();
        var rev = result.revision();
        return new SyncResponse(
                result.editorTimelineJson(),
                result.internalTimelineJson(),
                result.snapshotId(),
                result.sourceSchema(),
                s.internalClipCount(),
                s.targetRevision(),
                rev != null ? rev.id() : null,
                rev != null ? rev.revisionNumber() : 0,
                rev != null ? rev.parentRevisionId() : null);
    }

    public record PushRequest(
            @NotBlank String projectId,
            @NotBlank String timelineJson,
            boolean persistSnapshot) {

        public PushRequest(String projectId, String timelineJson) {
            this(projectId, timelineJson, false);
        }
    }

    public record PullRequest(String projectId, String snapshotId) {}

    public record SyncRequest(
            @NotBlank String projectId,
            @NotBlank String timelineJson,
            String authorUserId,
            String editSessionId,
            String message,
            String source) {}

    public record PushResponse(
            String internalTimelineJson,
            String sourceSchema,
            boolean alreadyInternal,
            String snapshotId,
            int sourceTrackOrLayerCount,
            int internalTrackOrLayerCount,
            int sourceClipCount,
            int internalClipCount,
            int targetRevision,
            int jsonByteDelta) {}

    public record PullResponse(
            String editorTimelineJson,
            String internalTimelineJson,
            String snapshotId,
            String projectId,
            String storedSchemaVersion,
            String editorSchema,
            String resolvedSourceSchema,
            int sourceTrackOrLayerCount,
            int internalTrackOrLayerCount,
            int sourceClipCount,
            int internalClipCount,
            int targetRevision,
            String headRevisionId,
            int headRevisionNumber,
            String headParentRevisionId) {}

    public record SyncResponse(
            String editorTimelineJson,
            String internalTimelineJson,
            String snapshotId,
            String sourceSchema,
            int internalClipCount,
            int targetRevision,
            String revisionId,
            int revisionNumber,
            String parentRevisionId) {}
}
