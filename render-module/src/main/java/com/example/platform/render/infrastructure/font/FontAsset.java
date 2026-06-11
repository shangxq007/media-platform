package com.example.platform.render.infrastructure.font;

import java.util.List;

public record FontAsset(
        String id,
        String fileName,
        String fontFamily,
        String fontSubfamily,
        String format,
        long fileSize,
        String sha256,
        String storageUri,
        FontAssetStatus status,
        FontSecurityResult securityResult,
        FontValidationResult validationResult,
        FontSubsetResult subsetResult
) {
    public boolean isProductionSafe() {
        return securityResult != null && securityResult.productionSafe()
                && (status == FontAssetStatus.READY || status == FontAssetStatus.READY_WITH_SUBSETS);
    }

    public boolean isReadyForRender() {
        return status == FontAssetStatus.READY || status == FontAssetStatus.READY_WITH_SUBSETS;
    }
}
