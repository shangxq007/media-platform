package com.example.platform.ai.infrastructure.video;

import com.example.platform.ai.api.video.AiVideoCapabilityUnavailableException;
import com.example.platform.ai.api.video.SubtitleTranslationPort;
import org.springframework.stereotype.Component;

@Component
public final class UnavailableSubtitleTranslationProvider implements SubtitleTranslationPort {
    @Override public TranslationResult translate(TranslationRequest request) {
        throw new AiVideoCapabilityUnavailableException("subtitle-translation");
    }
}
