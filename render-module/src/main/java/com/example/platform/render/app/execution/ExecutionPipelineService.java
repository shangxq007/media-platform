package com.example.platform.render.app.execution;

import com.example.platform.outbox.app.ExecutionBackendRegistry;
import com.example.platform.outbox.app.ExecutionRequest;
import com.example.platform.outbox.app.ExecutionResult;
import com.example.platform.outbox.domain.TaskCapability;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.domain.execution.*;
import com.example.platform.render.domain.planner.*;
import com.example.platform.render.domain.product.*;
import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Execution Pipeline — orchestrates existing runtimes in the canonical order.
 * Planner → Compiler → Backend → Product Update.
 * No new runtime. No retries. No scheduling.
 */
@Service
public class ExecutionPipelineService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionPipelineService.class);
    private final BackendCompilerRuntimeService compilerRuntime;
    private final ExecutionBackendRegistry backendRegistry;
    private final ProductRuntimeService productRuntime;

    public ExecutionPipelineService(BackendCompilerRuntimeService compilerRuntime,
                                      ExecutionBackendRegistry backendRegistry,
                                      ProductRuntimeService productRuntime) {
        this.compilerRuntime = compilerRuntime;
        this.backendRegistry = backendRegistry;
        this.productRuntime = productRuntime;
    }

    public ExecutionPipelineResult execute(ExecutionPlan plan) {
        long start = System.currentTimeMillis();
        log.info("Pipeline: executing plan={}", plan.planId());

        try {
            BackendExecutionSpec spec = compilerRuntime.compile(plan);
            if (spec == null) {
                return ExecutionPipelineResult.failure(plan.planId(),
                        "No compiler for plan", System.currentTimeMillis() - start);
            }

            TaskCapability cap = extractCapability(plan);
            if (cap == null) {
                return ExecutionPipelineResult.failure(plan.planId(),
                        "No capability in plan", System.currentTimeMillis() - start);
            }

            var backend = backendRegistry.resolve(cap);
            if (backend.isEmpty()) {
                return ExecutionPipelineResult.failure(plan.planId(),
                        "No backend for capability " + cap, System.currentTimeMillis() - start);
            }

            ExecutionRequest req = ExecutionRequest.of(plan.planId(), spec.executionSpecId(),
                    cap, List.of(), 300);
            ExecutionResult result = backend.get().execute(req);

            if (!result.success()) {
                return ExecutionPipelineResult.failure(plan.planId(),
                        result.errorMessage(), System.currentTimeMillis() - start);
            }

            List<String> produced = new ArrayList<>();
            try {
                var step = plan.stages().get(0).steps().get(0);
                if (step.expectedOutputTypes() != null) {
                    for (String ot : step.expectedOutputTypes()) {
                        Product product = new Product(null, "system", plan.projectId(),
                                plan.targetProductId(), ProductType.valueOf(ot.toUpperCase()),
                                RepresentationKind.JSON_DOCUMENT, "pipeline", plan.planId(),
                                null, ProductStatus.READY, null, null, "pipeline:" + plan.planId(),
                                null, 1, null, Instant.now(), Instant.now());
                        var saved = productRuntime.register(product);
                        produced.add(saved.productId());
                    }
                }
            } catch (Exception e) {
                log.warn("Product register failed for plan {}: {}", plan.planId(), e.getMessage());
            }

            long dur = System.currentTimeMillis() - start;
            log.info("Pipeline: plan={} completed dur={}ms products={}", plan.planId(), dur, produced.size());
            return ExecutionPipelineResult.success(plan.planId(), result, produced, dur);

        } catch (Exception e) {
            long dur = System.currentTimeMillis() - start;
            return ExecutionPipelineResult.failure(plan.planId(), e.getMessage(), dur);
        }
    }

    private TaskCapability extractCapability(ExecutionPlan plan) {
        if (plan.stages().isEmpty() || plan.stages().get(0).steps().isEmpty()) return null;
        var step = plan.stages().get(0).steps().get(0);
        return step.backendType() != null ? TaskCapability.valueOf(step.backendType()) : null;
    }
}
