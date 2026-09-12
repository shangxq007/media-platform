package com.example.platform.sandbox.execution;

import com.example.platform.sandbox.execution.TaskCapability;

/**
 * SPI for execution backends — abstracts away how a task is executed.
 * Current: LocalProcessExecutionBackend (ProcessBuilder).
 * Future: BmfExecutionBackend, OpenCueExecutionBackend, KubernetesExecutionBackend.
 */
public interface ExecutionBackend {

    String backendId();

    boolean supports(TaskCapability capability);

    ExecutionResult execute(ExecutionRequest request);
}
