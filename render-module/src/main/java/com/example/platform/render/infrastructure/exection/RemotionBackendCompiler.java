package com.example.platform.render.infrastructure.exection;

import com.example.platform.render.domain.execution.*;
import com.example.platform.render.domain.planner.ExecutionPlan;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Remotion backend compiler — translates ExecutionPlan → RemotionExecutionSpec.
 * Reuses existing BackendCompiler SPI. No kernel changes.
 */
@Component
public class RemotionBackendCompiler implements BackendCompiler {

    private static final Logger log = LoggerFactory.getLogger(RemotionBackendCompiler.class);

    @Override public String backendType() { return "MEDIA_PIPELINE"; }

    @Override
    public boolean supports(ExecutionPlan plan) {
        if (plan.stages().isEmpty() || plan.stages().get(0).steps().isEmpty()) return false;
        return "remotion-process".equals(plan.stages().get(0).steps().get(0).backendType());
    }

    @Override
    public BackendExecutionSpec compile(ExecutionPlan plan) {
        log.info("RemotionBackendCompiler: compiling plan={}", plan.planId());
        List<ExecutionInput> inputs = new ArrayList<>();
        for (var stage : plan.stages()) {
            for (var step : stage.steps()) {
                if (step.inputProductIds() != null) {
                    for (String pid : step.inputProductIds()) {
                        inputs.add(ExecutionInput.of(pid, null));
                    }
                }
            }
        }
        return RemotionExecutionSpec.of(plan.stages().get(0).steps().get(0).producerId(),
                "/compositions/main.tsx", inputs);
    }
}
