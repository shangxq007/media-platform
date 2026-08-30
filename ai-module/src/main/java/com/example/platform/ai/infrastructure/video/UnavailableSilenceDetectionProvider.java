package com.example.platform.ai.infrastructure.video;

import com.example.platform.ai.api.video.AiVideoCapabilityUnavailableException;
import com.example.platform.ai.api.video.SilenceDetectionPort;
import org.springframework.stereotype.Component;

@Component
public final class UnavailableSilenceDetectionProvider implements SilenceDetectionPort {
    @Override public SilenceDetectionResult detectSilence(SilenceDetectionRequest request) {
        throw new AiVideoCapabilityUnavailableException("silence-detection");
    }
}
