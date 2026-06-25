package com.example.platform.render.domain.asset.semantic;

/**
 * Request to a semantic metadata provider for analyzing an asset.
 */
public record SemanticMetadataRequest(
        String assetId,
        String assetVersion,
        String assetType,
        String storageUri,
        String language) {

    public static SemanticMetadataRequest of(String assetId, String assetVersion,
                                               String assetType, String storageUri) {
        return new SemanticMetadataRequest(assetId, assetVersion, assetType, storageUri, null);
    }
}
