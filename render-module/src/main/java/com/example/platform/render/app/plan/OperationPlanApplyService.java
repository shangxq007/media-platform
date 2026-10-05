package com.example.platform.render.app.plan;

import com.example.platform.timeline.api.revision.TimelineRevisionCommands;
import com.example.platform.operation.plan.ApplyContext;
import com.example.platform.operation.plan.ApplyResult;
import com.example.platform.operation.plan.OperationPlan;
import com.example.platform.operation.plan.OperationPlanner;
import com.example.platform.operation.plan.PlanErrorCode;
import com.example.platform.operation.plan.PlanException;
import com.example.platform.operation.plan.TargetRevisionRef;
import com.example.platform.timeline.api.revision.TimelineRevisionCommandConflictException;
import com.example.platform.timeline.api.revision.TimelineMutationContext;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.revisioncommand.RevisionRef;
import com.example.platform.timeline.version.TimelineConflictException;
import com.example.platform.shared.digest.CanonicalCommandFingerprint;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * APPLICATION_LAYER_COORDINATES_CANONICAL_MUTATION_V1.
 *
 * <p>This service is deliberately not a Timeline writer. It verifies the exact
 * frozen OperationPlan and authorization binding, then delegates the single
 * atomic mutation to {@link TimelineRevisionCommands}, the existing
 * canonical revision authority. No controller, provider, agent callback or
 * Operation type can insert a revision or advance a head through this class.
 */
@Service
public class OperationPlanApplyService {

    public static final String CURRENT_REVISION_REF = RevisionRef.MAIN_REF;

    private static final Logger log = LoggerFactory.getLogger(OperationPlanApplyService.class);

    private final TimelineRevisionCommands revisionSaveService;
    private final TimelineContentDigester digester = new TimelineContentDigester();
    private final OperationPlanner planner = new OperationPlanner();

    public OperationPlanApplyService(TimelineRevisionCommands revisionSaveService) {
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

        log.info("operation.apply.start planDigest={} baseRevisionId={}",
                plan.planDigest(), plan.baseRevisionId());

        verifyAuthorization(plan, context, projectId);
        if (!CURRENT_REVISION_REF.equals(context.targetRef().refId())) {
            log.warn("operation.apply.fail failureCode={} planDigest={}",
                    PlanErrorCode.AUTHORIZATION_CONTEXT_MISMATCH, plan.planDigest());
            throw new PlanException(PlanErrorCode.AUTHORIZATION_CONTEXT_MISMATCH,
                    "canonical apply targets only the main Timeline revision ref");
        }
        if (!plan.baseRevisionId().equals(context.expectedHeadRevisionId())) {
            log.warn("operation.apply.fail failureCode={} planDigest={}",
                    PlanErrorCode.STALE_BASE_REVISION, plan.planDigest());
            throw new PlanException(PlanErrorCode.STALE_BASE_REVISION,
                    "expected head must equal the plan base revision");
        }
        if (!plan.validated()) {
            log.warn("operation.apply.fail failureCode={} planDigest={}",
                    PlanErrorCode.INVALID_PLAN, plan.planDigest());
            throw new PlanException(PlanErrorCode.INVALID_PLAN, "plan is not validated");
        }
        String suppliedBaseHash = digester.digest(exactBase);
        if (!Objects.equals(plan.baseContentHash(), suppliedBaseHash)) {
            log.warn("operation.apply.fail failureCode={} planDigest={}",
                    PlanErrorCode.STALE_BASE_REVISION, plan.planDigest());
            throw new PlanException(PlanErrorCode.STALE_BASE_REVISION,
                    "exact base content hash no longer matches the plan");
        }

        // Candidate integrity: the frozen candidate must match its digest (ALL plans).
        if (!digester.digest(plan.candidateTimeline()).equals(plan.candidateContentHash())) {
            log.warn("operation.apply.fail failureCode={} planDigest={}",
                    PlanErrorCode.PLAN_CHANGED, plan.planDigest());
            throw new PlanException(PlanErrorCode.PLAN_CHANGED,
                    "OperationPlan or candidate changed after preview");
        }
        // Reproducibility re-plan (plannable plans only): re-plan from the immutable
        // instance + exact base immediately before authorization-consuming apply; any
        // plan/candidate drift fails closed. TEXT-OP-APPLY-NPE: typed text plans set
        // replanRequired=false — they are consumed as a frozen plan (the 3-arg
        // planner has no text cases, and text resolution inputs are absent at apply).
        if (plan.replanRequired()) {
            OperationPlan verified = planner.plan(
                    plan.sourceInstance(), context.expectedHeadRevisionId(), exactBase);
            if (!verified.planDigest().equals(plan.planDigest())
                    || !verified.candidateContentHash().equals(plan.candidateContentHash())) {
                log.warn("operation.apply.fail failureCode={} planDigest={}",
                        PlanErrorCode.PLAN_CHANGED, plan.planDigest());
                throw new PlanException(PlanErrorCode.PLAN_CHANGED,
                        "OperationPlan or candidate changed after preview");
            }
        }

        try {
            String commandFingerprint = fingerprint(
                    plan.planDigest(), context.targetRef(), context.expectedHeadRevisionId(),
                    projectId, context.tenantId(), context.principalRef(),
                    plan.sourceInstance().definitionId().value(),
                    plan.sourceInstance().parameterDigest());
            var command = new TimelineRevisionCommands.RevisionWriteCommand(
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
                log.warn("operation.apply.fail failureCode={} planDigest={}",
                        PlanErrorCode.PERSISTENCE_FAILURE, plan.planDigest());
                throw new PlanException(PlanErrorCode.PERSISTENCE_FAILURE,
                        "canonical writer committed a different Timeline digest");
            }
            return ApplyResult.applied(plan.planDigest(), context.applyCommandId(),
                    plan.baseRevisionId(), result.revisionId(), committedTimelineHash,
                    result.parentRevisionId(), context.targetRef().refId());
        } catch (TimelineConflictException conflict) {
            log.warn("operation.apply.fail failureCode={} planDigest={}",
                    PlanErrorCode.STALE_BASE_REVISION, plan.planDigest());
            throw new PlanException(PlanErrorCode.STALE_BASE_REVISION, conflict.getMessage());
        } catch (TimelineRevisionCommandConflictException conflict) {
            log.warn("operation.apply.fail failureCode={} planDigest={}",
                    PlanErrorCode.IDEMPOTENCY_KEY_CONFLICT, plan.planDigest());
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
            log.warn("operation.authorization decision=denied reason={}",
                    PlanErrorCode.AUTHORIZATION_CONTEXT_MISMATCH);
            throw new PlanException(PlanErrorCode.AUTHORIZATION_CONTEXT_MISMATCH,
                    "authorization context does not match apply context");
        }
        if (!authorization.allowed()) {
            log.warn("operation.authorization decision=denied reason={}",
                    PlanErrorCode.AUTHORIZATION_DENIED);
            throw new PlanException(PlanErrorCode.AUTHORIZATION_DENIED, "authorization denied");
        }
        log.debug("operation.authorization decision=allowed");
    }

}
