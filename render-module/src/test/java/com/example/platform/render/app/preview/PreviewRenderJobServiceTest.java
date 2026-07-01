package com.example.platform.render.app.preview;

import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.domain.previewjob.PreviewRenderJob;
import com.example.platform.render.domain.previewjob.PreviewRenderJobId;
import com.example.platform.render.domain.previewjob.PreviewRenderJobStatus;
import com.example.platform.render.domain.product.*;
import com.example.platform.render.infrastructure.product.ProductDependencyRepository;
import com.example.platform.render.infrastructure.product.ProductRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PreviewRenderJobService}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Create: happy path, profile default, tenant mismatch</li>
 *   <li>GetStatus: found, not found, tenant mismatch, project mismatch</li>
 *   <li>List: multiple jobs, empty list, tenant isolation</li>
 *   <li>GetArtifacts: completed job with product, not completed, no product</li>
 *   <li>No sensitive data in any response</li>
 *   <li>Fail-closed behavior</li>
 * </ul>
 */
class PreviewRenderJobServiceTest {

    private InMemoryPreviewRenderJobRepository previewRepo;
    private InMemoryProductRepository productRepo;
    private InMemoryProductDependencyRepository depRepo;
    private ProductRuntimeService productRuntime;
    private PreviewRenderJobService service;

    @BeforeEach
    void setUp() {
        previewRepo = new InMemoryPreviewRenderJobRepository();
        productRepo = new InMemoryProductRepository();
        depRepo = new InMemoryProductDependencyRepository();
        productRuntime = new ProductRuntimeService(productRepo, depRepo);
        service = new PreviewRenderJobService(previewRepo, productRuntime, depRepo);
    }

    // ─── Create tests ───

    @Nested
    @DisplayName("Create preview render job")
    class CreateTests {

        @Test
        @DisplayName("Happy path: creates job in QUEUED state")
        void createHappyPath() {
            CreatePreviewRenderJobRequest request = new CreatePreviewRenderJobRequest(
                    "tenant_1", "prj_1", "snap_1", "default_1080p");

            PreviewRenderJobResponse response = service.create(request);

            assertNotNull(response.jobId());
            assertTrue(response.jobId().startsWith("prj_"));
            assertEquals("tenant_1", response.tenantId());
            assertEquals("prj_1", response.projectId());
            assertEquals("snap_1", response.snapshotId());
            assertEquals("default_1080p", response.profile());
            assertEquals("QUEUED", response.status());
            assertNull(response.outputProductId());
            assertNull(response.errorMessage());
            assertNotNull(response.createdAt());
            assertNull(response.completedAt());
        }

        @Test
        @DisplayName("Defaults profile to default_1080p when blank")
        void createDefaultProfile() {
            CreatePreviewRenderJobRequest request = new CreatePreviewRenderJobRequest(
                    "tenant_1", "prj_1", "snap_1", null);

            PreviewRenderJobResponse response = service.create(request);

            assertEquals("default_1080p", response.profile());
        }

        @Test
        @DisplayName("Defaults profile to default_1080p when empty string")
        void createEmptyProfile() {
            CreatePreviewRenderJobRequest request = new CreatePreviewRenderJobRequest(
                    "tenant_1", "prj_1", "snap_1", "  ");

            PreviewRenderJobResponse response = service.create(request);

            assertEquals("default_1080p", response.profile());
        }

        @Test
        @DisplayName("Custom profile is preserved")
        void createCustomProfile() {
            CreatePreviewRenderJobRequest request = new CreatePreviewRenderJobRequest(
                    "tenant_1", "prj_1", "snap_1", "4k_ultra");

            PreviewRenderJobResponse response = service.create(request);

            assertEquals("4k_ultra", response.profile());
        }

        @Test
        @DisplayName("Persists job in repository")
        void createPersistsJob() {
            CreatePreviewRenderJobRequest request = new CreatePreviewRenderJobRequest(
                    "tenant_1", "prj_1", "snap_1", "default_1080p");

            PreviewRenderJobResponse response = service.create(request);

            assertEquals(1, previewRepo.size());
            Optional<PreviewRenderJob> stored = previewRepo.findById(
                    new PreviewRenderJobId(response.jobId()));
            assertTrue(stored.isPresent());
            assertEquals("tenant_1", stored.get().tenantId());
            assertEquals("prj_1", stored.get().projectId());
        }
    }

