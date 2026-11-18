package com.example.platform.web.render;

import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.operation.invocation.OperationInvocationContext;
import com.example.platform.operation.invocation.OperationInvocationException;
import com.example.platform.operation.invocation.OperationInvocationPort;
import com.example.platform.operation.invocation.OperationInvocationResult;
import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.semantics.selection.SelectionSpec;
import java.net.URI;
import java.util.List;
import java.util.Objects;
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

/** Authenticated transport for exact, same-track clip edits through Operation invocation. */
@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/timeline-operations/clips")
public class TimelineClipEditOperationController {
    private final OperationInvocationPort invocations;
    private final CanonicalActorResolver actors;

    public TimelineClipEditOperationController(OperationInvocationPort invocations,
                                               CanonicalActorResolver actors) {
        this.invocations = Objects.requireNonNull(invocations);
        this.actors = Objects.requireNonNull(actors);
    }

    @PostMapping("/apply")
    public ResponseEntity<OperationInvocationResult> apply(
            @PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody com.example.platform.render.app.operation.TimelineClipEditCommand body) {
        requireId(tenantId, "tenantId");
        requireId(projectId, "projectId");
        CanonicalActor actor = actors.resolveCurrentActor()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "authenticated actor required"));
        if (!tenantId.equals(TenantContext.get()) || !tenantId.equals(actor.tenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "tenant scope mismatch");
        }
        OperationRequest request = body.toOperationRequest(projectId);
        var result = invocations.invoke(request,
                new OperationInvocationContext(actor, body.commandId(), null, "timeline-http"));
        return ResponseEntity.status(result instanceof OperationInvocationResult.Applied
                ? HttpStatus.CREATED : HttpStatus.OK).body(result);
    }

    /**
     * Side-effect-free preview of the exact clip edit the same command would apply.
     *
     * <p>It runs the canonical Operation owner's preflight ({@link OperationInvocationPort#validate})
     * so source-range, duration and placement rules are evaluated by the same authority, and it
     * never writes a Revision. The client pairs it with the projected range it renders locally.
     */
    @PostMapping("/preview")
    public ResponseEntity<ClipEditPreview> preview(
            @PathVariable String tenantId, @PathVariable String projectId,
            @RequestBody com.example.platform.render.app.operation.TimelineClipEditCommand body) {
        requireId(tenantId, "tenantId");
        requireId(projectId, "projectId");
        CanonicalActor actor = actors.resolveCurrentActor()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "authenticated actor required"));
        if (!tenantId.equals(TenantContext.get()) || !tenantId.equals(actor.tenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "tenant scope mismatch");
        }
        OperationRequest request = body.toOperationRequest(projectId);
        invocations.validate(request,
                new OperationInvocationContext(actor, body.commandId(), null, "timeline-http"), projectId);
        return ResponseEntity.ok(new ClipEditPreview("VALID",
                request.definitionId().value(), body.clipId(), body.type(), body.edge(), body.delta(),
                body.splitTime(), body.rightClipId()));
    }

    /** Preview acknowledgement: the exact command shape is accepted; nothing was persisted. */
    public record ClipEditPreview(String status, String definitionId, String clipId, String type,
                                  String edge, String delta, String splitTime, String rightClipId) {}

    @ExceptionHandler(OperationInvocationException.class)
    ResponseEntity<ProblemDetail> operationFailure(OperationInvocationException failure) {
        HttpStatus status = switch (failure.code()) {
            case AUTHORIZATION_DENIED, AUTHORIZATION_CONTEXT_MISMATCH, INVALID_SCOPE -> HttpStatus.FORBIDDEN;
            case BASE_REVISION_NOT_FOUND, TARGET_MISSING -> HttpStatus.NOT_FOUND;
            case STALE_BASE_REVISION, PLAN_CHANGED, IDEMPOTENCY_CONFLICT -> HttpStatus.CONFLICT;
            case INVALID_REQUEST, INVALID_PARAMETER, UNSUPPORTED_OPERATION -> HttpStatus.BAD_REQUEST;
            case PERSISTENCE_FAILURE, APPLY_FAILURE -> HttpStatus.INTERNAL_SERVER_ERROR;
            case SOURCE_REFERENCE_INVALID, CANDIDATE_INVALID, PLACEMENT_CONFLICT,
                    CANONICAL_INVARIANT_VIOLATION -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, failure.code().name());
        problem.setType(URI.create("urn:media-platform:operation:" + failure.code().name().toLowerCase()));
        problem.setProperty("errorCode", failure.code().name());
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> invalidRequest(IllegalArgumentException failure) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, failure.getMessage());
        problem.setProperty("errorCode", "INVALID_REQUEST");
        return ResponseEntity.badRequest().body(problem);
    }

    private static void requireId(String value, String field) {
        if (value == null || value.length() > 64 || !value.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException(field + " has invalid syntax");
        }
    }

    private static void requireHash(String value, String field) {
        if (value == null || !value.matches("(?:[A-Za-z0-9+/]{43}=|[0-9a-fA-F]{64})")) {
            throw new IllegalArgumentException(field + " must be a canonical SHA-256 Timeline hash");
        }
    }

    private static MediaTime parseTime(String value) {
        return MediaTime.parse(value.contains("/") || "0".equals(value) ? value : value + "/1");
    }
}
