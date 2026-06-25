package com.example.platform.render.domain.asset.marketplace;

import java.time.Instant;

/**
 * Marketplace listing — a publishable view of an asset.
 * Derived from Asset Registry + Semantic Metadata. Rebuildable.
 */
public record MarketplaceListing(
        String id,
        String assetId,
        String tenantId,
        String projectId,
        MarketplaceListingType listingType,
        String title,
        String summary,
        String description,
        String previewUrl,
        String coverUrl,
        String version,
        MarketplaceListingStatus status,
        String reviewId,
        Instant createdAt,
        Instant updatedAt) {

    public static MarketplaceListing draft(String id, String assetId, String tenantId,
                                             String projectId, MarketplaceListingType type,
                                             String title) {
        Instant now = Instant.now();
        return new MarketplaceListing(id, assetId, tenantId, projectId, type, title,
                null, null, null, null, "1.0",
                MarketplaceListingStatus.DRAFT, null, now, now);
    }

    public MarketplaceListing withSummary(String summary) {
        return new MarketplaceListing(id, assetId, tenantId, projectId, listingType, title,
                summary, description, previewUrl, coverUrl, version, status, reviewId,
                createdAt, Instant.now());
    }

    public MarketplaceListing withStatus(MarketplaceListingStatus newStatus) {
        return new MarketplaceListing(id, assetId, tenantId, projectId, listingType, title,
                summary, description, previewUrl, coverUrl, version, newStatus, reviewId,
                createdAt, Instant.now());
    }
}
