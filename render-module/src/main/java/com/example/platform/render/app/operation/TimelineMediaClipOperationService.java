package com.example.platform.render.app.operation;

import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.ParameterDigest;
import com.example.platform.operation.invocation.OperationInvocationContext;
import com.example.platform.operation.invocation.OperationInvocationResult;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationRequestResolver;
import com.example.platform.operation.operation.OperationResolutionException;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.operation.plan.ApplyContext;
import com.example.platform.operation.plan.ApplyResult;
import com.example.platform.operation.plan.AuthorizationDecision;
import com.example.platform.operation.plan.OperationPlan;
import com.example.platform.operation.plan.OperationPlanPreview;
import com.example.platform.operation.plan.OperationPlanner;
import com.example.platform.operation.plan.PlanErrorCode;
import com.example.platform.operation.plan.PlanException;
import com.example.platform.operation.plan.TargetRevisionRef;
import com.example.platform.render.app.plan.OperationPlanApplyService;
import com.example.platform.shared.authorization.AuthorizationAction;
import com.example.platform.shared.authorization.AuthorizationContext;
import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import com.example.platform.shared.authorization.AuthorizationRequest;
import com.example.platform.shared.authorization.AuthorizationResourceType;
import com.example.platform.shared.authorization.AuthorizableResourceRef;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.api.composition.TimelineValidation;
import com.example.platform.timeline.api.revision.TimelineRevisionCommands;
import com.example.platform.timeline.api.composition.TimelineSourceValidation;
import com.example.platform.timeline.api.composition.TimelineCanonicalRejectionException;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.api.revision.TimelineMutationContext;
import com.example.platform.timeline.revisioncommand.RevisionRef;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonicalmodel.TimelineDiagnostic;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import com.example.platform.timeline.version.TimelineRevision;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * H7 canonical application coordinator:
 * REQUEST -> RESOLVE -> PLAN -> VALIDATE -> PREVIEW -> AUTHORIZE -> ATOMIC APPLY.
 *
 * <p>Operation remains provider-neutral semantic intent. Timeline remains the
 * sole composition and revision authority. This service owns only application
 * sequencing and never writes canonical state itself.
 */
@Service
public class TimelineMediaClipOperationService {

    public static final String OPERATION = "ADD_MEDIA_CLIP_V1";
    public static final String INITIAL_BASE_REVISION_ID = "__uninitialized__";
    public static final String INITIAL_BASE_CONTENT_HASH = "0".repeat(64);
    private static final AuthorizationAction TIMELINE_EDIT = new AuthorizationAction(
            "WRITE", AuthorizationResourceType.PROJECT, "Edit canonical Timeline");
    private static final AuthorizationAction TIMELINE_READ = new AuthorizationAction(
            "READ", AuthorizationResourceType.PROJECT, "Read canonical Timeline for preview");

    private final TimelineRevisionCommands revisionSaveService;
    private final TimelineSourceValidation sourceValidator;
    private final TimelineValidation timelineValidator;
    private final AuthorizationDecisionPort authorizationPort;
    private final OperationPlanApplyService applyService;
    private final OperationPlanner planner = new OperationPlanner();

    public TimelineMediaClipOperationService(
            TimelineRevisionCommands revisionSaveService,
            TimelineSourceValidation sourceValidator,
            TimelineValidation timelineValidator,
            AuthorizationDecisionPort authorizationPort,
            OperationPlanApplyService applyService) {
        this.revisionSaveService = Objects.requireNonNull(revisionSaveService, "revisionSaveService");
        this.sourceValidator = Objects.requireNonNull(sourceValidator, "sourceValidator");
        this.timelineValidator = Objects.requireNonNull(timelineValidator, "timelineValidator");
        this.authorizationPort = Objects.requireNonNull(authorizationPort, "authorizationPort");
        this.applyService = Objects.requireNonNull(applyService, "applyService");
    }

