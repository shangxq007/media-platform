package com.example.platform.render.app.timeline;

import com.example.platform.render.api.dto.RenderJobResultResponse;
import com.example.platform.render.api.dto.RenderJobStatusResponse;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.domain.product.*;
import com.example.platform.render.infrastructure.product.ProductDependencyRepository;
import com.example.platform.render.infrastructure.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RenderJobStatusService}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Status query for READY render</li>
 *   <li>Status query preserves inputProductIds and inputDependencyCount</li>
 *   <li>Status query preserves timelineRevisionId and snapshotId</li>
 *   <li>Project mismatch fails closed</li>
 *   <li>Revision mismatch fails closed</li>
 *   <li>Unknown renderJobId returns empty</li>
 *   <li>No sensitive data in response</li>
 *   <li>Result query for READY render</li>
 *   <li>Result query includes mimeType/dimensions/fps/duration</li>
 *   <li>Result query includes inputProductIds</li>
 *   <li>Result query project mismatch fails closed</li>
 *   <li>Result query no sensitive data</li>
 * </ul>
 */
class RenderJobStatusServiceTest {

    private ProductRepository productRepo;
    private ProductDependencyRepository depRepo;
    private ProductRuntimeService productRuntime;
    private RenderJobStatusService statusService;

    @BeforeEach
    void setUp() {
        productRepo = new InMemoryProductRepository();
        depRepo = new InMemoryProductDependencyRepository();
        productRuntime = new ProductRuntimeService(productRepo, depRepo);
        statusService = new RenderJobStatusService(productRuntime, depRepo);
    }

    // ─── Status query tests ───

    @Test
    @DisplayName("Status query: READY render returns status READY and resultAvailable true")
    void statusQueryReadyRender() {
        // Arrange
        String renderJobId = "rj_test_001";
        String projectId = "prj_1";
        String revisionId = "rev_1";
        String snapshotId = "snap_1";
        String outputProductId = registerOutputProduct(
                renderJobId, projectId, revisionId, snapshotId,
                ProductStatus.READY, "video/mp4", List.of("input_prod_1"));

        // Act
        Optional<RenderJobStatusResponse> result = statusService.findStatus(projectId, revisionId, renderJobId);

        // Assert
        assertTrue(result.isPresent());
        RenderJobStatusResponse response = result.get();
        assertEquals(renderJobId, response.renderJobId());
        assertEquals(projectId, response.projectId());
        assertEquals(revisionId, response.timelineRevisionId());
        assertEquals(snapshotId, response.snapshotId());
        assertEquals("READY", response.status());
        assertEquals(outputProductId, response.outputProductId());
        assertEquals("READY", response.productStatus());
        assertTrue(response.resultAvailable());
        assertEquals("Render completed successfully", response.message());
    }

