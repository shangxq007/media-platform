package com.example.platform.render.infrastructure.font;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class BasicFontManifestGenerator implements FontManifestGenerator {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BasicFontManifestGenerator.class);

    @Override
    public String generatorName() {
        return "BasicFontManifestGenerator";
    }

    @Override
    public FontManifest generate(List<FontAsset> assets, Map<String, FontStackResolver.FallbackChain> fallbackChains) {
        String version = "1.0.0";
        String projectId = "default";
        String generatedAt = java.time.Instant.now().toString();

        Map<String, FontSubsetResult> subsetResults = new java.util.HashMap<>();
        for (FontAsset asset : assets) {
            if (asset.subsetResult() != null) {
                subsetResults.put(asset.id(), asset.subsetResult());
            }
        }

        FontManifest manifest = new FontManifest(version, projectId, assets, fallbackChains, generatedAt);
        log.info("Generated FontManifest with {} assets, {} fallback chains", assets.size(), fallbackChains.size());
        return manifest;
    }
}
