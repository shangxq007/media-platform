package com.example.platform.render.domain.timeline.compile.remotion;

import java.util.List;
import java.util.Map;

/**
 * Internal Remotion input props — deterministic planning document for Remotion composition.
 *
 * <p>Internal only — not exposed in public APIs.</p>
 *
 * <p>v0: planning artifact only. generationReady=false.
 * No execution, no Node process, no npm/npx.</p>
 */
public record RemotionInputProps(
        String schemaVersion,
        RemotionCompositionSpec composition,
        RemotionTimelineSpec timeline,
        List<RemotionMediaAssetSpec> mediaAssets,
        List<RemotionCaptionSpec> captions,
        List<RemotionFontSpec> fonts,
        RemotionOutputSpec output,
        Map<String, String> metadata) {

    public static final String SCHEMA_VERSION = "remotion-input-props-v0";

    public boolean hasCaptions() {
        return captions != null && !captions.isEmpty();
    }

    public boolean hasFonts() {
        return fonts != null && !fonts.isEmpty();
    }
}
