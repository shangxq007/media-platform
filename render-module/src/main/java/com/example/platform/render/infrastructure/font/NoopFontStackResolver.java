package com.example.platform.render.infrastructure.font;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class NoopFontStackResolver implements FontStackResolver {
    private static final Logger log = LoggerFactory.getLogger(NoopFontStackResolver.class);

    @Override
    public String resolverName() {
        return "NoopFontStackResolver";
    }

    @Override
    public FontStack resolve(String fontFamily, Map<String, FontAsset> availableFonts) {
        log.warn("NoopFontStackResolver used for font family: {}. This is NOT production-safe.", fontFamily);
        return new FontStack(fontFamily, List.of(), "sans-serif");
    }

    @Override
    public FallbackChain resolveChain(String fontId, Set<Integer> requiredCodePoints, Map<String, FontAsset> availableFonts) {
        log.warn("NoopFontStackResolver used for font: {}. This is NOT production-safe.", fontId);
        return new FallbackChain(fontId, List.of(), true);
    }
}
