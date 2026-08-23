package com.example.platform.execution.planning;

import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.render.domain.renderplan.RenderPlan;
import com.example.platform.render.domain.renderplan.RenderPlanStatus;
import com.example.platform.render.domain.renderplan.RenderPlanningResult;
import java.util.Objects;

/**
 * Roadmap #21 Correction 6 (C6-C) — guarded application-facing execution
 * planning entry.
 *
 * <p>THE supported production boundary from render planning into #21:
 *
 * <pre>
 *   RenderPlanningResult
 *     → require status == PLANNABLE        (render-status gate)
 *     → exact RenderPlan + RenderGraph
 *     → internal logical/physical planning chain
 * </pre>
 *
 * <p>PLANNABLE/UNRENDERABLE authority stays with the render planning result
 * (RenderPlanStatus is NOT redefined here and #21 is not a render-status
 * authority). Any result whose status != PLANNABLE is REJECTED with
 * {@link ExecutionPlanningFailureReason#RENDER_PLANNING_RESULT_NOT_PLANNABLE}
 * and no LogicalExecutionGraph / PhysicalExecutionPlan is produced.
 */
public final class ExecutionPlanningEntry {

    private ExecutionPlanningEntry() {
    }

    /**
     * Guarded entry: only PLANNABLE render planning results may enter #21
     * structural planning.
     *
     * @throws ExecutionPlanningException with reason
     *         RENDER_PLANNING_RESULT_NOT_PLANNABLE when status != PLANNABLE
     */
    public static PlanningResult plan(
            RenderPlanningResult renderResult, ExecutionPlanId planId) {
        Objects.requireNonNull(renderResult, "renderResult");
        Objects.requireNonNull(planId, "planId");
        if (renderResult.status() != RenderPlanStatus.PLANNABLE) {
            throw new ExecutionPlanningException(
                    ExecutionPlanningFailureReason.RENDER_PLANNING_RESULT_NOT_PLANNABLE,
                    new ExecutionPlanningException.RenderStatusRejectedContext(
                            renderResult.status().name(),
                            "only PLANNABLE render planning results may enter #21"));
        }
        RenderPlan plan = renderResult.plan();
        Objects.requireNonNull(plan, "plan");
        return LogicalPhysicalPlanner.plan(
                plan, renderResult.graph(), planId);
    }

    /** Public execution-planning result carrier owned by the public entry. */
    public record PlanningResult(
            ExecutionRequirement executionRequirement,
            LogicalExecutionGraph logicalExecutionGraph,
            PhysicalExecutionPlan physicalExecutionPlan) {
    }
}
