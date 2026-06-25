package com.example.platform.render.domain.asset.semantic;

import java.time.Instant;
import java.util.List;

/**
 * Unified asset semantic metadata — the "Asset Understanding Truth."
 *
 * <p>Aggregates all AI enrichment results (transcripts, OCR text, scene detection,
 * object/face/brand recognition, embeddings) into a single provider-agnostic model.</p>
 *
 * <p>Phase 1 stores this as a JSON document. Phase 2 will normalize into
 * separate tables for each enrichment type.</p>
 *
 * @param assetId      the asset this metadata describes
 * @param assetVersion version of the asset when this metadata was created
 * @param status       enrichment status (PENDING, IN_PROGRESS, COMPLETE, FAILED)
 * @param language     detected language (e.g., "en", "zh")
 * @param transcripts  ASR transcripts and segments
 * @param detectedTexts OCR-detected text items
 * @param scenes       detected scenes with labels and time ranges
 * @param objects      detected objects with labels and confidences
 * @param people       detected people with optional identities
 * @param brands       detected brand logos
 * @param embeddings   references to vector embeddings (not the vectors themselves)
 * @param createdAt    creation timestamp
 * @param updatedAt    last update timestamp
 */
public record AssetSemanticMetadata(
        String assetId,
        String assetVersion,
        EnrichmentStatus status,
        String language,
        List<Transcript> transcripts,
        List<DetectedText> detectedTexts,
        List<Scene> scenes,
        List<DetectedObject> objects,
        List<DetectedPerson> people,
        List<DetectedBrand> brands,
        List<EmbeddingReference> embeddings,
        Instant createdAt,
        Instant updatedAt) {

    public enum EnrichmentStatus {
        PENDING, IN_PROGRESS, COMPLETE, FAILED
    }

    public static AssetSemanticMetadata empty(String assetId, String assetVersion) {
        Instant now = Instant.now();
        return new AssetSemanticMetadata(assetId, assetVersion, EnrichmentStatus.PENDING,
                null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), now, now);
    }
}
