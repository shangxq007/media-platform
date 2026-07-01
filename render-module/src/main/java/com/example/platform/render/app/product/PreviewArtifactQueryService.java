package com.example.platform.render.app.product;

import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.domain.product.*;
import com.example.platform.render.domain.storage.StorageReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Read-only service for querying Product artifacts and building
 * safe preview response DTOs.
 *
 * <p>Integrates ProductRuntime (Product lifecycle) and StorageRuntime
 * (StorageReference metadata) to produce {@link PreviewArtifactResponse}
 * objects suitable for API responses. Does NOT expose:
 * <ul>
 *   <li>Internal filesystem paths</li>
 *   <li>Storage provider internals (bucket, key, signed URLs)</li>
 *   <li>Provider/backend/environment selection</li>
 * </ul>
 *
 * <p>Architecture boundaries:
 * <ul>
 *   <li>Uses ProductRuntimeService for Product queries</li>
 *   <li>Uses StorageRuntimeService for StorageReference metadata</li>
 *   <li>Does NOT call repositories directly</li>
 *   <li>Does NOT expose storage paths or signed URLs in responses</li>
 *   <li>Does NOT modify any domain state</li>
 * </ul>
 */
@Service
public class PreviewArtifactQueryService {

    private static final Logger log = LoggerFactory.getLogger(PreviewArtifactQueryService.class);

    private final ProductRuntimeService productRuntime;
    private final StorageRuntimeService storageRuntime;

    public PreviewArtifactQueryService(ProductRuntimeService productRuntime,
                                        StorageRuntimeService storageRuntime) {
        this.productRuntime = productRuntime;
        this.storageRuntime = storageRuntime;
    }

    /**
     * Look up a Product by ID and build a preview artifact response.
     *
     * @param productId the Product identifier
     * @return the preview artifact response, or empty if not found
     */
    public Optional<PreviewArtifactResponse> findByProductId(String productId) {
        return productRuntime.find(productId)
                .map(product -> buildResponse(product, true));
    }

    /**
     * Look up a Product by ID without dependency details.
     *
     * @param productId the Product identifier
     * @return the preview artifact response, or empty if not found
     */
    public Optional<PreviewArtifactResponse> findByProductIdShallow(String productId) {
        return productRuntime.find(productId)
                .map(product -> buildResponse(product, false));
    }

    /**
     * Find the latest preview product for an asset.
     *
     * @param assetId the owner asset identifier
     * @return the latest preview product response, or empty if none
     */
    public Optional<PreviewArtifactResponse> findLatestPreviewByAsset(String assetId) {
        return productRuntime.findLatest(assetId, ProductType.PREVIEW)
                .map(product -> buildResponse(product, true));
    }

    /**
     * Find the latest product of a specific type for an asset.
     *
     * @param assetId the owner asset identifier
     * @param productType the product type to filter by
     * @return the latest product response, or empty if none
     */
    public Optional<PreviewArtifactResponse> findLatestByAssetAndType(
            String assetId, ProductType productType) {
        return productRuntime.findLatest(assetId, productType)
                .map(product -> buildResponse(product, true));
    }

    /**
     * Find all products for a project and build preview responses.
     *
     * @param projectId the project identifier
     * @param limit maximum number of results
     * @return list of preview artifact responses (may be empty)
     */
    public List<PreviewArtifactResponse> findByProject(String projectId, int limit) {
        return productRuntime.findByProject(projectId, limit).stream()
                .map(product -> buildResponse(product, false))
                .toList();
    }

    /**
     * Find all products for an asset and build preview responses.
     *
     * @param assetId the owner asset identifier
     * @return list of preview artifact responses (may be empty)
     */
    public List<PreviewArtifactResponse> findByAsset(String assetId) {
        return productRuntime.findByAsset(assetId).stream()
                .map(product -> buildResponse(product, false))
                .toList();
    }

    /**
     * Find all products by source timeline revision ID and build preview responses.
     *
     * @param timelineRevisionId the timeline revision identifier
     * @return list of preview artifact responses (may be empty)
     */
    public List<PreviewArtifactResponse> findByTimelineRevision(String timelineRevisionId) {
        return productRuntime.findBySourceTimelineRevisionId(timelineRevisionId).stream()
                .map(product -> buildResponse(product, true))
                .toList();
    }

    /**
     * Build a PreviewArtifactResponse from a Product domain object.
     *
     * <p>Resolves StorageReference metadata if available. Does NOT
     * expose internal paths or storage provider internals.</p>
     *
     * @param product the Product domain object
     * @param includeDependencies whether to include dependency details
     * @return the safe preview artifact response
     */
    private PreviewArtifactResponse buildResponse(Product product, boolean includeDependencies) {
        long fileSize = 0;
        String checksum = null;
        String contentHash = null;

        // Resolve storage metadata if Product has a storage reference
        if (product.storageReferenceId() != null) {
            Optional<StorageReference> refOpt = storageRuntime.find(product.storageReferenceId());
            if (refOpt.isPresent()) {
                StorageReference ref = refOpt.get();
                fileSize = ref.fileSize();
                checksum = ref.checksum();
                contentHash = ref.contentHash();
            } else {
                log.warn("StorageReference not found for product={}: storageRefId={}",
                        product.productId(), product.storageReferenceId());
            }
        }

        // Resolve dependency counts (and optionally IDs)
        int upstreamCount = 0;
        int downstreamCount = 0;
        List<String> upstreamIds = Collections.emptyList();
        List<String> downstreamIds = Collections.emptyList();

        if (includeDependencies) {
            upstreamIds = productRuntime.findUpstream(product.productId());
            downstreamIds = productRuntime.findDownstream(product.productId());
            upstreamCount = upstreamIds.size();
            downstreamCount = downstreamIds.size();
        } else {
            // Still compute counts without fetching full IDs
            upstreamCount = productRuntime.findUpstream(product.productId()).size();
            downstreamCount = productRuntime.findDownstream(product.productId()).size();
        }

        log.debug("Preview artifact response built: productId={} type={} status={} fileSize={}",
                product.productId(), product.productType(), product.status(), fileSize);

        return new PreviewArtifactResponse(
                product.productId(),
                product.projectId(),
                product.productType() != null ? product.productType().name() : null,
                product.status() != null ? product.status().name() : null,
                product.mimeType(),
                product.representationKind() != null ? product.representationKind().name() : null,
                fileSize,
                checksum,
                contentHash,
                product.producerType(),
                product.producerId(),
                product.sourceTimelineRevisionId(),
                product.ownerAssetId(),
                upstreamCount,
                downstreamCount,
                product.version(),
                product.createdAt(),
                product.updatedAt(),
                upstreamIds,
                downstreamIds);
    }
}
