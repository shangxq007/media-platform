package com.example.platform.shared.events;

/**
 * Published when asset enrichment (probe, ASR, OCR, vision, embedding) completes.
 */
public record AssetEnrichedEvent(
        String assetId,
        String assetVersion,
        String assetType,
        String projectId,
        String enrichmentStatus,
        String capabilities) {}
