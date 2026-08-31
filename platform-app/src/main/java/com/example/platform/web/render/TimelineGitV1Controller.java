package com.example.platform.web.render;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.app.PatchApplyResult;
import com.example.platform.timeline.app.PatchPreviewResult;
import com.example.platform.timeline.app.TimelineRevisionQueryService;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.render.app.timeline.RenderJobRevisionPinningService;
import com.example.platform.timeline.app.TimelinePatchApplicationService;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.app.TimelineSemanticDiffV1Service;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.diff.ChangeSummary;
import com.example.platform.timeline.diff.TimelineChange;
import com.example.platform.timeline.diff.TimelineChangeSet;
import com.example.platform.timeline.patch.PatchError;
import com.example.platform.timeline.patch.PatchErrorCode;
import com.example.platform.timeline.patch.PatchExecutionException;
import com.example.platform.timeline.patch.TimelinePatch;
import com.example.platform.timeline.version.TimelineConflictException;
import com.example.platform.timeline.version.TimelineRevision;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/timeline-git")
@Tag(name = "Timeline Git V1", description = "Immutable revision history and render pinning")
public class TimelineGitV1Controller {

    private final TimelineRevisionSaveService saveService;
    private final TimelineRevisionQueryService revisionQueryService;
    private final RenderJobRevisionPinningService pinningService;
    private final TimelineContentDigester contentDigester;
    private final TimelineSemanticDiffV1Service diffService;
    private final TimelinePatchApplicationService patchService;
    private final TimelineProjectAuthorizationService projectAuthorization;

    public TimelineGitV1Controller(TimelineRevisionSaveService saveService,
                                   TimelineRevisionQueryService revisionQueryService,
                                   RenderJobRevisionPinningService pinningService,
                                   TimelineContentDigester contentDigester,
                                   TimelineSemanticDiffV1Service diffService,
                                   TimelinePatchApplicationService patchService,
                                   TimelineProjectAuthorizationService projectAuthorization) {
        this.saveService = saveService;
        this.revisionQueryService = revisionQueryService;
        this.pinningService = pinningService;
        this.contentDigester = contentDigester;
        this.diffService = diffService;
        this.patchService = patchService;
        this.projectAuthorization = projectAuthorization;
    }

