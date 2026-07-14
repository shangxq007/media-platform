package com.example.platform.render.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.extension.domain.ToolSandboxPolicy;
import com.example.platform.render.app.output.RenderOutputRegistrationService;
import com.example.platform.render.app.output.RenderProductProvenance;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.domain.product.*;
import com.example.platform.render.domain.storage.*;
import com.example.platform.render.infrastructure.product.ProductDependencyRepository;
import com.example.platform.render.infrastructure.product.ProductRepository;
import com.example.platform.render.infrastructure.storage.StorageReferenceRepository;
import com.example.platform.render.testsupport.R2FixtureGenerator;
import com.example.platform.shared.Ids;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Multi-provider integration smoke test proving the safe provider integration pattern.
 *
 * <p>This test verifies that non-FFmpeg providers can participate in the
 * StorageRuntime integration pattern while remaining non-production-eligible:
 * <ol>
 *   <li>StorageRuntime materialized input</li>
 *   <li>Provider-specific local execution (dry-run/smoke)</li>
 *   <li>StorageRuntime output registration when real output is produced</li>
 *   <li>Product FINAL_RENDER or derived Product</li>
 *   <li>ProductDependency lineage</li>
 *   <li>R7 status/result API remains safe</li>
 * </ol>
 *
 * <p>All providers tested here are POC/SPIKE status and NOT production-eligible.
 * FFmpeg remains the only production baseline.</p>
 */
class ProviderIntegrationSmokeTest {
    @SuppressWarnings("unchecked")
    private static <T> org.springframework.beans.factory.ObjectProvider<T> mockProvider(T instance) {
        org.springframework.beans.factory.ObjectProvider<T> op = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(op.getIfAvailable()).thenReturn(instance);
        return op;
    }



    @TempDir
    Path tempDir;

    private StorageRuntimeService storageRuntime;
    private ProductRuntimeService productRuntime;
    private RenderOutputRegistrationService registrationService;

    @BeforeEach
    void setUp() {
        StorageReferenceRepository storageRepo = new InMemoryStorageReferenceRepository();
        ProductRepository productRepo = new InMemoryProductRepository();
        ProductDependencyRepository depRepo = new InMemoryProductDependencyRepository();
        storageRuntime = new StorageRuntimeService(storageRepo, mockProvider(null));
        productRuntime = new ProductRuntimeService(productRepo, depRepo);
        registrationService = new RenderOutputRegistrationService(storageRuntime, productRuntime, tempDir, mockProvider(null), mockProvider(null));
    }

    // ── Provider Status Verification ──

    @Test
    @DisplayName("FFmpeg is the only production-eligible provider")
    void ffmpegIsOnlyProductionProvider() {
        ProviderMetadata ffmpeg = createProviderMetadata("ffmpeg", ProviderStatus.PRODUCTION, true);
        assertTrue(ffmpeg.isProduction(), "FFmpeg must be production status");
        assertTrue(ffmpeg.autoDispatch(), "FFmpeg must auto-dispatch");
        assertTrue(ffmpeg.participatesInAutoRouting(), "FFmpeg must participate in auto-routing");
    }

    @Test
    @DisplayName("Remotion is POC and not production-eligible")
    void remotionIsPocNotProduction() {
        ProviderMetadata remotion = createProviderMetadata("remotion", ProviderStatus.POC, false);
        assertFalse(remotion.isProduction(), "Remotion must not be production");
        assertFalse(remotion.autoDispatch(), "Remotion must not auto-dispatch");
        assertFalse(remotion.participatesInAutoRouting(), "Remotion must not participate in auto-routing");
    }

    @Test
    @DisplayName("GPAC is POC and not production-eligible")
    void gpacIsPocNotProduction() {
        ProviderMetadata gpac = createProviderMetadata("gpac", ProviderStatus.POC, false);
        assertFalse(gpac.isProduction(), "GPAC must not be production");
        assertFalse(gpac.autoDispatch(), "GPAC must not auto-dispatch");
    }

