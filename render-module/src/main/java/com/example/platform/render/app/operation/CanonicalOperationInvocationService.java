package com.example.platform.render.app.operation;

import com.example.platform.operation.invocation.OperationInvocationContext;
import com.example.platform.operation.invocation.OperationInvocationException;
import com.example.platform.operation.invocation.OperationInvocationFailureCode;
import com.example.platform.operation.invocation.OperationInvocationPort;
import com.example.platform.operation.invocation.OperationInvocationResult;
import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationTargetRequest;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Canonical application-side invocation boundary for the currently executable
 * Operation slice. Semantic resolution, planning, and canonical writes remain
 * with their existing owners.
 */
@Service
public final class CanonicalOperationInvocationService implements OperationInvocationPort {

    private final TimelineMediaClipOperationService mediaClipService;

    CanonicalOperationInvocationService(TimelineMediaClipOperationService mediaClipService) {
        this.mediaClipService = Objects.requireNonNull(mediaClipService, "mediaClipService");
    }

    @Override
    public OperationInvocationResult invoke(
            OperationRequest request, OperationInvocationContext context) {
        if (request == null
                || request.definitionId() == null
                || request.version() == null
                || request.target() == null
                || request.parameters() == null
                || request.baseRevisionId() == null
                || request.baseRevisionId().isBlank()
                || request.baseContentHash() == null
                || request.baseContentHash().isBlank()) {
            throw failure(OperationInvocationFailureCode.INVALID_REQUEST, "invalid-request");
        }
        if (!OperationDefinition.V1.ADD_MEDIA_CLIP.definitionId().equals(request.definitionId())
                || !OperationDefinition.V1.ADD_MEDIA_CLIP.version().equals(request.version())) {
            throw failure(
                    OperationInvocationFailureCode.UNSUPPORTED_OPERATION,
                    "unsupported-operation");
        }
        if (!(request.target() instanceof OperationTargetRequest.TimelineTargetRequest)) {
            throw failure(OperationInvocationFailureCode.INVALID_SCOPE, "invalid-scope");
        }
        if (!(request.parameters() instanceof OperationParameters.AddMediaClipParameters)) {
            throw failure(OperationInvocationFailureCode.INVALID_PARAMETER, "invalid-parameter");
        }
        if (context == null
                || context.actor() == null
                || context.invocationId() == null
                || context.invocationId().isBlank()
                || context.provenance() == null) {
            throw failure(OperationInvocationFailureCode.INVALID_REQUEST, "invalid-request");
        }
        if (context.actor().tenantId() == null || context.actor().tenantId().isBlank()) {
            throw failure(
                    OperationInvocationFailureCode.AUTHORIZATION_CONTEXT_MISMATCH,
                    "authorization-context-mismatch");
        }

        try {
            var outcome = mediaClipService.invoke(request, context);
            var result = outcome.result();
            String correlationId = context.provenance().correlationId();
            if (com.example.platform.operation.plan.ApplyResult.NO_OP.equals(result.status())) {
                return new OperationInvocationResult.NoOp(
                        request.definitionId(), request.version(), result.planDigest(),
                        result.baseRevisionId(), result.newContentHash(),
                        context.invocationId(), correlationId);
            }
            if (com.example.platform.operation.plan.ApplyResult.APPLIED.equals(result.status())) {
                return new OperationInvocationResult.Applied(
                        request.definitionId(), request.version(), result.planDigest(),
                        result.baseRevisionId(), result.newRevisionId(), result.newContentHash(),
                        context.invocationId(), correlationId);
            }
            throw failure(OperationInvocationFailureCode.APPLY_FAILURE, "apply-failure");
        } catch (TimelineOperationException failure) {
            throw translate(failure.code());
        } catch (OperationInvocationException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw failure(OperationInvocationFailureCode.APPLY_FAILURE, "apply-failure");
        }
    }

    private static OperationInvocationException translate(TimelineOperationException.Code code) {
        return switch (code) {
            case BASE_REVISION_NOT_FOUND ->
                    failure(OperationInvocationFailureCode.BASE_REVISION_NOT_FOUND,
                            "base-revision-not-found");
            case STALE_BASE_REVISION, STALE_TARGET_REF ->
                    failure(OperationInvocationFailureCode.STALE_BASE_REVISION,
                            "stale-base-revision");
            case SOURCE_REFERENCE_INVALID ->
                    failure(OperationInvocationFailureCode.SOURCE_REFERENCE_INVALID,
                            "source-reference-invalid");
            case CANDIDATE_INVALID, INVALID_PLAN ->
                    failure(OperationInvocationFailureCode.CANDIDATE_INVALID,
                            "candidate-invalid");
            case PLAN_CHANGED ->
                    failure(OperationInvocationFailureCode.PLAN_CHANGED, "plan-changed");
            case AUTHORIZATION_DENIED ->
                    failure(OperationInvocationFailureCode.AUTHORIZATION_DENIED,
                            "authorization-denied");
            case AUTHORIZATION_CONTEXT_MISMATCH, TENANT_CONTEXT_MISMATCH ->
                    failure(OperationInvocationFailureCode.AUTHORIZATION_CONTEXT_MISMATCH,
                            "authorization-context-mismatch");
            case IDEMPOTENCY_KEY_CONFLICT ->
                    failure(OperationInvocationFailureCode.IDEMPOTENCY_CONFLICT,
                            "idempotency-conflict");
            case TARGET_MISSING ->
                    failure(OperationInvocationFailureCode.TARGET_MISSING, "target-missing");
            case PLACEMENT_CONFLICT ->
                    failure(OperationInvocationFailureCode.PLACEMENT_CONFLICT,
                            "placement-conflict");
            case CANONICAL_INVARIANT_VIOLATION ->
                    failure(OperationInvocationFailureCode.CANONICAL_INVARIANT_VIOLATION,
                            "canonical-invariant-violation");
            case PERSISTENCE_FAILURE, REF_UPDATE_FAILURE ->
                    failure(OperationInvocationFailureCode.PERSISTENCE_FAILURE,
                            "persistence-failure");
            case UNSUPPORTED_TEMPORAL_STATE, UNSUPPORTED_AUDIO_TEMPORAL_BEHAVIOR,
                    SYNC_ANCHOR_INVALIDATED, GROUP_CARDINALITY_CONFLICT,
                    BATCH_CONFLICT, APPLY_UNKNOWN_FAILURE ->
                    failure(OperationInvocationFailureCode.APPLY_FAILURE, "apply-failure");
        };
    }

    private static OperationInvocationException failure(
            OperationInvocationFailureCode code, String stableLabel) {
        return new OperationInvocationException(code, Map.of("failure", stableLabel));
    }
}
