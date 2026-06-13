package com.example.platform.product.domain;

/**
 * Template lifecycle status.
 */
public enum TemplateStatus {
    /**
     * Draft template (not yet published).
     */
    DRAFT,

    /**
     * Published and available for use.
     */
    PUBLISHED,

    /**
     * Archived (no longer available for new projects).
     */
    ARCHIVED,

    /**
     * Deprecated (replaced by newer version).
     */
    DEPRECATED
}
