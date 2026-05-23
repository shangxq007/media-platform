package com.example.platform.render.app.aaf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "render.aaf", name = "worker-enabled", havingValue = "true", matchIfMissing = true)
public class AafConversionWorkerProcessor {

    private static final Logger log = LoggerFactory.getLogger(AafConversionWorkerProcessor.class);

    private final AafConversionService conversionService;

    public AafConversionWorkerProcessor(AafConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Scheduled(fixedDelayString = "${render.aaf.poll-interval-ms:3000}")
    public void pollQueue() {
        conversionService.poll().ifPresent(job -> {
            log.info("Processing AAF conversion {}", job.conversionId());
            AafConversionResult result = conversionService.process(job);
            if (!result.success()) {
                log.warn("AAF conversion {} failed: {}", job.conversionId(), result.errorMessage());
            }
        });
    }
}
