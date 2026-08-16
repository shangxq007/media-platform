package com.example.platform.timeline.app;

import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.diff.TimelineChangeSet;
import com.example.platform.timeline.diff.TimelineDiffEngine;
import com.example.platform.timeline.diff.TimelineDiffErrors.TimelineDiffException;
import com.example.platform.timeline.version.TimelineRevision;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only application service for computing semantic diff between two TimelineRevisions.
 * 
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Load Base and Target Revisions</li>
 *   <li>Validate Product isolation</li>
 *   <li>Validate schema compatibility</li>
 *   <li>Validate payload/digest integrity</li>
 *   <li>Deserialize Canonical TimelineDocument</li>
 *   <li>Delegate to pure domain Diff Engine</li>
 *   <li>Return immutable TimelineChangeSet</li>
 * </ul>
 * 
 * <p><strong>Read-only:</strong> This service does NOT modify any database state.</p>
 */
@Service
public class TimelineSemanticDiffV1Service {

    private final TimelineRevisionSaveService revisionSaveService;
    private final TimelineContentDigester contentDigester;
    private final ObjectMapper objectMapper;

    public TimelineSemanticDiffV1Service(
            TimelineRevisionSaveService revisionSaveService,
            TimelineContentDigester contentDigester,
            ObjectMapper objectMapper) {
        this.revisionSaveService = revisionSaveService;
        this.contentDigester = contentDigester;
        this.objectMapper = objectMapper;
    }

    /**
     * Compute semantic diff between two revisions of the same product.
     * 
     * @param productId the product ID (both revisions must belong to this product)
     * @param baseRevisionId the base revision ID
     * @param targetRevisionId the target revision ID
     * @return immutable TimelineChangeSet
     * @throws TimelineDiffException if revisions not found, cross-product, schema incompatible, etc.
     */
    @Transactional(readOnly = true)
    public TimelineChangeSet diff(String productId, String baseRevisionId, String targetRevisionId) {
        // Load revisions
        TimelineRevision baseRevision = revisionSaveService.findById(baseRevisionId);
        if (baseRevision == null) {
            throw new com.example.platform.timeline.diff.TimelineDiffErrors.RevisionNotFoundException(baseRevisionId);
        }

        TimelineRevision targetRevision = revisionSaveService.findById(targetRevisionId);
        if (targetRevision == null) {
            throw new com.example.platform.timeline.diff.TimelineDiffErrors.RevisionNotFoundException(targetRevisionId);
        }

        // Validate product isolation
        if (!productId.equals(baseRevision.productId())) {
            throw new com.example.platform.timeline.diff.TimelineDiffErrors.CrossProductException(
                    productId, baseRevision.productId());
        }
        if (!productId.equals(targetRevision.productId())) {
            throw new com.example.platform.timeline.diff.TimelineDiffErrors.CrossProductException(
                    productId, targetRevision.productId());
        }

        // Validate schema compatibility
        String baseSchema = baseRevision.timelineSchemaVersion();
        String targetSchema = targetRevision.timelineSchemaVersion();
        if (!baseSchema.equals(targetSchema)) {
            throw new com.example.platform.timeline.diff.TimelineDiffErrors.SchemaIncompatibleException(
                    baseSchema, targetSchema);
        }

        // Validate digest integrity (only if canonicalTimeline is loaded)
        if (baseRevision.canonicalTimeline() != null) {
            String baseDigest = contentDigester.digest(baseRevision.canonicalTimeline());
            if (!baseDigest.equals(baseRevision.contentDigest())) {
                throw new com.example.platform.timeline.diff.TimelineDiffErrors.DigestMismatchException(
                        "Base revision digest mismatch: expected " + baseRevision.contentDigest() + ", computed " + baseDigest);
            }
        }
        if (targetRevision.canonicalTimeline() != null) {
            String targetDigest = contentDigester.digest(targetRevision.canonicalTimeline());
            if (!targetDigest.equals(targetRevision.contentDigest())) {
                throw new com.example.platform.timeline.diff.TimelineDiffErrors.DigestMismatchException(
                        "Target revision digest mismatch: expected " + targetRevision.contentDigest() + ", computed " + targetDigest);
            }
        }

        // Compute diff using pure domain engine
        return TimelineDiffEngine.diff(
                productId,
                baseRevisionId,
                targetRevisionId,
                baseRevision.contentDigest(),
                targetRevision.contentDigest(),
                baseRevision.canonicalTimeline(),
                targetRevision.canonicalTimeline()
        );
    }
}
