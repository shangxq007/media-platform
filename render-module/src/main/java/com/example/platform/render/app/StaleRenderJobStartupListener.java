package com.example.platform.render.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On cold start, fails in-flight local render jobs left by abrupt shutdown (skipped when {@code render.execution.mode=temporal}).
 */
@Component
@ConditionalOnProperty(prefix = "render.stale-compensator", name = "startup-enabled", havingValue = "true", matchIfMissing = true)
public class StaleRenderJobStartupListener {

    private static final Logger log = LoggerFactory.getLogger(StaleRenderJobStartupListener.class);

    private final StaleRenderJobCompensationService compensationService;

    @Value("${render.execution.mode:local}")
    private String executionMode;

    @Value("${render.stale-compensator.skip-on-temporal:true}")
    private boolean skipOnTemporal;

    @Value("${render.stale-compensator.startup-include-queued:false}")
    private boolean startupIncludeQueued;

    public StaleRenderJobStartupListener(StaleRenderJobCompensationService compensationService) {
        this.compensationService = compensationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void compensateOnStartup() {
        if (skipOnTemporal && "temporal".equalsIgnoreCase(executionMode)) {
            log.info("Skipping startup stale render compensation (execution.mode=temporal)");
            return;
        }
        var result = compensationService.compensate(
                StaleRenderJobCompensationService.CompensationRequest.startup(
                        startupIncludeQueued, executionMode));
        log.info(
                "Startup stale render compensation: scanned={} compensated={}",
                result.scanned(),
                result.compensated());
    }
}
