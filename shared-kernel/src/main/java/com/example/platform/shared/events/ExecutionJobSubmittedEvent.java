package com.example.platform.shared.events;

/**
 * Published when an execution job is submitted to an environment.
 */
public record ExecutionJobSubmittedEvent(
        String jobId,
        String environmentId,
        String backendId) {}
