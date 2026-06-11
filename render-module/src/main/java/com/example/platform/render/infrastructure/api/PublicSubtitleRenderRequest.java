package com.example.platform.render.infrastructure.api;

import java.util.List;

public record PublicSubtitleRenderRequest(
        PublicVideoInput video,
        List<PublicCaption> captions,
        PublicCaptionStyle style,
        PublicTemplateRef template,
        PublicOutputSpec output,
        String webhookUrl
) {
    public static final int MAX_CAPTIONS = 50;
    public static final int MAX_CAPTION_DURATION_SECONDS = 300;
    public static final int MAX_CAPTION_TEXT_LENGTH = 200;
    public static final int MAX_VIDEO_DURATION_SECONDS = 600;
    public static final String SUPPORTED_OUTPUT_FORMAT = "mp4";
    public static final int MAX_OUTPUT_WIDTH = 3840;
    public static final int MAX_OUTPUT_HEIGHT = 2160;
    public static final int MAX_OUTPUT_FPS = 60;
    public static final double MIN_FONT_SIZE = 8;
    public static final double MAX_FONT_SIZE = 200;
}
