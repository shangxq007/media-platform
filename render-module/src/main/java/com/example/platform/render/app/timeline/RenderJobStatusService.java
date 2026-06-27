package com.example.platform.render.app.timeline;

import com.example.platform.render.api.dto.RenderJobResultResponse;
import com.example.platform.render.api.dto.RenderJobStatusResponse;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.domain.product.Product;
import com.example.platform.render.domain.product.ProductStatus;
import com.example.platform.render.infrastructure.product.ProductDependencyRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Application service for querying render job status and result.
 *
 * <p>Reconstructs render job information from Product metadata and
 * ProductDependency lineage. In synchronous controlled-local mode,
 * the render job is not persisted in a dedicated table — all data
 * is derived from the output Product's metadataJson provenance.</p>
 *
 * <p>Architecture boundaries:
 * <ul>
 *   <li>Does not expose internal provider/backend/environment selection</li>
 *   <li>Does not expose signed URLs or absolute filesystem paths</li>
 *   <li>Does not expose storageReferenceId or storage details</li>
 *   <li>Fail-closed: returns empty Optional if not found or mismatched</li>
 * </ul>
 */
@Service
public class RenderJobStatusService {

    private static final Logger log = LoggerFactory.getLogger(RenderJobStatusService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ProductRuntimeService productRuntime;
    private final ProductDependencyRepository dependencyRepository;

    public RenderJobStatusService(ProductRuntimeService productRuntime,
                                   ProductDependencyRepository dependencyRepository) {
        this.productRuntime = productRuntime;
        this.dependencyRepository = dependencyRepository;
    }

    /**
     * Find render job status by project, revision, and renderJobId.
     *
     * <p>Scans Products in the project, matches by renderJobId in metadataJson.
     * Returns empty if no match, project mismatch, or revision mismatch.</p>
     *
     * @param projectId      the project identifier
     * @param revisionId     the timeline revision identifier
     * @param renderJobId    the render job identifier
     * @return the status response, or empty if not found
     */
    public Optional<RenderJobStatusResponse> findStatus(String projectId, String revisionId, String renderJobId) {
        return findOutputProduct(projectId, revisionId, renderJobId)
                .map(product -> {
                    Map<String, Object> metadata = parseMetadata(product.metadataJson());
                    return mapToStatusResponse(product, metadata);
                });
    }

    /**
     * Find render job result by project, revision, and renderJobId.
     *
     * <p>Scans Products in the project, matches by renderJobId in metadataJson.
     * Returns empty if no match, project mismatch, or revision mismatch.</p>
     *
     * @param projectId      the project identifier
     * @param revisionId     the timeline revision identifier
     * @param renderJobId    the render job identifier
     * @return the result response, or empty if not found
     */
    public Optional<RenderJobResultResponse> findResult(String projectId, String revisionId, String renderJobId) {
        return findOutputProduct(projectId, revisionId, renderJobId)
                .map(product -> {
                    Map<String, Object> metadata = parseMetadata(product.metadataJson());
                    return mapToResultResponse(product, metadata);
                });
    }

    /**
     * Find the output Product matching the given projectId, revisionId, and renderJobId.
     */
    private Optional<Product> findOutputProduct(String projectId, String revisionId, String renderJobId) {
        if (projectId == null || revisionId == null || renderJobId == null) {
            return Optional.empty();
        }

        // Scan Products in the project (bounded in synchronous mode)
        List<Product> products = productRuntime.findByProject(projectId, 500);

        for (Product product : products) {
            if (product.metadataJson() == null) continue;

            Map<String, Object> metadata = parseMetadata(product.metadataJson());
            if (metadata == null) continue;

            String storedJobId = (String) metadata.get("renderJobId");
            String storedRevisionId = (String) metadata.get("timelineRevisionId");

            if (renderJobId.equals(storedJobId) && revisionId.equals(storedRevisionId)) {
                log.debug("Found output product for renderJobId={} productId={}",
                        renderJobId, product.productId());
                return Optional.of(product);
            }
        }

        log.debug("No output product found for projectId={} revisionId={} renderJobId={}",
                projectId, revisionId, renderJobId);
        return Optional.empty();
    }

    /**
     * Parse metadataJson string into a Map. Returns null on parse failure.
     */
    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(metadataJson, MAP_TYPE);
        } catch (Exception e) {
            log.warn("Failed to parse product metadataJson: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Map Product + metadata to RenderJobStatusResponse.
     */
    private RenderJobStatusResponse mapToStatusResponse(Product product, Map<String, Object> metadata) {
        String status = mapProductStatusToRenderStatus(product.status());
        boolean resultAvailable = product.status() == ProductStatus.READY;

        List<String> inputProductIds = extractStringList(metadata, "inputProductIds");
        int inputDependencyCount = inputProductIds != null ? inputProductIds.size() : 0;

        return new RenderJobStatusResponse(
                getString(metadata, "renderJobId"),
                product.projectId(),
                getString(metadata, "timelineRevisionId"),
                getString(metadata, "snapshotId"),
                status,
                getString(metadata, "renderMode"),
                getString(metadata, "outputProfile"),
                getString(metadata, "outputFormat"),
                product.productId(),
                product.status().name(),
                inputProductIds,
                inputDependencyCount,
                product.createdAt() != null ? product.createdAt().toString() : null,
                product.updatedAt() != null ? product.updatedAt().toString() : null,
                buildStatusMessage(product.status()),
                resultAvailable);
    }

    /**
     * Map Product + metadata to RenderJobResultResponse.
     */
    private RenderJobResultResponse mapToResultResponse(Product product, Map<String, Object> metadata) {
        List<String> inputProductIds = extractStringList(metadata, "inputProductIds");
        int inputDependencyCount = inputProductIds != null ? inputProductIds.size() : 0;

        return new RenderJobResultResponse(
                getString(metadata, "renderJobId"),
                product.projectId(),
                getString(metadata, "timelineRevisionId"),
                getString(metadata, "snapshotId"),
                product.productId(),
                product.status().name(),
                product.mimeType(),
                getString(metadata, "outputFormat"),
                getInt(metadata, "width"),
                getInt(metadata, "height"),
                getInt(metadata, "fps"),
                getDouble(metadata, "durationSeconds"),
                getBoolean(metadata, "hasSubtitles"),
                getString(metadata, "baselineRenderer"),
                getString(metadata, "renderMode"),
                inputProductIds,
                inputDependencyCount,
                product.createdAt() != null ? product.createdAt().toString() : null,
                product.updatedAt() != null ? product.updatedAt().toString() : null,
                buildResultMessage(product.status()));
    }

    /**
     * Map ProductStatus to API-facing render status.
     * In synchronous mode, READY/FAILED are the primary states.
     */
    private String mapProductStatusToRenderStatus(ProductStatus productStatus) {
        return switch (productStatus) {
            case READY -> "READY";
            case FAILED -> "FAILED";
            case REGISTERED, PROCESSING -> "RUNNING";
            default -> productStatus.name();
        };
    }

    private String buildStatusMessage(ProductStatus status) {
        return switch (status) {
            case READY -> "Render completed successfully";
            case FAILED -> "Render failed";
            case PROCESSING -> "Render in progress";
            case REGISTERED -> "Render accepted";
            default -> "Status: " + status.name();
        };
    }

    private String buildResultMessage(ProductStatus status) {
        return switch (status) {
            case READY -> "Render result available";
            case FAILED -> "Render failed — no result available";
            default -> "Render result not yet available";
        };
    }

    // --- Safe metadata extraction helpers ---

    private String getString(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value != null ? value.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item != null)
                    .map(Object::toString)
                    .toList();
        }
        return null;
    }

    private int getInt(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    private double getDouble(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0.0; }
        }
        return 0.0;
    }

    private boolean getBoolean(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }
}
