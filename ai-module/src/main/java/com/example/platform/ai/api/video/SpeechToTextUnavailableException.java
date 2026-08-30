package com.example.platform.ai.api.video;

import com.example.platform.shared.web.ErrorCode;
import com.example.platform.shared.web.PlatformException;

/** Typed fail-closed outcome when no real speech-to-text authority is configured. */
public final class SpeechToTextUnavailableException extends PlatformException {

    private static final ErrorCode SPEECH_TO_TEXT_UNAVAILABLE = new ErrorCode() {
        @Override public String code() { return "AI-503-001"; }
        @Override public String title() { return "Speech-to-text provider unavailable"; }
        @Override public int status() { return 503; }
    };

    public SpeechToTextUnavailableException() {
        super(SPEECH_TO_TEXT_UNAVAILABLE,
                "No real speech-to-text provider is configured");
    }
}
