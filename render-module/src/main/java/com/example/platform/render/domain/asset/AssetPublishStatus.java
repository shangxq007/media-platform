package com.example.platform.render.domain.asset;

/**
 * Publish lifecycle for assets — from draft through review to published/archived.
 */
public enum AssetPublishStatus {
    DRAFT,
    IN_REVIEW,
    APPROVED,
    PUBLISHED,
    REJECTED,
    ARCHIVED
}