    @PostMapping("/products/{productId}/revisions")
    @Operation(summary = "Create first or new TimelineRevision")
    public ResponseEntity<RevisionResponse> saveRevision(
            @PathVariable String productId,
            @RequestBody SaveRevisionRequest request) {
        String tenantId = TenantContext.get();
        var actor = projectAuthorization.requireWrite(tenantId, productId);
        var document = request.toDocument();
        var revision = saveService.saveRevision(tenantId, productId,
                request.expectedCurrentRevisionId(), document, actor.actorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(RevisionResponse.from(revision));
    }

    @GetMapping("/products/{productId}/revisions/current")
    @Operation(summary = "Get current revision for product")
    public ResponseEntity<CurrentRevisionResponse> getCurrentRevision(
            @PathVariable String productId) {
        String tenantId = TenantContext.get();
        projectAuthorization.requireRead(tenantId, productId);
        var current = revisionQueryService.findHead(productId, tenantId);
        if (current.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String currentRevisionId = current.get().id();
        var revision = saveService.findById(tenantId, currentRevisionId);
        return ResponseEntity.ok(new CurrentRevisionResponse(currentRevisionId, revision));
    }

    @GetMapping("/products/{productId}/revisions/{revisionId}")
    @Operation(summary = "Get revision by ID")
    public ResponseEntity<RevisionResponse> getRevision(
            @PathVariable String productId,
            @PathVariable String revisionId) {
        String tenantId = TenantContext.get();
        projectAuthorization.requireRead(tenantId, productId);
        if (revisionQueryService.findById(productId, tenantId, revisionId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var revision = saveService.findById(tenantId, revisionId);
        if (revision == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(RevisionResponse.from(revision));
    }

    @PostMapping("/products/{productId}/revisions/{historicalRevisionId}/restore")
    @Operation(summary = "Restore historical revision as new revision")
    public ResponseEntity<RevisionResponse> restoreRevision(
            @PathVariable String productId,
            @PathVariable String historicalRevisionId,
            @RequestBody RestoreRequest request) {
        String tenantId = TenantContext.get();
        var actor = projectAuthorization.requireWrite(tenantId, productId);
        var revision = saveService.restoreRevision(tenantId, productId,
                historicalRevisionId, request.expectedCurrentRevisionId(), actor.actorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(RevisionResponse.from(revision));
    }

    @PostMapping("/render-jobs")
    @Operation(summary = "Create RenderJob pinned to a revision")
    public ResponseEntity<RenderJobResponse> createRenderJob(
            @RequestBody CreateRenderJobRequest request) {
        projectAuthorization.requireWrite(TenantContext.get(), request.productId());
        String jobId = UUID.randomUUID().toString();
        pinningService.createRenderJobWithRevision(jobId, request.productId(),
                request.timelineRevisionId(), request.backend());
        return ResponseEntity.status(HttpStatus.CREATED).body(new RenderJobResponse(jobId));
    }

    @ExceptionHandler(TimelineConflictException.class)
    public ResponseEntity<ConflictError> handleConflict(TimelineConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ConflictError(
                        TimelineConflictException.ERROR_CODE,
                        ex.getMessage(),
                        ex.getProductId(),
                        ex.getExpectedRevisionId(),
                        ex.getActualRevisionId()));
    }

    @GetMapping("/products/{productId}/diff")
    @Operation(summary = "Compute semantic diff between two revisions")
    public ResponseEntity<DiffResponse> getDiff(
            @PathVariable String productId,
            @RequestParam String baseRevisionId,
            @RequestParam String targetRevisionId) {
        projectAuthorization.requireRead(TenantContext.get(), productId);
        var changeSet = diffService.diff(
                TenantContext.get(), productId, baseRevisionId, targetRevisionId);
        return ResponseEntity.ok(DiffResponse.from(changeSet));
    }

    @ExceptionHandler(com.example.platform.timeline.diff.TimelineDiffErrors.TimelineDiffException.class)
    public ResponseEntity<DiffError> handleDiffError(
            com.example.platform.timeline.diff.TimelineDiffErrors.TimelineDiffException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new DiffError(ex.getErrorCode(), ex.getMessage()));
    }

    @PostMapping("/products/{productId}/patch/preview")
    @Operation(summary = "Preview patch application (dry run)")
    public ResponseEntity<PatchPreviewResponse> previewPatch(
            @PathVariable String productId,
            @RequestBody PatchRequest request) {
        projectAuthorization.requireRead(TenantContext.get(), productId);
        var patch = request.toPatch(productId);
        var result = patchService.preview(TenantContext.get(), patch);
        if (result instanceof PatchPreviewResult.Failure failure) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new PatchPreviewResponse(failure.error().code().name(), failure.error().message(), null, false));
        }
        return ResponseEntity.ok(new PatchPreviewResponse(null, null, ((PatchPreviewResult.Success) result).resultDigest(), false));
    }

    @PostMapping("/products/{productId}/patch/apply")
    @Operation(summary = "Apply patch and create new revision")
    public ResponseEntity<PatchApplyResponse> applyPatch(
            @PathVariable String productId,
            @RequestBody PatchRequest request) {
        String tenantId = TenantContext.get();
        var actor = projectAuthorization.requireWrite(tenantId, productId);
        var patch = request.toPatch(productId);
        var result = patchService.apply(tenantId, actor.actorId(), patch);
        if (result instanceof PatchApplyResult.Failure failure) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new PatchApplyResponse(failure.error().code().name(), failure.error().message(), null, null, null, false));
        }
        if (result instanceof PatchApplyResult.NoChanges noChanges) {
            return ResponseEntity.ok(new PatchApplyResponse(null, null, noChanges.baseRevisionId(), null, null, false));
        }
        PatchApplyResult.Success success = (PatchApplyResult.Success) result;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new PatchApplyResponse(null, null, success.newRevisionId(), success.parentRevisionId(), success.resultDigest(), true));
    }

    @ExceptionHandler(PatchExecutionException.class)
    public ResponseEntity<PatchError> handlePatchException(PatchExecutionException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new PatchError(PatchErrorCode.TIMELINE_PATCH_PRECONDITION_FAILED, ex.getMessage(), null, null));
    }

    // Request/Response DTOs

    public record SaveRevisionRequest(
            String expectedCurrentRevisionId,
            List<TrackDto> tracks) {
        TimelineDocument toDocument() {
            var tracks = this.tracks().stream().map(TrackDto::toTrack).toList();
            return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, tracks,
                    new TimelineMetadata("", "", Map.of()));
        }
    }

    public record TrackDto(String trackId, String name, String type, List<ClipDto> clips) {
        TimelineTrack toTrack() {
            var typeEnum = TrackType.valueOf(type.toUpperCase());
            var clips = this.clips().stream().map(ClipDto::toClip).toList();
            return new TimelineTrack(trackId, name, typeEnum, clips);
        }
    }

    public record ClipDto(String clipId, String assetId, long startMs, long endMs) {
        TimelineClip toClip() {
            // API projection boundary: ms input projected to exact MediaTime
            // (integer microseconds, never floating point).
            return new TimelineClip(clipId, assetId,
                    null, null, null,
                    MediaTime.ofMicros(startMs * 1000L), MediaTime.ofMicros(endMs * 1000L),
                    MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        }
    }

    public record RestoreRequest(String expectedCurrentRevisionId) {}

    public record CreateRenderJobRequest(String productId, String timelineRevisionId, String backend) {}

    public record RevisionResponse(
            String revisionId,
            String productId,
            String parentRevisionId,
            String schemaVersion,
            String contentDigest,
            Instant createdAt,
            String createdBy) {
        static RevisionResponse from(TimelineRevision revision) {
            return new RevisionResponse(
                    revision.revisionId(), revision.productId(), revision.parentRevisionId(),
                    revision.timelineSchemaVersion(), revision.contentDigest(),
                    revision.createdAt(), revision.createdBy());
        }
    }

    public record CurrentRevisionResponse(String revisionId, TimelineRevision revision) {}

    public record RenderJobResponse(String jobId) {}

    public record ConflictError(
            String code,
            String message,
            String productId,
            String expectedRevisionId,
            String actualRevisionId) {}

    public record DiffError(String code, String message) {}

    public record DiffResponse(
            String changeSetVersion,
            String productId,
            String baseRevisionId,
            String targetRevisionId,
            String baseDigest,
            String targetDigest,
            String schemaVersion,
            List<ChangeDto> changes,
            SummaryDto summary) {

        static DiffResponse from(TimelineChangeSet cs) {
            List<ChangeDto> changes = cs.getChanges().stream()
                    .map(c -> new ChangeDto(
                            c.getChangeType().name(),
                            c.getEntityKind().name(),
                            c.getEntityId(),
                            c.getPropertyName(),
                            c.getBeforeValue(),
                            c.getAfterValue(),
                            c.getTargetPosition()))
                    .toList();

            ChangeSummary s = cs.getSummary();
            SummaryDto summary = new SummaryDto(
                    s.getTotal(),
                    s.getTracksAdded(),
                    s.getTracksRemoved(),
                    s.getTracksChanged(),
                    s.getTracksReordered(),
                    s.getClipsAdded(),
                    s.getClipsRemoved(),
                    s.getClipsChanged(),
                    s.getClipsMoved(),
                    s.getClipsReordered());

            return new DiffResponse(
                    cs.getChangeSetVersion(),
                    cs.getProductId(),
                    cs.getBaseRevisionId(),
                    cs.getTargetRevisionId(),
                    cs.getBaseContentDigest(),
                    cs.getTargetContentDigest(),
                    cs.getTimelineSchemaVersion(),
                    changes,
                    summary);
        }
    }

    public record ChangeDto(
            String changeType,
            String entityKind,
            String entityId,
            String propertyName,
            String beforeValue,
            String afterValue,
            int targetPosition) {}

    public record SummaryDto(
            int total,
            int tracksAdded,
            int tracksRemoved,
            int tracksChanged,
            int tracksReordered,
            int clipsAdded,
            int clipsRemoved,
            int clipsChanged,
            int clipsMoved,
            int clipsReordered) {}

    // Patch DTOs

    public record PatchRequest(
            String patchVersion,
            String patchId,
            String baseRevisionId,
            String baseContentDigest,
            String expectedCurrentRevisionId,
            String timelineSchemaVersion,
            List<PatchOperationDto> operations,
            String expectedResultDigest) {

        TimelinePatch toPatch(String productId) {
            var ops = operations().stream().map(PatchOperationDto::toOperation).toList();
            return new TimelinePatch(
                    patchVersion(),
                    patchId(),
                    productId,
                    baseRevisionId(),
                    baseContentDigest(),
                    expectedCurrentRevisionId(),
                    timelineSchemaVersion(),
                    ops,
                    expectedResultDigest(),
                    null);
        }
    }

    public record PatchOperationDto(
            String operationId,
            String kind,
            String trackId,
            String clipId,
            String targetTrackId,
            String expectedTrackId,
            String expectedSourceTrackId,
            String property,
            String expectedBefore,
            String newValue,
            Integer targetPosition,
            TrackDto track,
            ClipDto clip) {

        com.example.platform.timeline.patch.TimelinePatchOperation toOperation() {
            return switch (kind()) {
                case "ADD_TRACK" -> new com.example.platform.timeline.patch.TimelinePatchOperation.AddTrack(operationId(), track().toTrack(), targetPosition());
                case "REMOVE_TRACK" -> new com.example.platform.timeline.patch.TimelinePatchOperation.RemoveTrack(operationId(), trackId());
                case "UPDATE_TRACK_PROPERTY" -> new com.example.platform.timeline.patch.TimelinePatchOperation.UpdateTrackProperty(operationId(), trackId(), property(), expectedBefore(), newValue());
                case "REORDER_TRACK" -> new com.example.platform.timeline.patch.TimelinePatchOperation.ReorderTrack(operationId(), trackId(), targetPosition());
                case "ADD_CLIP" -> new com.example.platform.timeline.patch.TimelinePatchOperation.AddClip(operationId(), targetTrackId(), clip().toClip(), targetPosition());
                case "REMOVE_CLIP" -> new com.example.platform.timeline.patch.TimelinePatchOperation.RemoveClip(operationId(), clipId(), expectedTrackId());
                case "UPDATE_CLIP_PROPERTY" -> new com.example.platform.timeline.patch.TimelinePatchOperation.UpdateClipProperty(operationId(), clipId(), property(), expectedBefore(), newValue());
                case "MOVE_CLIP" -> new com.example.platform.timeline.patch.TimelinePatchOperation.MoveClip(operationId(), clipId(), expectedSourceTrackId(), targetTrackId(), targetPosition());
                case "REORDER_CLIP" -> new com.example.platform.timeline.patch.TimelinePatchOperation.ReorderClip(operationId(), clipId(), trackId(), targetPosition());
                default -> throw new IllegalArgumentException("Unknown operation kind: " + kind());
            };
        }
    }

    public record PatchPreviewResponse(String error, String message, String resultDigest, boolean persisted) {}

    public record PatchApplyResponse(String error, String message, String revisionId, String parentRevisionId, String resultDigest, boolean persisted) {}

}
