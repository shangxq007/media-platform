package com.example.platform.render.infrastructure;

/**
 * Base interface for all provider jobs.
 */
public interface ProviderJob {
    String jobId();
    String profile();
    String mode();
}
