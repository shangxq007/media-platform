package com.example.platform.web.render;

import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.app.timeline.TimelineEditorSyncService;
import com.example.platform.shared.web.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/render/timeline-snapshots")
@Tag(name = "Timeline Snapshots", description = "Persist editor timelines for render jobs")
public class TimelineSnapshotController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TimelineSnapshotService timelineSnapshotService;
    private final TimelineEditorSyncService timelineEditorSyncService;

    public TimelineSnapshotController(
            TimelineSnapshotService timelineSnapshotService,
            TimelineEditorSyncService timelineEditorSyncService) {
        this.timelineSnapshotService = timelineSnapshotService;
        this.timelineEditorSyncService = timelineEditorSyncService;
    }

    @PostMapping
    @Operation(summary = "保存时间线快照", description = "将编辑器时间线 JSON 持久化并返回 snapshotId，供创建渲染任务使用")
    public ResponseEntity<SnapshotResponse> saveSnapshot(@Valid @RequestBody SaveSnapshotRequest request) {
        String tenantId = TenantContext.get();
        String payload = serializePayload(request);
        String schemaVersion = request.schemaVersion() != null ? request.schemaVersion() : "2.0.0";
        String snapshotId = Boolean.TRUE.equals(request.ensureInternal())
                ? timelineEditorSyncService.saveSnapshotEnsuringInternal(
                        request.projectId(), tenantId, payload, schemaVersion)
                : timelineSnapshotService.save(request.projectId(), tenantId, payload, schemaVersion);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SnapshotResponse(snapshotId, request.projectId()));
    }

    private String serializePayload(SaveSnapshotRequest request) {
        try {
            if (request.editorTimeline() != null) {
                return MAPPER.writeValueAsString(request.editorTimeline());
            }
            if (request.payloadJson() != null && !request.payloadJson().isBlank()) {
                return request.payloadJson();
            }
            throw new IllegalArgumentException("editorTimeline or payloadJson is required");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid timeline payload: " + e.getMessage(), e);
        }
    }

    public record SaveSnapshotRequest(
            @NotBlank String projectId,
            JsonNode editorTimeline,
            String payloadJson,
            String schemaVersion,
            Boolean ensureInternal) {}

    public record SnapshotResponse(String snapshotId, String projectId) {}
}
