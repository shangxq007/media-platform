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
import com.example.platform.observability.context.TraceKeys;
import java.util.Map;
import java.util.Objects;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Canonical application-side invocation boundary for the currently executable
 * Operation slice. Semantic resolution, planning, and canonical writes remain
 * with their existing owners.
 */
@Service
public final class CanonicalOperationInvocationService implements OperationInvocationPort {

    private final TimelineMediaClipOperationService mediaClipService;
    private final TextOperationService textOperationService;
    private final AlignmentOperationService alignmentService;

    CanonicalOperationInvocationService(TimelineMediaClipOperationService mediaClipService,
                                        TextOperationService textOperationService,
                                        AlignmentOperationService alignmentService) {
        this.mediaClipService = Objects.requireNonNull(mediaClipService, "mediaClipService");
        this.textOperationService = Objects.requireNonNull(textOperationService, "textOperationService");
        this.alignmentService = Objects.requireNonNull(alignmentService, "alignmentService");
    }

    @Override
    public OperationInvocationResult invoke(
            OperationRequest request, OperationInvocationContext context) {
        String correlationId = (context != null && context.provenance() != null)
                ? context.provenance().correlationId() : null;
        boolean mdcSet = false;
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(TraceKeys.WORKFLOW_ID, correlationId);
            mdcSet = true;
        }
        try {
            return invokeInternal(request, context);
        } finally {
            if (mdcSet) {
                MDC.remove(TraceKeys.WORKFLOW_ID);
            }
        }
    }

    private OperationInvocationResult invokeInternal(
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
        boolean addMediaClip = OperationDefinition.V1.ADD_MEDIA_CLIP.definitionId().equals(request.definitionId());
        boolean initialize = OperationDefinition.V3.INITIALIZE_TIMELINE.definitionId()
                .equals(request.definitionId());
        boolean importTimeline = OperationDefinition.V3.IMPORT_TIMELINE.definitionId()
                .equals(request.definitionId());
        boolean clipEdit = isClipEdit(request);
        boolean addTrack = request != null && OperationDefinition.V3.ADD_TRACK.definitionId().equals(request.definitionId());
        boolean mediaClip = addMediaClip || clipEdit || addTrack;
        boolean textOperation = request.definitionId().value() != null
                && request.definitionId().value().startsWith("timeline.text.");
        boolean alignment = OperationDefinition.V2.CREATE_ALIGNMENT.definitionId().equals(request.definitionId());
        if (!initialize && !importTimeline && !mediaClip && !textOperation && !alignment) {
            throw failure(
                    OperationInvocationFailureCode.UNSUPPORTED_OPERATION,
                    "unsupported-operation");
        }
        if ((clipEdit && !(request.target() instanceof OperationTargetRequest.ClipSelectionTargetRequest target
                && target.timelineId() != null && !target.timelineId().isBlank()))
                || (!clipEdit && !(request.target() instanceof OperationTargetRequest.TimelineTargetRequest))) {
            throw failure(OperationInvocationFailureCode.INVALID_SCOPE, "invalid-scope");
        }
        if (addMediaClip) {
            if (!OperationDefinition.V1.ADD_MEDIA_CLIP.version().equals(request.version())) {
                throw failure(
                        OperationInvocationFailureCode.UNSUPPORTED_OPERATION,
                        "unsupported-operation");
            }
            if (!(request.parameters() instanceof OperationParameters.AddMediaClipParameters)) {
                throw failure(OperationInvocationFailureCode.INVALID_PARAMETER, "invalid-parameter");
            }
        }
        if (initialize && (!OperationDefinition.V3.INITIALIZE_TIMELINE.version().equals(request.version())
                || !(request.parameters() instanceof OperationParameters.InitializeTimelineParameters))) {
            throw failure(OperationInvocationFailureCode.INVALID_PARAMETER, "invalid-parameter");
        }
        if (importTimeline && (!OperationDefinition.V3.IMPORT_TIMELINE.version().equals(request.version())
                || !(request.parameters() instanceof OperationParameters.ImportTimelineParameters))) {
            throw failure(OperationInvocationFailureCode.INVALID_PARAMETER, "invalid-parameter");
        }
        if (addTrack && (!OperationDefinition.V3.ADD_TRACK.version().equals(request.version())
                || !(request.parameters() instanceof OperationParameters.AddTrackParameters))) {
            throw failure(OperationInvocationFailureCode.INVALID_PARAMETER, "invalid-track-parameters");
        }
        if (clipEdit && !validClipEditParameters(request)) {
            throw failure(OperationInvocationFailureCode.INVALID_PARAMETER, "invalid-parameter");
        }
        if (alignment) {
            if (!OperationDefinition.V2.CREATE_ALIGNMENT.version().equals(request.version())
                    || !(request.parameters() instanceof OperationParameters.CreateAlignmentParameters)) {
                throw failure(
                        OperationInvocationFailureCode.UNSUPPORTED_OPERATION,
                        "unsupported-operation");
            }
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
            if (initialize) return mediaClipService.initialize(request, context);
            if (importTimeline) return mediaClipService.importTimeline(request, context);
            var result = mediaClip
                    ? mediaClipService.invoke(request, context).result()
                    : textOperation
                            ? textOperationService.invoke(request, context).result()
                            : alignmentService.invoke(request, context).result();
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
        } catch (com.example.platform.timeline.api.revision.TimelineRevisionCommandConflictException failure) {
            throw failure(OperationInvocationFailureCode.IDEMPOTENCY_CONFLICT, "idempotency-conflict");
        } catch (com.example.platform.timeline.version.TimelineConflictException failure) {
            throw failure(OperationInvocationFailureCode.STALE_BASE_REVISION, "stale-base-revision");
        } catch (OperationInvocationException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw failure(OperationInvocationFailureCode.APPLY_FAILURE, "apply-failure");
        }
    }

    @Override
    public void validate(OperationRequest request, OperationInvocationContext context, String projectId) {
        boolean addMediaClip = request != null && request.definitionId() != null
                && OperationDefinition.V1.ADD_MEDIA_CLIP.definitionId().equals(request.definitionId());
        boolean initialize = request != null && request.definitionId() != null
                && OperationDefinition.V3.INITIALIZE_TIMELINE.definitionId().equals(request.definitionId());
        boolean importTimeline = request != null && request.definitionId() != null
                && OperationDefinition.V3.IMPORT_TIMELINE.definitionId().equals(request.definitionId());
        boolean clipEdit = isClipEdit(request);
        boolean addTrack = request != null && OperationDefinition.V3.ADD_TRACK.definitionId().equals(request.definitionId());
        boolean mediaClip = addMediaClip || clipEdit || addTrack;
        boolean textOperation = request != null && request.definitionId() != null
                && request.definitionId().value() != null
                && request.definitionId().value().startsWith("timeline.text.");
        boolean alignment = request != null && request.definitionId() != null
                && OperationDefinition.V2.CREATE_ALIGNMENT.definitionId().equals(request.definitionId());
        if (projectId == null || request == null
                || !projectId.equals(targetTimelineId(request))
                || (!initialize && !importTimeline && !mediaClip && !textOperation && !alignment)) {
            throw failure(OperationInvocationFailureCode.UNSUPPORTED_OPERATION, "unsupported-operation-or-scope");
        }
        if (addMediaClip
                && (!OperationDefinition.V1.ADD_MEDIA_CLIP.version().equals(request.version())
                    || !(request.parameters() instanceof OperationParameters.AddMediaClipParameters))) {
            throw failure(OperationInvocationFailureCode.UNSUPPORTED_OPERATION, "unsupported-operation-or-scope");
        }
        if (addTrack && (!OperationDefinition.V3.ADD_TRACK.version().equals(request.version())
                || !(request.parameters() instanceof OperationParameters.AddTrackParameters))) {
            throw failure(OperationInvocationFailureCode.INVALID_PARAMETER, "invalid-track-parameters");
        }
        if (clipEdit && !validClipEditParameters(request)) {
            throw failure(OperationInvocationFailureCode.UNSUPPORTED_OPERATION, "unsupported-operation-or-scope");
        }
        if (alignment
                && (!OperationDefinition.V2.CREATE_ALIGNMENT.version().equals(request.version())
                    || !(request.parameters() instanceof OperationParameters.CreateAlignmentParameters))) {
            throw failure(OperationInvocationFailureCode.UNSUPPORTED_OPERATION, "unsupported-operation-or-scope");
        }
        if(context==null||context.actor()==null||context.actor().tenantId()==null||context.actor().tenantId().isBlank())
            throw failure(OperationInvocationFailureCode.INVALID_REQUEST,"invalid-context");
        try {
            if (initialize) {
                mediaClipService.validateInitialization(request, context);
            } else if (importTimeline) {
                mediaClipService.validateImport(request, context);
            } else if (mediaClip) {
                mediaClipService.validateInvocation(request, context);
            } else if (textOperation) {
                textOperationService.validateInvocation(request, context);
            } else {
                alignmentService.validateInvocation(request, context);
            }
        }
        catch(TimelineOperationException rejected){throw translate(rejected.code());}
        catch(OperationInvocationException rejected){throw rejected;}
        catch(RuntimeException rejected){throw failure(OperationInvocationFailureCode.APPLY_FAILURE,"preflight-failure");}
    }

    private static boolean isClipEdit(OperationRequest request) {
        if (request == null || request.definitionId() == null) return false;
        var id = request.definitionId();
        return OperationDefinition.V1.MOVE.definitionId().equals(id)
                || OperationDefinition.V1.TRIM.definitionId().equals(id)
                || OperationDefinition.V1.DELETE.definitionId().equals(id)
                || OperationDefinition.V3.SPLIT_CLIP.definitionId().equals(id)
                || OperationDefinition.V3.RELOCATE_CLIP.definitionId().equals(id);
    }

    private static boolean validClipEditParameters(OperationRequest request) {
        if (!OperationDefinition.V1.MOVE.version().equals(request.version())) return false;
        if (OperationDefinition.V1.MOVE.definitionId().equals(request.definitionId()))
            return request.parameters() instanceof OperationParameters.MoveParameters;
        if (OperationDefinition.V1.TRIM.definitionId().equals(request.definitionId()))
            return request.parameters() instanceof OperationParameters.TrimParameters;
        if (OperationDefinition.V3.SPLIT_CLIP.definitionId().equals(request.definitionId()))
            return request.parameters() instanceof OperationParameters.SplitParameters;
        if (OperationDefinition.V3.RELOCATE_CLIP.definitionId().equals(request.definitionId()))
            return request.parameters() instanceof OperationParameters.RelocateClipParameters;
        return request.parameters() instanceof OperationParameters.NoParameters;
    }

    private static String targetTimelineId(OperationRequest request) {
        if (request.target() instanceof OperationTargetRequest.TimelineTargetRequest target)
            return target.timelineId();
        if (request.target() instanceof OperationTargetRequest.ClipSelectionTargetRequest target)
            return target.timelineId();
        return null;
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
