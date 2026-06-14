package com.example.platform.policy.featureflag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Loads all feature flag definitions from JDBC into {@link FeatureFlagService} cache on startup.
 */
@Component

public class FeatureFlagStartupHydrator {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagStartupHydrator.class);

    private final LocalFeatureFlagProvider localProvider;
    private final FeatureFlagService featureFlagService;

    public FeatureFlagStartupHydrator(LocalFeatureFlagProvider localProvider, FeatureFlagService featureFlagService) {
        this.localProvider = localProvider;
        this.featureFlagService = featureFlagService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmCacheFromDatabase() {
        featureFlagService.reloadAllFlagsFromStore();
        log.info("Feature flags warmed from database: {} definitions", featureFlagService.listFlags().size());
    }
}
