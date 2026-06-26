package com.example.platform.render.domain.asset.semantic;

/**
 * Reference to a vector embedding — stores the embedding location,
 * not the embedding vector itself.
 */
public record EmbeddingReference(
        String embeddingId,
        String provider,
        String model,
        int vectorDimension,
        String storageUri,
        String contentHash,
        long processingTimeMs,
        String createdAt) {

    public static EmbeddingReference of(String provider, String model, int dim, String uri) {
        return new EmbeddingReference("emb_" + System.currentTimeMillis(), provider, model,
                dim, uri, null, 0, java.time.Instant.now().toString());
    }
}