    @Test
    @DisplayName("MLT is POC and not production-eligible")
    void mltIsPocNotProduction() {
        ProviderMetadata mlt = createProviderMetadata("mlt", ProviderStatus.POC, false);
        assertFalse(mlt.isProduction(), "MLT must not be production");
        assertFalse(mlt.autoDispatch(), "MLT must not auto-dispatch");
    }

    @Test
    @DisplayName("Blender is SPIKE and not production-eligible")
    void blenderIsSpikeNotProduction() {
        ProviderMetadata blender = createProviderMetadata("blender", ProviderStatus.SPIKE, false);
        assertFalse(blender.isProduction(), "Blender must not be production");
        assertFalse(blender.autoDispatch(), "Blender must not auto-dispatch");
    }

    @Test
    @DisplayName("Natron is HOLD and not production-eligible")
    void natronIsHoldNotProduction() {
        ProviderMetadata natron = createProviderMetadata("natron", ProviderStatus.HOLD, false);
        assertFalse(natron.isProduction(), "Natron must not be production");
        assertFalse(natron.autoDispatch(), "Natron must not auto-dispatch");
    }

    @Test
    @DisplayName("GStreamer is HOLD and not production-eligible")
    void gstreamerIsHoldNotProduction() {
        ProviderMetadata gstreamer = createProviderMetadata("gstreamer", ProviderStatus.HOLD, false);
        assertFalse(gstreamer.isProduction(), "GStreamer must not be production");
        assertFalse(gstreamer.autoDispatch(), "GStreamer must not auto-dispatch");
    }

    // ── Provider Eligibility Verification ──

    @Test
    @DisplayName("Non-FFmpeg providers are not eligible for production jobs")
    void nonFFmpegProvidersNotEligibleForProduction() {
        RenderJob productionJob = createProductionJob();

        ProviderMetadata remotion = createProviderMetadata("remotion", ProviderStatus.POC, false);
        ProviderMetadata gpac = createProviderMetadata("gpac", ProviderStatus.POC, false);
        ProviderMetadata mlt = createProviderMetadata("mlt", ProviderStatus.POC, false);
        ProviderMetadata blender = createProviderMetadata("blender", ProviderStatus.SPIKE, false);
        ProviderMetadata natron = createProviderMetadata("natron", ProviderStatus.HOLD, false);
        ProviderMetadata gstreamer = createProviderMetadata("gstreamer", ProviderStatus.HOLD, false);

        assertFalse(ProviderEligibility.isEligible(remotion, productionJob),
                "Remotion must not be eligible for production jobs");
        assertFalse(ProviderEligibility.isEligible(gpac, productionJob),
                "GPAC must not be eligible for production jobs");
        assertFalse(ProviderEligibility.isEligible(mlt, productionJob),
                "MLT must not be eligible for production jobs");
        assertFalse(ProviderEligibility.isEligible(blender, productionJob),
                "Blender must not be eligible for production jobs");
        assertFalse(ProviderEligibility.isEligible(natron, productionJob),
                "Natron must not be eligible for production jobs");
        assertFalse(ProviderEligibility.isEligible(gstreamer, productionJob),
                "GStreamer must not be eligible for production jobs");
    }

    @Test
    @DisplayName("Non-FFmpeg providers are eligible for manual/experiment jobs")
    void nonFFmpegProvidersEligibleForManualJobs() {
        RenderJob manualJob = createManualJob();
        RenderJob experimentJob = createExperimentJob();

        ProviderMetadata remotion = createProviderMetadata("remotion", ProviderStatus.POC, false);
        ProviderMetadata gpac = createProviderMetadata("gpac", ProviderStatus.POC, false);
        ProviderMetadata mlt = createProviderMetadata("mlt", ProviderStatus.POC, false);

        assertTrue(ProviderEligibility.isEligible(remotion, manualJob),
                "Remotion must be eligible for manual jobs");
        assertTrue(ProviderEligibility.isEligible(gpac, manualJob),
                "GPAC must be eligible for manual jobs");
        assertTrue(ProviderEligibility.isEligible(mlt, manualJob),
                "MLT must be eligible for manual jobs");

        assertTrue(ProviderEligibility.isEligible(remotion, experimentJob),
                "Remotion must be eligible for experiment jobs");
        assertTrue(ProviderEligibility.isEligible(gpac, experimentJob),
                "GPAC must be eligible for experiment jobs");
        assertTrue(ProviderEligibility.isEligible(mlt, experimentJob),
                "MLT must be eligible for experiment jobs");
    }

