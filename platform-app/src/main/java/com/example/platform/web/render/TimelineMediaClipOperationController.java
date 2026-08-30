package com.example.platform.web.render;

import com.example.platform.render.app.operation.AddOrTrimMediaClipCommand;
import com.example.platform.render.app.operation.AddOrTrimMediaClipPreview;
import com.example.platform.render.app.operation.AddOrTrimMediaClipResult;
import com.example.platform.render.app.operation.TimelineMediaClipOperationService;
import com.example.platform.render.app.operation.TimelineOperationException;
import com.example.platform.shared.authorization.CanonicalActorResolver;
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

    @PostMapping("/add-or-trim-media-clip/preview")
    public AddOrTrimMediaClipPreview preview(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @RequestBody AddMediaClipRequest request) {
        return operationService.preview(tenantId, projectId, request.toCommand());
    }

    @PostMapping("/add-or-trim-media-clip/apply")
    public ResponseEntity<AddOrTrimMediaClipResult> apply(
            @PathVariable String tenantId,
            @PathVariable String projectId,
            @RequestBody ApplyMediaClipRequest request) {
        var actor = actorResolver.resolveCurrentActor()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "authenticated actor required"));
        AddOrTrimMediaClipResult applied = operationService.authorizeAndApply(
                tenantId, projectId, request.request().toCommand(),
                request.expectedPlanDigest(), request.applyCommandId(), actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(applied);
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
            AddOrTrimMediaClipCommand.Direction direction) {

        AddOrTrimMediaClipCommand toCommand() {
            return new AddOrTrimMediaClipCommand(
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
            if (request == null || expectedPlanDigest == null || expectedPlanDigest.isBlank()
                    || applyCommandId == null || applyCommandId.isBlank()) {
                throw new IllegalArgumentException(
                        "request, expectedPlanDigest and applyCommandId required");
            }
        }
    }

    @ExceptionHandler(TimelineOperationException.class)
    ResponseEntity<ProblemDetail> operationFailure(TimelineOperationException failure) {
        HttpStatus status = switch (failure.code()) {
            case STALE_BASE_REVISION, STALE_TARGET_REF, PLAN_CHANGED -> HttpStatus.CONFLICT;
            case AUTHORIZATION_DENIED, AUTHORIZATION_CONTEXT_MISMATCH -> HttpStatus.FORBIDDEN;
            case BASE_REVISION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case SOURCE_REFERENCE_INVALID, CANDIDATE_INVALID -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return problem(status, failure.code().name(), failure.getMessage(), failure.failures());
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
