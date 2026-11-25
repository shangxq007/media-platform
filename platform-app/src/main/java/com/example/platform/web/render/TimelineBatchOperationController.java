package com.example.platform.web.render;

import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.operation.batch.BatchCommitResult;
import com.example.platform.operation.batch.BatchErrorCode;
import com.example.platform.operation.batch.BatchException;
import com.example.platform.operation.batch.BatchInvocationPort;
import com.example.platform.operation.batch.BatchPreview;
import com.example.platform.operation.batch.OperationBatchContext;
import com.example.platform.operation.batch.OperationBatchRequest;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.operation.application.TimelineBatchCommand;
import com.example.platform.operation.application.TimelineBatchCommandMapper;
import com.example.platform.shared.web.TenantContext;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
 * Authenticated Timeline-domain entry for ordered Operation batches.
 *
 * <p>Preview plans and validates the full ordered chain without writing; apply
 * commits exactly one revision (or the semantic NO_OP) through the canonical
 * batch port. Single-operation endpoints are untouched.
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/projects/{projectId}/timeline-operations/batch/v2")
public class TimelineBatchOperationController {

    private static final ObjectMapper BATCH_JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final BatchInvocationPort invocations;
    private final TimelineBatchCommandMapper mapper;
    private final CanonicalActorResolver actors;

    public TimelineBatchOperationController(BatchInvocationPort invocations,
                                            TimelineBatchCommandMapper mapper,
                                            CanonicalActorResolver actors) {
        this.invocations = invocations;
        this.mapper = mapper;
        this.actors = actors;
    }

    @PostMapping(value = "/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BatchPreview> preview(
            @PathVariable String tenantId, @PathVariable String projectId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = TimelineBatchCommand.class)))
            @RequestBody String body) {
        CanonicalActor actor = requireActor(tenantId, projectId);
        OperationBatchRequest batch = mapper.toBatchRequest(tenantId, projectId, decode(body));
        return ResponseEntity.ok(invocations.preview(batch,
                OperationBatchContext.preview(actor, "timeline-batch-http")));
    }

    @PostMapping(value = "/apply", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BatchCommitResult> apply(
            @PathVariable String tenantId, @PathVariable String projectId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(schema = @Schema(implementation = TimelineBatchCommand.class)))
            @RequestBody String body,
            @org.springframework.web.bind.annotation.RequestHeader(value = "If-Match", required = false) String confirmation) {
        CanonicalActor actor = requireActor(tenantId, projectId);
        OperationBatchRequest batch = mapper.toBatchRequest(tenantId, projectId, decode(body));
        BatchCommitResult result = invocations.commit(batch,
                OperationBatchContext.apply(batch.batchId(), actor, null, "timeline-batch-http", planDigest(confirmation)));
        HttpStatus status = BatchCommitResult.APPLIED.equals(result.status()) && !result.replayed()
                ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }

    private static String planDigest(String confirmation) {
        if (confirmation == null) throw new IllegalArgumentException("Preview digest required in If-Match");
        if (!confirmation.matches("\"[0-9a-f]{64}\"")) {
            throw new IllegalArgumentException("If-Match must contain a quoted batch digest");
        }
        return confirmation.substring(1, confirmation.length() - 1);
    }

    static TimelineBatchCommand decode(String body) {
        try {
            var root = BATCH_JSON.readTree(body);
            if (root == null || !root.isObject()) throw new IllegalArgumentException("A batch object is required");
            for (String key : java.util.List.of("batchId", "baseRevisionId", "baseContentHash")) {
                if (!root.path(key).isTextual()) throw new IllegalArgumentException(key + " must be a string");
            }
            if (root.has("message") && !root.get("message").isTextual()) throw new IllegalArgumentException("Invalid message");
            if (!root.path("baseContentHash").textValue().matches(TimelineBatchCommand.TIMELINE_DIGEST)) {
                throw new IllegalArgumentException("An exact revision content digest is required");
            }
            var steps = root.path("operations");
            if (!steps.isArray() || steps.isEmpty() || steps.size() > OperationBatchRequest.MAX_STEPS) {
                throw new IllegalArgumentException("Invalid batch size");
            }
            for (var step : steps) {
                if (!step.isObject() || !step.path("definitionId").isTextual()
                        || step.has("version") && !step.path("version").isTextual()) {
                    throw new IllegalArgumentException("Invalid operation identity");
                }
                for (String key : java.util.List.of("input", "parameters", "target")) {
                    if (step.has(key) && !step.get(key).isObject()) throw new IllegalArgumentException("Invalid " + key);
                }
                for (String key : java.util.List.of("input", "target")) {
                    for (var entry : step.path(key).properties()) {
                        var value = entry.getValue();
                        if ("clipIds".equals(entry.getKey()) && key.equals("target")) {
                            if (!value.isArray() || value.isEmpty()) throw new IllegalArgumentException("Invalid clip list");
                            for (var clip : value) if (!clip.isTextual()) throw new IllegalArgumentException("Invalid clip identity");
                        } else if (!value.isTextual()) throw new IllegalArgumentException("Expected a stable identity string");
                    }
                }
            }
            return BATCH_JSON.treeToValue(root, TimelineBatchCommand.class);
        } catch (JsonProcessingException malformed) {
            throw new IllegalArgumentException("Invalid or ambiguous Timeline batch JSON");
        }
    }

    private CanonicalActor requireActor(String tenantId, String projectId) {
        requireId(tenantId, "tenantId");
        requireId(projectId, "projectId");
        CanonicalActor actor = actors.resolveCurrentActor()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "authenticated actor required"));
        if (!tenantId.equals(TenantContext.get()) || !tenantId.equals(actor.tenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "tenant scope mismatch");
        }
        return actor;
    }

    private static void requireId(String value, String field) {
        if (value == null || value.length() > 64 || !value.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException(field + " has invalid syntax");
        }
    }

    @ExceptionHandler(BatchException.class)
    ResponseEntity<ProblemDetail> batchFailure(BatchException failure) {
        HttpStatus status = switch (failure.code()) {
            case EMPTY_BATCH, MIXED_BASE -> HttpStatus.BAD_REQUEST;
            case AUTHORIZATION_DENIED, AUTHORIZATION_CONTEXT_MISMATCH -> HttpStatus.FORBIDDEN;
            case BASE_REVISION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case STALE_BASE_REVISION, PLAN_CHANGED, COMMAND_ID_CONFLICT,
                 CROSS_AGGREGATE_BATCH_UNSUPPORTED, REF_NOT_SUPPORTED -> HttpStatus.CONFLICT;
            case STEP_RESOLUTION_FAILED, STEP_PLAN_FAILED, STEP_VALIDATION_FAILED,
                 FINAL_VALIDATION_FAILED, UNSUPPORTED_BATCH_OPERATION ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            case PERSISTENCE_FAILURE -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        String detail = failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.code().name()
                : failure.code().name() + ": " + failure.getMessage();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:media-platform:operation-batch:"
                + failure.code().name().toLowerCase()));
        problem.setProperty("errorCode", failure.code().name());
        if (failure.stepIndex() != null) {
            problem.setProperty("stepIndex", failure.stepIndex());
        }
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> invalidRequest(IllegalArgumentException failure) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                failure.getMessage());
        problem.setProperty("errorCode", "INVALID_REQUEST");
        return ResponseEntity.badRequest().body(problem);
    }
}
