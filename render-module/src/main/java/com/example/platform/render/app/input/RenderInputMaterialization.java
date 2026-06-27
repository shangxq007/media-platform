package com.example.platform.render.app.input;

import java.nio.file.Path;
import java.util.List;

/**
 * Internal value object representing a materialized render input.
 *
 * <p>Carries the result of resolving an input Product through
 * StorageRuntime materialization. Used internally by the render
 * pipeline — not exposed as public API.</p>
 *
 * <p>Architecture boundaries:
 * <ul>
 *   <li>Does not expose StorageProvider details</li>
 *   <li>Does not persist signed URLs</li>
 *   <li>Does not expose absolute paths in public metadata</li>
 *   <li>StorageRuntime owns physical materialization</li>
 * </ul>
 */
public record RenderInputMaterialization(
        String inputProductId,
        String storageReferenceId,
        String expectedMimeType,
        String sourceAssetId,
        String timelineClipId,
        Path materializedPath,
        boolean valid,
        String failureReason
) {
    /**
     * Create a successful materialization result.
     */
    public static RenderInputMaterialization success(
            String inputProductId,
            String storageReferenceId,
            String expectedMimeType,
            String sourceAssetId,
            String timelineClipId,
            Path materializedPath) {
        return new RenderInputMaterialization(
                inputProductId, storageReferenceId, expectedMimeType,
                sourceAssetId, timelineClipId, materializedPath, true, null);
    }

    /**
     * Create a failed materialization result.
     */
    public static RenderInputMaterialization failure(
            String inputProductId,
            String storageReferenceId,
            String failureReason) {
        return new RenderInputMaterialization(
                inputProductId, storageReferenceId, null, null, null, null, false, failureReason);
    }

    /**
     * Returns the input product IDs as a single-element list for provenance tracking.
     */
    public List<String> inputProductIdList() {
        return inputProductId != null ? List.of(inputProductId) : List.of();
    }

    /**
     * Returns the source asset IDs as a single-element list for provenance tracking.
     */
    public List<String> sourceAssetIdList() {
        return sourceAssetId != null ? List.of(sourceAssetId) : List.of();
    }
}
