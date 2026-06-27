package com.example.platform.render.domain.storage;

import java.util.List;

/**
 * Minimal meter declaration for a Producer — declares measurable units.
 * Never prices. Never bills. Records facts only.
 */
public record MeterDescriptor(
        String capability,
        List<String> meters,
        List<String> units) {

    public static MeterDescriptor asr() {
        return new MeterDescriptor("ASR",
                List.of("AUDIO_MINUTES", "PROCESSING_DURATION", "REQUEST_COUNT"),
                List.of("minutes", "seconds", "requests"));
    }
}
