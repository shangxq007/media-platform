package com.example.platform.render.app.operation;

import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import com.example.platform.operation.invocation.OperationInvocationContext;
import com.example.platform.operation.operation.OperationErrorCode;
import com.example.platform.operation.operation.OperationInstance;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationRequestResolver;
import com.example.platform.operation.operation.OperationResolutionException;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.operation.operation.TextOperationPlanner;
import com.example.platform.operation.plan.ApplyContext;
import com.example.platform.operation.plan.ApplyResult;
import com.example.platform.operation.plan.AuthorizationDecision;
import com.example.platform.operation.plan.OperationPlan;
import com.example.platform.operation.plan.OperationPlanDigest;
import com.example.platform.operation.plan.OperationPlanner;
import com.example.platform.operation.plan.PlanException;
import com.example.platform.operation.plan.TargetRevisionRef;
import com.example.platform.render.app.plan.OperationPlanApplyService;
import com.example.platform.shared.authorization.AuthorizableResourceRef;
import com.example.platform.shared.authorization.AuthorizationAction;
import com.example.platform.shared.authorization.AuthorizationContext;
import com.example.platform.shared.authorization.AuthorizationRequest;
import com.example.platform.shared.authorization.AuthorizationResourceType;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.timeline.api.composition.TimelineCanonicalRejectionException;
import com.example.platform.timeline.api.composition.TimelineValidation;
import com.example.platform.timeline.api.revision.TimelineRevisionCommands;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonicalmodel.TimelineDiagnostic;
import com.example.platform.timeline.version.TimelineRevision;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * P2-5.5 canonical caption write path — application coordinator for the nine
 * timeline.text.* operations:
 * REQUEST -&gt; RESOLVE -&gt; PLAN (text planner) -&gt; VALIDATE -&gt; PREVIEW -&gt; AUTHORIZE -&gt; ATOMIC APPLY.
 *
 * <p>Mirrors {@link TimelineMediaClipOperationService} but plans through the
 * text-op overload of {@link OperationPlanner} which delegates to
 * {@link TextOperationPlanner}. Timeline remains the sole composition and
 * revision authority; this service owns sequencing only.
 */
@Service
public class TextOperationService {

    public static final String OPERATION = "TEXT_OPERATION_V1";
    private static final AuthorizationAction TIMELINE_EDIT = new AuthorizationAction(
            "WRITE", AuthorizationResourceType.PROJECT, "Edit canonical Timeline");
    private static final AuthorizationAction TIMELINE_READ = new AuthorizationAction(
            "READ", AuthorizationResourceType.PROJECT, "Read canonical Timeline for preview");

    private final TimelineRevisionCommands revisionSaveService;
    private final TimelineValidation timelineValidator;
    private final AuthorizationDecisionPort authorizationPort;
    private final OperationPlanApplyService applyService;
    private final OperationPlanner planner = new OperationPlanner();
    private final TextOperationPlanner.FontResolutionInput fontResolutionInput;

    public TextOperationService(
            TimelineRevisionCommands revisionSaveService,
            TimelineValidation timelineValidator,
            AuthorizationDecisionPort authorizationPort,
            OperationPlanApplyService applyService,
            TextOperationPlanner.FontResolutionInput fontResolutionInput) {
        this.revisionSaveService = Objects.requireNonNull(revisionSaveService, "revisionSaveService");
        this.timelineValidator = Objects.requireNonNull(timelineValidator, "timelineValidator");
        this.authorizationPort = Objects.requireNonNull(authorizationPort, "authorizationPort");
        this.applyService = Objects.requireNonNull(applyService, "applyService");
        this.fontResolutionInput = Objects.requireNonNull(fontResolutionInput, "fontResolutionInput");
    }

    public TextOperationPreview preview(
            String tenantId, String projectId, OperationRequest request, CanonicalActor actor) {
        requirePreparationAuthorization(tenantId, projectId, actor, TIMELINE_READ);
        return prepare(tenantId, projectId, request).preview();
    }

    public TextOperationResult authorizeAndApply(
            String tenantId, String projectId, OperationRequest request,
            String expectedPlanDigest, String applyCommandId, CanonicalActor actor) {
        requirePreparationAuthorization(tenantId, projectId, actor, TIMELINE_READ);
        PreparedOperation prepared = prepare(tenantId, projectId, request);
        ApplyResult result = executePrepared(
                tenantId, projectId, prepared, expectedPlanDigest, applyCommandId, actor);
        return new TextOperationResult(result.status(), result.planDigest(),
                result.baseRevisionId(), result.newRevisionId(), result.newContentHash(),
                result.parentRevisionId());
    }

    void validateInvocation(OperationRequest request, OperationInvocationContext context) {
        String projectId = ((OperationTargetRequest.TimelineTargetRequest) request.target()).timelineId();
        requirePreparationAuthorization(context.actor().tenantId(), projectId, context.actor(), TIMELINE_EDIT);
        prepare(tenantIdOf(context), projectId, request);
    }

