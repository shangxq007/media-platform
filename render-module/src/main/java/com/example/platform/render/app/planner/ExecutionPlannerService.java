package com.example.platform.render.app.planner;

import com.example.platform.render.app.producer.ProducerRuntimeService;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.domain.planner.*;
import com.example.platform.shared.Ids;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ExecutionPlannerService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionPlannerService.class);
    private final ProducerRuntimeService producerRuntime;
    private final ProductRuntimeService productRuntime;
    private final CapabilityResolutionService capabilityResolver;

    public ExecutionPlannerService(ProducerRuntimeService producerRuntime,
                                     ProductRuntimeService productRuntime,
                                     CapabilityResolutionService capabilityResolver) {
        this.producerRuntime = producerRuntime;
        this.productRuntime = productRuntime;
        this.capabilityResolver = capabilityResolver;
    }

    public ExecutionPlan plan(String targetProductId, String targetProductType,
                                String tenantId, String projectId) {
        log.info("Planner: planning target={} type={}", targetProductId, targetProductType);
        var existing = productRuntime.find(targetProductId);
        if (existing.isPresent() && existing.get().status() ==
                com.example.platform.render.domain.product.ProductStatus.READY) {
            return ExecutionPlan.of(Ids.newId("eplan"), tenantId, projectId,
                    targetProductId, targetProductType, List.of());
        }

        List<ExecutionStep> steps = new ArrayList<>();
        var resolution = capabilityResolver.resolve(targetProductType);
        String producer = resolution.producerId() != null ? resolution.producerId() : "unknown";

        ExecutionStep step = ExecutionStep.of(producer, List.of(targetProductId), List.of(targetProductType));
        if (resolution.resolved()) {
            step = step.withBackend(resolution.backendId(), resolution.backendType(), resolution.selectionReason());
        }
        steps.add(step);

        var dependencies = productRuntime.findDependencies(targetProductId);
        for (var dep : dependencies) {
            var upstream = productRuntime.find(dep.dependsOnProductId());
            if (upstream.isEmpty() || upstream.get().status() !=
                    com.example.platform.render.domain.product.ProductStatus.READY) {
                String ut = upstream.map(p -> p.productType().name()).orElse("UNKNOWN");
                var res = capabilityResolver.resolve(ut);
                var dStep = ExecutionStep.of(res.producerId() != null ? res.producerId() : "unknown",
                        List.of(dep.dependsOnProductId()), List.of(ut));
                if (res.resolved()) dStep = dStep.withBackend(res.backendId(), res.backendType(), res.selectionReason());
                steps.add(dStep);
            }
        }

        boolean parallel = steps.size() > 1;
        ExecutionStage stage = ExecutionStage.of(Ids.newId("estg"), 0, parallel, steps);
        ExecutionPlan plan = ExecutionPlan.of(Ids.newId("eplan"), tenantId, projectId,
                targetProductId, targetProductType, List.of(stage));
        log.info("Planner: plan created id={} steps={} parallel={}", plan.planId(), steps.size(), parallel);
        return plan;
    }

    public String explain(ExecutionPlan plan) {
        StringBuilder sb = new StringBuilder("Plan " + plan.planId() + ":\n");
        for (var stage : plan.stages()) {
            sb.append("  Stage ").append(stage.order())
                    .append(stage.parallel() ? " (parallel)" : " (sequential)").append(":\n");
            for (var step : stage.steps()) {
                var existing = productRuntime.find(step.inputProductIds().get(0));
                String status = existing.map(p -> p.status().name()).orElse("MISSING");
                sb.append("    - ").append(status).append(" → Producer ").append(step.producerId())
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
}
