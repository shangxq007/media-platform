package com.example.platform.ai.infrastructure.video;

import com.example.platform.ai.api.video.VideoUnderstandingPort;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoopVideoUnderstandingProvider implements VideoUnderstandingPort {

    private static final Logger log = LoggerFactory.getLogger(NoopVideoUnderstandingProvider.class);

    @Override
    public VideoUnderstandingResult analyze(VideoUnderstandingRequest request) {
        log.warn("VideoUnderstandingPort using Noop — returning stub result for {}", request.videoUri());
        return new VideoUnderstandingResult(
                "Video understanding unavailable — configure a real provider",
                List.of(), List.of(), List.of());
    }
}
