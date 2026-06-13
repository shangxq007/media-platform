package com.example.platform.render.infrastructure.productization.marketplace;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Marketplace for effects, templates, providers, and AI plugins.
 * Supports publishing, versioning, usage tracking, and discovery.
 */
public record Marketplace(
        String marketplaceId,
        List<MarketplaceItem> items,
        List<MarketplaceCategory> categories,
        MarketplaceStats stats,
        Instant lastUpdated
) {
    /**
     * Create an empty marketplace.
     */
    public static Marketplace create(String marketplaceId) {
        return new Marketplace(
                marketplaceId, List.of(), List.of(),
                new MarketplaceStats(0, 0, 0, 0),
                Instant.now()
        );
    }

    /**
     * Add an item to the marketplace.
     */
    public Marketplace addItem(MarketplaceItem item) {
        List<MarketplaceItem> newItems = new java.util.ArrayList<>(items);
        newItems.add(item);
        return new Marketplace(
                marketplaceId, List.copyOf(newItems), categories,
                stats.incrementItems(), Instant.now()
        );
    }

    /**
     * Search items by query.
     */
    public List<MarketplaceItem> search(String query) {
        String lowerQuery = query.toLowerCase();
        return items.stream()
                .filter(item -> item.name().toLowerCase().contains(lowerQuery)
                        || item.description().toLowerCase().contains(lowerQuery)
                        || item.tags().stream().anyMatch(t -> t.toLowerCase().contains(lowerQuery)))
                .toList();
    }

    /**
     * Get items by category.
     */
    public List<MarketplaceItem> getByCategory(String category) {
        return items.stream()
                .filter(item -> item.category().equals(category))
                .toList();
    }

    /**
     * Get items by type.
     */
    public List<MarketplaceItem> getByType(MarketplaceItemType type) {
        return items.stream()
                .filter(item -> item.type() == type)
                .toList();
    }

    /**
     * Get top rated items.
     */
    public List<MarketplaceItem> getTopRated(int limit) {
        return items.stream()
                .sorted((a, b) -> Double.compare(b.rating(), a.rating()))
                .limit(limit)
                .toList();
    }

    /**
     * Get most popular items.
     */
    public List<MarketplaceItem> getMostPopular(int limit) {
        return items.stream()
                .sorted((a, b) -> Long.compare(b.downloadCount(), a.downloadCount()))
                .limit(limit)
                .toList();
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record MarketplaceItem(
            String itemId,
            String name,
            String description,
            MarketplaceItemType type,
            String category,
            String authorId,
            String authorName,
            String version,
            List<String> tags,
            double rating,
            int reviewCount,
            long downloadCount,
            Map<String, Object> metadata,
            ItemStatus status,
            Instant publishedAt,
            Instant updatedAt
    ) {
        public MarketplaceItem incrementDownloads() {
            return new MarketplaceItem(
                    itemId, name, description, type, category,
                    authorId, authorName, version, tags,
                    rating, reviewCount, downloadCount + 1,
                    metadata, status, publishedAt, Instant.now()
            );
        }

        public MarketplaceItem updateRating(double newRating, int newReviewCount) {
            return new MarketplaceItem(
                    itemId, name, description, type, category,
                    authorId, authorName, version, tags,
                    newRating, newReviewCount, downloadCount,
                    metadata, status, publishedAt, Instant.now()
            );
        }

        public boolean isPublished() {
            return status == ItemStatus.PUBLISHED;
        }
    }

    public enum MarketplaceItemType {
        EFFECT,
        TEMPLATE,
        PROVIDER,
        AI_PLUGIN,
        TRANSITION,
        FILTER,
        FONT,
        AUDIO
    }

    public enum ItemStatus {
        DRAFT,
        REVIEW,
        PUBLISHED,
        REJECTED,
        ARCHIVED
    }

    public record MarketplaceCategory(
            String categoryId,
            String name,
            String description,
            int itemCount
    ) {}

    public record MarketplaceStats(
            int totalItems,
            int publishedItems,
            int totalAuthors,
            long totalDownloads
    ) {
        public MarketplaceStats incrementItems() {
            return new MarketplaceStats(totalItems + 1, publishedItems, totalAuthors, totalDownloads);
        }
    }
}
