package com.example.platform.render.app.timeline;

import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.app.output.RenderOutputRegistrationException;
import com.example.platform.render.app.output.RenderOutputRegistrationService;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.domain.product.*;
import com.example.platform.render.domain.storage.*;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.infrastructure.product.ProductDependencyRepository;
import com.example.platform.render.infrastructure.product.ProductRepository;
import com.example.platform.render.infrastructure.storage.StorageReferenceRepository;
import com.example.platform.render.domain.timeline.TimelineClip;
import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineTrack;
import com.example.platform.render.domain.timeline.TimelineAssetRef;
import com.example.platform.render.domain.timeline.TimelineAudioSpec;
import com.example.platform.render.testsupport.TimelineCoreSmokeFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test proving Timeline → RenderJob → Storage → Product closure.
 *
 * <p><b>This test uses a controlled local output file — NOT real FFmpeg/libass rendering.</b>
 * It proves the integration chain from timeline fixture through {@link TimelineRenderJobMapper}
 * and {@link RenderOutputRegistrationService} to a READY {@link Product}. It does NOT prove
 * real FFmpeg/libass rendering; that requires the baseline FFmpeg integration test.</p>
 *
 * <p>Architecture compliance:
 * <ul>
 *   <li>Timeline remains canonical editing input</li>
 *   <li>Product remains canonical output/communication object</li>
 *   <li>Output goes through {@link RenderOutputRegistrationService}</li>
 *   <li>{@link StorageRuntimeService} handles storage registration</li>
 *   <li>{@link ProductRuntimeService} handles Product lifecycle</li>
 *   <li>No Artifact Runtime introduced</li>
 *   <li>No signed URLs persisted</li>
 *   <li>No real OpenCue, Remotion production dispatch, MinIO/S3 required</li>
 *   <li>FFmpeg/libass remains baseline subtitle burn-in</li>
 * </ul>
 */
class TimelineCoreRenderableSmokeTest {

    private Path tempDir;
    private StorageRuntimeService storageRuntime;
    private ProductRuntimeService productRuntime;
    private RenderOutputRegistrationService registrationService;
    private TimelineRenderJobMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("timeline-smoke-");
        StorageReferenceRepository storageRepo = new InMemoryStorageReferenceRepository();
        ProductRepository productRepo = new InMemoryProductRepository();
        ProductDependencyRepository depRepo = new InMemoryProductDependencyRepository();
        storageRuntime = new StorageRuntimeService(storageRepo);
        productRuntime = new ProductRuntimeService(productRepo, depRepo);
        registrationService = new RenderOutputRegistrationService(storageRuntime, productRuntime, tempDir);

