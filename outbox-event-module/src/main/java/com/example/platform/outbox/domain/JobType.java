package com.example.platform.outbox.domain;

/**
 * Job types for the platform coordination layer.
 */
public enum JobType {
    ASSET_ENRICHMENT,
    SEARCH_REINDEX,
    MARKETPLACE_PREPARE,
    REVIEW_CHECK,
    RENDER_PREFLIGHT
}
