package com.example.platform.render.infrastructure.font;

import java.util.List;
import java.util.Map;

public record FontManifest(
        String version,
        String projectId,
        List<FontAsset> assets,
        Map<String, FontStackResolver.FallbackChain> fallbackChains,
        String generatedAt
) {
    public FontAsset findAsset(String fontId) {
        return assets.stream()
                .filter(a -> a.id().equals(fontId))
                .findFirst()
                .orElse(null);
    }

    public FontAsset findReadyAsset(String fontId) {
        return assets.stream()
                .filter(a -> a.id().equals(fontId) && a.isReadyForRender())
                .findFirst()
                .orElse(null);
    }

    public boolean allAssetsReady() {
        return assets.stream().allMatch(FontAsset::isReadyForRender);
    }

    public List<FontAsset> getAssetsByStatus(FontAssetStatus status) {
        return assets.stream()
                .filter(a -> a.status() == status)
                .toList();
    }
}