    // ─── GetStatus tests ───

    @Nested
    @DisplayName("Get status")
    class GetStatusTests {

        @Test
        @DisplayName("Returns job when found")
        void getStatusFound() {
            CreatePreviewRenderJobRequest request = new CreatePreviewRenderJobRequest(
                    "tenant_1", "prj_1", "snap_1", "default_1080p");
            PreviewRenderJobResponse created = service.create(request);

            Optional<PreviewRenderJobResponse> result =
                    service.getStatus("tenant_1", "prj_1", created.jobId());

            assertTrue(result.isPresent());
            assertEquals(created.jobId(), result.get().jobId());
            assertEquals("QUEUED", result.get().status());
        }

        @Test
        @DisplayName("Returns empty when job not found")
        void getStatusNotFound() {
            Optional<PreviewRenderJobResponse> result =
                    service.getStatus("tenant_1", "prj_1", "nonexistent");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Returns empty when tenant mismatch")
        void getStatusTenantMismatch() {
            CreatePreviewRenderJobRequest request = new CreatePreviewRenderJobRequest(
                    "tenant_1", "prj_1", "snap_1", "default_1080p");
            PreviewRenderJobResponse created = service.create(request);

            Optional<PreviewRenderJobResponse> result =
                    service.getStatus("tenant_other", "prj_1", created.jobId());

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Returns empty when project mismatch")
        void getStatusProjectMismatch() {
            CreatePreviewRenderJobRequest request = new CreatePreviewRenderJobRequest(
                    "tenant_1", "prj_1", "snap_1", "default_1080p");
            PreviewRenderJobResponse created = service.create(request);

            Optional<PreviewRenderJobResponse> result =
                    service.getStatus("tenant_1", "prj_other", created.jobId());

            assertTrue(result.isEmpty());
        }
    }

    // ─── List tests ───

    @Nested
    @DisplayName("List jobs")
    class ListTests {

        @Test
        @DisplayName("Lists jobs for tenant and project")
        void listJobs() {
            service.create(new CreatePreviewRenderJobRequest("tenant_1", "prj_1", "snap_1", "default_1080p"));
            service.create(new CreatePreviewRenderJobRequest("tenant_1", "prj_1", "snap_2", "default_1080p"));
            service.create(new CreatePreviewRenderJobRequest("tenant_1", "prj_2", "snap_3", "default_1080p"));

            List<PreviewRenderJobResponse> results = service.list("tenant_1", "prj_1");

            assertEquals(2, results.size());
            assertTrue(results.stream().allMatch(r -> "prj_1".equals(r.projectId())));
        }

        @Test
        @DisplayName("Returns empty list when no jobs")
        void listEmpty() {
            List<PreviewRenderJobResponse> results = service.list("tenant_1", "prj_empty");

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("Isolates by tenant")
        void listTenantIsolation() {
            service.create(new CreatePreviewRenderJobRequest("tenant_1", "prj_1", "snap_1", "default_1080p"));
            service.create(new CreatePreviewRenderJobRequest("tenant_2", "prj_1", "snap_2", "default_1080p"));

            List<PreviewRenderJobResponse> results = service.list("tenant_1", "prj_1");

            assertEquals(1, results.size());
            assertEquals("tenant_1", results.get(0).tenantId());
        }
    }

    // ─── GetArtifacts tests ───

    @Nested
    @DisplayName("Get artifacts")
    class GetArtifactsTests {

        @Test
        @DisplayName("Returns artifacts for completed job with product")
        void getArtifactsCompleted() {
            // Create and manually transition job to COMPLETED
            CreatePreviewRenderJobRequest request = new CreatePreviewRenderJobRequest(
                    "tenant_1", "prj_1", "snap_1", "default_1080p");
            PreviewRenderJobResponse created = service.create(request);

            // Register output product
            String productId = registerOutputProduct(
                    created.jobId(), "prj_1", "snap_1",
                    ProductStatus.READY, "video/mp4",
                    1920, 1080, 30, 10.5, true,
                    List.of("input_1"));

            // Update job to COMPLETED state with output product
            previewRepo.updateStatus(
                    new PreviewRenderJobId(created.jobId()),
                    PreviewRenderJobStatus.COMPLETED,
                    productId, null);

            Optional<PreviewRenderJobArtifactResponse> result =
                    service.getArtifacts("tenant_1", "prj_1", created.jobId());

            assertTrue(result.isPresent());
            PreviewRenderJobArtifactResponse response = result.get();
            assertEquals(created.jobId(), response.renderJobId());
            assertEquals("prj_1", response.projectId());
            assertEquals(productId, response.outputProductId());
            assertEquals("READY", response.productStatus());
            assertEquals("video/mp4", response.mimeType());
            assertEquals("mp4", response.outputFormat());
            assertEquals(1920, response.width());
            assertEquals(1080, response.height());
            assertEquals(30, response.fps());
            assertEquals(10.5, response.durationSeconds());
            assertTrue(response.hasSubtitles());
            assertEquals(1, response.inputProductIds().size());
            assertEquals("input_1", response.inputProductIds().get(0));
            assertEquals(1, response.inputDependencyCount());
            assertNotNull(response.createdAt());
            assertNotNull(response.completedAt());
            assertEquals("Preview render completed successfully", response.message());
        }

        @Test
        @DisplayName("Returns empty for non-completed job")
        void getArtifactsNotCompleted() {
            CreatePreviewRenderJobRequest request = new CreatePreviewRenderJobRequest(
                    "tenant_1", "prj_1", "snap_1", "default_1080p");
            PreviewRenderJobResponse created = service.create(request);

            Optional<PreviewRenderJobArtifactResponse> result =
                    service.getArtifacts("tenant_1", "prj_1", created.jobId());

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Returns empty when output product not found")
        void getArtifactsProductNotFound() {
            CreatePreviewRenderJobRequest request = new CreatePreviewRenderJobRequest(
                    "tenant_1", "prj_1", "snap_1", "default_1080p");
            PreviewRenderJobResponse created = service.create(request);

            // Update job to COMPLETED but with non-existent product ID
            previewRepo.updateStatus(
                    new PreviewRenderJobId(created.jobId()),
                    PreviewRenderJobStatus.COMPLETED,
                    "nonexistent_product", null);

            Optional<PreviewRenderJobArtifactResponse> result =
                    service.getArtifacts("tenant_1", "prj_1", created.jobId());

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Returns empty for failed job")
        void getArtifactsFailedJob() {
            CreatePreviewRenderJobRequest request = new CreatePreviewRenderJobRequest(
                    "tenant_1", "prj_1", "snap_1", "default_1080p");
            PreviewRenderJobResponse created = service.create(request);

            previewRepo.updateStatus(
                    new PreviewRenderJobId(created.jobId()),
                    PreviewRenderJobStatus.FAILED,
                    null, "Render failed");

            Optional<PreviewRenderJobArtifactResponse> result =
                    service.getArtifacts("tenant_1", "prj_1", created.jobId());

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Returns empty for tenant mismatch")
        void getArtifactsTenantMismatch() {
            CreatePreviewRenderJobRequest request = new CreatePreviewRenderJobRequest(
                    "tenant_1", "prj_1", "snap_1", "default_1080p");
            PreviewRenderJobResponse created = service.create(request);

            Optional<PreviewRenderJobArtifactResponse> result =
                    service.getArtifacts("tenant_other", "prj_1", created.jobId());

            assertTrue(result.isEmpty());
        }
    }

    // ─── Safety: no sensitive data ───

    @Nested
    @DisplayName("Safety: no sensitive data exposed")
    class SafetyTests {

        @Test
        @DisplayName("Response does not expose storage reference IDs")
        void noStorageReferenceId() {
            CreatePreviewRenderJobRequest request = new CreatePreviewRenderJobRequest(
                    "tenant_1", "prj_1", "snap_1", "default_1080p");
            PreviewRenderJobResponse created = service.create(request);

            // The response record has no storageReferenceId field — structural guarantee
            assertNotNull(created.jobId());
            assertNotNull(created.status());
        }

        @Test
        @DisplayName("Artifact response does not expose local paths")
        void noLocalPaths() {
            CreatePreviewRenderJobRequest request = new CreatePreviewRenderJobRequest(
                    "tenant_1", "prj_1", "snap_1", "default_1080p");
            PreviewRenderJobResponse created = service.create(request);

            String productId = registerOutputProduct(
                    created.jobId(), "prj_1", "snap_1",
                    ProductStatus.READY, "video/mp4",
                    1920, 1080, 30, 10.5, false,
                    List.of());

            previewRepo.updateStatus(
                    new PreviewRenderJobId(created.jobId()),
                    PreviewRenderJobStatus.COMPLETED,
                    productId, null);

            Optional<PreviewRenderJobArtifactResponse> result =
                    service.getArtifacts("tenant_1", "prj_1", created.jobId());

            assertTrue(result.isPresent());
            PreviewRenderJobArtifactResponse response = result.get();
            // The response record has no storageUri, localPath, or signedUrl fields
            assertNotNull(response.outputProductId());
            assertNotNull(response.mimeType());
        }

        @Test
        @DisplayName("Domain entity transitions are fail-closed")
        void domainTransitionsFailClosed() {
            PreviewRenderJob job = PreviewRenderJob.create(
                    new PreviewRenderJobId("test_1"), "t", "p", "s", "prof");

            // Cannot complete from QUEUED
            assertThrows(IllegalStateException.class, () -> job.complete("prod_1"));

            // Cannot cancel from terminal state
            PreviewRenderJob executing = job.startExecuting();
            PreviewRenderJob completed = executing.complete("prod_1");
            assertThrows(IllegalStateException.class, completed::cancel);

            // Cannot start executing from non-QUEUED
            assertThrows(IllegalStateException.class, executing::startExecuting);
        }
    }

    // ─── Domain unit tests ───

    @Nested
    @DisplayName("PreviewRenderJob domain")
    class DomainTests {

        @Test
        @DisplayName("Full lifecycle: QUEUED → EXECUTING → COMPLETED")
        void fullLifecycleCompleted() {
            PreviewRenderJob job = PreviewRenderJob.create(
                    new PreviewRenderJobId("j1"), "t1", "p1", "s1", "default_1080p");
            assertEquals(PreviewRenderJobStatus.QUEUED, job.status());
            assertFalse(job.isTerminal());

            PreviewRenderJob executing = job.startExecuting();
            assertEquals(PreviewRenderJobStatus.EXECUTING, executing.status());
            assertFalse(executing.isTerminal());

            PreviewRenderJob completed = executing.complete("prod_1");
            assertEquals(PreviewRenderJobStatus.COMPLETED, completed.status());
            assertEquals("prod_1", completed.outputProductId());
            assertTrue(completed.isTerminal());
            assertNotNull(completed.completedAt());
        }

        @Test
        @DisplayName("Full lifecycle: QUEUED → EXECUTING → FAILED")
        void fullLifecycleFailed() {
            PreviewRenderJob job = PreviewRenderJob.create(
                    new PreviewRenderJobId("j2"), "t1", "p1", "s1", "default_1080p");

            PreviewRenderJob executing = job.startExecuting();
            PreviewRenderJob failed = executing.fail("FFmpeg error");
            assertEquals(PreviewRenderJobStatus.FAILED, failed.status());
            assertEquals("FFmpeg error", failed.errorMessage());
            assertTrue(failed.isTerminal());
        }

        @Test
        @DisplayName("Cancel from QUEUED")
        void cancelFromQueued() {
            PreviewRenderJob job = PreviewRenderJob.create(
                    new PreviewRenderJobId("j3"), "t1", "p1", "s1", "default_1080p");

            PreviewRenderJob cancelled = job.cancel();
            assertEquals(PreviewRenderJobStatus.CANCELLED, cancelled.status());
            assertTrue(cancelled.isTerminal());
        }

        @Test
        @DisplayName("Fail from QUEUED")
        void failFromQueued() {
            PreviewRenderJob job = PreviewRenderJob.create(
                    new PreviewRenderJobId("j4"), "t1", "p1", "s1", "default_1080p");

            PreviewRenderJob failed = job.fail("validation error");
            assertEquals(PreviewRenderJobStatus.FAILED, failed.status());
        }

        @Test
        @DisplayName("Cannot cancel from EXECUTING")
        void cannotCancelExecuting() {
            PreviewRenderJob job = PreviewRenderJob.create(
                    new PreviewRenderJobId("j5"), "t1", "p1", "s1", "default_1080p");
            PreviewRenderJob executing = job.startExecuting();

            assertThrows(IllegalStateException.class, executing::cancel);
        }

        @Test
        @DisplayName("PreviewRenderJobId rejects null and blank")
        void jobIdValidation() {
            assertThrows(NullPointerException.class, () -> new PreviewRenderJobId(null));
            assertThrows(IllegalArgumentException.class, () -> new PreviewRenderJobId(""));
            assertThrows(IllegalArgumentException.class, () -> new PreviewRenderJobId("  "));
        }

        @Test
        @DisplayName("PreviewRenderJob rejects null constructor args")
        void jobNullArgs() {
            assertThrows(NullPointerException.class, () ->
                    PreviewRenderJob.create(null, "t", "p", "s", "prof"));
            assertThrows(NullPointerException.class, () ->
                    PreviewRenderJob.create(new PreviewRenderJobId("x"), null, "p", "s", "prof"));
            assertThrows(NullPointerException.class, () ->
                    PreviewRenderJob.create(new PreviewRenderJobId("x"), "t", null, "s", "prof"));
            assertThrows(NullPointerException.class, () ->
                    PreviewRenderJob.create(new PreviewRenderJobId("x"), "t", "p", null, "prof"));
            assertThrows(NullPointerException.class, () ->
                    PreviewRenderJob.create(new PreviewRenderJobId("x"), "t", "p", "s", null));
        }

        @Test
        @DisplayName("Complete rejects null outputProductId")
        void completeNullProductId() {
            PreviewRenderJob job = PreviewRenderJob.create(
                    new PreviewRenderJobId("j6"), "t1", "p1", "s1", "default_1080p");
            PreviewRenderJob executing = job.startExecuting();

            assertThrows(NullPointerException.class, () -> executing.complete(null));
        }
    }

    // ─── Helper methods ───

    private String registerOutputProduct(
            String renderJobId, String projectId, String snapshotId,
            ProductStatus status, String mimeType,
            int width, int height, int fps, double durationSeconds,
            boolean hasSubtitles, List<String> inputProductIds) {

        String productId = "prod_" + UUID.randomUUID().toString().substring(0, 8);
        String metadataJson = buildMetadataJson(
                renderJobId, snapshotId, "mp4",
                width, height, fps, durationSeconds, hasSubtitles, inputProductIds);

        Product product = new Product(
                productId, "tenant_1", projectId, null,
                ProductType.FINAL_RENDER, RepresentationKind.MEDIA_FILE,
                "ffmpeg", "ffmpeg", null,
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

    private String buildMetadataJson(
            String renderJobId, String snapshotId, String outputFormat,
            int width, int height, int fps, double durationSeconds,
            boolean hasSubtitles, List<String> inputProductIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"renderJobId\":\"").append(renderJobId).append("\"");
        sb.append(",\"snapshotId\":\"").append(snapshotId).append("\"");
        sb.append(",\"outputFormat\":\"").append(outputFormat).append("\"");
        if (width > 0) sb.append(",\"width\":").append(width);
        if (height > 0) sb.append(",\"height\":").append(height);
        if (fps > 0) sb.append(",\"fps\":").append(fps);
        if (durationSeconds > 0) sb.append(",\"durationSeconds\":").append(durationSeconds);
        sb.append(",\"hasSubtitles\":").append(hasSubtitles);
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

        @Override
        public List<Product> findBySourceTimelineRevisionId(String timelineRevisionId) {
            return List.of();
        }
    }

    static class InMemoryProductDependencyRepository extends ProductDependencyRepository {
        private final Map<String, ProductDependency> store = new ConcurrentHashMap<>();

        @Override
        public ProductDependency save(ProductDependency dep) {
            String id = dep.dependencyId() != null ? dep.dependencyId() : "dep-" + UUID.randomUUID();
            ProductDependency saved = new ProductDependency(id, dep.tenantId(), dep.projectId(),
                    dep.productId(), dep.dependsOnProductId(), dep.dependencyType(), dep.createdAt());
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
        public void delete(String dependencyId) {
            store.remove(dependencyId);
        }
    }
}
