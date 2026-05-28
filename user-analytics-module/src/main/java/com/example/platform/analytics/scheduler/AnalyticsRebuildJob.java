package com.example.platform.analytics.scheduler;

import com.example.platform.analytics.app.UserProfileService;
import com.example.platform.analytics.app.UserSegmentService;
import com.example.platform.analytics.domain.UserProfile;
import com.example.platform.analytics.domain.UserSegment;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AnalyticsRebuildJob {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsRebuildJob.class);

    private final UserProfileService profileService;
    private final UserSegmentService segmentService;
    private final Counter profileRebuildCounter;
    private final Counter segmentRebuildCounter;
    private final Timer profileRebuildTimer;
    private final Timer segmentRebuildTimer;
    private final Map<String, Instant> lastRunTimes = new ConcurrentHashMap<>();

    /**
     * Comma-separated list of tenant IDs to rebuild. If empty, rebuilds all tenants from DB.
     * Configured via app.analytics.scheduler.tenants.
     */
    @Value("${app.analytics.scheduler.tenants:}")
    private String configuredTenants;

    public AnalyticsRebuildJob(UserProfileService profileService,
                               UserSegmentService segmentService,
                               MeterRegistry meterRegistry) {
        this.profileService = profileService;
        this.segmentService = segmentService;
        this.profileRebuildCounter = Counter.builder("analytics.scheduler.profiles.rebuilt")
                .description("Number of user profiles rebuilt by scheduler")
                .register(meterRegistry);
        this.segmentRebuildCounter = Counter.builder("analytics.scheduler.segments.rebuilt")
                .description("Number of user segments rebuilt by scheduler")
                .register(meterRegistry);
        this.profileRebuildTimer = Timer.builder("analytics.scheduler.profiles.rebuild.time")
                .description("Time to rebuild all profiles")
                .register(meterRegistry);
        this.segmentRebuildTimer = Timer.builder("analytics.scheduler.segments.rebuild.time")
                .description("Time to rebuild all segments")
                .register(meterRegistry);
    }

    @Scheduled(cron = "${app.analytics.scheduler.profiles-cron:0 0 2 * * ?}")
    void scheduledProfileRebuild() {
        String jobKey = "analytics-profile-rebuild";
        if (!shouldRun(jobKey)) {
            log.debug("Skipping profile rebuild — already run recently");
            return;
        }
        log.info("Starting scheduled user profile rebuild");
        Timer.Sample sample = Timer.start();
        try {
            List<String> tenants = resolveTargetTenants();
            int totalCount = 0;
            for (String tenantId : tenants) {
                try {
                    List<UserProfile> profiles = profileService.listProfilesByTenant(tenantId, 10000);
                    int count = 0;
                    for (UserProfile p : profiles) {
                        try {
                            profileService.aggregateProfile(p.tenantId(), p.userId());
                            count++;
                        } catch (Exception e) {
                            log.warn("Failed to rebuild profile for user {}: {}", p.userId(), e.getMessage());
                        }
                    }
                    totalCount += count;
                    log.debug("Rebuilt {} profiles for tenant {}", count, tenantId);
                } catch (Exception e) {
                    log.warn("Failed to rebuild profiles for tenant {}: {}", tenantId, e.getMessage());
                }
            }
            profileRebuildCounter.increment(totalCount);
            lastRunTimes.put(jobKey, Instant.now());
            log.info("Completed profile rebuild: {} profiles across {} tenants", totalCount, tenants.size());
        } finally {
            sample.stop(profileRebuildTimer);
        }
    }

    @Scheduled(cron = "${app.analytics.scheduler.segments-cron:0 0 3 * * ?}")
    void scheduledSegmentRebuild() {
        String jobKey = "analytics-segment-rebuild";
        if (!shouldRun(jobKey)) {
            log.debug("Skipping segment rebuild — already run recently");
            return;
        }
        log.info("Starting scheduled user segment rebuild");
        Timer.Sample sample = Timer.start();
        try {
            int count = computeDefaultSegments();
            segmentRebuildCounter.increment(count);
            lastRunTimes.put(jobKey, Instant.now());
            log.info("Completed segment rebuild: {} segments", count);
        } finally {
            sample.stop(segmentRebuildTimer);
        }
    }

    public int rebuildAllProfiles() {
        log.info("Starting manual profile rebuild");
        Timer.Sample sample = Timer.start();
        try {
            List<String> tenants = resolveTargetTenants();
            int totalCount = 0;
            for (String tenantId : tenants) {
                try {
                    List<UserProfile> profiles = profileService.listProfilesByTenant(tenantId, 10000);
                    int count = 0;
                    for (UserProfile p : profiles) {
                        try {
                            profileService.aggregateProfile(p.tenantId(), p.userId());
                            count++;
                            profileRebuildCounter.increment();
                        } catch (Exception e) {
                            log.warn("Failed to rebuild profile for user {}: {}", p.userId(), e.getMessage());
                        }
                    }
                    totalCount += count;
                } catch (Exception e) {
                    log.warn("Failed to rebuild profiles for tenant {}: {}", tenantId, e.getMessage());
                }
            }
            log.info("Manual profile rebuild complete: {} profiles across {} tenants", totalCount, tenants.size());
            return totalCount;
        } finally {
            sample.stop(profileRebuildTimer);
        }
    }

    public int rebuildAllSegments() {
        log.info("Starting manual segment rebuild");
        Timer.Sample sample = Timer.start();
        try {
            int count = computeDefaultSegments();
            log.info("Manual segment rebuild complete: {} segments", count);
            return count;
        } finally {
            sample.stop(segmentRebuildTimer);
        }
    }

    public int computeDefaultSegments() {
        List<String> tenants = resolveTargetTenants();
        int total = 0;
        for (String tenantId : tenants) {
            segmentService.computeNewUsersSegment(tenantId, 7);
            total++;
            segmentService.computeActiveUsersSegment(tenantId, 30);
            total++;
            segmentService.computePowerUsersSegment(tenantId, 100);
            total++;
            segmentService.computeAtRiskUsersSegment(tenantId, 30);
            total++;
            segmentService.computeDormantUsersSegment(tenantId, 60);
            total++;
            segmentService.computeFailedRenderUsersSegment(tenantId, 3);
            total++;
        }
        log.info("Computed default segments for {} tenants: {} total segments", tenants.size(), total);
        segmentRebuildCounter.increment(total);
        return total;
    }

    /**
     * Resolve target tenants from configuration or database.
     * If app.analytics.scheduler.tenants is configured, use that list.
     * Otherwise, query all distinct tenant IDs from the profile service.
     */
    private List<String> resolveTargetTenants() {
        if (configuredTenants != null && !configuredTenants.isBlank()) {
            return List.of(configuredTenants.split(","));
        }
        // Fallback: query all tenants from DB
        try {
            List<String> allTenants = profileService.listAllTenantIds();
            if (allTenants != null && !allTenants.isEmpty()) {
                return allTenants;
            }
        } catch (Exception e) {
            log.warn("Failed to query tenant list from DB: {}", e.getMessage());
        }
        // If no tenants found, return empty list — do not default to "tenant-1"
        log.warn("No target tenants resolved for analytics rebuild");
        return List.of();
    }

    private boolean shouldRun(String jobKey) {
        Instant lastRun = lastRunTimes.get(jobKey);
        if (lastRun == null) return true;
        return lastRun.isBefore(Instant.now().minusSeconds(3600));
    }

    public Map<String, Object> status() {
        return Map.of(
                "lastProfileRebuild", lastRunTimes.getOrDefault("analytics-profile-rebuild", Instant.EPOCH).toString(),
                "lastSegmentRebuild", lastRunTimes.getOrDefault("analytics-segment-rebuild", Instant.EPOCH).toString()
        );
    }
}
