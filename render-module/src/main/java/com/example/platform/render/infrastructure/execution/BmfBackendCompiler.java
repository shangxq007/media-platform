package com.example.platform.render.infrastructure.execution;

import com.example.platform.render.domain.execution.*;
import com.example.platform.render.domain.planner.ExecutionPlan;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * BMF backend compiler — translates ExecutionPlan → BackendExecutionSpec for BMF backends.
 * Handles THUMBNAIL, PROXY, PREVIEW, FINAL_RENDER product types.
 */
@Component
public class BmfBackendCompiler implements BackendCompiler {

    private static final Logger log = LoggerFactory.getLogger(BmfBackendCompiler.class);

    @Override
    public String backendType() { return "bmf"; }

    @Override
    public boolean supports(ExecutionPlan plan) {
        if (plan.stages().isEmpty() || plan.stages().get(0).steps().isEmpty()) return false;
        String bt = plan.stages().get(0).steps().get(0).backendType();
        return "MEDIA_PIPELINE".equals(bt) || "TRANSCODE".equals(bt)
                || "THUMBNAIL".equals(bt) || "FRAME_EXTRACTION".equals(bt)
                || "FILTER".equals(bt);
    }

    @Override
    public BackendExecutionSpec compile(ExecutionPlan plan) {
        log.info("BmfBackendCompiler: compiling plan={}", plan.planId());
        List<ExecutionInput> inputs = new ArrayList<>();
        List<ExecutionOutput> outputs = new ArrayList<>();
        String graphType = "MEDIA_PIPELINE";

        for (var stage : plan.stages()) {
            for (var step : stage.steps()) {
                if (step.inputProductIds() != null) {
                    for (String pid : step.inputProductIds()) {
                        inputs.add(ExecutionInput.of(pid, null));
                    }
                }
                if (step.expectedOutputTypes() != null) {
                    for (String ot : step.expectedOutputTypes()) {
                        graphType = mapToGraphType(ot);
                        outputs.add(ExecutionOutput.of(ot, "MEDIA_FILE"));
                    }
                }
            }
        }

        String producer = plan.stages().get(0).steps().get(0).producerId();
        BmfExecutionSpec spec = BmfExecutionSpec.of("bmf", producer, graphType, inputs, outputs);

        log.info("BmfBackendCompiler: compiled spec={} graphType={} inputs={} outputs={}",
                spec.executionSpecId(), graphType, inputs.size(), outputs.size());
        return spec;
    }

    private String mapToGraphType(String productType) {
        return switch (productType.toUpperCase()) {
            case "THUMBNAIL" -> "THUMBNAIL";
            case "PROXY", "TRANSCODE" -> "TRANSCODE";
            case "PREVIEW", "FINAL_RENDER" -> "MEDIA_PIPELINE";
            case "PACKAGE" -> "MEDIA_PIPELINE";
            default -> "MEDIA_PIPELINE";
        };
    }
}
