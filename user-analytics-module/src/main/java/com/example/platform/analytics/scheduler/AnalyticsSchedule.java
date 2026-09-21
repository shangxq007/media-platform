package com.example.platform.analytics.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Timed entry points only; manual processing retains the same rebuild owner. */
@Component
@ConditionalOnProperty(prefix = "app.analytics.scheduler", name = "enabled", havingValue = "true")
public class AnalyticsSchedule {
    private final AnalyticsRebuildJob job;
    public AnalyticsSchedule(AnalyticsRebuildJob job) { this.job = job; }
    @Scheduled(cron = "${app.analytics.scheduler.profiles-cron:0 0 2 * * ?}")
    public void profiles() { job.scheduledProfileRebuild(); }
    @Scheduled(cron = "${app.analytics.scheduler.segments-cron:0 0 3 * * ?}")
    public void segments() { job.scheduledSegmentRebuild(); }
}
