package com.example.platform.render.infrastructure.font;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DefaultFontManifestResolver implements FontManifestResolver {

    private final FontAssetRepository assetRepository;
    private final FontStackResolver stackResolver;

    public DefaultFontManifestResolver(FontAssetRepository assetRepository, FontStackResolver stackResolver) {
        this.assetRepository = assetRepository;
        this.stackResolver = stackResolver;
    }

    @Override
    public ResolvedFont resolvePrimary(String fontAssetId, String mode) {
        FontAsset asset = assetRepository.findById(fontAssetId)
                .orElseThrow(() -> new FontResolutionException("Font asset not found: " + fontAssetId));

        if (!asset.isReadyForRender()) {
            throw new FontResolutionException(
                    "Font asset not ready for render: " + fontAssetId + " (status=" + asset.status() + ")");
        }

        if (!asset.isProductionSafe() && !"experiment".equals(mode) && !"manual".equals(mode)) {
            throw new FontResolutionException(
                    "Font asset not production-safe: " + fontAssetId);
        }

        String subsetUrl = Optional.ofNullable(asset.subsetResult())
                .map(FontSubsetResult::subsetUri)
                .orElse(null);

        return new ResolvedFont(
                asset.id(),
                asset.fontFamily(),
                null,
                null,
                asset.storageUri(),
                subsetUrl,
                asset.sha256(),
                ResolvedFontRole.PRIMARY,
                asset.isProductionSafe()
        );
    }

    @Override
    public ResolvedFont resolvePrimary(String fontAssetId) {
        FontAsset asset = assetRepository.findById(fontAssetId)
                .orElseThrow(() -> new FontResolutionException("Font asset not found: " + fontAssetId));

        if (!asset.isReadyForRender()) {
            throw new FontResolutionException(
                    "Font asset not ready for render: " + fontAssetId + " (status=" + asset.status() + ")");
        }

        if (!asset.isProductionSafe()) {
            throw new FontResolutionException(
                    "Font asset not production-safe: " + fontAssetId);
        }

        String subsetUrl = Optional.ofNullable(asset.subsetResult())
                .map(FontSubsetResult::subsetUri)
                .orElse(null);

        return new ResolvedFont(
                asset.id(),
                asset.fontFamily(),
                null,
                null,
                asset.storageUri(),
                subsetUrl,
                asset.sha256(),
                ResolvedFontRole.PRIMARY,
                asset.isProductionSafe()
        );
    }

    @Override
    public List<ResolvedFont> resolveFallbackChain(String fontAssetId, Set<Integer> requiredCodePoints) {
        FontAsset primaryAsset = assetRepository.findById(fontAssetId)
                .orElseThrow(() -> new FontResolutionException("Font asset not found: " + fontAssetId));

        Map<String, FontAsset> availableFonts = new java.util.HashMap<>();
        assetRepository.findAll().forEach(a -> availableFonts.put(a.id(), a));

        FontStackResolver.FallbackChain chain = stackResolver.resolveChain(
                fontAssetId, requiredCodePoints, availableFonts);

        List<ResolvedFont> result = new java.util.ArrayList<>();
        result.add(resolvePrimary(fontAssetId));

        for (String fallbackId : chain.fallbackFontIds()) {
            FontAsset fallbackAsset = availableFonts.get(fallbackId);
            if (fallbackAsset != null && fallbackAsset.isReadyForRender()) {
                String subsetUrl = Optional.ofNullable(fallbackAsset.subsetResult())
                        .map(FontSubsetResult::subsetUri)
                        .orElse(null);
                result.add(new ResolvedFont(
                        fallbackAsset.id(),
                        fallbackAsset.fontFamily(),
                        null,
                        null,
                        fallbackAsset.storageUri(),
                        subsetUrl,
                        fallbackAsset.sha256(),
                        ResolvedFontRole.FALLBACK,
                        fallbackAsset.isProductionSafe()
                ));
            }
        }

        return result;
    }
}
