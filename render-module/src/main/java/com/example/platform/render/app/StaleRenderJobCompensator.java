package com.example.platform.render.app;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StaleRenderJobCompensator {

    private static final Logger log = LoggerFactory.getLogger(StaleRenderJobCompensator.class);

    private final StaleRenderJobCompensationService compensationService;
    private final Duration staleThreshold;
    private final boolean enabled;

    public StaleRenderJobCompensator(
            StaleRenderJobCompensationService compensationService,
            @Value("${render.stale-compensator.enabled:true}") boolean enabled,
            @Value("${render.stale-compensator.threshold:PT30M}") Duration staleThreshold) {
        this.compensationService = compensationService;
        this.enabled = enabled;
        this.staleThreshold = staleThreshold;
    }

    @Scheduled(fixedDelayString = "${render.stale-compensator.interval:PT5M}")
    public void compensateStaleJobs() {
        if (!enabled) {
            log.debug("StaleRenderJobCompensator is disabled, skipping");
            return;
        }
        log.debug("Running scheduled stale render job compensation, threshold={}", staleThreshold);
        compensationService.compensate(StaleRenderJobCompensationService.CompensationRequest.scheduled(staleThreshold));
    }
}
