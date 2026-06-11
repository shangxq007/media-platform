package com.example.platform.render.infrastructure.font;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

public class NoopMissingGlyphDetector implements MissingGlyphDetector {
    private static final Logger log = LoggerFactory.getLogger(NoopMissingGlyphDetector.class);

    @Override
    public String detectorName() {
        return "NoopMissingGlyphDetector";
    }

    @Override
    public List<MissingGlyph> detectMissingGlyphs(String fontId, Set<Integer> requiredCodePoints) {
        log.warn("NoopMissingGlyphDetector used for font: {}. This is NOT production-safe.", fontId);
        return List.of();
    }
}
