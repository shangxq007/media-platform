package com.example.platform.render.domain.asset.semantic;

import java.util.Map;

/**
 * Result from a semantic metadata provider analysis.
 */
public record SemanticMetadataResult(
        AssetSemanticMetadata semanticMetadata,
        String providerName,
        boolean success,
        String errorMessage,
        Map<String, Object> additionalData) {

    public static SemanticMetadataResult success(AssetSemanticMetadata metadata, String providerName) {
        return new SemanticMetadataResult(metadata, providerName, true, null, Map.of());
    }

    public static SemanticMetadataResult success(AssetSemanticMetadata metadata, String providerName,
                                                   Map<String, Object> additionalData) {
        return new SemanticMetadataResult(metadata, providerName, true, null, additionalData);
    }

    public static SemanticMetadataResult failure(String providerName, String errorMessage) {
        return new SemanticMetadataResult(null, providerName, false, errorMessage, Map.of());
    }
}
