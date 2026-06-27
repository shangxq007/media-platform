package com.example.platform.render.domain.product;

import java.time.Instant;

/**
 * Product — a build result produced by a Producer.
 * Aggregate root for the Product Runtime.
 */
public record Product(
        String productId,
        String tenantId,
        String projectId,
        String ownerAssetId,
        ProductType productType,
        RepresentationKind representationKind,
        String producerType,
        String producerId,
        String sourceTimelineRevisionId,
        ProductStatus status,
        String storageReferenceId,
        String checksum,
        String contentHash,
        String mimeType,
        int version,
        String metadataJson,
        Instant createdAt,
        Instant updatedAt) {

    public Product withStatus(ProductStatus newStatus) {
        return new Product(productId, tenantId, projectId, ownerAssetId, productType,
                representationKind, producerType, producerId, sourceTimelineRevisionId,
                newStatus, storageReferenceId, checksum, contentHash, mimeType, version,
                metadataJson, createdAt, Instant.now());
    }

    /**
     * A Product must have at least one provenance source.
     * Root products: ownerAssetId. Derived products: producerId. Timeline products: sourceTimelineRevisionId.
     */
    public boolean hasProvenance() {
        return ownerAssetId != null || producerId != null || sourceTimelineRevisionId != null;
    }
}
