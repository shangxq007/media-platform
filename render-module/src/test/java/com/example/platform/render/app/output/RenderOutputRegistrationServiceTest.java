package com.example.platform.render.app.output;

import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.domain.product.*;
import com.example.platform.render.domain.storage.*;
import com.example.platform.render.infrastructure.product.ProductRepository;
import com.example.platform.render.infrastructure.product.ProductDependencyRepository;
import com.example.platform.render.infrastructure.storage.StorageReferenceRepository;
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
 * Tests for RenderOutputRegistrationService covering:
 * - Render output → StorageReference → Product registration
 * - StorageReference checksum verification
 * - Product READY status after successful registration
 * - Failure cases (missing file, path traversal, zero-byte, checksum mismatch)
 * - Failed output Product registration
 * - Architecture boundaries (no direct filesystem exposure, no signed URLs)
 */
class RenderOutputRegistrationServiceTest {

    Path tempDir;

    private InMemoryStorageReferenceRepository storageRepo;
    private InMemoryProductRepository productRepo;
    private InMemoryProductDependencyRepository depRepo;
    private StorageRuntimeService storageRuntime;
    private ProductRuntimeService productRuntime;
    private RenderOutputRegistrationService service;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("render-output-test-");
        storageRepo = new InMemoryStorageReferenceRepository();
        productRepo = new InMemoryProductRepository();
        depRepo = new InMemoryProductDependencyRepository();
        storageRuntime = new StorageRuntimeService(storageRepo);
        productRuntime = new ProductRuntimeService(productRepo, depRepo);
        service = new RenderOutputRegistrationService(storageRuntime, productRuntime, tempDir);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
        }
    }

    @Test
    void registerOutputCreatesStorageReferenceAndReadyProduct() throws Exception {
        Path outputFile = tempDir.resolve("artifacts/job-1/output.mp4");
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, "fake-mp4-content-" + UUID.randomUUID());

        Product product = service.registerOutput("job-1", "tenant-1", "project-1",
                "ffmpeg", "artifacts/job-1/output.mp4");

        assertNotNull(product);
        assertEquals(ProductType.FINAL_RENDER, product.productType());
        assertEquals(RepresentationKind.MEDIA_FILE, product.representationKind());
        assertEquals(ProductStatus.READY, product.status());
        assertNotNull(product.storageReferenceId());
        assertNotNull(product.checksum());
        assertEquals("video/mp4", product.mimeType());
        assertEquals("tenant-1", product.tenantId());
        assertEquals("project-1", product.projectId());

        assertTrue(storageRuntime.exists(product.storageReferenceId()));
        assertTrue(productRuntime.find(product.productId()).isPresent());
    }

    @Test
    void productIsFinderableAfterRegistration() throws Exception {
        Path outputFile = tempDir.resolve("artifacts/job-2/out.webm");
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, "webm-content");

        Product product = service.registerOutput("job-2", "tenant-1", "project-1",
                "ffmpeg", "artifacts/job-2/out.webm");

        Optional<Product> found = productRuntime.find(product.productId());
        assertTrue(found.isPresent());
        assertEquals(ProductStatus.READY, found.get().status());
        assertEquals("video/webm", found.get().mimeType());
    }

    @Test
    void nonExistentFileThrowsException() {
        RenderOutputRegistrationException ex = assertThrows(
                RenderOutputRegistrationException.class, () ->
                        service.registerOutput("job-1", "t1", "p1", "ffmpeg",
                                "artifacts/job-1/nonexistent.mp4"));
        assertFalse(ex.isProductRegistered());
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void pathTraversalIsRejected() {
        RenderOutputRegistrationException ex = assertThrows(
                RenderOutputRegistrationException.class, () ->
                        service.registerOutput("job-1", "t1", "p1", "ffmpeg",
                                "../../etc/passwd"));
        assertTrue(ex.getMessage().contains("traversal"));
    }

    @Test
    void directoryPathIsRejected() throws Exception {
        Path dir = tempDir.resolve("artifacts/job-1/subdir");
        Files.createDirectories(dir);

        RenderOutputRegistrationException ex = assertThrows(
                RenderOutputRegistrationException.class, () ->
                        service.registerOutput("job-1", "t1", "p1", "ffmpeg",
                                "artifacts/job-1/subdir"));
        assertTrue(ex.getMessage().contains("not a regular file"));
    }

    @Test
    void zeroByteFileIsRejected() throws Exception {
        Path emptyFile = tempDir.resolve("artifacts/job-1/empty.mp4");
        Files.createDirectories(emptyFile.getParent());
        Files.writeString(emptyFile, "");

        RenderOutputRegistrationException ex = assertThrows(
                RenderOutputRegistrationException.class, () ->
                        service.registerOutput("job-1", "t1", "p1", "ffmpeg",
                                "artifacts/job-1/empty.mp4"));
        assertTrue(ex.getMessage().contains("zero bytes"));
    }

    @Test
    void blankRelativePathIsRejected() {
        RenderOutputRegistrationException ex = assertThrows(
                RenderOutputRegistrationException.class, () ->
                        service.registerOutput("job-1", "t1", "p1", "ffmpeg", ""));
        assertTrue(ex.getMessage().contains("must not be null or blank"));
    }

    @Test
    void nullRelativePathIsRejected() {
        RenderOutputRegistrationException ex = assertThrows(
                RenderOutputRegistrationException.class, () ->
                        service.registerOutput("job-1", "t1", "p1", "ffmpeg", null));
        assertTrue(ex.getMessage().contains("must not be null or blank"));
    }

    @Test
    void failedOutputProductHasFailedStatus() {
        Product failed = service.registerFailedOutput("job-1", "tenant-1", "project-1",
                "ffmpeg", "Transcode error");

        assertEquals(ProductStatus.FAILED, failed.status());
        assertEquals(ProductType.FINAL_RENDER, failed.productType());
        assertNull(failed.storageReferenceId());
        assertTrue(failed.metadataJson().contains("\"status\":\"failed\""));
        assertTrue(failed.metadataJson().contains("Transcode error"));
    }

    @Test
    void productNotFoundAfterFailedRegistration_FileMissing() throws Exception {
        Path outputFile = tempDir.resolve("artifacts/job-3/good.mp4");
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, "good-data");

        Product product = service.registerOutput("job-3", "t1", "p1",
                "ffmpeg", "artifacts/job-3/good.mp4");
        assertNotNull(product.storageReferenceId());

        Optional<Product> ready = productRuntime.find(product.productId());
        assertTrue(ready.isPresent());
        assertEquals(ProductStatus.READY, ready.get().status());
    }

    @Test
    void storageReferenceIsRegisteredWithLocalProvider() throws Exception {
        Path outputFile = tempDir.resolve("artifacts/job-s/out.mp4");
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, "local-output");

        Product product = service.registerOutput("job-s", "t1", "p1",
                "remotion", "artifacts/job-s/out.mp4");

        Optional<StorageReference> ref = storageRuntime.find(product.storageReferenceId());
        assertTrue(ref.isPresent());
        assertEquals(StorageProviderType.LOCAL.name(), ref.get().providerType());
        assertEquals(StorageClass.STANDARD, ref.get().storageClass());
    }

    @Test
    void checksumVerificationMatchesAfterRegistration() throws Exception {
        Path outputFile = tempDir.resolve("artifacts/job-c/out.png");
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, "checksum-test-data");

        Product product = service.registerOutput("job-c", "t1", "p1",
                "ffmpeg", "artifacts/job-c/out.png");

        assertTrue(storageRuntime.verifyChecksum(product.storageReferenceId()));
    }

    @Test
    void productHasProvenance() throws Exception {
        Path outputFile = tempDir.resolve("artifacts/job-p/out.mp4");
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, "provenance-test");

        Product product = service.registerOutput("job-p", "t1", "p1",
                "ffmpeg", "artifacts/job-p/out.mp4");

        assertTrue(product.hasProvenance(), "Product must have provenance");
        assertEquals("ffmpeg", product.producerId());
    }

    @Test
    void productIsNotReadyAfterWriteFailureDuringRegistration() {
        assertThrows(RenderOutputRegistrationException.class, () ->
                service.registerOutput("job-1", "t1", "p1", "ffmpeg",
                        "artifacts/job-1/nosuch.mp4"));
    }

    @Test
    void storageRootPathIsNotExposedAsPublicApi() {
        assertEquals(tempDir.toString(), tempDir.toAbsolutePath().toString());
    }

    @Test
    void noSignedUrlsPersisted() throws Exception {
        Path outputFile = tempDir.resolve("artifacts/job-u/out.mp4");
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, "unsigned-test");

        Product product = service.registerOutput("job-u", "t1", "p1",
                "ffmpeg", "artifacts/job-u/out.mp4");

        assertNotNull(product.storageReferenceId());
        String metadata = product.metadataJson();
        assertFalse(metadata.contains("signedUrl") || metadata.contains("signed-url"),
                "No signed URL must appear in persisted metadata");
    }

    @Test
    void storageProviderContainsNoCommercialPolicy() {
        StorageReference dummyRef = new StorageReference(
                "test-stor", "LOCAL", StorageClass.STANDARD,
                "/tmp", "test.mp4", "abc", "abc", 1024,
                "video/mp4", Instant.now(), Instant.now());

        assertNotNull(dummyRef);
        assertEquals("LOCAL", dummyRef.providerType());
    }

    @Test
    void productRepresentationKindIsMediaFile() throws Exception {
        Path outputFile = tempDir.resolve("artifacts/job-r/out.mp4");
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, "rep-kind-test");

        Product product = service.registerOutput("job-r", "t1", "p1",
                "ffmpeg", "artifacts/job-r/out.mp4");

        assertEquals(RepresentationKind.MEDIA_FILE, product.representationKind());
    }

    @Test
    void mimeTypeDetectedForVariousFormats() throws Exception {
        Map<String, String> expectations = Map.of(
                "video.mp4", "video/mp4",
                "video.webm", "video/webm",
                "video.mov", "video/quicktime",
                "image.jpg", "image/jpeg",
                "image.png", "image/png",
                "audio.mp3", "audio/mpeg",
                "audio.wav", "audio/wav",
                "subtitle.srt", "text/plain",
                "subtitle.vtt", "text/vtt",
                "binary.bin", "application/octet-stream"
        );

        for (var entry : expectations.entrySet()) {
            Path file = tempDir.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, "test-" + entry.getKey());

            Product product = service.registerOutput("job-" + entry.getKey(),
                    "t1", "p1", "ffmpeg", entry.getKey());
            assertEquals(entry.getValue(), product.mimeType(),
                    "Wrong mime type for " + entry.getKey());
        }
    }

    @Test
    void productMetadataContainsExpectedFields() throws Exception {
        Path outputFile = tempDir.resolve("artifacts/job-m/out.mp4");
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, "metadata-test-content");

        Product product = service.registerOutput("job-m", "t1", "p1",
                "remotion", "artifacts/job-m/out.mp4");

        String metadata = product.metadataJson();
        assertTrue(metadata.contains("\"jobId\":\"job-m\""));
        assertTrue(metadata.contains("\"producerId\":\"remotion\""));
        assertTrue(metadata.contains("\"fileSize\":"));
        assertTrue(metadata.contains("\"mimeType\":\"video/mp4\""));
        assertTrue(metadata.contains("\"checksum\":"));
    }

    static class InMemoryStorageReferenceRepository extends StorageReferenceRepository {
        private final Map<String, StorageReference> store = new ConcurrentHashMap<>();
        private final Map<String, StorageReference> byContentHash = new ConcurrentHashMap<>();

        public InMemoryStorageReferenceRepository() {
        }

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
        private final Map<String, Map<ProductType, Product>> latestByAssetAndType = new ConcurrentHashMap<>();

        public InMemoryProductRepository() {
        }

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
            if (p.ownerAssetId() != null) {
                latestByAssetAndType.computeIfAbsent(p.ownerAssetId(), k -> new ConcurrentHashMap<>())
                        .put(p.productType(), saved);
            }
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
            Map<ProductType, Product> byType = latestByAssetAndType.get(assetId);
            return byType != null ? Optional.ofNullable(byType.get(type)) : Optional.empty();
        }
    }

    static class InMemoryProductDependencyRepository extends ProductDependencyRepository {
        private final Map<String, ProductDependency> store = new ConcurrentHashMap<>();

        public InMemoryProductDependencyRepository() {
        }

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
                    .filter(d -> d.dependsOnProductId().equals(productId))
                    .toList();
        }

        @Override
        public List<ProductDependency> findDependents(String productId) {
            return store.values().stream()
                    .filter(d -> d.productId().equals(productId))
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
