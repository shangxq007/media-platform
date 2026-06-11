package com.example.platform.render.infrastructure.api;

public record PublicCaptionStyle(
        String fontAssetId,
        Double fontSize,
        String fontColor,
        String backgroundColor,
        String outlineColor,
        Double outlineWidth,
        String alignment,
        String position,
        Boolean bold,
        Boolean italic,
        Double opacity
) {}