    InvocationOutcome invoke(OperationRequest request, OperationInvocationContext context) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(context, "context");
        String projectId = ((OperationTargetRequest.TimelineTargetRequest) request.target()).timelineId();
        String tenantId = tenantIdOf(context);
        PreparedOperation prepared = prepare(tenantId, projectId, request);
        ApplyResult result = executePrepared(tenantId, projectId, prepared,
                prepared.plan().planDigest(), context.invocationId(), context.actor());
        return new InvocationOutcome(result);
    }

    private static String tenantIdOf(OperationInvocationContext context) {
        String tenantId = context.actor() == null ? null : context.actor().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new TimelineOperationException(
                    TimelineOperationException.Code.TENANT_CONTEXT_MISMATCH,
                    List.of("authenticated actor tenant required"));
        }
        return tenantId;
    }

    private PreparedOperation prepare(String tenantId, String projectId, OperationRequest request) {
        requireTextOperation(request);
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

        final OperationInstance instance;
        try {
            instance = OperationRequestResolver.resolve(request,
                    new OperationRequestResolver.OperationBaseContext(
                            request.baseRevisionId(), authoritativeBaseHash, exactBase, projectId));
        } catch (OperationResolutionException resolution) {
            TimelineOperationException.Code code =
                    resolution.code() == OperationErrorCode.STALE_BASE_REVISION
                            ? TimelineOperationException.Code.STALE_BASE_REVISION
                            : TimelineOperationException.Code.CANDIDATE_INVALID;
            throw new TimelineOperationException(code, List.of(resolution.getMessage()));
        }

        final OperationPlan plan;
        try {
            plan = planner.plan(instance, baseRevision.revisionId(), exactBase, fontResolutionInput);
        } catch (TextOperationPlanner.TextPlanException textFailure) {
            throw new TimelineOperationException(
                    TimelineOperationException.Code.INVALID_PLAN, List.of(textFailure.getMessage()));
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
        List<String> changeKeys = plan.plannedChanges().stream()
                .map(OperationPlanDigest::changeKey).toList();
        TextOperationPreview preview = new TextOperationPreview(
                OPERATION, plan.planDigest(), projectId, plan.baseRevisionId(),
                plan.baseContentHash(), plan.candidateContentHash(), plan.noOp(),
                changeKeys, validationProjection);
        return new PreparedOperation(plan, preview, exactBase);
    }

    private ApplyResult executePrepared(
            String tenantId, String projectId, PreparedOperation prepared,
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
                new AuthorizationContext("timeline-text-operation", null,
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
            throw new TimelineOperationException(
                    TimelineOperationException.Code.CANDIDATE_INVALID,
                    java.util.stream.Stream.concat(
                            rejection.diagnostics().stream().map(d -> d.message()),
                            rejection.adapterDiagnostics().stream().map(d -> d.message()))
                            .toList());
        } catch (PlanException failure) {
            throw translatePlanFailure(failure);
        }
    }

    private void requirePreparationAuthorization(
            String tenantId, String projectId, CanonicalActor actor, AuthorizationAction action) {
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
                        "timeline-text-operation-prepare", null,
                        Map.of("operation", OPERATION))));
        if (!decision.allowed()) {
            throw new TimelineOperationException(
                    TimelineOperationException.Code.AUTHORIZATION_DENIED,
                    List.of("project operation access denied"));
        }
    }

    private static void requireTextOperation(OperationRequest request) {
        if (request == null || request.definitionId() == null
                || request.definitionId().value() == null
                || !request.definitionId().value().startsWith("timeline.text.")
                || !(request.target() instanceof OperationTargetRequest.TimelineTargetRequest)) {
            throw new TimelineOperationException(
                    TimelineOperationException.Code.CANDIDATE_INVALID,
                    List.of("timeline.text.* request required"));
        }
    }

    private static String policyRef(
            com.example.platform.shared.authorization.AuthorizationDecision decision) {
        return decision.ruleRef() == null || decision.ruleRef().isBlank()
                ? decision.reasonCode() : decision.ruleRef();
    }

    private static TimelineOperationException translatePlanFailure(PlanException failure) {
        return new TimelineOperationException(
                TimelineOperationException.Code.INVALID_PLAN, List.of(failure.getMessage()));
    }

    private record PreparedOperation(
            OperationPlan plan, TextOperationPreview preview, TimelineDocument exactBase) {
    }

    public record TextOperationPreview(
            String operation,
            String planDigest,
            String projectId,
            String baseRevisionId,
            String baseContentHash,
            String candidateContentHash,
            boolean noOp,
            List<String> changeKeys,
            List<String> validationProjection) {
    }

    public record TextOperationResult(
            String status,
            String planDigest,
            String baseRevisionId,
            String newRevisionId,
            String newContentHash,
            String parentRevisionId) {
    }

    record InvocationOutcome(ApplyResult result) {
        InvocationOutcome {
            Objects.requireNonNull(result, "result");
        }
    }
}
