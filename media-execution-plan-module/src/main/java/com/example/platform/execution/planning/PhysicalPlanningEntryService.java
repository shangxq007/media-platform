package com.example.platform.execution.planning;

import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.render.domain.renderplan.RenderPlanningResult;
import java.util.Objects;

/**
 * Production entry into typed-chain stage #21 (provider-neutral execution planning).
 *
 * <p>This is the application-facing boundary that turns a #20 render planning
 * result into the #21 structural execution plan:
 *
 * <pre>
 *   RenderPlanningResult (status must be PLANNABLE — guarded by #21)
 *     → ExecutionPlanningEntry.plan(...)   (fail closed, typed reason on rejection)
 *     → ExecutionRequirement
 *     → LogicalExecutionGraph
 *     → PhysicalExecutionPlan
 * </pre>
 *
 * <p>Boundary contract:
 * <ul>
 *   <li>The PLANNABLE gate is owned by #20 ({@code RenderPlanStatus}); this entry
 *       does not redefine it. Any result whose status != PLANNABLE is rejected
 *       with {@link ExecutionPlanningFailureReason#RENDER_PLANNING_RESULT_NOT_PLANNABLE}
 *       and no logical or physical plan is produced.</li>
 *   <li>{@link ExecutionPlanId} is caller-supplied plan identity, never derived
 *       from semantic content or the plan fingerprint.</li>
 *   <li>No provider/worker/device/queue binding and no mutable runtime read:
 *       provider binding is stage #22 and is deliberately NOT invoked here.</li>
 * </ul>
 *
 * <p>Scope: stage #21 only. Compatibility/feasibility and the provider-bound
 * executable task graph (#22) consume the returned physical plan through their
 * own entries; runtime lowering/adapter execution (#15/#16) is out of scope.
 */
public final class PhysicalPlanningEntryService {

    /**
     * Plans a #20 render result into the #21 structural execution plan.
     *
     * @throws com.example.platform.execution.planning.ExecutionPlanningException
     *         when the render result is not PLANNABLE, or when the physical plan
     *         postconditions cannot be established (fail closed)
     */
    public ExecutionPlanningEntry.PlanningResult plan(
            RenderPlanningResult renderResult, ExecutionPlanId planId) {
        Objects.requireNonNull(renderResult, "renderResult");
        Objects.requireNonNull(planId, "planId");
        return ExecutionPlanningEntry.plan(renderResult, planId);
    }

    /**
     * Plans a #20 render result and projects it onto the platform admission
     * contract owned by this stage.
     *
     * <p>The underlying adapter re-runs the guarded #21 entry (same PLANNABLE
     * gate and structural checks) and preserves the exact plan fingerprint.
     */
    public PlatformExecutionPlan planForAdmission(
            RenderPlanningResult renderResult, ExecutionPlanId planId, AdmissionIdentity identity) {
        Objects.requireNonNull(renderResult, "renderResult");
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(identity, "identity");
        return RenderPlatformExecutionPlanAdapter.adapt(
                renderResult,
                planId,
                identity.scope(),
                identity.idempotencyKey(),
                identity.requestHash(),
                identity.quota(),
                identity.audit());
    }

    /**
     * Typed admission identity required to build the platform admission
     * contract. Every component is caller-supplied — nothing is derived from
     * render semantics.
     */
    public record AdmissionIdentity(
            PlatformExecutionPlan.Scope scope,
            String idempotencyKey,
            String requestHash,
            PlatformExecutionPlan.EntitlementQuotaSnapshot quota,
            PlatformExecutionPlan.CorrelationAuditIdentity audit) {

        public AdmissionIdentity {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey");
            Objects.requireNonNull(requestHash, "requestHash");
            Objects.requireNonNull(quota, "quota");
            Objects.requireNonNull(audit, "audit");
        }
    }
}
