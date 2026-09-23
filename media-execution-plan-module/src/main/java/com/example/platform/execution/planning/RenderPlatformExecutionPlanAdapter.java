package com.example.platform.execution.planning;

import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.render.domain.renderplan.RenderPlanStatus;
import com.example.platform.render.domain.renderplan.RenderPlanningResult;
import java.util.List;
import java.util.Objects;

/** Adapter that keeps Render validation in the Render planning authority. */
public final class RenderPlatformExecutionPlanAdapter {
    private RenderPlatformExecutionPlanAdapter() {}
    public static PlatformExecutionPlan adapt(RenderPlanningResult result, ExecutionPlanId planId,
            PlatformExecutionPlan.Scope scope, String idempotencyKey, String requestHash,
            PlatformExecutionPlan.EntitlementQuotaSnapshot quota,
            PlatformExecutionPlan.CorrelationAuditIdentity audit) {
        Objects.requireNonNull(result); Objects.requireNonNull(planId);
        if (result.status() != RenderPlanStatus.PLANNABLE) {
            throw new ExecutionPlanningException(ExecutionPlanningFailureReason.RENDER_PLANNING_RESULT_NOT_PLANNABLE,
                    new ExecutionPlanningException.RenderStatusRejectedContext(result.status().name(), "render result is not plannable"));
        }
        // This call preserves the existing graph/plan fingerprint and structural checks.
        ExecutionPlanningEntry.plan(result, planId);
        var p = result.plan();
        var inputs = p.nodes().stream().map(n -> new PlatformExecutionPlan.TypedInputReference(
                n.id().value(), "render-node", n.kind().name(), n.id().value())).toList();
        var outputs = p.request().outputs().stream().map(o -> new PlatformExecutionPlan.TypedOutputExpectation(
                o.role().name(), "render-output", "1", "artifact")).toList();
        return new PlatformExecutionPlan(scope,
                new PlatformExecutionPlan.SourceRevision("render", p.revision().revisionId(), p.revision().contentDigest().value()),
                planId, new PlatformExecutionPlan.OperationIdentity("render", "render-plan"), inputs, outputs,
                PlatformExecutionPlan.ExecutionMode.ASYNCHRONOUS,
                new PlatformExecutionPlan.IdempotencyIdentity(idempotencyKey, requestHash), quota, audit);
    }
}
