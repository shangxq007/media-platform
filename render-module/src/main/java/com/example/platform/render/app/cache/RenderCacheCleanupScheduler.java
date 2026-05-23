package com.example.platform.render.app.cache;

import com.example.platform.render.infrastructure.RenderCacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RenderCacheCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RenderCacheCleanupScheduler.class);

    private final RenderCacheCleanupService cleanupService;
    private final RenderCacheProperties cacheProperties;

    public RenderCacheCleanupScheduler(RenderCacheCleanupService cleanupService,
                                       RenderCacheProperties cacheProperties) {
        this.cleanupService = cleanupService;
        this.cacheProperties = cacheProperties;
    }

    @Scheduled(fixedDelayString = "${render.cache.cleanup-interval:PT24H}")
    public void runScheduledCleanup() {
        if (!cacheProperties.isCleanupEnabled()) {
            return;
        }
        try {
            RenderCacheCleanupService.CleanupResult result = cleanupService.runCleanup(null, null);
            log.debug("Scheduled render cache cleanup: {}", result);
        } catch (Exception e) {
            log.warn("Scheduled render cache cleanup failed: {}", e.getMessage());
        }
    }
}
