package com.example.platform.execution.taskgraph;

/**
 * Fail-closed execution cacheability policy result for one {@link ExecutableTask}.
 *
 * <p>Owned by the execution-plan module (the task/determinism model lives here); consumed by the
 * worker-fabric runtime loop when it derives the per-task reuse policy.
 */
public enum Cacheability {
    CACHEABLE,
    CACHEABLE_WHEN_FULLY_PINNED,
    NOT_CACHEABLE
}
