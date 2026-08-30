package com.example.platform.ai.infrastructure.video;

import com.example.platform.ai.api.video.SpeechToTextPort;
import com.example.platform.ai.api.video.SpeechToTextUnavailableException;
import org.springframework.stereotype.Component;

/** Fail-closed production wiring used until a real speech-to-text provider is authorized. */
@Component
public final class UnavailableSpeechToTextProvider implements SpeechToTextPort {

    @Override
    public SpeechToTextResult transcribe(TranscribeRequest request) {
        throw new SpeechToTextUnavailableException();
    }
}
