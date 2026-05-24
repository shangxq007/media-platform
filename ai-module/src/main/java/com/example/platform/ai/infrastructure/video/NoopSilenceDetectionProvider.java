package com.example.platform.ai.infrastructure.video;

import com.example.platform.ai.api.video.SilenceDetectionPort;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoopSilenceDetectionProvider implements SilenceDetectionPort {

    private static final Logger log = LoggerFactory.getLogger(NoopSilenceDetectionProvider.class);

    @Override
    public SilenceDetectionResult detectSilence(SilenceDetectionRequest request) {
        log.warn("SilenceDetectionPort using Noop — returning empty result for {}", request.audioUri());
        return new SilenceDetectionResult(0.0, 0.0, 0.0, List.of());
    }
}
