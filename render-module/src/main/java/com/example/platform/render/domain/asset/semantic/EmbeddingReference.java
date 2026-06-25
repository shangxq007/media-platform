package com.example.platform.render.domain.asset.semantic;

/**
 * Reference to a vector embedding — stores the embedding location,
 * not the embedding vector itself.
 */
public record EmbeddingReference(
        String embeddingId,
        String provider,
        int vectorDimension,
        String storageUri) {
}
