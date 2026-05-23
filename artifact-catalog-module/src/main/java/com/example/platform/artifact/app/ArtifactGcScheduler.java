package com.example.platform.artifact.app;

import com.example.platform.artifact.infrastructure.ArtifactGcProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "platform.artifact.gc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ArtifactGcScheduler {

    private static final Logger log = LoggerFactory.getLogger(ArtifactGcScheduler.class);

    private final ArtifactGcService gcService;
    private final ArtifactGcProperties properties;

    public ArtifactGcScheduler(ArtifactGcService gcService, ArtifactGcProperties properties) {
        this.gcService = gcService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${platform.artifact.gc.schedule-interval:PT6H}")
    public void runScheduledGc() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            ArtifactGcService.GcResult result = gcService.runGc();
            log.info("Scheduled artifact GC: scanned={} purged={} skipped={}",
                    result.scanned(), result.purged(), result.skipped());
        } catch (Exception e) {
            log.warn("Scheduled artifact GC failed: {}", e.getMessage());
        }
    }
}
