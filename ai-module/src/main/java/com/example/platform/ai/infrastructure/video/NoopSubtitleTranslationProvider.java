package com.example.platform.ai.infrastructure.video;

import com.example.platform.ai.api.video.SubtitleTranslationPort;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NoopSubtitleTranslationProvider implements SubtitleTranslationPort {

    private static final Logger log = LoggerFactory.getLogger(NoopSubtitleTranslationProvider.class);

    @Override
    public TranslationResult translate(TranslationRequest request) {
        log.warn("SubtitleTranslationPort using Noop — returning original text for target={}", request.targetLanguage());
        List<TranslatedSegment> segments = request.segments().stream()
                .map(s -> new TranslatedSegment(s.index(), s.startTimeMs(), s.endTimeMs(),
                        s.text(), "[translated] " + s.text(), 0.0))
                .toList();
        return new TranslationResult(request.targetLanguage(), segments);
    }
}
