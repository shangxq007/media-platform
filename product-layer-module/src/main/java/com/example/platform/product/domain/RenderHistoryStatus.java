package com.example.platform.product.domain;

/**
 * Render history entry status.
 */
public enum RenderHistoryStatus {
    /**
     * Render job started.
     */
    STARTED,

    /**
     * Render job in progress.
     */
    IN_PROGRESS,

    /**
     * Render completed successfully.
     */
    COMPLETED,

    /**
     * Render failed.
     */
    FAILED,

    /**
     * Render cancelled by user.
     */
    CANCELLED
}
