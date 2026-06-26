package com.example.platform.render.domain.execution;

/**
 * Resource hints for execution — future scheduling optimization.
 * No scheduling logic. Purely metadata.
 */
public record ExecutionResourceHints(
        int cpu,
        int memoryMb,
        int gpu,
        int diskMb,
        int priority,
        int timeoutSec) {

    public static ExecutionResourceHints defaults() {
        return new ExecutionResourceHints(1, 1024, 0, 1024, 50, 300);
    }
}
