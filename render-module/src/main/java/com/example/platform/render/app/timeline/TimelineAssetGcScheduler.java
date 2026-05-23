package com.example.platform.render.app.timeline;

import com.example.platform.render.infrastructure.TimelineAssetGcProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "platform.timeline.asset.gc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TimelineAssetGcScheduler {

    private static final Logger log = LoggerFactory.getLogger(TimelineAssetGcScheduler.class);

    private final TimelineAssetGcService gcService;
    private final TimelineAssetGcProperties properties;

    public TimelineAssetGcScheduler(TimelineAssetGcService gcService, TimelineAssetGcProperties properties) {
        this.gcService = gcService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${platform.timeline.asset.gc.schedule-interval:PT6H}")
    public void runScheduledGc() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            TimelineAssetGcService.GcRunResult result = gcService.runGlobalGc();
            log.info("Scheduled timeline asset GC: projects={} purged={} skipped={}",
                    result.projectsScanned(), result.assetsPurged(), result.skipped());
        } catch (Exception e) {
            log.warn("Scheduled timeline asset GC failed: {}", e.getMessage());
        }
    }
}
