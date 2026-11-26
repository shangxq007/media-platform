package com.example.platform.web.render;

import com.example.platform.operation.invocation.OperationInvocationContext;
import com.example.platform.operation.invocation.OperationInvocationException;
import com.example.platform.operation.invocation.OperationInvocationPort;
import com.example.platform.operation.invocation.OperationInvocationResult;
import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.operation.application.TimelineMediaClipOperationService;
import com.example.platform.timeline.canonical.TrackType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Initializes or adds tracks to the canonical Timeline through Operation. */
@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/timeline-operations")
public final class TimelineInitializeOperationController {
    private final TimelineProjectAuthorizationService authorization;
    private final OperationInvocationPort invocations;

    public TimelineInitializeOperationController(TimelineProjectAuthorizationService authorization,
            OperationInvocationPort invocations) {
        this.authorization = authorization;
        this.invocations = invocations;
    }

    @PostMapping("/initialize")
    public ResponseEntity<OperationInvocationResult.Applied> initialize(
            @PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody InitializeRequest body) {
        var actor = authorization.requireWrite(tenantId, projectId);
        var definition = OperationDefinition.V3.INITIALIZE_TIMELINE;
        var request = new OperationRequest(definition.definitionId(), definition.version(),
                new OperationTargetRequest.TimelineTargetRequest(projectId),
                new OperationParameters.InitializeTimelineParameters(body.trackId(), body.subtitleTrackLanguage()),
                TimelineMediaClipOperationService.INITIAL_BASE_REVISION_ID,
                TimelineMediaClipOperationService.INITIAL_BASE_CONTENT_HASH, null);
        var result = invocations.invoke(request,
                new OperationInvocationContext(actor, body.commandId(), null, "timeline-http"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body((OperationInvocationResult.Applied) result);
    }

    @PostMapping("/tracks")
    public OperationInvocationResult addTrack(@PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody AddTrackRequest body) {
        var actor = authorization.requireWrite(tenantId, projectId);
        var definition = OperationDefinition.V3.ADD_TRACK;
        var request = new OperationRequest(definition.definitionId(), definition.version(),
                new OperationTargetRequest.TimelineTargetRequest(projectId),
                new OperationParameters.AddTrackParameters(body.trackId(), body.trackType()),
                body.baseRevisionId(), body.baseContentHash(), null);
        return invocations.invoke(request,
                new OperationInvocationContext(actor, body.commandId(), null, "timeline-http"));
    }

    @ExceptionHandler(OperationInvocationException.class)
    ResponseEntity<ProblemDetail> operationFailure(OperationInvocationException failure) {
        return TimelineClipEditOperationController.operationFailureResponse(failure);
    }

    public record AddTrackRequest(String trackId, TrackType trackType,
            String baseRevisionId, String baseContentHash, String commandId) {
        public AddTrackRequest {
            if (commandId == null || !commandId.matches("[A-Za-z0-9._:-]{1,128}")) {
                throw new IllegalArgumentException("bounded commandId required");
            }
        }
    }

    public record InitializeRequest(String trackId, String commandId, String subtitleTrackLanguage) {
        public InitializeRequest(String trackId, String commandId) { this(trackId, commandId, null); }
        public InitializeRequest {
            if (trackId == null || !trackId.matches("[A-Za-z0-9._:-]{1,64}")
                    || commandId == null || !commandId.matches("[A-Za-z0-9._:-]{1,128}")) {
                throw new IllegalArgumentException("bounded trackId and commandId required");
            }
        }
    }
}