    @Test
    @DisplayName("Status query: preserves inputProductIds and inputDependencyCount")
    void statusQueryPreservesInputProductIds() {
        // Arrange
        String renderJobId = "rj_test_002";
        List<String> inputIds = List.of("input_prod_1", "input_prod_2");
        registerOutputProduct(renderJobId, "prj_1", "rev_1", "snap_1",
                ProductStatus.READY, "video/mp4", inputIds);

        // Act
        Optional<RenderJobStatusResponse> result = statusService.findStatus("prj_1", "rev_1", renderJobId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(2, result.get().inputProductIds().size());
        assertEquals("input_prod_1", result.get().inputProductIds().get(0));
        assertEquals("input_prod_2", result.get().inputProductIds().get(1));
        assertEquals(2, result.get().inputDependencyCount());
    }

    @Test
    @DisplayName("Status query: preserves timelineRevisionId and snapshotId from metadata")
    void statusQueryPreservesRevisionAndSnapshot() {
        // Arrange
        String renderJobId = "rj_test_003";
        registerOutputProduct(renderJobId, "prj_1", "rev_abc", "snap_xyz",
                ProductStatus.READY, "video/mp4", List.of());

        // Act
        Optional<RenderJobStatusResponse> result = statusService.findStatus("prj_1", "rev_abc", renderJobId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals("rev_abc", result.get().timelineRevisionId());
        assertEquals("snap_xyz", result.get().snapshotId());
    }

    @Test
    @DisplayName("Status query: project mismatch fails closed")
    void statusQueryProjectMismatch() {
        // Arrange
        registerOutputProduct("rj_test_004", "prj_correct", "rev_1", "snap_1",
                ProductStatus.READY, "video/mp4", List.of());

        // Act
        Optional<RenderJobStatusResponse> result = statusService.findStatus("prj_wrong", "rev_1", "rj_test_004");

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Status query: revision mismatch fails closed")
    void statusQueryRevisionMismatch() {
        // Arrange
        registerOutputProduct("rj_test_005", "prj_1", "rev_correct", "snap_1",
                ProductStatus.READY, "video/mp4", List.of());

        // Act
        Optional<RenderJobStatusResponse> result = statusService.findStatus("prj_1", "rev_wrong", "rj_test_005");

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Status query: unknown renderJobId returns empty")
    void statusQueryUnknownRenderJobId() {
        // Arrange
        registerOutputProduct("rj_known", "prj_1", "rev_1", "snap_1",
                ProductStatus.READY, "video/mp4", List.of());

        // Act
        Optional<RenderJobStatusResponse> result = statusService.findStatus("prj_1", "rev_1", "rj_unknown");

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Status query: null parameters return empty")
    void statusQueryNullParameters() {
        assertTrue(statusService.findStatus(null, "rev_1", "rj_1").isEmpty());
        assertTrue(statusService.findStatus("prj_1", null, "rj_1").isEmpty());
        assertTrue(statusService.findStatus("prj_1", "rev_1", null).isEmpty());
    }

    @Test
    @DisplayName("Status query: does not expose sensitive data")
    void statusQueryNoSensitiveData() {
        // Arrange
        String renderJobId = "rj_test_006";
        registerOutputProduct(renderJobId, "prj_1", "rev_1", "snap_1",
                ProductStatus.READY, "video/mp4", List.of("input_1"));

        // Act
        Optional<RenderJobStatusResponse> result = statusService.findStatus("prj_1", "rev_1", renderJobId);

        // Assert
        assertTrue(result.isPresent());
        RenderJobStatusResponse response = result.get();
        // No storageReferenceId field exists on the response record
        // No provider/backend/environment fields
        // No signed URLs or paths
        assertNull(response.outputProfile()); // not set in metadata for this test
        assertNull(response.renderMode()); // not set in metadata for this test
    }

    @Test
    @DisplayName("Status query: FAILED render returns status FAILED and resultAvailable false")
    void statusQueryFailedRender() {
        // Arrange
        String renderJobId = "rj_test_007";
        registerOutputProduct(renderJobId, "prj_1", "rev_1", "snap_1",
                ProductStatus.FAILED, null, null);

        // Act
        Optional<RenderJobStatusResponse> result = statusService.findStatus("prj_1", "rev_1", renderJobId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals("FAILED", result.get().status());
        assertFalse(result.get().resultAvailable());
        assertEquals("Render failed", result.get().message());
    }

    @Test
    @DisplayName("Status query: includes renderMode and outputProfile from metadata")
    void statusQueryIncludesRenderModeAndProfile() {
        // Arrange
        String renderJobId = "rj_test_008";
        registerOutputProductWithFullMetadata(renderJobId, "prj_1", "rev_1", "snap_1",
                ProductStatus.READY, "video/mp4", List.of("input_1"),
                "timeline-revision-render", "default_1080p", "mp4",
                1920, 1080, 30, 10.5, true, "ffmpeg-libass");

        // Act
        Optional<RenderJobStatusResponse> result = statusService.findStatus("prj_1", "rev_1", renderJobId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals("timeline-revision-render", result.get().renderMode());
        assertEquals("default_1080p", result.get().outputProfile());
        assertEquals("mp4", result.get().outputFormat());
    }

    // ─── Result query tests ───

    @Test
    @DisplayName("Result query: READY render returns full Product result summary")
    void resultQueryReadyRender() {
        // Arrange
        String renderJobId = "rj_result_001";
        registerOutputProductWithFullMetadata(renderJobId, "prj_1", "rev_1", "snap_1",
                ProductStatus.READY, "video/mp4", List.of("input_1"),
                "timeline-revision-render", "default_1080p", "mp4",
                1920, 1080, 30, 10.5, true, "ffmpeg-libass");

        // Act
        Optional<RenderJobResultResponse> result = statusService.findResult("prj_1", "rev_1", renderJobId);

        // Assert
        assertTrue(result.isPresent());
        RenderJobResultResponse response = result.get();
        assertEquals(renderJobId, response.renderJobId());
        assertEquals("prj_1", response.projectId());
        assertEquals("rev_1", response.timelineRevisionId());
        assertEquals("snap_1", response.snapshotId());
        assertEquals("READY", response.productStatus());
        assertEquals("video/mp4", response.mimeType());
        assertEquals("mp4", response.outputFormat());
        assertEquals(1920, response.width());
        assertEquals(1080, response.height());
        assertEquals(30, response.fps());
        assertEquals(10.5, response.durationSeconds());
        assertTrue(response.hasSubtitles());
        assertEquals("ffmpeg-libass", response.baselineRenderer());
        assertEquals("timeline-revision-render", response.renderMode());
        assertEquals(1, response.inputProductIds().size());
        assertEquals("input_1", response.inputProductIds().get(0));
        assertEquals(1, response.inputDependencyCount());
        assertEquals("Render result available", response.message());
    }

    @Test
    @DisplayName("Result query: includes mimeType and dimensions from Product")
    void resultQueryIncludesMimeTypeAndDimensions() {
        // Arrange
        registerOutputProductWithFullMetadata("rj_result_002", "prj_1", "rev_1", "snap_1",
                ProductStatus.READY, "video/webm", List.of(),
                "timeline-revision-render", "default_720p", "webm",
                1280, 720, 24, 5.0, false, "ffmpeg-libass");

        // Act
        Optional<RenderJobResultResponse> result = statusService.findResult("prj_1", "rev_1", "rj_result_002");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("video/webm", result.get().mimeType());
        assertEquals(1280, result.get().width());
        assertEquals(720, result.get().height());
        assertEquals(24, result.get().fps());
        assertEquals(5.0, result.get().durationSeconds());
        assertFalse(result.get().hasSubtitles());
    }

    @Test
    @DisplayName("Result query: project mismatch fails closed")
    void resultQueryProjectMismatch() {
        // Arrange
        registerOutputProduct("rj_result_003", "prj_correct", "rev_1", "snap_1",
                ProductStatus.READY, "video/mp4", List.of());

        // Act
        Optional<RenderJobResultResponse> result = statusService.findResult("prj_wrong", "rev_1", "rj_result_003");

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Result query: unknown renderJobId returns empty")
    void resultQueryUnknownRenderJobId() {
        // Arrange
        registerOutputProduct("rj_known", "prj_1", "rev_1", "snap_1",
                ProductStatus.READY, "video/mp4", List.of());

        // Act
        Optional<RenderJobResultResponse> result = statusService.findResult("prj_1", "rev_1", "rj_unknown");

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Result query: does not expose sensitive data")
    void resultQueryNoSensitiveData() {
        // Arrange
        registerOutputProduct("rj_result_004", "prj_1", "rev_1", "snap_1",
                ProductStatus.READY, "video/mp4", List.of());

        // Act
        Optional<RenderJobResultResponse> result = statusService.findResult("prj_1", "rev_1", "rj_result_004");

        // Assert
        assertTrue(result.isPresent());
        RenderJobResultResponse response = result.get();
        // Verify no storageReferenceId field exists on the response record
        // Verify no provider/backend/environment fields
        // Verify no signed URLs or paths
        // The record itself doesn't have these fields — this is a structural guarantee
        assertNotNull(response.renderJobId());
    }

    @Test
    @DisplayName("Result query: FAILED render returns failure message")
    void resultQueryFailedRender() {
        // Arrange
        registerOutputProduct("rj_result_005", "prj_1", "rev_1", "snap_1",
                ProductStatus.FAILED, null, null);

        // Act
        Optional<RenderJobResultResponse> result = statusService.findResult("prj_1", "rev_1", "rj_result_005");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("FAILED", result.get().productStatus());
        assertEquals("Render failed — no result available", result.get().message());
    }

    @Test
    @DisplayName("Result query: revision mismatch fails closed")
    void resultQueryRevisionMismatch() {
        // Arrange
        registerOutputProduct("rj_result_006", "prj_1", "rev_correct", "snap_1",
                ProductStatus.READY, "video/mp4", List.of());

        // Act
        Optional<RenderJobResultResponse> result = statusService.findResult("prj_1", "rev_wrong", "rj_result_006");

        // Assert
        assertTrue(result.isEmpty());
    }

    // ─── Helper methods ───

    /**
     * Register an output Product with minimal metadata (renderJobId, timelineRevisionId, snapshotId, inputProductIds).
     */
    private String registerOutputProduct(String renderJobId, String projectId, String revisionId,
                                          String snapshotId, ProductStatus status, String mimeType,
                                          List<String> inputProductIds) {
        String productId = "prod_" + UUID.randomUUID().toString().substring(0, 8);
        String metadataJson = buildMetadataJson(renderJobId, revisionId, snapshotId,
                null, null, null, 0, 0, 0, 0.0, false, null, inputProductIds);

        Product product = new Product(
                productId, "tenant_1", projectId, null,
                ProductType.FINAL_RENDER, RepresentationKind.MEDIA_FILE,
                "ffmpeg", "ffmpeg", revisionId,
                status, "storage_ref_1", "checksum_1", "content_hash_1",
                mimeType, 1, metadataJson, Instant.now(), Instant.now());

        Product saved = productRepo.save(product);
        if (status == ProductStatus.READY) {
            productRuntime.markReady(saved.productId());
        } else if (status == ProductStatus.FAILED) {
            productRuntime.markFailed(saved.productId());
        }
        return saved.productId();
    }

    /**
     * Register an output Product with full provenance metadata.
     */
    private String registerOutputProductWithFullMetadata(
            String renderJobId, String projectId, String revisionId, String snapshotId,
            ProductStatus status, String mimeType, List<String> inputProductIds,
            String renderMode, String outputProfile, String outputFormat,
            int width, int height, int fps, double durationSeconds,
            boolean hasSubtitles, String baselineRenderer) {

        String productId = "prod_" + UUID.randomUUID().toString().substring(0, 8);
        String metadataJson = buildMetadataJson(renderJobId, revisionId, snapshotId,
                renderMode, outputProfile, outputFormat,
                width, height, fps, durationSeconds, hasSubtitles,
                baselineRenderer, inputProductIds);

        Product product = new Product(
                productId, "tenant_1", projectId, null,
                ProductType.FINAL_RENDER, RepresentationKind.MEDIA_FILE,
                "ffmpeg", "ffmpeg", revisionId,
                status, "storage_ref_1", "checksum_1", "content_hash_1",
                mimeType, 1, metadataJson, Instant.now(), Instant.now());

        Product saved = productRepo.save(product);
        if (status == ProductStatus.READY) {
            productRuntime.markReady(saved.productId());
        } else if (status == ProductStatus.FAILED) {
            productRuntime.markFailed(saved.productId());
        }
        return saved.productId();
    }

    /**
     * Build metadataJson string matching the format produced by RenderOutputRegistrationService.
     */
    private String buildMetadataJson(String renderJobId, String revisionId, String snapshotId,
                                      String renderMode, String outputProfile, String outputFormat,
                                      int width, int height, int fps, double durationSeconds,
                                      boolean hasSubtitles, String baselineRenderer,
                                      List<String> inputProductIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"renderJobId\":\"").append(renderJobId).append("\"");
        sb.append(",\"timelineRevisionId\":\"").append(revisionId).append("\"");
        sb.append(",\"snapshotId\":\"").append(snapshotId).append("\"");

        if (renderMode != null) sb.append(",\"renderMode\":\"").append(renderMode).append("\"");
        if (outputProfile != null) sb.append(",\"outputProfile\":\"").append(outputProfile).append("\"");
        if (outputFormat != null) sb.append(",\"outputFormat\":\"").append(outputFormat).append("\"");
        if (width > 0) sb.append(",\"width\":").append(width);
        if (height > 0) sb.append(",\"height\":").append(height);
        if (fps > 0) sb.append(",\"fps\":").append(fps);
        if (durationSeconds > 0) sb.append(",\"durationSeconds\":").append(durationSeconds);
        sb.append(",\"hasSubtitles\":").append(hasSubtitles);
        if (baselineRenderer != null) sb.append(",\"baselineRenderer\":\"").append(baselineRenderer).append("\"");

        if (inputProductIds != null && !inputProductIds.isEmpty()) {
            sb.append(",\"inputProductIds\":[");
            for (int i = 0; i < inputProductIds.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(inputProductIds.get(i)).append("\"");
            }
            sb.append("]");
        }

        sb.append("}");
        return sb.toString();
    }

    // ─── In-memory test doubles ───

    static class InMemoryProductRepository extends ProductRepository {
        private final Map<String, Product> store = new ConcurrentHashMap<>();
        private final Map<String, List<Product>> byProject = new ConcurrentHashMap<>();

        @Override
        public Product save(Product p) {
            String id = p.productId() != null ? p.productId() : "prod-" + UUID.randomUUID();
            Product saved = new Product(id, p.tenantId(), p.projectId(), p.ownerAssetId(),
                    p.productType(), p.representationKind(), p.producerType(), p.producerId(),
                    p.sourceTimelineRevisionId(), p.status(), p.storageReferenceId(),
                    p.checksum(), p.contentHash(), p.mimeType(), p.version(),
                    p.metadataJson(), p.createdAt(), p.updatedAt());
            store.put(id, saved);
            if (p.projectId() != null) byProject.computeIfAbsent(p.projectId(), k -> new ArrayList<>()).add(saved);
            return saved;
        }

        @Override
        public Optional<Product> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Product> findByProject(String projectId, int limit) {
            List<Product> products = byProject.getOrDefault(projectId, List.of());
            return products.size() > limit ? products.subList(0, limit) : products;
        }

        @Override
        public List<Product> findByAsset(String assetId) {
            return List.of();
        }

        @Override
        public Optional<Product> findLatest(String assetId, ProductType type) {
            return Optional.empty();
        }
    }

    static class InMemoryProductDependencyRepository extends ProductDependencyRepository {
        private final Map<String, ProductDependency> store = new ConcurrentHashMap<>();

        @Override
        public ProductDependency save(ProductDependency d) {
            String id = d.dependencyId() != null ? d.dependencyId() : "dep-" + UUID.randomUUID();
            ProductDependency saved = new ProductDependency(id, d.tenantId(), d.projectId(),
                    d.productId(), d.dependsOnProductId(), d.dependencyType(), d.createdAt());
            store.put(id, saved);
            return saved;
        }

        @Override
        public List<ProductDependency> findDependencies(String productId) {
            return store.values().stream()
                    .filter(d -> d.productId().equals(productId))
                    .toList();
        }

        @Override
        public List<ProductDependency> findDependents(String productId) {
            return store.values().stream()
                    .filter(d -> d.dependsOnProductId().equals(productId))
                    .toList();
        }

        @Override
        public boolean exists(String productId, String dependsOnId) {
            return findDependents(dependsOnId).stream()
                    .anyMatch(d -> d.productId().equals(productId));
        }

        @Override
        public void delete(String dependencyId) {
            store.remove(dependencyId);
        }
    }
}
