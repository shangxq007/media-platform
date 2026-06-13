package com.example.platform.render.infrastructure.productization.marketplace;

import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing marketplace items.
 */
@Service
public class MarketplaceService {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceService.class);

    private final Map<String, Marketplace> marketplaces = new ConcurrentHashMap<>();
    private final Map<String, Marketplace.MarketplaceItem> items = new ConcurrentHashMap<>();

    /**
     * Get or create marketplace for a workspace.
     */
    public Marketplace getMarketplace(String marketplaceId) {
        return marketplaces.computeIfAbsent(marketplaceId, Marketplace::create);
    }

    /**
     * Publish an item to the marketplace.
     */
    public Marketplace.MarketplaceItem publishItem(
            String marketplaceId,
            String name,
            String description,
            Marketplace.MarketplaceItemType type,
            String category,
            String authorId,
            String authorName,
            String version,
            List<String> tags) {
        
        String itemId = Ids.newId("mkt");
        Marketplace.MarketplaceItem item = new Marketplace.MarketplaceItem(
                itemId, name, description, type, category,
                authorId, authorName, version, tags,
                0.0, 0, 0, Map.of(),
                Marketplace.ItemStatus.PUBLISHED,
                Instant.now(), Instant.now()
        );

        items.put(itemId, item);

        Marketplace marketplace = getMarketplace(marketplaceId);
        marketplace = marketplace.addItem(item);
        marketplaces.put(marketplaceId, marketplace);

        log.info("Published marketplace item: {} ({}) by {}", name, itemId, authorName);
        return item;
    }

    /**
     * Get an item by ID.
     */
    public Marketplace.MarketplaceItem getItem(String itemId) {
        return items.get(itemId);
    }

    /**
     * Search items in a marketplace.
     */
    public List<Marketplace.MarketplaceItem> searchItems(String marketplaceId, String query) {
        Marketplace marketplace = getMarketplace(marketplaceId);
        return marketplace.search(query);
    }

    /**
     * Get items by category.
     */
    public List<Marketplace.MarketplaceItem> getItemsByCategory(String marketplaceId, String category) {
        Marketplace marketplace = getMarketplace(marketplaceId);
        return marketplace.getByCategory(category);
    }

    /**
     * Get items by type.
     */
    public List<Marketplace.MarketplaceItem> getItemsByType(String marketplaceId, Marketplace.MarketplaceItemType type) {
        Marketplace marketplace = getMarketplace(marketplaceId);
        return marketplace.getByType(type);
    }

    /**
     * Get top rated items.
     */
    public List<Marketplace.MarketplaceItem> getTopRated(String marketplaceId, int limit) {
        Marketplace marketplace = getMarketplace(marketplaceId);
        return marketplace.getTopRated(limit);
    }

    /**
     * Get most popular items.
     */
    public List<Marketplace.MarketplaceItem> getMostPopular(String marketplaceId, int limit) {
        Marketplace marketplace = getMarketplace(marketplaceId);
        return marketplace.getMostPopular(limit);
    }

    /**
     * Record a download.
     */
    public void recordDownload(String itemId) {
        Marketplace.MarketplaceItem item = items.get(itemId);
        if (item != null) {
            items.put(itemId, item.incrementDownloads());
        }
    }

    /**
     * Update item rating.
     */
    public void updateRating(String itemId, double rating) {
        Marketplace.MarketplaceItem item = items.get(itemId);
        if (item != null) {
            items.put(itemId, item.updateRating(rating, item.reviewCount() + 1));
        }
    }
}
