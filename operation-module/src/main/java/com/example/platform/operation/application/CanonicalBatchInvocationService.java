package com.example.platform.operation.application;

import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import com.example.platform.operation.batch.BatchCommitResult;
import com.example.platform.operation.batch.BatchErrorCode;
import com.example.platform.operation.batch.BatchException;
import com.example.platform.operation.batch.BatchInvocationPort;
import com.example.platform.operation.batch.BatchPreview;
import com.example.platform.operation.batch.BatchTargetDomain;
import com.example.platform.operation.batch.OperationBatchContext;
import com.example.platform.operation.batch.OperationBatchPlan;
import com.example.platform.operation.batch.OperationBatchPlanner;
import com.example.platform.operation.batch.OperationBatchRequest;
import com.example.platform.operation.plan.AuthorizationDecision;
import com.example.platform.shared.authorization.AuthorizationAction;
import com.example.platform.shared.authorization.AuthorizationContext;
import com.example.platform.shared.authorization.AuthorizationRequest;
import com.example.platform.shared.authorization.AuthorizationResourceType;
import com.example.platform.shared.authorization.AuthorizableResourceRef;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.timeline.canonical.TimelineDocument;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Application boundary for the generic batch port: project/tenant authorization
 * followed by the generic ordered planner and the Timeline domain commit.
 *
 * <p>This is the only batch entry service; REST (and future agents) call the
 * {@link BatchInvocationPort} contract instead of re-implementing planning or
 * commit semantics.
 */
@Service
public final class CanonicalBatchInvocationService implements BatchInvocationPort {

    private static final AuthorizationAction TIMELINE_EDIT = new AuthorizationAction(
            "WRITE", AuthorizationResourceType.PROJECT, "Edit canonical Timeline");
    private static final AuthorizationAction TIMELINE_READ = new AuthorizationAction(
            "READ", AuthorizationResourceType.PROJECT, "Read canonical Timeline for batch planning");

    private final BatchTargetDomain<TimelineDocument> domain;
    private final AuthorizationDecisionPort authorizationPort;
    private final OperationBatchPlanner planner = new OperationBatchPlanner();

    public CanonicalBatchInvocationService(TimelineBatchTargetDomain domain,
                                           AuthorizationDecisionPort authorizationPort) {
        this.domain = Objects.requireNonNull(domain, "domain");
        this.authorizationPort = Objects.requireNonNull(authorizationPort, "authorizationPort");
    }

    @Override
    public BatchPreview preview(OperationBatchRequest request, OperationBatchContext context) {
        requireTenantContext(request, context.actor());
        requireProjectAccess(request, context.actor(), TIMELINE_READ);
        OperationBatchPlan<TimelineDocument> plan = planner.plan(request, domain);
        return BatchPreview.of(plan);
    }

    @Override
    public BatchCommitResult commit(OperationBatchRequest request, OperationBatchContext context) {
        if (context.applyCommandId() == null || context.applyCommandId().isBlank()) {
            throw new BatchException(BatchErrorCode.AUTHORIZATION_CONTEXT_MISMATCH, null,
                    "applyCommandId required for batch commit");
        }
        requireTenantContext(request, context.actor());
        requireProjectAccess(request, context.actor(), TIMELINE_READ);
        OperationBatchPlan<TimelineDocument> plan = planner.plan(request, domain);

        if (context.expectedBatchDigest() != null && !plan.batchDigest().equals(context.expectedBatchDigest())) {
            throw new BatchException(BatchErrorCode.PLAN_CHANGED, null, "Confirmed plan differs from submitted batch");
        }
        var securityDecision = authorizationPort.decide(new AuthorizationRequest(
                context.actor(),
                TIMELINE_EDIT,
                new AuthorizableResourceRef(AuthorizationResourceType.PROJECT,
                        request.target().projectId(), request.target().tenantId(),
                        request.target().projectId(), null),
                new AuthorizationContext("timeline-batch", null,
                        Map.of("batchDigest", plan.batchDigest()))));
        String policyRef = securityDecision.ruleRef() == null || securityDecision.ruleRef().isBlank()
                ? securityDecision.reasonCode() : securityDecision.ruleRef();
        AuthorizationDecision bound = securityDecision.allowed()
                ? AuthorizationDecision.allow(plan.batchDigest(), context.actor().actorId(),
                        request.target().projectId(), request.target().tenantId(),
                        request.target().refId(), policyRef)
                : AuthorizationDecision.deny(plan.batchDigest(), context.actor().actorId(),
                        request.target().projectId(), request.target().tenantId(),
                        request.target().refId(), policyRef);
        if (!bound.allowed()) {
            throw new BatchException(BatchErrorCode.AUTHORIZATION_DENIED, null,
                    "project batch write access denied");
        }
        OperationBatchContext authorized = OperationBatchContext.apply(
                context.applyCommandId(), context.actor(), bound, context.correlationId());
        return domain.commit(request, plan, authorized);
    }

    private void requireTenantContext(OperationBatchRequest request, CanonicalActor actor) {
        if (actor.tenantId() == null || actor.tenantId().isBlank()
                || !actor.tenantId().equals(request.target().tenantId())) {
            throw new BatchException(BatchErrorCode.AUTHORIZATION_CONTEXT_MISMATCH, null,
                    "authenticated actor tenant must equal the batch target tenant");
        }
    }

    private void requireProjectAccess(OperationBatchRequest request, CanonicalActor actor,
                                      AuthorizationAction action) {
        var decision = authorizationPort.decide(new AuthorizationRequest(
                actor,
                action,
                new AuthorizableResourceRef(AuthorizationResourceType.PROJECT,
                        request.target().projectId(), request.target().tenantId(),
                        request.target().projectId(), null),
                new AuthorizationContext("timeline-batch-prepare", null,
                        Map.of("operation", "OPERATION_BATCH"))));
        if (!decision.allowed()) {
            throw new BatchException(BatchErrorCode.AUTHORIZATION_DENIED, null,
                    "project batch access denied");
        }
    }
}
