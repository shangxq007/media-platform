package com.example.platform.outbox.coordination;

import com.example.platform.outbox.coordination.TaskCapability;

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