    /** Atomic first revision through the Timeline owner's durable Operation command receipt. */
    public OperationInvocationResult.Applied initialize(OperationRequest request,
            OperationInvocationContext context) {
        if (!OperationDefinition.V3.INITIALIZE_TIMELINE.definitionId().equals(request.definitionId())
                || !OperationDefinition.V3.INITIALIZE_TIMELINE.version().equals(request.version())
                || !(request.target() instanceof OperationTargetRequest.TimelineTargetRequest target)
                || !(request.parameters() instanceof OperationParameters.InitializeTimelineParameters parameters)
                || !INITIAL_BASE_REVISION_ID.equals(request.baseRevisionId())
                || !INITIAL_BASE_CONTENT_HASH.equals(request.baseContentHash())) {
            throw new TimelineOperationException(TimelineOperationException.Code.CANDIDATE_INVALID,
                    List.of("invalid first-revision Operation contract"));
        }
        String tenantId = context.actor().tenantId();
        String projectId = target.timelineId();
        requirePreparationAuthorization(tenantId, projectId, context.actor(), TIMELINE_EDIT);
        var document = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack(parameters.trackId(), "Video", TrackType.VIDEO, List.of())),
                TimelineMetadata.empty());
        String contentHash = new TimelineContentDigester().digest(document);
        String parameterDigest = ParameterDigest.compute(request.definitionId(), request.version(),
                request.parameters());
        String planDigest = sha256("timeline-initialize-v1\0" + tenantId + "\0" + projectId
                + "\0" + parameterDigest + "\0" + contentHash);
        String fingerprint = sha256("timeline-initialize-command-v1\0" + planDigest + "\0"
                + context.actor().actorId());
        var result = revisionSaveService.saveRevisionForCommand(
                new TimelineMutationContext(tenantId, projectId, context.actor()),
                RevisionRef.main(tenantId, projectId), null, document,
                new TimelineRevisionCommands.RevisionWriteCommand(context.invocationId(),
                        planDigest, fingerprint, "OPERATION_INITIALIZE", tenantId));
        if (!contentHash.equals(result.timelineContentHash())) {
            throw new IllegalStateException("initialized Timeline digest differs from authoritative result");
        }
        return new OperationInvocationResult.Applied(request.definitionId(), request.version(),
                planDigest, INITIAL_BASE_REVISION_ID, result.revisionId(),
                result.timelineContentHash(), context.invocationId(),
                context.provenance().correlationId());
    }

    public void validateInitialization(OperationRequest request, OperationInvocationContext context) {
        if (!(request.parameters() instanceof OperationParameters.InitializeTimelineParameters)
                || !(request.target() instanceof OperationTargetRequest.TimelineTargetRequest target)
                || !INITIAL_BASE_REVISION_ID.equals(request.baseRevisionId())
                || !INITIAL_BASE_CONTENT_HASH.equals(request.baseContentHash())) {
            throw new TimelineOperationException(TimelineOperationException.Code.CANDIDATE_INVALID,
                    List.of("invalid first-revision Operation contract"));
        }
        requirePreparationAuthorization(context.actor().tenantId(), target.timelineId(),
                context.actor(), TIMELINE_EDIT);
    }

    /** Versioned import Operation for existing editor formats and canonical document imports. */
    public OperationInvocationResult importTimeline(OperationRequest request,
            OperationInvocationContext context) {
        ImportedCandidate candidate = prepareImport(request, context);
        String tenantId = context.actor().tenantId();
        String projectId = candidate.projectId();
        String parameterDigest = ParameterDigest.compute(request.definitionId(), request.version(),
                request.parameters());
        String planDigest = sha256("timeline-import-v1\0" + tenantId + "\0" + projectId + "\0"
                + request.baseRevisionId() + "\0" + request.baseContentHash() + "\0"
                + parameterDigest);
        String fingerprint = sha256("timeline-import-command-v1\0" + planDigest + "\0"
                + context.actor().actorId());
        var command = new TimelineRevisionCommands.RevisionWriteCommand(context.invocationId(),
                planDigest, fingerprint, "OPERATION_IMPORT", tenantId);
        var mutation = new TimelineMutationContext(tenantId, projectId, context.actor());
        var ref = RevisionRef.main(tenantId, projectId);
        String expected = candidate.initial() ? null : request.baseRevisionId();
        if (!candidate.initial() && candidate.contentHash().equals(request.baseContentHash())) {
            var unchanged = revisionSaveService.recordNoOpCommand(mutation, ref, expected,
                    candidate.contentHash(), command);
            return new OperationInvocationResult.NoOp(request.definitionId(), request.version(),
                    planDigest, request.baseRevisionId(), unchanged.timelineContentHash(),
                    context.invocationId(), context.provenance().correlationId());
        }
        var result = revisionSaveService.saveRevisionForCommand(mutation, ref, expected,
                candidate.document(), command);
        if (!candidate.contentHash().equals(result.timelineContentHash())) {
            throw new IllegalStateException("imported Timeline digest differs from authoritative result");
        }
        return new OperationInvocationResult.Applied(request.definitionId(), request.version(),
                planDigest, request.baseRevisionId(), result.revisionId(),
                result.timelineContentHash(), context.invocationId(),
                context.provenance().correlationId());
    }

    public void validateImport(OperationRequest request, OperationInvocationContext context) {
        prepareImport(request, context);
    }

    private ImportedCandidate prepareImport(OperationRequest request, OperationInvocationContext context) {
        if (!OperationDefinition.V3.IMPORT_TIMELINE.definitionId().equals(request.definitionId())
                || !OperationDefinition.V3.IMPORT_TIMELINE.version().equals(request.version())
                || !(request.target() instanceof OperationTargetRequest.TimelineTargetRequest target)
                || !(request.parameters() instanceof OperationParameters.ImportTimelineParameters parameters)) {
            throw new TimelineOperationException(TimelineOperationException.Code.CANDIDATE_INVALID,
                    List.of("invalid Timeline import Operation"));
        }
        String tenantId = context.actor().tenantId();
        String projectId = target.timelineId();
        requirePreparationAuthorization(tenantId, projectId, context.actor(), TIMELINE_EDIT);
        boolean initial = INITIAL_BASE_REVISION_ID.equals(request.baseRevisionId())
                && INITIAL_BASE_CONTENT_HASH.equals(request.baseContentHash());
        if (!initial) {
            var base = revisionSaveService.findById(tenantId, request.baseRevisionId());
            if (base == null || !projectId.equals(base.productId())) {
                throw new TimelineOperationException(TimelineOperationException.Code.BASE_REVISION_NOT_FOUND,
                        List.of("import base revision unavailable in project"));
            }
            if (!request.baseContentHash().equals(base.semanticContext().timelineContentDigest())) {
                throw new TimelineOperationException(TimelineOperationException.Code.STALE_BASE_REVISION,
                        List.of("import base content hash mismatch"));
            }
        }
        TimelineDocument document = parameters.document();
        var validation = timelineValidator.validateDocument(projectId, document);
        if (validation.hasFatalErrors()) {
            throw new TimelineOperationException(TimelineOperationException.Code.CANDIDATE_INVALID,
                    validation.diagnostics().stream().map(TimelineDiagnostic::message).toList());
        }
        for (var track : document.getTracks()) {
            for (var clip : track.clips()) {
                final MediaStreamSourceBinding binding;
                try {
                    binding = new MediaStreamSourceBinding(MediaAssetId.of(clip.getMediaAssetId()),
                            MediaStreamId.of(clip.getMediaStreamId()), new ArtifactId(clip.getArtifactId()),
                            ContentDigest.sha256(clip.getContentDigest()),
                            new MediaClip.TimeRange(clip.getTrimStart(), clip.getTrimEnd()));
                } catch (RuntimeException invalid) {
                    throw new TimelineOperationException(TimelineOperationException.Code.CANDIDATE_INVALID,
                            List.of("invalid imported clip source binding"));
                }
                var source = sourceValidator.validate(binding, tenantId, projectId, track.type());
                if (!source.valid()) {
                    throw new TimelineOperationException(TimelineOperationException.Code.SOURCE_REFERENCE_INVALID,
                            source.violations());
                }
            }
        }
        return new ImportedCandidate(projectId, document, new TimelineContentDigester().digest(document), initial);
    }

    private record ImportedCandidate(String projectId, TimelineDocument document,
                                     String contentHash, boolean initial) {}

    private static String sha256(String value) {
        try {
            byte[] bytes = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }

    public AddMediaClipPreview preview(
            String tenantId, String projectId, AddMediaClipCommand command,
            CanonicalActor actor) {
        requirePreparationAuthorization(tenantId, projectId, actor, TIMELINE_READ);
        return prepare(tenantId, projectId, toOperationRequest(projectId, command)).preview();
    }

    public AddMediaClipResult authorizeAndApply(
            String tenantId,
            String projectId,
            AddMediaClipCommand command,
            String expectedPlanDigest,
            String applyCommandId,
            CanonicalActor actor) {
        requirePreparationAuthorization(tenantId, projectId, actor, TIMELINE_READ);
        PreparedOperation prepared = prepare(
                tenantId, projectId, toOperationRequest(projectId, command));
        ExecutionOutcome execution = executePrepared(
                tenantId, projectId, prepared, expectedPlanDigest, applyCommandId, actor);
        ApplyResult result = execution.result();
        return new AddMediaClipResult(
                result.status(), result.planDigest(), result.baseRevisionId(),
                result.newRevisionId(), result.newContentHash(), result.parentRevisionId(),
                new AddMediaClipResult.TimelineRevisionRenderHandoff(
                        projectId, result.newRevisionId(), result.newContentHash()),
                prepared.preview().expectedChangedCanonicalObjects());
    }

    void validateInvocation(OperationRequest request, OperationInvocationContext context) {
        String projectId = targetProjectId(request);
        requirePreparationAuthorization(context.actor().tenantId(), projectId, context.actor(), TIMELINE_EDIT);
        prepareForInvocation(context.actor().tenantId(), projectId, request, context.actor());
    }

    InvocationOutcome invoke(OperationRequest request, OperationInvocationContext context) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(context, "context");
        String projectId = targetProjectId(request);
        String tenantId = context.actor().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new TimelineOperationException(
                    TimelineOperationException.Code.TENANT_CONTEXT_MISMATCH,
                    List.of("authenticated actor tenant required"));
        }
        PreparedOperation prepared = prepareForInvocation(
                tenantId, projectId, request, context.actor());
        ExecutionOutcome execution = executePrepared(
                tenantId, projectId, prepared,
                prepared.plan().planDigest(), context.invocationId(), context.actor());
        return new InvocationOutcome(execution.result());
    }

    private static String targetProjectId(OperationRequest request) {
        if (request.target() instanceof OperationTargetRequest.TimelineTargetRequest target) {
            return target.timelineId();
        }
        if (request.target() instanceof OperationTargetRequest.ClipSelectionTargetRequest target
                && target.timelineId() != null && !target.timelineId().isBlank()) {
            return target.timelineId();
        }
        throw new TimelineOperationException(TimelineOperationException.Code.CANDIDATE_INVALID,
                List.of("exact Timeline target required"));
    }

    private static boolean isClipEdit(OperationRequest request) {
        var id = request.definitionId();
        return OperationDefinition.V1.MOVE.definitionId().equals(id)
                || OperationDefinition.V1.TRIM.definitionId().equals(id)
                || OperationDefinition.V1.DELETE.definitionId().equals(id)
                || OperationDefinition.V3.SPLIT_CLIP.definitionId().equals(id)
                || OperationDefinition.V3.RELOCATE_CLIP.definitionId().equals(id);
    }

    private PreparedOperation prepareForInvocation(String tenantId, String projectId,
            OperationRequest request, CanonicalActor actor) {
        return isClipEdit(request) || OperationDefinition.V3.ADD_TRACK.definitionId().equals(request.definitionId())
                ? prepareClipEdit(tenantId, projectId, request, actor)
                : prepareInternal(tenantId, projectId, request, actor);
    }

    private PreparedOperation prepareClipEdit(String tenantId, String projectId,
            OperationRequest request, CanonicalActor actor) {
        boolean trackAddition = OperationDefinition.V3.ADD_TRACK.definitionId().equals(request.definitionId())
                && request.target() instanceof OperationTargetRequest.TimelineTargetRequest target
                && projectId.equals(target.timelineId());
        boolean clipEdit = isClipEdit(request)
                && request.target() instanceof OperationTargetRequest.ClipSelectionTargetRequest target
                && projectId.equals(target.timelineId());
        if (!trackAddition && !clipEdit) {
            throw new TimelineOperationException(TimelineOperationException.Code.CANDIDATE_INVALID,
                    List.of("exact clip selection and Timeline target required"));
        }
        requirePreparationAuthorization(tenantId, projectId, actor, TIMELINE_READ);
        TimelineRevision baseRevision = revisionSaveService.findById(tenantId, request.baseRevisionId());
        if (baseRevision == null || !projectId.equals(baseRevision.productId())) {
            throw new TimelineOperationException(TimelineOperationException.Code.BASE_REVISION_NOT_FOUND,
                    List.of("base revision not found in target Timeline"));
        }
        String authoritativeHash = baseRevision.semanticContext().timelineContentDigest();
        if (!authoritativeHash.equals(request.baseContentHash())) {
            throw new TimelineOperationException(TimelineOperationException.Code.STALE_BASE_REVISION,
                    List.of("base Timeline content hash mismatch"));
        }
        TimelineDocument exactBase = revisionSaveService.findPayloadDocument(tenantId, request.baseRevisionId())
                .orElseThrow(() -> new TimelineOperationException(
                        TimelineOperationException.Code.BASE_REVISION_NOT_FOUND,
                        List.of("base revision canonical payload unavailable")));
        final com.example.platform.operation.operation.OperationInstance instance;
        try {
            instance = OperationRequestResolver.resolve(request,
                    new OperationRequestResolver.OperationBaseContext(
                            request.baseRevisionId(), authoritativeHash, exactBase, projectId));
        } catch (OperationResolutionException failure) {
            throw new TimelineOperationException(TimelineOperationException.Code.CANDIDATE_INVALID,
                    List.of(failure.getMessage()));
        } catch (com.example.platform.timeline.semantics.selection.TimelineSelectionException failure) {
            throw new TimelineOperationException(
                    TimelineOperationException.Code.TARGET_MISSING,
                    List.of(failure.getMessage() == null ? "invalid clip selection" : failure.getMessage()));
        } catch (IllegalArgumentException failure) {
            throw new TimelineOperationException(
                    TimelineOperationException.Code.CANDIDATE_INVALID,
                    List.of(failure.getMessage() == null ? "invalid clip selection" : failure.getMessage()));
        }
        final OperationPlan plan;
        try {
            plan = planner.plan(instance, baseRevision.revisionId(), exactBase);
        } catch (PlanException failure) {
            throw translatePlanFailure(failure);
        }
        var validation = timelineValidator.validateDocument(projectId, plan.candidateTimeline());
        if (validation.hasFatalErrors()) {
            throw new TimelineOperationException(TimelineOperationException.Code.CANDIDATE_INVALID,
                    validation.diagnostics().stream().map(TimelineDiagnostic::message).toList());
        }
        return new PreparedOperation(plan, null, exactBase);
    }

    private PreparedOperation prepareInternal(
            String tenantId,
            String projectId,
            OperationRequest request,
            CanonicalActor actor) {
        requirePreparationAuthorization(tenantId, projectId, actor, TIMELINE_READ);
        return prepare(tenantId, projectId, request);
    }

    private ExecutionOutcome executePrepared(
            String tenantId,
            String projectId,
            PreparedOperation prepared,
            String expectedPlanDigest,
            String applyCommandId,
            CanonicalActor actor) {
        Objects.requireNonNull(actor, "actor");
        if (!prepared.plan().planDigest().equals(expectedPlanDigest)) {
            throw new TimelineOperationException(TimelineOperationException.Code.PLAN_CHANGED,
                    List.of("expected plan digest does not match freshly validated plan"));
        }

        var securityDecision = authorizationPort.decide(new AuthorizationRequest(
                actor,
                TIMELINE_EDIT,
                new AuthorizableResourceRef(
                        AuthorizationResourceType.PROJECT, projectId, tenantId, projectId, null),
                new AuthorizationContext("timeline-operation", null,
                        Map.of("operationPlanDigest", prepared.plan().planDigest()))));
        AuthorizationDecision boundDecision = securityDecision.allowed()
                ? AuthorizationDecision.allow(prepared.plan().planDigest(), actor.actorId(),
                        projectId, tenantId, OperationPlanApplyService.CURRENT_REVISION_REF,
                        policyRef(securityDecision))
                : AuthorizationDecision.deny(prepared.plan().planDigest(), actor.actorId(),
                        projectId, tenantId, OperationPlanApplyService.CURRENT_REVISION_REF,
                        policyRef(securityDecision));

        ApplyContext context = new ApplyContext(
                applyCommandId,
                new TargetRevisionRef(OperationPlanApplyService.CURRENT_REVISION_REF),
                prepared.plan().baseRevisionId(), tenantId, actor, boundDecision);
        final ApplyResult result;
        try {
            result = applyService.apply(
                    prepared.plan(), context, projectId, prepared.exactBase());
        } catch (TimelineCanonicalRejectionException rejection) {
            boolean sourceFailure = rejection.adapterDiagnostics().stream()
                    .anyMatch(diagnostic -> diagnostic.code()
                            == TimelineCanonicalRejectionException.Code.TIMELINE_SOURCE_REF_INVALID);
            throw new TimelineOperationException(
                    sourceFailure
                            ? TimelineOperationException.Code.SOURCE_REFERENCE_INVALID
                            : TimelineOperationException.Code.CANDIDATE_INVALID,
                    java.util.stream.Stream.concat(
                            rejection.diagnostics().stream().map(d -> d.message()),
                            rejection.adapterDiagnostics().stream().map(d -> d.message()))
                            .toList());
        } catch (PlanException failure) {
            throw translatePlanFailure(failure);
        }
        return new ExecutionOutcome(result, prepared);
    }

    private void requirePreparationAuthorization(
            String tenantId, String projectId, CanonicalActor actor,
            AuthorizationAction action) {
        Objects.requireNonNull(actor, "actor");
        if (!Objects.equals(tenantId, actor.tenantId())) {
            throw new TimelineOperationException(
                    TimelineOperationException.Code.TENANT_CONTEXT_MISMATCH,
                    List.of("tenant context does not match authenticated actor"));
        }
        var decision = authorizationPort.decide(new AuthorizationRequest(
                actor,
                action,
                new AuthorizableResourceRef(
                        AuthorizationResourceType.PROJECT, projectId, tenantId, projectId, null),
                new AuthorizationContext(
                        "timeline-operation-prepare", null,
                        Map.of("operation", OPERATION))));
        if (!decision.allowed()) {
            throw new TimelineOperationException(
                    TimelineOperationException.Code.AUTHORIZATION_DENIED,
                    List.of("project operation access denied"));
        }
    }

    private PreparedOperation prepare(String tenantId, String projectId, OperationRequest request) {
        requireExactDefinition(request);
        TimelineRevision baseRevision = revisionSaveService.findById(tenantId, request.baseRevisionId());
        if (baseRevision == null || !projectId.equals(baseRevision.productId())) {
            throw new TimelineOperationException(
                    TimelineOperationException.Code.BASE_REVISION_NOT_FOUND,
                    List.of("base revision not found in target Timeline"));
        }
        String authoritativeBaseHash = baseRevision.semanticContext().timelineContentDigest();
        if (!Objects.equals(request.baseContentHash(), authoritativeBaseHash)) {
            throw new TimelineOperationException(
                    TimelineOperationException.Code.STALE_BASE_REVISION,
                    List.of("base Timeline content hash mismatch"));
        }
        TimelineDocument exactBase = revisionSaveService.findPayloadDocument(tenantId, request.baseRevisionId())
                .orElseThrow(() -> new TimelineOperationException(
                        TimelineOperationException.Code.BASE_REVISION_NOT_FOUND,
                        List.of("base revision canonical payload unavailable")));

        final com.example.platform.operation.operation.OperationInstance instance;
        try {
            instance = OperationRequestResolver.resolve(request,
                    new OperationRequestResolver.OperationBaseContext(
                            request.baseRevisionId(), authoritativeBaseHash, exactBase, projectId));
        } catch (OperationResolutionException resolution) {
            TimelineOperationException.Code code =
                    resolution.code() == com.example.platform.operation.operation.OperationErrorCode
                            .STALE_BASE_REVISION
                            ? TimelineOperationException.Code.STALE_BASE_REVISION
                            : TimelineOperationException.Code.CANDIDATE_INVALID;
            throw new TimelineOperationException(code, List.of(resolution.getMessage()));
        }

        var parameters = (OperationParameters.AddMediaClipParameters) instance.parameters();
        TimelineTrack targetTrack = exactBase.getTracks().stream()
                .filter(track -> track.trackId().equals(parameters.trackId()))
                .findFirst()
                .orElseThrow(() -> new TimelineOperationException(
                        TimelineOperationException.Code.CANDIDATE_INVALID,
                        List.of("target track does not exist: " + parameters.trackId())));
        var sourceValidation = sourceValidator.validate(
                parameters.sourceBinding(), tenantId, projectId, targetTrack.type());
        if (!sourceValidation.valid()) {
            throw new TimelineOperationException(
                    TimelineOperationException.Code.SOURCE_REFERENCE_INVALID,
                    sourceValidation.violations());
        }

        final OperationPlan plan;
        try {
            plan = planner.plan(instance, baseRevision.revisionId(), exactBase);
        } catch (PlanException failure) {
            throw translatePlanFailure(failure);
        }
        var validation = timelineValidator.validateDocument(projectId, plan.candidateTimeline());
        if (validation.hasFatalErrors()) {
            throw new TimelineOperationException(
                    TimelineOperationException.Code.CANDIDATE_INVALID,
                    validation.diagnostics().stream().map(TimelineDiagnostic::message).toList());
        }
        List<String> validationProjection = validation.diagnostics().isEmpty()
                ? List.of("CANONICAL_TIMELINE_VALID")
                : validation.diagnostics().stream()
                        .map(d -> d.code().name() + ":" + d.message()).toList();
        OperationPlanPreview genericPreview = OperationPlanPreview.of(plan);
        AddMediaClipPreview preview = new AddMediaClipPreview(
                OPERATION, plan.planDigest(), projectId, plan.baseRevisionId(),
                plan.baseContentHash(), parameters.sourceBinding(),
                parameters.sourceBinding().sourceRange(), parameters.placement(),
                parameters.temporalMapping(),
                List.of("timeline:" + projectId, "track:" + parameters.trackId(),
                        "clip:" + parameters.clipId().value()),
                validationProjection, List.of(), genericPreview.warnings(),
                genericPreview.blockers(), plan.candidateContentHash());
        return new PreparedOperation(plan, preview, exactBase);
    }

    public static OperationRequest toOperationRequest(
            String timelineId, AddMediaClipCommand command) {
        Objects.requireNonNull(command, "command");
        MediaClip.TimeRange sourceRange = new MediaClip.TimeRange(
                parseTime(command.sourceStart()), parseTime(command.sourceEnd()));
        MediaStreamSourceBinding sourceBinding = new MediaStreamSourceBinding(
                MediaAssetId.of(command.mediaAssetId()),
                MediaStreamId.of(command.mediaStreamId()),
                new ArtifactId(command.artifactId()),
                ContentDigest.sha256(command.contentDigest()),
                sourceRange);
        var parameters = new OperationParameters.AddMediaClipParameters(
                command.trackId(),
                TimelineClipId.of(command.clipId()),
                sourceBinding,
                new MediaClip.TimeRange(
                        parseTime(command.timelineStart()),
                        parseTime(command.timelineEnd())),
                ConstantRateTemporalMapping.of(
                        command.rateNumerator(),
                        command.rateDenominator(),
                        toCanonicalDirection(command.direction())));
        return new OperationRequest(
                OperationDefinition.V1.ADD_MEDIA_CLIP.definitionId(),
                OperationDefinition.V1.ADD_MEDIA_CLIP.version(),
                new OperationTargetRequest.TimelineTargetRequest(timelineId),
                parameters,
                command.baseRevisionId(),
                command.baseContentHash(),
                null);
    }

    private static MediaTime parseTime(String value) {
        return MediaTime.parse(value.contains("/") || "0".equals(value) ? value : value + "/1");
    }

    private static void requireExactDefinition(OperationRequest request) {
        if (!OperationDefinition.V1.ADD_MEDIA_CLIP.definitionId()
                .equals(request.definitionId())
                || !OperationDefinition.V1.ADD_MEDIA_CLIP.version()
                .equals(request.version())
                || !(request.target() instanceof OperationTargetRequest.TimelineTargetRequest)
                || !(request.parameters()
                        instanceof OperationParameters.AddMediaClipParameters)) {
            throw new TimelineOperationException(
                    TimelineOperationException.Code.CANDIDATE_INVALID,
                    List.of("ADD_MEDIA_CLIP_V1 request required"));
        }
    }

    private static String policyRef(
            com.example.platform.shared.authorization.AuthorizationDecision decision) {
        return decision.ruleRef() == null || decision.ruleRef().isBlank()
                ? decision.reasonCode() : decision.ruleRef();
    }

    private static PlaybackDirection toCanonicalDirection(AddMediaClipCommand.Direction direction) {
        if (direction == null || direction == AddMediaClipCommand.Direction.FORWARD) {
            return PlaybackDirection.FORWARD;
        }
        return PlaybackDirection.REVERSE;
    }

    private static TimelineOperationException translatePlanFailure(PlanException failure) {
        return new TimelineOperationException(
                translatePlanErrorCode(failure.code()),
                List.of(failure.getMessage()));
    }

    private static TimelineOperationException.Code translatePlanErrorCode(PlanErrorCode code) {
        return switch (code) {
            case INVALID_PLAN -> TimelineOperationException.Code.INVALID_PLAN;
            case STALE_BASE_REVISION -> TimelineOperationException.Code.STALE_BASE_REVISION;
            case STALE_TARGET_REF -> TimelineOperationException.Code.STALE_TARGET_REF;
            case PLAN_CHANGED -> TimelineOperationException.Code.PLAN_CHANGED;
            case AUTHORIZATION_DENIED -> TimelineOperationException.Code.AUTHORIZATION_DENIED;
            case AUTHORIZATION_CONTEXT_MISMATCH ->
                    TimelineOperationException.Code.AUTHORIZATION_CONTEXT_MISMATCH;
            case IDEMPOTENCY_KEY_CONFLICT ->
                    TimelineOperationException.Code.IDEMPOTENCY_KEY_CONFLICT;
            case TARGET_MISSING -> TimelineOperationException.Code.TARGET_MISSING;
            case UNSUPPORTED_TEMPORAL_STATE ->
                    TimelineOperationException.Code.UNSUPPORTED_TEMPORAL_STATE;
            case UNSUPPORTED_AUDIO_TEMPORAL_BEHAVIOR ->
                    TimelineOperationException.Code.UNSUPPORTED_AUDIO_TEMPORAL_BEHAVIOR;
            case SYNC_ANCHOR_INVALIDATED ->
                    TimelineOperationException.Code.SYNC_ANCHOR_INVALIDATED;
            case GROUP_CARDINALITY_CONFLICT ->
                    TimelineOperationException.Code.GROUP_CARDINALITY_CONFLICT;
            case PLACEMENT_CONFLICT -> TimelineOperationException.Code.PLACEMENT_CONFLICT;
            case BATCH_CONFLICT -> TimelineOperationException.Code.BATCH_CONFLICT;
            case CANONICAL_INVARIANT_VIOLATION ->
                    TimelineOperationException.Code.CANONICAL_INVARIANT_VIOLATION;
            case PERSISTENCE_FAILURE -> TimelineOperationException.Code.PERSISTENCE_FAILURE;
            case REF_UPDATE_FAILURE -> TimelineOperationException.Code.REF_UPDATE_FAILURE;
            case APPLY_UNKNOWN_FAILURE -> TimelineOperationException.Code.APPLY_UNKNOWN_FAILURE;
        };
    }

    private record PreparedOperation(
            OperationPlan plan,
            AddMediaClipPreview preview,
            TimelineDocument exactBase) {
    }

    record InvocationOutcome(ApplyResult result) {
        InvocationOutcome {
            Objects.requireNonNull(result, "result");
        }
    }

    private record ExecutionOutcome(ApplyResult result, PreparedOperation prepared) {
    }

}
