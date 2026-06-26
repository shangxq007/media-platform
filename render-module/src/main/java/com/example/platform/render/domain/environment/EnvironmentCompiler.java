package com.example.platform.render.domain.environment;

import com.example.platform.render.domain.execution.BackendExecutionSpec;
import com.example.platform.render.domain.execution.ExecutionJob;

/**
 * Environment compiler — translates BackendExecutionSpec → ExecutionJob.
 * Owns job construction for a specific environment type.
 */
public interface EnvironmentCompiler {
    String environmentType();
    boolean supports(String environmentType);
    ExecutionJob compile(BackendExecutionSpec backendSpec);
}
