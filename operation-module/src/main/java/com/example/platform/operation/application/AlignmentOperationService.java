package com.example.platform.operation.application;

import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import com.example.platform.operation.invocation.OperationInvocationContext;
import com.example.platform.operation.operation.OperationDefinition;
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
import com.example.platform.operation.plan.PlanException;
import com.example.platform.operation.plan.TargetRevisionRef;
import com.example.platform.shared.authorization.AuthorizationAction;
import com.example.platform.shared.authorization.AuthorizationContext;
import com.example.platform.shared.authorization.AuthorizationRequest;
import com.example.platform.shared.authorization.AuthorizationResourceType;
import com.example.platform.shared.authorization.AuthorizableResourceRef;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.timeline.api.composition.TimelineCanonicalRejectionException;
import com.example.platform.timeline.api.composition.TimelineValidation;
import com.example.platform.timeline.api.revision.TimelineRevisionCommands;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonicalmodel.TimelineDiagnostic;
import com.example.platform.timeline.semantics.relationship.AlignmentAttribute;
import com.example.platform.timeline.semantics.relationship.AlignmentId;
import com.example.platform.timeline.semantics.relationship.SourceUnitId;
import com.example.platform.timeline.version.TimelineRevision;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * L5 lineage canonical application coordinator (Operation V2):
 * REQUEST -> RESOLVE -> PLAN -> VALIDATE -> PREVIEW -> AUTHORIZE -> ATOMIC APPLY.
 *
 * <p>The alignment is materialised by {@link OperationPlanner} into the candidate Timeline; this
 * service owns only application sequencing and never writes canonical state itself — persistence
 * remains the single {@link OperationPlanApplyService} boundary.
 */
@Service
public class AlignmentOperationService {

    public static final String OPERATION = "CREATE_ALIGNMENT_V1";
    private static final AuthorizationAction TIMELINE_EDIT = new AuthorizationAction(
            "WRITE", AuthorizationResourceType.PROJECT, "Edit canonical Timeline");
    private static final AuthorizationAction TIMELINE_READ = new AuthorizationAction(
            "READ", AuthorizationResourceType.PROJECT, "Read canonical Timeline for preview");

    private final TimelineRevisionCommands revisionSaveService;
    private final AuthorizationDecisionPort authorizationPort;
    private final OperationPlanApplyService applyService;
    private final TimelineValidation timelineValidator;
    private final OperationPlanner planner = new OperationPlanner();

    public AlignmentOperationService(TimelineRevisionCommands revisionSaveService,
                                     AuthorizationDecisionPort authorizationPort,
                                     OperationPlanApplyService applyService,
                                     TimelineValidation timelineValidator) {
        this.revisionSaveService = Objects.requireNonNull(revisionSaveService, "revisionSaveService");
        this.authorizationPort = Objects.requireNonNull(authorizationPort, "authorizationPort");
        this.applyService = Objects.requireNonNull(applyService, "applyService");
        this.timelineValidator = Objects.requireNonNull(timelineValidator, "timelineValidator");
    }

    /** Caller-supplied typed alignment descriptor + exact immutable base binding. */
    public record CreateAlignmentCommand(
            AlignmentId alignmentId,
            Set<SourceUnitId> sourceUnits,
            Set<SourceUnitId> derivedUnits,
            List<AlignmentAttribute> attributes,
            String baseRevisionId,
            String baseContentHash) {

        OperationRequest toOperationRequest(String timelineId) {
            return new OperationRequest(
                    OperationDefinition.V2.CREATE_ALIGNMENT.definitionId(),
                    OperationDefinition.V2.CREATE_ALIGNMENT.version(),
                    new OperationTargetRequest.TimelineTargetRequest(timelineId),
                    new OperationParameters.CreateAlignmentParameters(
                            alignmentId, sourceUnits, derivedUnits, attributes),
                    baseRevisionId, baseContentHash, null);
        }
    }

    public record AlignmentPreview(
            String operation, String planDigest, String projectId, String baseRevisionId,
            String baseContentHash, String candidateContentHash,
            List<String> validation, List<String> warnings, List<String> blockers) {
    }

    public record AlignmentResult(
            String status, String planDigest, String baseRevisionId,
            String newRevisionId, String newContentHash, String parentRevisionId) {
    }

    /** Invocation-port outcome carrying the canonical apply result. */
    record InvocationOutcome(ApplyResult result) {
    }

    private record PreparedOperation(OperationPlan plan, AlignmentPreview preview, TimelineDocument exactBase) {
    }

    public AlignmentPreview preview(String tenantId, String projectId,
                                    CreateAlignmentCommand command, CanonicalActor actor) {
        requirePreparationAuthorization(tenantId, projectId, actor, TIMELINE_READ);
        return prepare(tenantId, projectId, command.toOperationRequest(projectId)).preview();
    }

    public AlignmentResult authorizeAndApply(String tenantId, String projectId,
                                             CreateAlignmentCommand command, String expectedPlanDigest,
                                             String applyCommandId, CanonicalActor actor) {
        requirePreparationAuthorization(tenantId, projectId, actor, TIMELINE_READ);
        PreparedOperation prepared = prepare(tenantId, projectId, command.toOperationRequest(projectId));
        ApplyResult result = executePrepared(tenantId, projectId, prepared, expectedPlanDigest, applyCommandId, actor);
        return new AlignmentResult(result.status(), result.planDigest(), result.baseRevisionId(),
                result.newRevisionId(), result.newContentHash(), result.parentRevisionId());
    }

