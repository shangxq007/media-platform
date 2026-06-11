package com.example.platform.render.infrastructure.remotion;

import java.util.List;

public record RemotionFontSpec(
        String fontFamily,
        Integer weight,
        String style,
        String sourceUrl,
        String subsetUrl,
        String hash,
        boolean productionSafe
) {
    public String effectiveUrl() {
        return subsetUrl != null ? subsetUrl : sourceUrl;
    }
}
