package com.example.platform.shared.events;

/**
 * Published when an execution job fails.
 */
public record ExecutionJobFailedEvent(
        String jobId,
        String reason) {}
