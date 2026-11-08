package com.example.platform.web.render;

import com.example.platform.timeline.api.revision.TimelineRevisionQueries;
import com.example.platform.render.app.timeline.TimelineConversionService;
import com.example.platform.timeline.app.InternalTimelineCandidateAdapter;
import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;
import com.example.platform.timeline.api.revision.TimelineRevisionCommands;
import com.example.platform.timeline.api.revision.TimelineMutationContext;
import com.example.platform.timeline.diff.calculation.TimelineSnapshotConverter;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.render.app.operation.TimelineMediaClipOperationService;
import com.example.platform.operation.invocation.OperationInvocationPort;
import com.example.platform.operation.invocation.OperationInvocationContext;
import com.example.platform.operation.invocation.OperationInvocationResult;
import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.timeline.canonical.TimelineContentDigester;
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

    private final TimelineRevisionCommands revisionSaveService;
    private final TimelineRevisionQueries revisionQueryService;
    private final TimelineConversionService conversionService;
    private final TimelineProjectAuthorizationService projectAuthorization;
    private final OperationInvocationPort invocations;

    public TimelineSnapshotController(
            TimelineRevisionCommands revisionSaveService,
            TimelineRevisionQueries revisionQueryService,
            TimelineConversionService conversionService,
            TimelineProjectAuthorizationService projectAuthorization,
            OperationInvocationPort invocations) {
        this.revisionSaveService = revisionSaveService;
        this.revisionQueryService = revisionQueryService;
        this.conversionService = conversionService;
        this.projectAuthorization = projectAuthorization;
        this.invocations = invocations;
    }

    @PostMapping
    @Operation(summary = "保存时间线快照", description = "将编辑器时间线 JSON 持久化并返回 snapshotId，供创建渲染任务使用")
    public ResponseEntity<SnapshotResponse> saveSnapshot(@Valid @RequestBody SaveSnapshotRequest request) {
        String tenantId = TenantContext.get();
        var actor = projectAuthorization.requireWrite(tenantId, request.projectId());
        var document = toCanonicalDocument(request);
        String baseRevisionId = request.expectedCurrentRevisionId() == null
                ? TimelineMediaClipOperationService.INITIAL_BASE_REVISION_ID
                : request.expectedCurrentRevisionId();
        String baseHash = request.expectedCurrentRevisionId() == null
                ? TimelineMediaClipOperationService.INITIAL_BASE_CONTENT_HASH
                : java.util.Optional.ofNullable(revisionSaveService.findById(
                        tenantId, request.expectedCurrentRevisionId()))
                        .filter(revision -> request.projectId().equals(revision.productId()))
                        .map(revision -> revision.semanticContext().timelineContentDigest())
                        .orElseThrow(() -> new IllegalArgumentException("import base revision unavailable"));
        String contentHash = new TimelineContentDigester().digest(document);
        String commandId = "import-" + sha256(tenantId + "\0" + request.projectId() + "\0"
                + baseRevisionId + "\0" + contentHash);
        var definition = OperationDefinition.V3.IMPORT_TIMELINE;
        var result = invocations.invoke(new OperationRequest(
                definition.definitionId(), definition.version(),
                new OperationTargetRequest.TimelineTargetRequest(request.projectId()),
                new OperationParameters.ImportTimelineParameters(document),
                baseRevisionId, baseHash, null),
                new OperationInvocationContext(actor, commandId, null, "timeline-import-http"));
        String revisionId = result instanceof OperationInvocationResult.Applied applied
                ? applied.newRevisionId() : baseRevisionId;
        String snapshotId = revisionQueryService
                .findById(request.projectId(), tenantId, revisionId)
                .map(TimelineRevisionQueries.RevisionInfo::snapshotId)
                .orElseThrow(() -> new IllegalStateException(
                        "canonical revision result could not be reloaded: " + revisionId));
        return ResponseEntity.status(result instanceof OperationInvocationResult.Applied
                        ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new SnapshotResponse(snapshotId, request.projectId(), revisionId));
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
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
