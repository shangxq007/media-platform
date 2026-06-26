package com.example.platform.render.domain.environment;

import com.example.platform.render.domain.execution.ExecutionJob;
import java.util.List;

/**
 * SPI for execution environments — represents WHERE execution happens.
 * Accepts ExecutionJob (common contract) instead of raw EnvironmentExecutionSpec.
 */
public interface ExecutionEnvironment {
    String environmentId();
    String environmentType();
    boolean supports(List<String> capabilities);
    String submit(ExecutionJob job);
    boolean cancel(String executionId);
    String status(String executionId);
}
