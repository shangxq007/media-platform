package com.example.platform.render.app.plan;

import com.example.platform.operation.plan.ApplyContext;
import com.example.platform.operation.plan.ApplyResult;
import com.example.platform.operation.plan.OperationPlan;
import com.example.platform.operation.plan.OperationPlanner;
import com.example.platform.operation.plan.PlanErrorCode;
import com.example.platform.operation.plan.PlanException;
import com.example.platform.operation.plan.TargetRevisionRef;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.app.TimelineRevisionCommandConflictException;
import com.example.platform.timeline.app.TimelineMutationContext;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.revisioncommand.RevisionRef;
import com.example.platform.timeline.version.TimelineConflictException;
import com.example.platform.shared.digest.CanonicalCommandFingerprint;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * APPLICATION_LAYER_COORDINATES_CANONICAL_MUTATION_V1.
 *
 * <p>This service is deliberately not a Timeline writer. It verifies the exact
 * frozen OperationPlan and authorization binding, then delegates the single
 * atomic mutation to {@link TimelineRevisionSaveService}, the existing
 * canonical revision authority. No controller, provider, agent callback or
 * Operation type can insert a revision or advance a head through this class.
 */
@Service
public class OperationPlanApplyService {

    public static final String CURRENT_REVISION_REF = RevisionRef.MAIN_REF;

    private final TimelineRevisionSaveService revisionSaveService;
    private final TimelineContentDigester digester = new TimelineContentDigester();
    private final OperationPlanner planner = new OperationPlanner();

    public OperationPlanApplyService(TimelineRevisionSaveService revisionSaveService) {
        this.revisionSaveService = Objects.requireNonNull(revisionSaveService, "revisionSaveService");
    }

    public static String fingerprint(String planDigest, TargetRevisionRef ref, String expectedHead,
                                     String projectId, String tenantId, String principalRef,
                                     String operationIdentity, String parameterDigest) {
        return CanonicalCommandFingerprint.builder("OPERATION_PLAN")
                .required("tenantId", tenantId)
                .required("projectId", projectId)
                .required("targetRefId", ref.refId())
                .required("expectedHeadRevisionId", expectedHead)
                .required("operationIdentity", operationIdentity)
                .required("parameterDigest", parameterDigest)
                .required("planDigest", planDigest)
                .required("principalRef", principalRef)
                .sha256Hex();
    }

    /**
     * Fail-closed atomic apply. {@code exactBase} is the same immutable base
     * used for REQUEST -> RESOLVE -> PLAN; it is never re-resolved as latest.
     */
    public ApplyResult apply(OperationPlan plan, ApplyContext context,
                             String projectId, TimelineDocument exactBase) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(exactBase, "exactBase");

        verifyAuthorization(plan, context, projectId);
        if (!CURRENT_REVISION_REF.equals(context.targetRef().refId())) {
            throw new PlanException(PlanErrorCode.AUTHORIZATION_CONTEXT_MISMATCH,
                    "canonical apply targets only the main Timeline revision ref");
        }
        if (!plan.baseRevisionId().equals(context.expectedHeadRevisionId())) {
            throw new PlanException(PlanErrorCode.STALE_BASE_REVISION,
                    "expected head must equal the plan base revision");
        }
        if (!plan.validated()) {
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "plan is not validated");
        }
        String suppliedBaseHash = digester.digest(exactBase);
        if (!Objects.equals(plan.baseContentHash(), suppliedBaseHash)) {
            throw new PlanException(PlanErrorCode.STALE_BASE_REVISION,
                    "exact base content hash no longer matches the plan");
        }

        // Re-plan from the immutable instance + exact base immediately before
        // authorization-consuming apply. Any plan/candidate drift fails closed.
        OperationPlan verified = planner.plan(
                plan.sourceInstance(), context.expectedHeadRevisionId(), exactBase);
        if (!verified.planDigest().equals(plan.planDigest())
                || !verified.candidateContentHash().equals(plan.candidateContentHash())
                || !digester.digest(plan.candidateTimeline()).equals(plan.candidateContentHash())) {
            throw new PlanException(PlanErrorCode.PLAN_CHANGED,
                    "OperationPlan or candidate changed after preview");
        }

        try {
            String commandFingerprint = fingerprint(
                    plan.planDigest(), context.targetRef(), context.expectedHeadRevisionId(),
                    projectId, context.tenantId(), context.principalRef(),
                    plan.sourceInstance().definitionId().value(),
                    plan.sourceInstance().parameterDigest());
            var command = new TimelineRevisionSaveService.RevisionWriteCommand(
                    context.applyCommandId(), plan.planDigest(), commandFingerprint,
                    "OPERATION_PLAN", context.tenantId());
            RevisionRef targetRef = new RevisionRef(
                    context.tenantId(), projectId, context.targetRef().refId());
            TimelineMutationContext mutationContext = new TimelineMutationContext(
                    context.tenantId(), projectId, context.actor());
            if (plan.noOp()) {
                var noOp = revisionSaveService.recordNoOpCommand(
                        mutationContext, targetRef, plan.baseRevisionId(),
                        plan.baseContentHash(), command);
                return ApplyResult.noOp(plan.planDigest(), context.applyCommandId(),
                        plan.baseRevisionId(), noOp.timelineContentHash(),
                        context.targetRef().refId());
            }
            var result = revisionSaveService.saveRevisionForCommand(
                    mutationContext, targetRef, plan.baseRevisionId(),
                    plan.candidateTimeline(), command);
            String committedTimelineHash = result.timelineContentHash();
            if (!plan.candidateContentHash().equals(committedTimelineHash)) {
                throw new PlanException(PlanErrorCode.PERSISTENCE_FAILURE,
                        "canonical writer committed a different Timeline digest");
            }
            return ApplyResult.applied(plan.planDigest(), context.applyCommandId(),
                    plan.baseRevisionId(), result.revisionId(), committedTimelineHash,
                    result.parentRevisionId(), context.targetRef().refId());
        } catch (TimelineConflictException conflict) {
            throw new PlanException(PlanErrorCode.STALE_BASE_REVISION, conflict.getMessage());
        } catch (TimelineRevisionCommandConflictException conflict) {
            throw new PlanException(PlanErrorCode.IDEMPOTENCY_KEY_CONFLICT, conflict.getMessage());
        }
    }

    private static void verifyAuthorization(OperationPlan plan, ApplyContext context, String projectId) {
        var authorization = context.authorization();
        if (!authorization.planDigest().equals(plan.planDigest())) {
            throw new PlanException(PlanErrorCode.PLAN_CHANGED,
                    "authorization does not bind the exact plan digest");
        }
        if (!authorization.projectId().equals(projectId)
                || !authorization.tenantId().equals(context.tenantId())
                || !authorization.targetRefId().equals(context.targetRef().refId())
                || !authorization.principalRef().equals(context.principalRef())) {
            throw new PlanException(PlanErrorCode.AUTHORIZATION_CONTEXT_MISMATCH,
                    "authorization context does not match apply context");
        }
        if (!authorization.allowed()) {
            throw new PlanException(PlanErrorCode.AUTHORIZATION_DENIED, "authorization denied");
        }
    }

}
