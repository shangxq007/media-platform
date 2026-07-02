package com.example.platform.render.app.preview;

import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.domain.previewjob.PreviewRenderJob;
import com.example.platform.render.domain.previewjob.PreviewRenderJobId;
import com.example.platform.render.domain.previewjob.PreviewRenderJobRepository;
import com.example.platform.render.domain.previewjob.PreviewRenderJobStatus;
import com.example.platform.render.domain.product.Product;
import com.example.platform.render.domain.product.ProductStatus;
import com.example.platform.render.infrastructure.product.ProductDependencyRepository;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Application service for the Preview Render Job API boundary.
 *
 * <p>Provides the contract for creating, querying status, and retrieving
 * product/artifact metadata for preview render jobs. This service is the
 * single entry point for preview render job operations.</p>
 *
 * <h3>Architecture boundaries</h3>
 * <ul>
 *   <li>Does not expose internal provider/backend/environment selection</li>
 *   <li>Does not expose local filesystem paths or signed URLs</li>
 *   <li>Does not expose storageReferenceId or storage details</li>
 *   <li>ProductRuntime/StorageRuntime boundaries preserved</li>
 *   <li>Fail-closed: returns empty Optional if not found or mismatched</li>
 *   <li>FFmpeg/libass only — no Remotion, no Artifact DAG, no Spring AI</li>
 * </ul>
 */
@Service
public class PreviewRenderJobService {

