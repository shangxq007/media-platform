package com.example.platform.render.domain.timeline.compile.remotion;

/**
 * Remotion font reference — safe internal reference only.
 * No local paths, no download URLs.
 * Internal only.
 */
public record RemotionFontSpec(
        String family,
        int weight,
        String style,
        String safeFontRef) {}
