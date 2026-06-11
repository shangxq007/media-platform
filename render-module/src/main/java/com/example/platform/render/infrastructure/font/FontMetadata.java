package com.example.platform.render.infrastructure.font;

import java.util.Map;
import java.util.Set;

public record FontMetadata(
        String fontFamily,
        String fontSubfamily,
        String postScriptName,
        Integer weight,
        String style,
        String format,
        long fileSize,
        String sha256,
        boolean hasCmap,
        boolean hasGlyf,
        boolean hasHead,
        boolean hasHhea,
        boolean hasMaxp,
        boolean hasOs2,
        boolean hasPost,
        boolean hasName,
        Set<String> supportedLanguages,
        Map<String, Object> openTypeFeatures,
        Map<String, Object> variationAxes
) {}