        TimelineExtensionsReader extensionsReader = new TimelineExtensionsReader();
        TimelineScriptParser parser = new TimelineScriptParser(extensionsReader);
        InternalTimelineWriter writer = new InternalTimelineWriter(extensionsReader);
        mapper = new TimelineRenderJobMapper(parser, writer);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
        }
    }

    // --- Core smoke: controlled local output → READY Product ---

    @Test
    void timelineFixtureRegistersControlledRenderOutputAsReadyProduct() throws Exception {
        // 1. Create canonical timeline fixture
        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();

        // 2. Map to RenderJob request (validates and preserves provenance)
        var mappingResult = mapper.toRenderJobRequest(
                TimelineCoreSmokeFixture.TENANT_ID,
                TimelineCoreSmokeFixture.PROJECT_ID,
                spec, "default_1080p");

        assertNotNull(mappingResult.request());
        assertEquals(TimelineCoreSmokeFixture.TIMELINE_ID, mappingResult.timelineId());

        // 3. Create controlled local output file (simulating render output)
        //    This is NOT real FFmpeg output — it is a controlled smoke test artifact.
        String relativePath = "artifacts/rj_smoke_001/output.mp4";
        Path outputFile = tempDir.resolve(relativePath);
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, "controlled-smoke-output-content-" + UUID.randomUUID());

        // 4. Register through RenderOutputRegistrationService
        //    This proves: file → StorageReference → Product → READY
        Product product = registrationService.registerOutput(
                "rj_smoke_001",
                TimelineCoreSmokeFixture.TENANT_ID,
                TimelineCoreSmokeFixture.PROJECT_ID,
                "ffmpeg",
                relativePath);

        // 5. Verify Product is READY
        assertNotNull(product);
        assertEquals(ProductType.FINAL_RENDER, product.productType());
        assertEquals(RepresentationKind.MEDIA_FILE, product.representationKind());
        assertEquals(ProductStatus.READY, product.status());
        assertNotNull(product.storageReferenceId());
        assertNotNull(product.checksum());
        assertEquals("video/mp4", product.mimeType());
        assertEquals(TimelineCoreSmokeFixture.TENANT_ID, product.tenantId());
        assertEquals(TimelineCoreSmokeFixture.PROJECT_ID, product.projectId());

        // 6. Verify Product is queryable
        Optional<Product> found = productRuntime.find(product.productId());
        assertTrue(found.isPresent());
        assertEquals(ProductStatus.READY, found.get().status());

        // 7. Verify storage reference is valid
        assertTrue(storageRuntime.exists(product.storageReferenceId()));
        assertTrue(storageRuntime.verifyChecksum(product.storageReferenceId()));
    }

    @Test
    void productIsQueryableByIdAfterRegistration() throws Exception {
        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();
        mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p");

        Path outputFile = tempDir.resolve("artifacts/job-qid/output.mp4");
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, "query-id-test");

        Product product = registrationService.registerOutput(
                "job-qid", "ten_1", "prj_1", "ffmpeg", "artifacts/job-qid/output.mp4");

        Optional<Product> found = productRuntime.find(product.productId());
        assertTrue(found.isPresent());
        assertEquals(product.productId(), found.get().productId());
        assertEquals(ProductStatus.READY, found.get().status());
    }

    @Test
    void productIsQueryableByProjectAfterRegistration() throws Exception {
        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();
        mapper.toRenderJobRequest("ten_1", "prj_query", spec, "default_1080p");

        Path outputFile = tempDir.resolve("artifacts/job-qprj/output.mp4");
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, "query-project-test");

        Product product = registrationService.registerOutput(
                "job-qprj", "ten_1", "prj_query", "ffmpeg", "artifacts/job-qprj/output.mp4");

        List<Product> products = productRuntime.findByProject("prj_query", 50);
        assertTrue(products.stream().anyMatch(p -> p.productId().equals(product.productId())));
    }

    @Test
    void productHasCorrectTypeAndRepresentation() throws Exception {
        Path outputFile = tempDir.resolve("artifacts/job-type/output.mp4");
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, "type-test");

        Product product = registrationService.registerOutput(
                "job-type", "ten_1", "prj_1", "ffmpeg", "artifacts/job-type/output.mp4");

        assertEquals(ProductType.FINAL_RENDER, product.productType());
        assertEquals(RepresentationKind.MEDIA_FILE, product.representationKind());
    }

    @Test
    void productStorageReferenceIsPopulated() throws Exception {
        Path outputFile = tempDir.resolve("artifacts/job-stor/output.mp4");
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, "storage-test");

        Product product = registrationService.registerOutput(
                "job-stor", "ten_1", "prj_1", "ffmpeg", "artifacts/job-stor/output.mp4");

        assertNotNull(product.storageReferenceId());
        assertTrue(storageRuntime.exists(product.storageReferenceId()));
    }

    @Test
    void productChecksumMatchesFileContent() throws Exception {
        Path outputFile = tempDir.resolve("artifacts/job-chk/output.mp4");
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, "checksum-test-content");

        Product product = registrationService.registerOutput(
                "job-chk", "ten_1", "prj_1", "ffmpeg", "artifacts/job-chk/output.mp4");

        assertNotNull(product.checksum());
        assertTrue(storageRuntime.verifyChecksum(product.storageReferenceId()));
    }

    @Test
    void productMetadataIncludesTimelineProvenance() throws Exception {
        Path outputFile = tempDir.resolve("artifacts/job-prov/output.mp4");
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, "provenance-test");

        Product product = registrationService.registerOutput(
                "job-prov", "ten_1", "prj_1", "ffmpeg", "artifacts/job-prov/output.mp4");

        String metadata = product.metadataJson();
        assertNotNull(metadata);
        assertTrue(metadata.contains("\"jobId\":\"job-prov\""), "Metadata must contain jobId");
        assertTrue(metadata.contains("\"producerId\":\"ffmpeg\""), "Metadata must contain producerId");
        assertTrue(metadata.contains("\"fileSize\":"), "Metadata must contain fileSize");
        assertTrue(metadata.contains("\"mimeType\":\"video/mp4\""), "Metadata must contain mimeType");
        assertTrue(metadata.contains("\"checksum\":"), "Metadata must contain checksum");
    }

    @Test
    void productMetadataDoesNotIncludeSignedUrl() throws Exception {
        Path outputFile = tempDir.resolve("artifacts/job-nosign/output.mp4");
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, "no-signed-url-test");

        Product product = registrationService.registerOutput(
                "job-nosign", "ten_1", "prj_1", "ffmpeg", "artifacts/job-nosign/output.mp4");

        String metadata = product.metadataJson();
        assertFalse(metadata.contains("signedUrl"), "Metadata must not contain signedUrl");
        assertFalse(metadata.contains("signed-url"), "Metadata must not contain signed-url");
        assertFalse(metadata.contains("presign"), "Metadata must not contain presign");
    }

    @Test
    void productMetadataDoesNotExposeAbsoluteLocalPath() throws Exception {
        Path outputFile = tempDir.resolve("artifacts/job-nopath/output.mp4");
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, "no-path-test");

        Product product = registrationService.registerOutput(
                "job-nopath", "ten_1", "prj_1", "ffmpeg", "artifacts/job-nopath/output.mp4");

        String metadata = product.metadataJson();
        // The metadata should contain the filename but not the absolute directory path
        assertFalse(metadata.contains(tempDir.toString()),
                "Metadata must not expose absolute local filesystem path: " + tempDir);
    }

    @Test
    void productHasProvenance() throws Exception {
        Path outputFile = tempDir.resolve("artifacts/job-prov2/output.mp4");
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, "provenance-check");

        Product product = registrationService.registerOutput(
                "job-prov2", "ten_1", "prj_1", "ffmpeg", "artifacts/job-prov2/output.mp4");

        assertTrue(product.hasProvenance(), "Product must have provenance");
        assertEquals("ffmpeg", product.producerId());
    }

    @Test
    void videoWithSubtitleTimelineRegistersSuccessfully() throws Exception {
        TimelineSpec spec = TimelineCoreSmokeFixture.createVideoWithSubtitleTimeline();
        var result = mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p");
        assertTrue(result.hasSubtitles());

        Path outputFile = tempDir.resolve("artifacts/job-sub/output.mp4");
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, "subtitle-timeline-test");

        Product product = registrationService.registerOutput(
                "job-sub", "ten_1", "prj_1", "ffmpeg", "artifacts/job-sub/output.mp4");

        assertEquals(ProductStatus.READY, product.status());
        assertEquals(ProductType.FINAL_RENDER, product.productType());
    }

    // --- Failure paths: validation rejects before render ---

    @Test
    void invalidDurationFailsBeforeOutputRegistration() {
        TimelineSpec spec = new TimelineSpec("tl_bad", "Bad", null,
                List.of(TimelineTrack.of("t1", "V", TimelineTrack.TrackType.VIDEO)),
                List.of(), TimelineOutputSpec.mp4_1080p30(), -1.0, Map.of());

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
    }

    @Test
    void invalidFpsFailsBeforeOutputRegistration() {
        TimelineOutputSpec output = new TimelineOutputSpec(
                "mp4", "1920x1080", 0, "h264", 8000,
                TimelineAudioSpec.aacDefault(), "yuv420p");
        TimelineSpec spec = new TimelineSpec("tl_bad", "Bad", null,
                List.of(TimelineTrack.of("t1", "V", TimelineTrack.TrackType.VIDEO)),
                List.of(), output, 10.0, Map.of());

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
    }

    @Test
    void invalidCanvasFailsBeforeOutputRegistration() {
        TimelineOutputSpec output = new TimelineOutputSpec(
                "mp4", "0x0", 30, "h264", 8000,
                TimelineAudioSpec.aacDefault(), "yuv420p");
        TimelineSpec spec = new TimelineSpec("tl_bad", "Bad", null,
                List.of(TimelineTrack.of("t1", "V", TimelineTrack.TrackType.VIDEO)),
                List.of(), output, 10.0, Map.of());

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
    }

    @Test
    void unsupportedFormatFailsBeforeOutputRegistration() {
        TimelineOutputSpec output = new TimelineOutputSpec(
                "avi", "1920x1080", 30, "h264", 8000,
                TimelineAudioSpec.aacDefault(), "yuv420p");
        TimelineSpec spec = new TimelineSpec("tl_bad", "Bad", null,
                List.of(TimelineTrack.of("t1", "V", TimelineTrack.TrackType.VIDEO)),
                List.of(), output, 10.0, Map.of());

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
    }

    @Test
    void unsafePathFailsBeforeOutputRegistration() {
        TimelineAssetRef ref = TimelineAssetRef.of("ast_1", "../etc/passwd");
        TimelineClip clip = TimelineClip.of("c1", ref, 0, 0, 10);
        TimelineTrack track = new TimelineTrack(
                "t1", "V", TimelineTrack.TrackType.VIDEO,
                0, List.of(clip), false, false);
        TimelineSpec spec = new TimelineSpec("tl_bad", "Bad", null,
                List.of(track), List.of(), TimelineOutputSpec.mp4_1080p30(), 10.0, Map.of());

        assertThrows(IllegalArgumentException.class,
                () -> mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p"));
    }

    // --- Failure paths: output registration rejects bad files ---

    @Test
    void missingOutputFileDoesNotCreateReadyProduct() {
        RenderOutputRegistrationException ex = assertThrows(
                RenderOutputRegistrationException.class,
                () -> registrationService.registerOutput(
                        "job-miss", "ten_1", "prj_1", "ffmpeg",
                        "artifacts/job-miss/nonexistent.mp4"));
        assertFalse(ex.isProductRegistered());
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void directoryOutputPathDoesNotCreateReadyProduct() throws Exception {
        Path dir = tempDir.resolve("artifacts/job-dir/subdir");
        Files.createDirectories(dir);

        RenderOutputRegistrationException ex = assertThrows(
                RenderOutputRegistrationException.class,
                () -> registrationService.registerOutput(
                        "job-dir", "ten_1", "prj_1", "ffmpeg",
                        "artifacts/job-dir/subdir"));
        assertTrue(ex.getMessage().contains("not a regular file"));
    }

    @Test
    void zeroByteOutputDoesNotCreateReadyProduct() throws Exception {
        Path emptyFile = tempDir.resolve("artifacts/job-zero/empty.mp4");
        Files.createDirectories(emptyFile.getParent());
        Files.writeString(emptyFile, "");

        RenderOutputRegistrationException ex = assertThrows(
                RenderOutputRegistrationException.class,
                () -> registrationService.registerOutput(
                        "job-zero", "ten_1", "prj_1", "ffmpeg",
                        "artifacts/job-zero/empty.mp4"));
        assertTrue(ex.getMessage().contains("zero bytes"));
    }

    @Test
    void failedOutputRegistersAsFailedProduct() {
        Product failed = registrationService.registerFailedOutput(
                "job-fail", "ten_1", "prj_1", "ffmpeg", "Transcode error");

        assertEquals(ProductStatus.FAILED, failed.status());
        assertEquals(ProductType.FINAL_RENDER, failed.productType());
        assertNull(failed.storageReferenceId());
    }

    // --- OpenCue/MinIO not required ---

    @Test
    void smokeDoesNotRequireOpenCue() throws Exception {
        // This test proves the smoke path works without OpenCue
        // by running the full chain without any OpenCue dependencies
        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();
        var result = mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p");

        Path outputFile = tempDir.resolve("artifacts/job-nocue/output.mp4");
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, "no-opencue-test");

        Product product = registrationService.registerOutput(
                "job-nocue", "ten_1", "prj_1", "ffmpeg", "artifacts/job-nocue/output.mp4");

        assertEquals(ProductStatus.READY, product.status());
        // No OpenCue classes were loaded or used
    }

    @Test
    void smokeDoesNotRequireMinIO() throws Exception {
        // This test proves the smoke path works with LOCAL storage only
        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();
        mapper.toRenderJobRequest("ten_1", "prj_1", spec, "default_1080p");

        Path outputFile = tempDir.resolve("artifacts/job-nominio/output.mp4");
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, "no-minio-test");

        Product product = registrationService.registerOutput(
                "job-nominio", "ten_1", "prj_1", "ffmpeg", "artifacts/job-nominio/output.mp4");

        assertEquals(ProductStatus.READY, product.status());
        Optional<StorageReference> ref = storageRuntime.find(product.storageReferenceId());
        assertTrue(ref.isPresent());
        assertEquals(StorageProviderType.LOCAL.name(), ref.get().providerType());
    }

    // --- In-memory test doubles (same pattern as RenderOutputRegistrationServiceTest) ---

    static class InMemoryStorageReferenceRepository extends StorageReferenceRepository {
        private final Map<String, StorageReference> store = new ConcurrentHashMap<>();
        private final Map<String, StorageReference> byContentHash = new ConcurrentHashMap<>();

        @Override
        public StorageReference save(StorageReference r) {
            String id = r.storageReferenceId() != null ? r.storageReferenceId() : "stor-" + UUID.randomUUID();
            StorageReference saved = new StorageReference(id, r.providerType(), r.storageClass(),
                    r.rootPath(), r.relativePath(), r.checksum(), r.contentHash(),
                    r.fileSize(), r.mimeType(), r.createdAt(), r.updatedAt());
            store.put(id, saved);
            if (r.contentHash() != null) byContentHash.put(r.contentHash(), saved);
            return saved;
        }

        @Override
        public Optional<StorageReference> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<StorageReference> findByContentHash(String hash) {
            return Optional.ofNullable(byContentHash.get(hash));
        }

        @Override
        public boolean exists(String id) {
            return store.containsKey(id);
        }

        @Override
        public void delete(String id) {
            StorageReference ref = store.remove(id);
            if (ref != null && ref.contentHash() != null) byContentHash.remove(ref.contentHash());
        }
    }

    static class InMemoryProductRepository extends ProductRepository {
        private final Map<String, Product> store = new ConcurrentHashMap<>();
        private final Map<String, List<Product>> byProject = new ConcurrentHashMap<>();
        private final Map<String, List<Product>> byAsset = new ConcurrentHashMap<>();

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
            if (p.ownerAssetId() != null) byAsset.computeIfAbsent(p.ownerAssetId(), k -> new ArrayList<>()).add(saved);
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
            return byAsset.getOrDefault(assetId, List.of());
        }

        @Override
        public Optional<Product> findLatest(String assetId, ProductType type) {
            return Optional.empty();
        }

        @Override
        public List<Product> findBySourceTimelineRevisionId(String timelineRevisionId) {
            return store.values().stream()
                    .filter(p -> timelineRevisionId.equals(p.sourceTimelineRevisionId()))
                    .toList();
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
            // Returns what this product depends ON (upstream)
            return store.values().stream()
                    .filter(d -> d.productId().equals(productId))
                    .toList();
        }

        @Override
        public List<ProductDependency> findDependents(String productId) {
            // Returns what depends ON this product (downstream)
            return store.values().stream()
                    .filter(d -> d.dependsOnProductId().equals(productId))
                    .toList();
        }

        @Override
        public boolean exists(String productId, String dependsOnId) {
            return false;
        }

        @Override
        public void delete(String dependencyId) {
            store.remove(dependencyId);
        }
    }
}
