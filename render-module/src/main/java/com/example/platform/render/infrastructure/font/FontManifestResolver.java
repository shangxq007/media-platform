package com.example.platform.render.infrastructure.font;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface FontManifestResolver {

    ResolvedFont resolvePrimary(String fontAssetId);

    ResolvedFont resolvePrimary(String fontAssetId, String mode);

    List<ResolvedFont> resolveFallbackChain(String fontAssetId, Set<Integer> requiredCodePoints);

    record ResolvedFont(
            String fontAssetId,
            String family,
            Integer weight,
            String style,
            String sourceUrl,
            String subsetUrl,
            String hash,
            ResolvedFontRole role,
            boolean productionSafe
    ) {}

    enum ResolvedFontRole {
        PRIMARY,
        FALLBACK,
        SYSTEM_FALLBACK
    }
}
