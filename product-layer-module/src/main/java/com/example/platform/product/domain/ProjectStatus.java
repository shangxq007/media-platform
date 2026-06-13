package com.example.platform.product.domain;

/**
 * Project lifecycle status.
 */
public enum ProjectStatus {
    /**
     * Initial draft state.
     */
    DRAFT,

    /**
     * Actively being edited.
     */
    IN_PROGRESS,

    /**
     * Render completed, ready for review.
     */
    REVIEW,

    /**
     * Project completed and delivered.
     */
    COMPLETED,

    /**
     * Archived (read-only).
     */
    ARCHIVED
}
