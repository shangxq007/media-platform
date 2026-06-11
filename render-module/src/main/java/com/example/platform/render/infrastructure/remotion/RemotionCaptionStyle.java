package com.example.platform.render.infrastructure.remotion;

public record RemotionCaptionStyle(
        String fontFamily,
        double fontSize,
        String fontColor,
        String backgroundColor,
        String outlineColor,
        double outlineWidth,
        String alignment,
        String position,
        boolean bold,
        boolean italic,
        double opacity
) {
    public static RemotionCaptionStyle defaultStyle() {
        return new RemotionCaptionStyle(
                "sans-serif", 24.0, "#FFFFFF", "#000000",
                null, 0.0, "center", "bottom",
                false, false, 1.0
        );
    }
}