    void validateInvocation(OperationRequest request, OperationInvocationContext context) {
        String projectId = ((OperationTargetRequest.TimelineTargetRequest) request.target()).timelineId();
        requirePreparationAuthorization(context.actor().tenantId(), projectId, context.actor(), TIMELINE_EDIT);
        prepare(context.actor().tenantId(), projectId, request);
    }

    InvocationOutcome invoke(OperationRequest request, OperationInvocationContext context) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(context, "context");
        var target = (OperationTargetRequest.TimelineTargetRequest) request.target();
        String tenantId = context.actor().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new TimelineOperationException(
                    TimelineOperationException.Code.TENANT_CONTEXT_MISMATCH,
                    List.of("authenticated actor tenant required"));
        }
        PreparedOperation prepared = prepare(tenantId, target.timelineId(), request);
        ApplyResult result = executePrepared(tenantId, target.timelineId(), prepared,
                prepared.plan().planDigest(), context.invocationId(), context.actor());
        return new InvocationOutcome(result);
    }

    private PreparedOperation prepare(String tenantId, String projectId, OperationRequest request) {
        requireExactDefinition(request);
        TimelineRevision baseRevision = revisionSaveService.findById(tenantId, request.baseRevisionId());
        if (baseRevision == null || !projectId.equals(baseRevision.productId())) {
            throw new TimelineOperationException(TimelineOperationException.Code.BASE_REVISION_NOT_FOUND,
                    List.of("base revision not found in target Timeline"));
        }
        String authoritativeBaseHash = baseRevision.semanticContext().timelineContentDigest();
        if (!Objects.equals(request.baseContentHash(), authoritativeBaseHash)) {
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
                            request.baseRevisionId(), authoritativeBaseHash, exactBase, projectId));
        } catch (OperationResolutionException resolution) {
            TimelineOperationException.Code code =
                    resolution.code() == com.example.platform.operation.operation.OperationErrorCode.STALE_BASE_REVISION
                            ? TimelineOperationException.Code.STALE_BASE_REVISION
                            : TimelineOperationException.Code.CANDIDATE_INVALID;
            throw new TimelineOperationException(code, List.of(resolution.getMessage()));
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
        List<String> validationProjection = validation.diagnostics().isEmpty()
                ? List.of("CANONICAL_TIMELINE_VALID")
                : validation.diagnostics().stream()
                        .map(d -> d.code().name() + ":" + d.message()).toList();
        OperationPlanPreview genericPreview = OperationPlanPreview.of(plan);
        AlignmentPreview preview = new AlignmentPreview(
                OPERATION, plan.planDigest(), projectId, plan.baseRevisionId(), plan.baseContentHash(),
                plan.candidateContentHash(), validationProjection,
                genericPreview.warnings(), genericPreview.blockers());
        return new PreparedOperation(plan, preview, exactBase);
    }

    private ApplyResult executePrepared(String tenantId, String projectId, PreparedOperation prepared,
                                        String expectedPlanDigest, String applyCommandId, CanonicalActor actor) {
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
                new AuthorizationContext("alignment-operation", null,
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
        try {
            return applyService.apply(prepared.plan(), context, projectId, prepared.exactBase());
        } catch (TimelineCanonicalRejectionException rejection) {
            throw new TimelineOperationException(TimelineOperationException.Code.CANDIDATE_INVALID,
                    java.util.stream.Stream.concat(
                            rejection.diagnostics().stream().map(d -> d.message()),
                            rejection.adapterDiagnostics().stream().map(d -> d.message())).toList());
        } catch (PlanException failure) {
            throw translatePlanFailure(failure);
        }
    }

    private void requirePreparationAuthorization(String tenantId, String projectId,
                                                 CanonicalActor actor, AuthorizationAction action) {
        Objects.requireNonNull(actor, "actor");
        if (!Objects.equals(tenantId, actor.tenantId())) {
            throw new TimelineOperationException(TimelineOperationException.Code.TENANT_CONTEXT_MISMATCH,
                    List.of("tenant context does not match authenticated actor"));
        }
        var decision = authorizationPort.decide(new AuthorizationRequest(
                actor,
                action,
                new AuthorizableResourceRef(
                        AuthorizationResourceType.PROJECT, projectId, tenantId, projectId, null),
                new AuthorizationContext("alignment-operation-prepare", null,
                        Map.of("operation", OPERATION))));
        if (!decision.allowed()) {
            throw new TimelineOperationException(TimelineOperationException.Code.AUTHORIZATION_DENIED,
                    List.of("project operation access denied"));
        }
    }

    private static void requireExactDefinition(OperationRequest request) {
        if (!OperationDefinition.V2.CREATE_ALIGNMENT.definitionId().equals(request.definitionId())
                || !OperationDefinition.V2.CREATE_ALIGNMENT.version().equals(request.version())
                || !(request.target() instanceof OperationTargetRequest.TimelineTargetRequest)
                || !(request.parameters() instanceof OperationParameters.CreateAlignmentParameters)) {
            throw new TimelineOperationException(TimelineOperationException.Code.CANDIDATE_INVALID,
                    List.of("CREATE_ALIGNMENT_V1 request required"));
        }
    }

    private static String policyRef(com.example.platform.shared.authorization.AuthorizationDecision decision) {
        return decision.ruleRef() == null || decision.ruleRef().isBlank()
                ? decision.reasonCode() : decision.ruleRef();
    }

    private static TimelineOperationException translatePlanFailure(PlanException failure) {
        return new TimelineOperationException(TimelineOperationException.Code.INVALID_PLAN,
                List.of(failure.getMessage()));
    }
}
