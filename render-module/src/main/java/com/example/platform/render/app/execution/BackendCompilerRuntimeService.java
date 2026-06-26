package com.example.platform.render.app.execution;

import com.example.platform.render.domain.execution.BackendCompiler;
import com.example.platform.render.domain.execution.BackendExecutionSpec;
import com.example.platform.render.domain.planner.ExecutionPlan;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Backend Compiler Runtime — canonical entry point for all Backend Compilers.
 * Auto-discovers BackendCompiler implementations via Spring injection.
 * Translates ExecutionPlan → BackendExecutionSpec.
 * Never executes. Never accesses storage. Never calls Producer.
 */
@Service
public class BackendCompilerRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(BackendCompilerRuntimeService.class);
    private final Map<String, BackendCompiler> compilers = new LinkedHashMap<>();

    public BackendCompilerRuntimeService(List<BackendCompiler> allCompilers) {
        for (BackendCompiler c : allCompilers) {
            compilers.put(c.backendType(), c);
            log.info("Backend compiler registered: type={}", c.backendType());
        }
    }

    public BackendExecutionSpec compile(ExecutionPlan plan) {
        if (plan.stages().isEmpty() || plan.stages().get(0).steps().isEmpty()) {
            return null;
        }
        var step = plan.stages().get(0).steps().get(0);
        String backendType = step.backendType();
        if (backendType == null) {
            log.warn("No backend type in plan step — cannot compile");
            return null;
        }
        BackendCompiler compiler = compilers.get(backendType);
        if (compiler == null || !compiler.supports(plan)) {
            log.warn("No compiler found for backend type: {}", backendType);
            return null;
        }
        log.info("Compiling plan {} with backend {}", plan.planId(), backendType);
        return compiler.compile(plan);
    }

    public List<String> listCompilers() {
        return new ArrayList<>(compilers.keySet());
    }

    public boolean supports(String backendType) {
        return compilers.containsKey(backendType);
    }
}
