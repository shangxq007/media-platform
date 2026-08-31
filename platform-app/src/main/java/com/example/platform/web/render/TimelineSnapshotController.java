package com.example.platform.web.render;

import com.example.platform.render.app.timeline.TimelineConversionService;
import com.example.platform.timeline.app.InternalTimelineCandidateAdapter;
import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;
import com.example.platform.timeline.app.TimelineRevisionQueryService;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.app.TimelineMutationContext;
import com.example.platform.timeline.diff.calculation.TimelineSnapshotConverter;
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
@RequestMapping("/api/render/timeline-snapshots")
@Tag(name = "Timeline Snapshots", description = "Persist editor timelines for render jobs")
public class TimelineSnapshotController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TimelineRevisionSaveService revisionSaveService;
    private final TimelineRevisionQueryService revisionQueryService;
    private final TimelineConversionService conversionService;
    private final TimelineProjectAuthorizationService projectAuthorization;

    public TimelineSnapshotController(
            TimelineRevisionSaveService revisionSaveService,
            TimelineRevisionQueryService revisionQueryService,
            TimelineConversionService conversionService,
            TimelineProjectAuthorizationService projectAuthorization) {
        this.revisionSaveService = revisionSaveService;
        this.revisionQueryService = revisionQueryService;
        this.conversionService = conversionService;
        this.projectAuthorization = projectAuthorization;
    }

    @PostMapping
    @Operation(summary = "保存时间线快照", description = "将编辑器时间线 JSON 持久化并返回 snapshotId，供创建渲染任务使用")
    public ResponseEntity<SnapshotResponse> saveSnapshot(@Valid @RequestBody SaveSnapshotRequest request) {
        String tenantId = TenantContext.get();
        var actor = projectAuthorization.requireWrite(tenantId, request.projectId());
        var document = toCanonicalDocument(request);
        var revision = revisionSaveService.saveRevision(
                new TimelineMutationContext(tenantId, request.projectId(), actor),
                request.expectedCurrentRevisionId(), document);
        String snapshotId = revisionQueryService
                .findById(request.projectId(), tenantId, revision.revisionId())
                .map(TimelineRevisionQueryService.RevisionInfo::snapshotId)
                .orElseThrow(() -> new IllegalStateException(
                        "canonical revision result could not be reloaded: " + revision.revisionId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SnapshotResponse(snapshotId, request.projectId(), revision.revisionId()));
    }

    private com.example.platform.timeline.canonical.TimelineDocument toCanonicalDocument(
            SaveSnapshotRequest request) {
        try {
            if (request.payloadJson() != null && !request.payloadJson().isBlank()) {
                try {
                    return TimelineDocumentJsonSerializer.deserialize(request.payloadJson());
                } catch (IllegalArgumentException importedFormat) {
                    return importDocument(request.projectId(), request.payloadJson());
                }
            }
            if (request.editorTimeline() != null) {
                return importDocument(
                        request.projectId(), MAPPER.writeValueAsString(request.editorTimeline()));
            }
            throw new IllegalArgumentException("editorTimeline or payloadJson is required");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid timeline payload: " + e.getMessage(), e);
        }
    }

    private com.example.platform.timeline.canonical.TimelineDocument importDocument(
            String projectId, String externalJson) {
        String importProjection = conversionService.ensureInternalTimelineJson(externalJson);
        var candidate = InternalTimelineCandidateAdapter.map(projectId, importProjection);
        return TimelineSnapshotConverter.toDocument(
                TimelineSnapshotConverter.toSnapshot(candidate, "import"));
    }

    public record SaveSnapshotRequest(
            @NotBlank String projectId,
            JsonNode editorTimeline,
            String payloadJson,
            String expectedCurrentRevisionId) {}

    public record SnapshotResponse(String snapshotId, String projectId, String revisionId) {}
}
