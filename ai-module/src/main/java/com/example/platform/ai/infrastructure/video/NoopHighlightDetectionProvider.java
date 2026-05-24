package com.example.platform.ai.infrastructure.video;

import com.example.platform.ai.api.video.HighlightDetectionPort;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoopHighlightDetectionProvider implements HighlightDetectionPort {

    private static final Logger log = LoggerFactory.getLogger(NoopHighlightDetectionProvider.class);

    @Override
    public HighlightDetectionResult detectHighlights(HighlightDetectionRequest request) {
        log.warn("HighlightDetectionPort using Noop — returning empty result");
        return new HighlightDetectionResult(List.of(), "Highlight detection unavailable — configure a real provider");
    }
}
