package com.example.platform.render.domain.execution;

import com.example.platform.render.domain.planner.ExecutionPlan;

/**
 * SPI for backend compilers — translates ExecutionPlan → BackendExecutionSpec.
 * No implementation in this sprint.
 */
public interface BackendCompiler {
    String backendType();
    boolean supports(ExecutionPlan plan);
    BackendExecutionSpec compile(ExecutionPlan plan);
}
