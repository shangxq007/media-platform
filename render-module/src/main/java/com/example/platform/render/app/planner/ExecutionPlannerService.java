package com.example.platform.render.app.planner;

import com.example.platform.render.domain.planner.*;
import com.example.platform.shared.Ids;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Logical execution planner (PRE-#21 W1).
 *
 * <p>PURITY CONTRACT (frozen C1/C2/C3): this service is pure computation over
 * an explicit {@link FrozenPlanningContext}. It MUST NOT read mutable runtime
 * state — no ProductRuntimeService, no ProducerRuntimeService, no runtime
 * registries, no capability resolution during planning. All runtime/capability
 * facts arrive pre-resolved and frozen in the context.
 *
 * <p>Target flow:
 * <pre>
 *   Runtime / Capability Resolution
 *           → FrozenPlanningContext
 *           → this pure planner
 *           → ExecutionPlan
 * </pre>
 */
@Service
public class ExecutionPlannerService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionPlannerService.class);

    public ExecutionPlannerService() {
        // No runtime dependencies. The planner is pure.
    }

    public ExecutionPlan plan(FrozenPlanningContext ctx) {
        log.info("Planner: planning target={} type={}", ctx.targetProductId(), ctx.targetProductType());
        if (ctx.targetAlreadyReady()) {
            return ExecutionPlan.of(Ids.newId("eplan"), ctx.tenantId(), ctx.projectId(),
                    ctx.targetProductId(), ctx.targetProductType(), List.of());
        }

        List<ExecutionStep> steps = new ArrayList<>();
        steps.add(buildStep(ctx, ctx.targetProductType(), ctx.targetProductId()));

        for (FrozenPlanningContext.DependencyFact dep : ctx.dependencyFacts().values()) {
            // Frozen fact: dependency is NOT ready → must be planned.
            if (!"READY".equals(dep.status())) {
                steps.add(buildStep(ctx, dep.productType(), dep.productId()));
            }
        }

        boolean parallel = steps.size() > 1;
        ExecutionStage stage = ExecutionStage.of(Ids.newId("estg"), 0, parallel, steps);
        ExecutionPlan plan = ExecutionPlan.of(Ids.newId("eplan"), ctx.tenantId(), ctx.projectId(),
                ctx.targetProductId(), ctx.targetProductType(), List.of(stage));
        log.info("Planner: plan created id={} steps={} parallel={}", plan.planId(), steps.size(), parallel);
        return plan;
    }

    public String explain(ExecutionPlan plan) {
        StringBuilder sb = new StringBuilder("Plan " + plan.planId() + ":\n");
        for (var stage : plan.stages()) {
            sb.append("  Stage ").append(stage.order())
                    .append(stage.parallel() ? " (parallel)" : " (sequential)").append(":\n");
            for (var step : stage.steps()) {
                sb.append("    - ").append("PLANNED").append(" → Producer ").append(step.producerId())
                        .append(" → ").append(step.expectedOutputTypes());
                if (step.backendResolved()) {
                    sb.append(" [backend=").append(step.backendId())
                            .append(" reason=").append(step.backendSelectionReason()).append("]");
                } else {
                    sb.append(" [UNRESOLVED]");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private ExecutionStep buildStep(FrozenPlanningContext ctx, String productType, String productId) {
        FrozenPlanningContext.CapabilityResolutionFact fact = ctx.capabilityFacts().get(productType);
        String producer = fact != null && fact.producerId() != null ? fact.producerId() : "unknown";
        ExecutionStep step = ExecutionStep.of(producer, List.of(productId), List.of(productType));
        if (fact != null && fact.resolved()) {
            step = step.withBackend(fact.backendId(), fact.backendType(), fact.selectionReason());
        }
        return step;
    }
}
