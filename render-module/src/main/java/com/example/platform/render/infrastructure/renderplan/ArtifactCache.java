package com.example.platform.render.infrastructure.renderplan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Artifact Cache - hash-based artifact reuse.
 * 
 * <p>Features:
 * <ul>
 *   <li>Hash-based artifact reuse</li>
 *   <li>Skip execution if artifact exists</li>
 *   <li>Store output in artifact store</li>
 * </ul>
 */
@Component
public class ArtifactCache {

    private static final Logger log = LoggerFactory.getLogger(ArtifactCache.class);

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private long hits = 0;
    private long misses = 0;

    /**
     * Get cached artifact by input hash.
     */
    public String get(String inputHash) {
        String output = cache.get(inputHash);
        if (output != null) {
            hits++;
            log.debug("Cache hit for hash {}", inputHash);
        } else {
            misses++;
        }
        return output;
    }

    /**
     * Store artifact in cache.
     */
    public void put(String inputHash, String outputUri) {
        cache.put(inputHash, outputUri);
        log.debug("Cached artifact for hash {}: {}", inputHash, outputUri);
    }

    /**
     * Check if artifact is cached.
     */
    public boolean contains(String inputHash) {
        return cache.containsKey(inputHash);
    }

    /**
     * Remove artifact from cache.
     */
    public void remove(String inputHash) {
        cache.remove(inputHash);
    }

    /**
     * Clear the cache.
     */
    public void clear() {
        cache.clear();
        hits = 0;
        misses = 0;
    }

    /**
     * Get cache statistics.
     */
    public CacheStats getStats() {
        return new CacheStats(cache.size(), hits, misses, getHitRate());
    }

    private double getHitRate() {
        long total = hits + misses;
        return total > 0 ? (double) hits / total : 0;
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record CacheStats(
            int size,
            long hits,
            long misses,
            double hitRate
    ) {}
}
