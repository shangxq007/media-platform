package com.example.platform.render.infrastructure.font;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface FontStackResolver {

    String resolverName();

    record FontStack(
            String primaryFont,
            List<String> fallbackFonts,
            String systemFallback
    ) {}

    FontStack resolve(String fontFamily, Map<String, FontAsset> availableFonts);

    record FallbackChain(
            String primaryFontId,
            List<String> fallbackFontIds,
            boolean systemFallbackUsed
    ) {}

    FallbackChain resolveChain(String fontId, Set<Integer> requiredCodePoints, Map<String, com.example.platform.render.infrastructure.font.FontAsset> availableFonts);
}
