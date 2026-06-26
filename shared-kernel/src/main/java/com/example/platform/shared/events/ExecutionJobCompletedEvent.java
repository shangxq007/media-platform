package com.example.platform.shared.events;

/**
 * Published when an execution job completes successfully.
 */
public record ExecutionJobCompletedEvent(
        String jobId,
        String environmentId) {}
