package com.example.platform.render.app.input;

import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.domain.product.Product;
import com.example.platform.render.domain.product.ProductStatus;
import com.example.platform.render.domain.product.RepresentationKind;
import com.example.platform.render.domain.storage.StorageReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Materializes render inputs through StorageRuntime.
 *
 * <p>Accepts input Products, verifies they are READY file-backed Products
 * with StorageReferences, and delegates to StorageRuntimeService.materialize()
 * to obtain a local file path for FFmpeg/libass command construction.</p>
 *
 * <p>This service is the integration glue between input Products and the
 * render pipeline. It does NOT modify StorageRuntime or ProductRuntime
 * semantic contracts.</p>
 *
 * <p>Architecture boundaries:
 * <ul>
 *   <li>StorageRuntime owns physical materialization</li>
 *   <li>ProductRuntime owns Product lifecycle</li>
 *   <li>Render code never resolves StorageReference paths directly</li>
 *   <li>No signed URLs persisted</li>
 *   <li>No absolute paths exposed in public metadata</li>
 * </ul>
 */
@Service
public class RenderInputMaterializationService {

    private static final Logger log = LoggerFactory.getLogger(RenderInputMaterializationService.class);

    /**
     * Supported video MIME types for render input.
     */
    private static final Set<String> SUPPORTED_VIDEO_MIME_TYPES = Set.of(
            "video/mp4", "video/webm", "video/quicktime", "video/x-matroska",
            "video/avi", "video/x-msvideo", "video/mpeg", "video/ogg",
            "application/octet-stream" // fallback for unknown extensions
    );

    private final StorageRuntimeService storageRuntime;
    private final ProductRuntimeService productRuntime;

    public RenderInputMaterializationService(StorageRuntimeService storageRuntime,
                                              ProductRuntimeService productRuntime) {
        this.storageRuntime = storageRuntime;
        this.productRuntime = productRuntime;
    }

    /**
     * Materialize an input Product for rendering.
     *
     * @param inputProductId the input Product identifier
     * @param sourceAssetId  optional source asset identifier for provenance
     * @param timelineClipId optional timeline clip identifier for provenance
     * @return the materialization result (success or failure)
     */
    public RenderInputMaterialization materialize(String inputProductId,
                                                   String sourceAssetId,
                                                   String timelineClipId) {
        // 1. Verify Product exists
        Optional<Product> productOpt = productRuntime.find(inputProductId);
        if (productOpt.isEmpty()) {
            log.warn("Input Product not found: {}", inputProductId);
            return RenderInputMaterialization.failure(inputProductId, null,
                    "Input Product not found: " + inputProductId);
        }
        Product product = productOpt.get();

        // 2. Verify Product status is READY
        if (product.status() != ProductStatus.READY) {
            log.warn("Input Product not READY: {} status={}", inputProductId, product.status());
            return RenderInputMaterialization.failure(inputProductId, product.storageReferenceId(),
                    "Input Product not READY: " + product.status());
        }

        // 3. Verify representationKind is MEDIA_FILE
        if (product.representationKind() != RepresentationKind.MEDIA_FILE) {
            log.warn("Input Product not MEDIA_FILE: {} kind={}", inputProductId, product.representationKind());
            return RenderInputMaterialization.failure(inputProductId, product.storageReferenceId(),
                    "Input Product not MEDIA_FILE: " + product.representationKind());
        }

        // 4. Verify storageReferenceId is present
        if (product.storageReferenceId() == null || product.storageReferenceId().isBlank()) {
            log.warn("Input Product has no storageReferenceId: {}", inputProductId);
            return RenderInputMaterialization.failure(inputProductId, null,
                    "Input Product has no storageReferenceId");
        }

        // 5. Verify StorageReference exists
        Optional<StorageReference> refOpt = storageRuntime.find(product.storageReferenceId());
        if (refOpt.isEmpty()) {
            log.warn("StorageReference not found: {} for Product {}",
                    product.storageReferenceId(), inputProductId);
            return RenderInputMaterialization.failure(inputProductId, product.storageReferenceId(),
                    "StorageReference not found: " + product.storageReferenceId());
        }
        StorageReference ref = refOpt.get();

        // 6. Materialize through StorageRuntime
        String materializedPathStr;
        try {
            materializedPathStr = storageRuntime.materialize(product.storageReferenceId()).orElse(null);
        } catch (IllegalArgumentException e) {
            log.warn("StorageReference not found during materialization: {}", e.getMessage());
            return RenderInputMaterialization.failure(inputProductId, product.storageReferenceId(),
                    "StorageReference not found: " + e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("File not materialized: {}", e.getMessage());
            return RenderInputMaterialization.failure(inputProductId, product.storageReferenceId(),
                    "File not materialized: " + e.getMessage());
        }

        // 7. Validate materialized path
        Path materializedPath = Path.of(materializedPathStr);
        if (!Files.exists(materializedPath)) {
            log.warn("Materialized file missing: {}", materializedPath);
            return RenderInputMaterialization.failure(inputProductId, product.storageReferenceId(),
                    "Materialized file missing: " + materializedPath);
        }
        if (Files.isDirectory(materializedPath)) {
            log.warn("Materialized path is directory: {}", materializedPath);
            return RenderInputMaterialization.failure(inputProductId, product.storageReferenceId(),
                    "Materialized path is directory: " + materializedPath);
        }
        if (materializedPath.toFile().length() == 0) {
            log.warn("Materialized file is zero-byte: {}", materializedPath);
            return RenderInputMaterialization.failure(inputProductId, product.storageReferenceId(),
                    "Materialized file is zero-byte: " + materializedPath);
        }

        // 8. Validate MIME type for video input
        String mimeType = product.mimeType();
        if (mimeType != null && !SUPPORTED_VIDEO_MIME_TYPES.contains(mimeType)) {
            log.warn("Unsupported input MIME type: {} for Product {}", mimeType, inputProductId);
            return RenderInputMaterialization.failure(inputProductId, product.storageReferenceId(),
                    "Unsupported input MIME type: " + mimeType);
        }

        // 9. Validate path safety (no traversal)
        String absolutePath = materializedPath.toAbsolutePath().toString();
        if (absolutePath.contains("..")) {
            log.warn("Unsafe materialized path (traversal): {}", absolutePath);
            return RenderInputMaterialization.failure(inputProductId, product.storageReferenceId(),
                    "Unsafe materialized path: path traversal detected");
        }

        log.info("Input materialized: productId={} storageRefId={} path={}",
                inputProductId, product.storageReferenceId(), materializedPath);

        return RenderInputMaterialization.success(
                inputProductId,
                product.storageReferenceId(),
                mimeType,
                sourceAssetId,
                timelineClipId,
                materializedPath);
    }

    /**
     * Materialize multiple input Products.
     *
     * @param inputRefs list of (productId, sourceAssetId, timelineClipId) triples
     * @return list of materialization results
     */
    public List<RenderInputMaterialization> materializeAll(List<InputRef> inputRefs) {
        return inputRefs.stream()
                .map(ref -> materialize(ref.productId, ref.sourceAssetId, ref.timelineClipId))
                .toList();
    }

    /**
     * Internal reference to an input Product with provenance hints.
     */
    public record InputRef(String productId, String sourceAssetId, String timelineClipId) {}
}
