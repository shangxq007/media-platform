package com.example.platform.ai.infrastructure.video;

import com.example.platform.ai.api.video.AiVideoCapabilityUnavailableException;
import com.example.platform.ai.api.video.VideoUnderstandingPort;
import org.springframework.stereotype.Component;

@Component
public final class UnavailableVideoUnderstandingProvider implements VideoUnderstandingPort {
    @Override public VideoUnderstandingResult analyze(VideoUnderstandingRequest request) {
        throw new AiVideoCapabilityUnavailableException("video-understanding");
    }
}
