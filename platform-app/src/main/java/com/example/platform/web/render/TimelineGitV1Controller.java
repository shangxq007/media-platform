package com.example.platform.web.render;

import com.example.platform.render.app.timeline.ProductCurrentRevisionService;
import com.example.platform.render.app.timeline.RenderJobRevisionPinningService;
import com.example.platform.render.app.timeline.TimelineRevisionSaveService;
import com.example.platform.render.domain.timeline.canonical.TimelineContentDigester;
import com.example.platform.render.domain.timeline.canonical.TimelineDocument;
import com.example.platform.render.domain.timeline.canonical.TimelineClip;
import com.example.platform.render.domain.timeline.canonical.TimelineTrack;
import com.example.platform.render.domain.timeline.canonical.TimelineMetadata;
import com.example.platform.render.domain.timeline.canonical.TrackType;
import com.example.platform.render.domain.timeline.version.TimelineConflictException;
import com.example.platform.render.domain.timeline.version.TimelineRevision;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/timeline-git")
@Tag(name = "Timeline Git V1", description = "Immutable revision history and render pinning")
public class TimelineGitV1Controller {

    private final TimelineRevisionSaveService saveService;
    private final ProductCurrentRevisionService currentRevisionService;
    private final RenderJobRevisionPinningService pinningService;
    private final TimelineContentDigester contentDigester;

    public TimelineGitV1Controller(TimelineRevisionSaveService saveService,
                                   ProductCurrentRevisionService currentRevisionService,
                                   RenderJobRevisionPinningService pinningService,
                                   TimelineContentDigester contentDigester) {
        this.saveService = saveService;
        this.currentRevisionService = currentRevisionService;
        this.pinningService = pinningService;
        this.contentDigester = contentDigester;
    }

    @PostMapping("/products/{productId}/revisions")
    @Operation(summary = "Create first or new TimelineRevision")
    public ResponseEntity<RevisionResponse> saveRevision(
            @PathVariable String productId,
            @RequestBody SaveRevisionRequest request) {
        var document = request.toDocument();
        var revision = saveService.saveRevision(productId, request.expectedCurrentRevisionId(),
                document, request.createdBy());
        return ResponseEntity.status(HttpStatus.CREATED).body(RevisionResponse.from(revision));
    }

    @GetMapping("/products/{productId}/revisions/current")
    @Operation(summary = "Get current revision for product")
    public ResponseEntity<CurrentRevisionResponse> getCurrentRevision(
            @PathVariable String productId) {
        String currentRevisionId = currentRevisionService.getCurrentRevisionId(productId);
        if (currentRevisionId == null) {
            return ResponseEntity.notFound().build();
        }
        var revision = saveService.findById(currentRevisionId);
        return ResponseEntity.ok(new CurrentRevisionResponse(currentRevisionId, revision));
    }

    @GetMapping("/revisions/{revisionId}")
    @Operation(summary = "Get revision by ID")
    public ResponseEntity<RevisionResponse> getRevision(
            @PathVariable String revisionId) {
        var revision = saveService.findById(revisionId);
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
        var revision = saveService.restoreRevision(productId, historicalRevisionId,
                request.expectedCurrentRevisionId(), request.createdBy());
        return ResponseEntity.status(HttpStatus.CREATED).body(RevisionResponse.from(revision));
    }

    @PostMapping("/render-jobs")
    @Operation(summary = "Create RenderJob pinned to a revision")
    public ResponseEntity<RenderJobResponse> createRenderJob(
            @RequestBody CreateRenderJobRequest request) {
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

    // Request/Response DTOs

    public record SaveRevisionRequest(
            String expectedCurrentRevisionId,
            String createdBy,
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
            return new TimelineClip(clipId, assetId,
                    Duration.ofMillis(startMs), Duration.ofMillis(endMs),
                    Duration.ZERO, Duration.ZERO);
        }
    }

    public record RestoreRequest(String expectedCurrentRevisionId, String createdBy) {}

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
}
