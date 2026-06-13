package com.example.platform.product.domain;

/**
 * Workspace lifecycle status.
 */
public enum WorkspaceStatus {
    /**
     * Active workspace with full functionality.
     */
    ACTIVE,

    /**
     * Archived workspace (read-only).
     */
    ARCHIVED,

    /**
     * Suspended workspace (administrative action).
     */
    SUSPENDED,

    /**
     * Pending deletion (grace period).
     */
    PENDING_DELETION
}
