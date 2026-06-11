package com.example.platform.render.infrastructure.font;

import java.util.List;
import java.util.Map;

public interface FontManifestGenerator {

    String generatorName();

    FontManifest generate(List<FontAsset> assets, Map<String, FontStackResolver.FallbackChain> fallbackChains);

    record FontManifest(
            String version,
            String projectId,
            List<FontAsset> assets,
            Map<String, FontStackResolver.FallbackChain> fallbackChains,
            String generatedAt
    ) {}
}