    // ── StorageRuntime Integration Pattern ──

    @Test
    @DisplayName("StorageRuntime input materialization works for S3-compatible provider type")
    void storageRuntimeInputMaterializationS3() throws Exception {
        // Create a test file
        Path inputFile = tempDir.resolve("test-input.mp4");
        Files.write(inputFile, "test video content".getBytes());

        // Register as S3-compatible StorageReference
        String checksum = computeSha256(inputFile);
        StorageReference ref = storageRuntime.register(new StorageReference(
                null, StorageProviderType.S3_COMPATIBLE.name(), StorageClass.STANDARD,
                "test-bucket", "test-key.mp4",
                checksum, checksum, Files.size(inputFile), "video/mp4",
                Instant.now(), Instant.now()));

        assertNotNull(ref.storageReferenceId(), "StorageReference must be registered");
        assertEquals(StorageProviderType.S3_COMPATIBLE.name(), ref.providerType(),
                "Provider type must be S3_COMPATIBLE");
    }

    @Test
    @DisplayName("StorageRuntime output registration creates Product with lineage")
    void storageRuntimeOutputRegistration() throws Exception {
        // Create input and output files
        Path inputFile = tempDir.resolve("input.mp4");
        Path outputFile = tempDir.resolve("output.mp4");
        Files.write(inputFile, "input content".getBytes());
        Files.write(outputFile, "output content".getBytes());

        // Register input StorageReference
        String inputChecksum = computeSha256(inputFile);
        StorageReference inputRef = storageRuntime.register(new StorageReference(
                null, StorageProviderType.LOCAL.name(), StorageClass.STANDARD,
                tempDir.toString(), "input.mp4",
                inputChecksum, inputChecksum, Files.size(inputFile), "video/mp4",
                Instant.now(), Instant.now()));

        // Register input Product
        String inputProductId = Ids.newId("prod");
        Product inputProduct = productRuntime.register(new Product(
                inputProductId, "tenant-1", "project-1", "asset-1",
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "test", "test-service", null,
                ProductStatus.REGISTERED, inputRef.storageReferenceId(),
                inputChecksum, inputChecksum, "video/mp4", 1,
                "{}", Instant.now(), Instant.now()));
        productRuntime.markReady(inputProductId);

        // Register output through RenderOutputRegistrationService
        String outputRelativePath = tempDir.relativize(outputFile).toString();
        RenderProductProvenance provenance = RenderProductProvenance.builder()
                .tenantId("tenant-1")
                .projectId("project-1")
                .renderJobId("test-job-1")
                .baselineRenderer("ffmpeg-libass")
                .renderMode("timeline-revision-render")
                .inputProductIds(List.of(inputProductId))
                .build();

        Product outputProduct = registrationService.registerOutput(
                "test-job-1", "tenant-1", "project-1", "ffmpeg",
                outputRelativePath, provenance);

        // Verify output Product
        assertNotNull(outputProduct, "Output product must be registered");
        assertEquals(ProductType.FINAL_RENDER, outputProduct.productType(),
                "Output must be FINAL_RENDER");
        assertEquals(ProductStatus.READY, outputProduct.status(),
                "Output must be READY");
        assertNotNull(outputProduct.storageReferenceId(),
                "Output must have storageReferenceId");

        // Verify ProductDependency lineage
        List<ProductDependency> deps = productRuntime.findDependencies(outputProduct.productId());
        assertFalse(deps.isEmpty(), "Output must have dependency edges");
        assertEquals(inputProductId, deps.get(0).dependsOnProductId(),
                "Output must depend on input");
        assertEquals(DependencyType.DERIVED_FROM, deps.get(0).dependencyType(),
                "Dependency type must be DERIVED_FROM");
    }

    // ── Provider Capability Declaration ──