    private static final Logger log = LoggerFactory.getLogger(PreviewRenderJobService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final int DEFAULT_LIST_LIMIT = 100;

    private final PreviewRenderJobRepository repository;
    private final ProductRuntimeService productRuntime;
    private final ProductDependencyRepository dependencyRepository;

    public PreviewRenderJobService(
            PreviewRenderJobRepository repository,
            ProductRuntimeService productRuntime,
            ProductDependencyRepository dependencyRepository) {
        this.repository = repository;
        this.productRuntime = productRuntime;
        this.dependencyRepository = dependencyRepository;
    }

    /**
     * Create a new preview render job.
     *
     * <p>Validates tenant access, creates the job in QUEUED state, and
     * returns the response. Does NOT execute the render — execution is
     * a separate concern.</p>
     *
     * @param request the creation request (must not be null)
     * @return the created job response
     * @throws IllegalArgumentException if tenant/project validation fails
     */
    @Transactional
    public PreviewRenderJobResponse create(CreatePreviewRenderJobRequest request) {
        log.info("Creating preview render job: tenant={}, project={}, profile={}",
                request.tenantId(), request.projectId(), request.profileOrDefault());

        assertTenantAccess(request.tenantId());

        PreviewRenderJobId jobId = new PreviewRenderJobId(Ids.newId("prj"));
        PreviewRenderJob job = PreviewRenderJob.create(
                jobId,
                request.tenantId(),
                request.projectId(),
                request.snapshotId(),
                request.profileOrDefault());

        PreviewRenderJob saved = repository.save(job);
        log.info("Preview render job created: id={}", saved.jobId().value());
        return PreviewRenderJobResponse.fromDomain(saved);
    }

    /**
     * Query the status of a preview render job.
     *
     * <p>Fails closed: returns empty if not found or tenant/project mismatch.</p>
     *
     * @param tenantId  the tenant identifier
     * @param projectId the project identifier
     * @param jobId     the job identifier
     * @return the job response, or empty if not found
     */
    public Optional<PreviewRenderJobResponse> getStatus(
            String tenantId, String projectId, String jobId) {
        assertTenantAccess(tenantId);

        PreviewRenderJobId id = new PreviewRenderJobId(jobId);
        return repository.findByIdAndTenantAndProject(id, tenantId, projectId)
                .map(PreviewRenderJobResponse::fromDomain);
    }

    /**
     * List all preview render jobs for a project within a tenant.
     *
     * @param tenantId  the tenant identifier
     * @param projectId the project identifier
     * @return list of job responses (may be empty)
     */
    public List<PreviewRenderJobResponse> list(String tenantId, String projectId) {
        assertTenantAccess(tenantId);
        return repository.listByTenantAndProject(tenantId, projectId, DEFAULT_LIST_LIMIT)
                .stream()
                .map(PreviewRenderJobResponse::fromDomain)
                .toList();
    }

    /**
     * Retrieve product/artifact metadata for a completed preview render job.
     *
     * <p>Reconstructs artifact information from Product metadata and
     * ProductDependency lineage. Returns empty if:
     * <ul>
     *   <li>Job not found or tenant/project mismatch</li>
     *   <li>Job not in COMPLETED state</li>
     *   <li>Output product not found</li>
     * </ul>
     *
     * @param tenantId  the tenant identifier
     * @param projectId the project identifier
     * @param jobId     the job identifier
     * @return the artifact response, or empty if not available
     */
    public Optional<PreviewRenderJobArtifactResponse> getArtifacts(
            String tenantId, String projectId, String jobId) {
        assertTenantAccess(tenantId);

        PreviewRenderJobId id = new PreviewRenderJobId(jobId);
        Optional<PreviewRenderJob> jobOpt =
                repository.findByIdAndTenantAndProject(id, tenantId, projectId);

        if (jobOpt.isEmpty()) {
            return Optional.empty();
        }

        PreviewRenderJob job = jobOpt.get();
        if (job.status() != PreviewRenderJobStatus.COMPLETED
                || job.outputProductId() == null) {
            return Optional.empty();
        }

        Optional<Product> productOpt = productRuntime.find(job.outputProductId());
        if (productOpt.isEmpty()) {
            log.warn("Output product not found for preview job {}: productId={}",
                    jobId, job.outputProductId());
            return Optional.empty();
        }

        Product product = productOpt.get();
        Map<String, Object> metadata = parseMetadata(product.metadataJson());

        List<String> inputProductIds = metadata != null
                ? extractStringList(metadata, "inputProductIds")
                : List.of();
        int inputDependencyCount = inputProductIds != null ? inputProductIds.size() : 0;

        return Optional.of(new PreviewRenderJobArtifactResponse(
                jobId,
                job.projectId(),
                product.productId(),
                product.status().name(),
                product.mimeType(),
                getString(metadata, "outputFormat"),
                getInt(metadata, "width"),
                getInt(metadata, "height"),
                getInt(metadata, "fps"),
                getDouble(metadata, "durationSeconds"),
                getBoolean(metadata, "hasSubtitles"),
                inputProductIds != null ? inputProductIds : List.of(),
                inputDependencyCount,
                job.createdAt() != null ? job.createdAt().toString() : null,
                job.completedAt() != null ? job.completedAt().toString() : null,
                buildMessage(job.status())));
    }

    // --- Private helpers ---

    private void assertTenantAccess(String tenantId) {
        String currentTenant = TenantContext.get();
        if (currentTenant != null && !currentTenant.equals(tenantId)) {
            throw new IllegalArgumentException("Resource not found for tenant");
        }
    }

    private String buildMessage(PreviewRenderJobStatus status) {
        return switch (status) {
            case QUEUED -> "Preview render job queued";
            case EXECUTING -> "Preview render in progress";
            case COMPLETED -> "Preview render completed successfully";
            case FAILED -> "Preview render failed";
            case CANCELLED -> "Preview render cancelled";
        };
    }

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

    private String getString(Map<String, Object> metadata, String key) {
        if (metadata == null) return null;
        Object value = metadata.get(key);
        return value != null ? value.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Map<String, Object> metadata, String key) {
        if (metadata == null) return null;
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
        if (metadata == null) return 0;
        Object value = metadata.get(key);
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    private double getDouble(Map<String, Object> metadata, String key) {
        if (metadata == null) return 0.0;
        Object value = metadata.get(key);
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0.0; }
        }
        return 0.0;
    }

    private boolean getBoolean(Map<String, Object> metadata, String key) {
        if (metadata == null) return false;
        Object value = metadata.get(key);
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }
}
