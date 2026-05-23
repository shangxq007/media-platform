package com.example.platform.render.domain.timeline;

/**
 * Final timeline compositor selection (MLT vs FFmpeg).
 */
public enum FinalComposerHint {
    AUTO,
    MLT,
    FFMPEG;

    public static FinalComposerHint fromString(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        return switch (value.toLowerCase()) {
            case "mlt" -> MLT;
            case "ffmpeg" -> FFMPEG;
            default -> AUTO;
        };
    }
}