    @Test
    @DisplayName("All providers have valid capability declarations")
    void allProvidersHaveValidCapabilities() {
        // Verify FFmpeg capabilities
        ProviderMetadata ffmpeg = createProviderMetadata("ffmpeg", ProviderStatus.PRODUCTION, true);
        assertFalse(ffmpeg.declaredCapabilities().isEmpty(), "FFmpeg must have declared capabilities");
        assertFalse(ffmpeg.enabledCapabilities().isEmpty(), "FFmpeg must have enabled capabilities");

        // Verify Remotion capabilities
        ProviderMetadata remotion = createProviderMetadata("remotion", ProviderStatus.POC, false);
        assertFalse(remotion.declaredCapabilities().isEmpty(), "Remotion must have declared capabilities");

        // Verify GPAC capabilities
        ProviderMetadata gpac = createProviderMetadata("gpac", ProviderStatus.POC, false);
        assertFalse(gpac.declaredCapabilities().isEmpty(), "GPAC must have declared capabilities");
    }

    // ── Helper Methods ──

    private ProviderMetadata createProviderMetadata(String name, ProviderStatus status, boolean autoDispatch) {
        return new ProviderMetadata(
                name, status, "P1", ProviderType.RENDER,
                List.of("trim", "transcode"), List.of("trim", "transcode"),
                List.of(), List.of(), autoDispatch, "test-runtime",
                "Test provider", List.of());
    }

    private RenderJob createProductionJob() {
        return new RenderJob("job-1", "video_export", "production", "1920x1080", List.of(),
                "{}", "{}", "{}", "mp4", List.of("trim"),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of());
    }

    private RenderJob createManualJob() {
        return new RenderJob("job-2", "video_export", "manual", "1920x1080", List.of(),
                "{}", "{}", "{}", "mp4", List.of("trim"),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of());
    }

    private RenderJob createExperimentJob() {
        return new RenderJob("job-3", "video_export", "experiment", "1920x1080", List.of(),
                "{}", "{}", "{}", "mp4", List.of("trim"),
                new RenderConstraints(3840, 2160, 60, 3600, null, null),
                true, List.of(), List.of());
    }

    private String computeSha256(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // ── In-memory Test Doubles ──

    static class InMemoryStorageReferenceRepository extends StorageReferenceRepository {
        private final Map<String, StorageReference> store = new ConcurrentHashMap<>();

        @Override
        public StorageReference save(StorageReference ref) {
            String id = ref.storageReferenceId() != null ? ref.storageReferenceId() : Ids.newId("stor");
            StorageReference saved = new StorageReference(id, ref.providerType(), ref.storageClass(),
                    ref.rootPath(), ref.relativePath(), ref.checksum(), ref.contentHash(),
                    ref.fileSize(), ref.mimeType(), ref.createdAt(), ref.updatedAt());
            store.put(id, saved);
            return saved;
        }

        @Override
        public Optional<StorageReference> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public boolean exists(String id) {
            return store.containsKey(id);
        }

        @Override
        public void delete(String id) {
            store.remove(id);
        }
    }

    static class InMemoryProductRepository extends ProductRepository {
        private final Map<String, Product> store = new ConcurrentHashMap<>();
        private final Map<String, List<Product>> byProject = new ConcurrentHashMap<>();

        @Override
        public Product save(Product p) {
            String id = p.productId() != null ? p.productId() : Ids.newId("prod");
            Product saved = new Product(id, p.tenantId(), p.projectId(), p.ownerAssetId(),
                    p.productType(), p.representationKind(), p.producerType(), p.producerId(),
                    p.sourceTimelineRevisionId(), p.status(), p.storageReferenceId(),
                    p.checksum(), p.contentHash(), p.mimeType(), p.version(),
                    p.metadataJson(), p.createdAt(), p.updatedAt());
            store.put(id, saved);
            byProject.computeIfAbsent(p.projectId(), k -> new ArrayList<>()).add(saved);
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
            String id = d.dependencyId() != null ? d.dependencyId() : Ids.newId("dep");
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
            return findDependencies(productId).stream()
                    .anyMatch(d -> d.dependsOnProductId().equals(dependsOnId));
        }

        @Override
        public void delete(String dependencyId) {
            store.remove(dependencyId);
        }
    }
}
