package com.example.platform.ai.infrastructure.video;

import com.example.platform.ai.api.video.AiVideoCapabilityUnavailableException;
import com.example.platform.ai.api.video.HighlightDetectionPort;
import org.springframework.stereotype.Component;

@Component
public final class UnavailableHighlightDetectionProvider implements HighlightDetectionPort {
    @Override public HighlightDetectionResult detectHighlights(HighlightDetectionRequest request) {
        throw new AiVideoCapabilityUnavailableException("highlight-detection");
    }
}
