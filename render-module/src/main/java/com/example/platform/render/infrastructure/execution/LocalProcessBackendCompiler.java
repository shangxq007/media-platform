package com.example.platform.render.infrastructure.execution;

import com.example.platform.render.domain.execution.*;
import com.example.platform.render.domain.planner.ExecutionPlan;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Backend compiler for local-process execution.
 * Translates ExecutionPlan → BackendExecutionSpec for CLI-based backends.
 */
@Component
public class LocalProcessBackendCompiler implements BackendCompiler {

    private static final Logger log = LoggerFactory.getLogger(LocalProcessBackendCompiler.class);

    @Override
    public String backendType() {
        return "local-process";
    }

    @Override
    public boolean supports(ExecutionPlan plan) {
        if (plan.stages().isEmpty() || plan.stages().get(0).steps().isEmpty()) return false;
        String bt = plan.stages().get(0).steps().get(0).backendType();
        return "ASR".equals(bt) || "OCR".equals(bt) || "VISION".equals(bt) || "EMBEDDING".equals(bt);
    }

    @Override
    public BackendExecutionSpec compile(ExecutionPlan plan) {
        log.info("LocalProcessBackendCompiler: compiling plan={}", plan.planId());
        List<ExecutionInput> inputs = new ArrayList<>();
        List<ExecutionOutput> outputs = new ArrayList<>();
        List<String> args = new ArrayList<>();
        String producer = plan.stages().get(0).steps().get(0).producerId();

        for (var stage : plan.stages()) {
            for (var step : stage.steps()) {
                if (step.inputProductIds() != null) {
                    for (String pid : step.inputProductIds()) {
                        inputs.add(ExecutionInput.of(pid, null));
                        args.add(pid);
                    }
                }
                if (step.expectedOutputTypes() != null) {
                    for (String ot : step.expectedOutputTypes()) {
                        outputs.add(ExecutionOutput.of(ot, "JSON_DOCUMENT"));
                    }
                }
            }
        }

        return LocalProcessExecutionSpec.of("local-process", producer, inputs, outputs,
                producer, args);
    }
}
