package com.example.platform.ai.infrastructure.video;

import com.example.platform.ai.api.video.SpeechToTextPort;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoopSpeechToTextProvider implements SpeechToTextPort {

    private static final Logger log = LoggerFactory.getLogger(NoopSpeechToTextProvider.class);

    @Override
    public SpeechToTextResult transcribe(TranscribeRequest request) {
        log.warn("SpeechToTextPort using Noop implementation — returning stub result for {}", request.audioUri());
        return new SpeechToTextResult(
                request.language() != null ? request.language() : "en",
                0.0,
                List.of(new SubtitleSegment(
                        0, 0, 5000,
                        "[Auto captions unavailable — configure a real SpeechToText provider]",
                        "en", 0.0, List.of())));
    }
}
