package com.example.platform.render.infrastructure.otio;

public record OTIOFontRef(
        String fontRefId,
        String assetId,
        String fontFamily,
        String fontWeight,
        String fontStyle,
        String subsetRef
) {}
