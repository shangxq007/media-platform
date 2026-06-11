package com.example.platform.render.infrastructure.font;

public record MissingGlyph(
        int codePoint,
        String character,
        String script,
        boolean resolvedByFallback
) {}
