package com.example.platform.web.render;

import com.example.platform.render.app.operation.AddMediaClipCommand;
import com.example.platform.render.app.operation.AddMediaClipPreview;
import com.example.platform.render.app.operation.AddMediaClipResult;
import com.example.platform.render.app.operation.TimelineMediaClipOperationService;
import com.example.platform.render.app.operation.TimelineOperationException;
import com.example.platform.shared.authorization.CanonicalActorResolver;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.web.TenantContext;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * H7 backend-only projection. The controller translates transport fields into
 * typed canonical intent and delegates all mutation to the application layer.
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/timeline-operations")
public class TimelineMediaClipOperationController {

    private final TimelineMediaClipOperationService operationService;
    private final CanonicalActorResolver actorResolver;

    public TimelineMediaClipOperationController(
            TimelineMediaClipOperationService operationService,
            CanonicalActorResolver actorResolver) {
        this.operationService = operationService;
        this.actorResolver = actorResolver;
    }

    @PostMapping("/add-media-clip/preview")
    public AddMediaClipPreview preview(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @RequestBody AddMediaClipRequest request) {
        requireTransportId(tenantId, "tenantId");
        requireTransportId(projectId, "projectId");
        return operationService.preview(
                tenantId, projectId, request.toCommand(), authenticatedActor(tenantId));
    }

    @PostMapping("/add-media-clip/apply")
    public ResponseEntity<AddMediaClipResult> apply(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @RequestBody ApplyMediaClipRequest request) {
        requireTransportId(tenantId, "tenantId");
        requireTransportId(projectId, "projectId");
        var actor = authenticatedActor(tenantId);
        AddMediaClipResult applied = operationService.authorizeAndApply(
                tenantId, projectId, request.request().toCommand(),
                request.expectedPlanDigest(), request.applyCommandId(), actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(applied);
    }

    private CanonicalActor authenticatedActor(String explicitTenantId) {
        CanonicalActor actor = actorResolver.resolveCurrentActor()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "authenticated actor required"));
        String ambientTenantId = TenantContext.get();
        requireBoundedText(actor.actorId(), "principalRef", 128);
        if (!java.util.Objects.equals(explicitTenantId, ambientTenantId)
                || !java.util.Objects.equals(explicitTenantId, actor.tenantId())) {
            throw new TimelineOperationException(
                    TimelineOperationException.Code.TENANT_CONTEXT_MISMATCH,
                    List.of("explicit, ambient and authenticated tenant must match"));
        }
        return actor;
    }

    /** Transport shape uses canonical rational strings; no float time fields. */
    public record AddMediaClipRequest(
            String baseRevisionId,
            String baseContentHash,
            String trackId,
            String clipId,
            String mediaAssetId,
            String mediaStreamId,
            String artifactId,
            String contentDigest,
            String sourceStart,
            String sourceEnd,
            String timelineStart,
            String timelineEnd,
            long rateNumerator,
            long rateDenominator,
            AddMediaClipCommand.Direction direction) {

        public AddMediaClipRequest {
            requireTransportId(baseRevisionId, "baseRevisionId");
            requireDigest(baseContentHash, "baseContentHash");
            requireTransportId(trackId, "trackId");
            requireTransportId(clipId, "clipId");
            requireTransportId(mediaAssetId, "mediaAssetId");
            requireTransportId(mediaStreamId, "mediaStreamId");
            requireTransportId(artifactId, "artifactId");
            requireDigest(contentDigest, "contentDigest");
            requireRational(sourceStart, "sourceStart");
            requireRational(sourceEnd, "sourceEnd");
            requireRational(timelineStart, "timelineStart");
            requireRational(timelineEnd, "timelineEnd");
        }

        AddMediaClipCommand toCommand() {
            return new AddMediaClipCommand(
                    baseRevisionId, baseContentHash, trackId, clipId,
                    mediaAssetId, mediaStreamId, artifactId, contentDigest,
                    sourceStart, sourceEnd, timelineStart, timelineEnd,
                    rateNumerator, rateDenominator, direction);
        }
    }

    public record ApplyMediaClipRequest(
            AddMediaClipRequest request,
            String expectedPlanDigest,
            String applyCommandId) {
        public ApplyMediaClipRequest {
            if (request == null) {
                throw new TimelineTransportException("request required");
            }
            requireDigest(expectedPlanDigest, "expectedPlanDigest");
            requireTransportId(applyCommandId, "applyCommandId");
        }
    }

    @ExceptionHandler(TimelineOperationException.class)
    ResponseEntity<ProblemDetail> operationFailure(TimelineOperationException failure) {
        HttpStatus status = switch (failure.code()) {
            case STALE_BASE_REVISION, STALE_TARGET_REF, PLAN_CHANGED -> HttpStatus.CONFLICT;
            case AUTHORIZATION_DENIED, AUTHORIZATION_CONTEXT_MISMATCH, TENANT_CONTEXT_MISMATCH ->
                    HttpStatus.FORBIDDEN;
            case BASE_REVISION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case TARGET_MISSING -> HttpStatus.NOT_FOUND;
            case IDEMPOTENCY_KEY_CONFLICT -> HttpStatus.CONFLICT;
            case SOURCE_REFERENCE_INVALID, CANDIDATE_INVALID, INVALID_PLAN,
                    UNSUPPORTED_TEMPORAL_STATE, UNSUPPORTED_AUDIO_TEMPORAL_BEHAVIOR,
                    SYNC_ANCHOR_INVALIDATED, GROUP_CARDINALITY_CONFLICT,
                    PLACEMENT_CONFLICT, BATCH_CONFLICT, CANONICAL_INVARIANT_VIOLATION ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            case PERSISTENCE_FAILURE, REF_UPDATE_FAILURE, APPLY_UNKNOWN_FAILURE ->
                    HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return problem(status, failure.code().name(), failure.getMessage(), failure.failures());
    }

    @ExceptionHandler(TimelineTransportException.class)
    ResponseEntity<ProblemDetail> malformedInput(TimelineTransportException failure) {
        return problem(HttpStatus.BAD_REQUEST, "MALFORMED_INPUT", failure.getMessage(), List.of());
    }

    private static void requireTransportId(String value, String field) {
        requireBoundedText(value, field, 64);
        if (!value.matches("[A-Za-z0-9._:-]+")) {
            throw new TimelineTransportException(field + " has invalid syntax");
        }
    }

    private static void requireDigest(String value, String field) {
        if (value == null || !value.matches("[0-9a-fA-F]{64}")) {
            throw new TimelineTransportException(field + " must be a 64-character hex digest");
        }
    }

    private static void requireRational(String value, String field) {
        requireBoundedText(value, field, 64);
        if (!value.matches("[+-]?[0-9]+(?:/[1-9][0-9]*)?")) {
            throw new TimelineTransportException(field + " must use bounded rational syntax");
        }
    }

    private static void requireBoundedText(String value, String field, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new TimelineTransportException(field + " required and bounded to " + maximum);
        }
    }

    private static final class TimelineTransportException extends RuntimeException {
        private TimelineTransportException(String message) {
            super(message);
        }
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String code, String detail, List<String> failures) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:media-platform:timeline-operation:" + code.toLowerCase()));
        problem.setTitle("Timeline operation failed");
        problem.setProperty("errorCode", code);
        problem.setProperty("failures", failures);
        return ResponseEntity.status(status).body(problem);
    }
}
