package com.example.platform.render.app.product;

import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.domain.product.*;
import com.example.platform.render.domain.storage.StorageClass;
import com.example.platform.render.domain.storage.StorageReference;
import com.example.platform.render.infrastructure.product.ProductDependencyRepository;
import com.example.platform.render.infrastructure.product.ProductRepository;
import com.example.platform.render.infrastructure.storage.StorageReferenceRepository;
import com.example.platform.shared.Ids;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PreviewArtifactQueryService covering:
 * - Product lookup by ID with storage metadata
 * - Shallow vs deep dependency resolution
 * - Product lookup by project, asset, timeline revision
 * - Latest preview/asset product lookup
 * - Missing storage reference graceful handling
 * - Architecture boundaries (no paths, no signed URLs in response)
 */
class PreviewArtifactQueryServiceTest {
    @SuppressWarnings("unchecked")
    private static <T> org.springframework.beans.factory.ObjectProvider<T> mockProvider(T instance) {
        org.springframework.beans.factory.ObjectProvider<T> op = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(op.getIfAvailable()).thenReturn(instance);
        return op;
    }


    private InMemoryProductRepository productRepo;
    private InMemoryProductDependencyRepository depRepo;
    private InMemoryStorageReferenceRepository storageRepo;
    private ProductRuntimeService productRuntime;
    private StorageRuntimeService storageRuntime;
    private PreviewArtifactQueryService service;

    @BeforeEach
    void setUp() {
        productRepo = new InMemoryProductRepository();
        depRepo = new InMemoryProductDependencyRepository();
        storageRepo = new InMemoryStorageReferenceRepository();
        productRuntime = new ProductRuntimeService(productRepo, depRepo);
        storageRuntime = new StorageRuntimeService(storageRepo, mockProvider(null));
        service = new PreviewArtifactQueryService(productRuntime, storageRuntime);
    }

    // ─── Find by Product ID ───

    @Test
    void findByProductIdReturnsFullResponse() {
        StorageReference ref = registerStorageRef("video/mp4", 1024L, "abc123");
        Product product = createReadyProductWithStorage(
                "t1", "p1", ProductType.PREVIEW, ref.storageReferenceId());

        Optional<PreviewArtifactResponse> result = service.findByProductId(product.productId());

        assertTrue(result.isPresent());
        PreviewArtifactResponse r = result.get();
        assertEquals(product.productId(), r.productId());
        assertEquals("p1", r.projectId());
        assertEquals("PREVIEW", r.productType());
        assertEquals("READY", r.status());
        assertEquals("video/mp4", r.mimeType());
        assertEquals("MEDIA_FILE", r.representationKind());
        assertEquals(1024L, r.fileSize());
        assertEquals("abc123", r.checksum());
        assertEquals("abc123", r.contentHash());
        assertEquals("ffmpeg", r.producerType());
        assertEquals("ffmpeg", r.producerId());
        assertEquals(1, r.version());
        assertTrue(r.isReady());
        assertFalse(r.isFailed());
        assertTrue(r.hasStorageReference());
    }

    @Test
    void findByProductIdReturnsEmptyForMissingId() {
        Optional<PreviewArtifactResponse> result = service.findByProductId("nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    void findByProductIdIncludesDependencyCounts() {
        Product input1 = createReadyProduct("t1", "p1", ProductType.RAW_MEDIA, "in1");
        Product input2 = createReadyProduct("t1", "p1", ProductType.RAW_MEDIA, "in2");
        Product downstream = createReadyProduct("t1", "p1", ProductType.FINAL_RENDER, "down");

        StorageReference ref = registerStorageRef("video/mp4", 2048L, "def456");
        Product output = createReadyProductWithStorage(
                "t1", "p1", ProductType.FINAL_RENDER, ref.storageReferenceId());

        // Link output depends on input1, input2 (upstream for output)
        productRuntime.linkDependency(output.productId(), input1.productId(),
                DependencyType.DERIVED_FROM, "t1", "p1");
        productRuntime.linkDependency(output.productId(), input2.productId(),
                DependencyType.DERIVED_FROM, "t1", "p1");

        // Link downstream depends on output (downstream for output)
        productRuntime.linkDependency(downstream.productId(), output.productId(),
                DependencyType.DERIVED_FROM, "t1", "p1");

        Optional<PreviewArtifactResponse> result = service.findByProductId(output.productId());
        assertTrue(result.isPresent());

        PreviewArtifactResponse r = result.get();
        assertEquals(2, r.upstreamDependencyCount());
        assertEquals(1, r.downstreamDependentCount());
        assertTrue(r.hasDependencies());
        assertEquals(2, r.upstreamDependencyIds().size());
        assertTrue(r.upstreamDependencyIds().contains(input1.productId()));
        assertTrue(r.upstreamDependencyIds().contains(input2.productId()));
    }

    @Test
    void findByProductIdHandlesProductWithoutStorage() {
        Product product = createReadyProduct("t1", "p1", ProductType.PREVIEW, "nostor");

        Optional<PreviewArtifactResponse> result = service.findByProductId(product.productId());
        assertTrue(result.isPresent());

        PreviewArtifactResponse r = result.get();
        assertEquals(0L, r.fileSize());
        assertNull(r.checksum());
        assertNull(r.contentHash());
        assertFalse(r.hasStorageReference());
    }

    @Test
    void findByProductIdHandlesFailedProduct() {
        Product product = createFailedProduct("t1", "p1", ProductType.PREVIEW, "failed");

        Optional<PreviewArtifactResponse> result = service.findByProductId(product.productId());
        assertTrue(result.isPresent());

        PreviewArtifactResponse r = result.get();
        assertEquals("FAILED", r.status());
        assertTrue(r.isFailed());
        assertFalse(r.isReady());
    }

    // ─── Shallow vs Deep Lookup ───

    @Test
    void shallowLookupDoesNotPopulateDependencyIds() {
        Product input = createReadyProduct("t1", "p1", ProductType.RAW_MEDIA, "sh-in");

        StorageReference ref = registerStorageRef("video/mp4", 512L, "shallow123");
        Product output = createReadyProductWithStorage(
                "t1", "p1", ProductType.FINAL_RENDER, ref.storageReferenceId());

        productRuntime.linkDependency(output.productId(), input.productId(),
                DependencyType.DERIVED_FROM, "t1", "p1");

        Optional<PreviewArtifactResponse> result = service.findByProductIdShallow(output.productId());
        assertTrue(result.isPresent());

        PreviewArtifactResponse r = result.get();
        // Counts are still populated
        assertEquals(1, r.upstreamDependencyCount());
        // But IDs should be empty for shallow lookup
        assertTrue(r.upstreamDependencyIds().isEmpty());
    }

    // ─── Find Latest by Asset ───

    @Test
    void findLatestPreviewByAssetReturnsLatest() {
        StorageReference ref1 = registerStorageRef("video/mp4", 100L, "hash1");
        StorageReference ref2 = registerStorageRef("video/mp4", 200L, "hash2");

        // Create two preview products for the same asset
        createReadyProductWithStorageAndAsset("t1", "p1", "asset-1", ProductType.PREVIEW,
                ref1.storageReferenceId());
        Product latest = createReadyProductWithStorageAndAsset("t1", "p1", "asset-1",
                ProductType.PREVIEW, ref2.storageReferenceId());

        Optional<PreviewArtifactResponse> result = service.findLatestPreviewByAsset("asset-1");
        assertTrue(result.isPresent());
        assertEquals(latest.productId(), result.get().productId());
    }

    @Test
    void findLatestPreviewByAssetReturnsEmptyWhenNone() {
        Optional<PreviewArtifactResponse> result = service.findLatestPreviewByAsset("no-asset");
        assertTrue(result.isEmpty());
    }

    @Test
    void findLatestByAssetAndTypeRespectsType() {
        StorageReference ref = registerStorageRef("video/mp4", 300L, "type-hash");
        createReadyProductWithStorageAndAsset("t1", "p1", "asset-2", ProductType.FINAL_RENDER,
                ref.storageReferenceId());

        // Query for PREVIEW type (none registered) should return empty
        Optional<PreviewArtifactResponse> result =
                service.findLatestByAssetAndType("asset-2", ProductType.PREVIEW);
        assertTrue(result.isEmpty());

        // Query for FINAL_RENDER type should find it
        Optional<PreviewArtifactResponse> found =
                service.findLatestByAssetAndType("asset-2", ProductType.FINAL_RENDER);
        assertTrue(found.isPresent());
    }

    // ─── Find by Project ───

    @Test
    void findByProjectReturnsMultipleProducts() {
        StorageReference ref1 = registerStorageRef("video/mp4", 100L, "proj-hash1");
        StorageReference ref2 = registerStorageRef("image/png", 50L, "proj-hash2");

        createReadyProductWithStorage("t1", "proj-1", ProductType.PREVIEW, ref1.storageReferenceId());
        createReadyProductWithStorage("t1", "proj-1", ProductType.THUMBNAIL, ref2.storageReferenceId());

        List<PreviewArtifactResponse> results = service.findByProject("proj-1", 10);
        assertEquals(2, results.size());
    }

    @Test
    void findByProjectRespectsLimit() {
        for (int i = 0; i < 5; i++) {
            StorageReference ref = registerStorageRef("video/mp4", 100L, "limit-hash-" + i);
            createReadyProductWithStorage("t1", "proj-limit", ProductType.PREVIEW,
                    ref.storageReferenceId());
        }

        List<PreviewArtifactResponse> results = service.findByProject("proj-limit", 3);
        assertEquals(3, results.size());
    }

    @Test
    void findByProjectReturnsEmptyWhenNone() {
        List<PreviewArtifactResponse> results = service.findByProject("no-project", 10);
        assertTrue(results.isEmpty());
    }

    // ─── Find by Asset ───

    @Test
    void findByAssetReturnsMultipleProducts() {
        StorageReference ref1 = registerStorageRef("video/mp4", 100L, "asset-hash1");
        StorageReference ref2 = registerStorageRef("image/png", 50L, "asset-hash2");

        createReadyProductWithStorageAndAsset("t1", "p1", "asset-multi", ProductType.PREVIEW,
                ref1.storageReferenceId());
        createReadyProductWithStorageAndAsset("t1", "p1", "asset-multi", ProductType.THUMBNAIL,
                ref2.storageReferenceId());

        List<PreviewArtifactResponse> results = service.findByAsset("asset-multi");
        assertEquals(2, results.size());
    }

    // ─── Find by Timeline Revision ───

    @Test
    void findByTimelineRevisionReturnsMatchingProducts() {
        StorageReference ref1 = registerStorageRef("video/mp4", 100L, "rev-hash1");
        StorageReference ref2 = registerStorageRef("video/mp4", 200L, "rev-hash2");

        createReadyProductWithStorageAndRevision("t1", "p1", "rev-abc",
                ProductType.FINAL_RENDER, ref1.storageReferenceId());
        createReadyProductWithStorageAndRevision("t1", "p1", "rev-abc",
                ProductType.PREVIEW, ref2.storageReferenceId());

        List<PreviewArtifactResponse> results = service.findByTimelineRevision("rev-abc");
        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(r -> "rev-abc".equals(r.sourceTimelineRevisionId())));
    }

    @Test
    void findByTimelineRevisionReturnsEmptyWhenNone() {
        List<PreviewArtifactResponse> results = service.findByTimelineRevision("no-rev");
        assertTrue(results.isEmpty());
    }

    // ─── Architecture Boundaries ───

    @Test
    void responseDoesNotExposeStoragePaths() {
        StorageReference ref = registerStorageRef("video/mp4", 1024L, "path-hash");
        Product product = createReadyProductWithStorage(
                "t1", "p1", ProductType.PREVIEW, ref.storageReferenceId());

        Optional<PreviewArtifactResponse> result = service.findByProductId(product.productId());
        assertTrue(result.isPresent());

        // Verify no paths are exposed
        PreviewArtifactResponse r = result.get();
        String responseStr = r.toString();
        assertFalse(responseStr.contains("rootPath"), "Response must not expose rootPath");
        assertFalse(responseStr.contains("relativePath"), "Response must not expose relativePath");
        assertFalse(responseStr.contains("absolutePath"), "Response must not expose absolutePath");
        assertFalse(responseStr.contains("/tmp"), "Response must not expose filesystem paths");
    }

    @Test
    void responseDoesNotExposeSignedUrls() {
        StorageReference ref = registerStorageRef("video/mp4", 1024L, "url-hash");
        Product product = createReadyProductWithStorage(
                "t1", "p1", ProductType.PREVIEW, ref.storageReferenceId());

        Optional<PreviewArtifactResponse> result = service.findByProductId(product.productId());
        assertTrue(result.isPresent());

        String responseStr = result.get().toString();
        assertFalse(responseStr.contains("signedUrl"), "Response must not contain signedUrl");
        assertFalse(responseStr.contains("signed-url"), "Response must not contain signed-url");
        assertFalse(responseStr.contains("presigned"), "Response must not contain presigned");
    }

    @Test
    void responseDoesNotExposeProviderInternals() {
        StorageReference ref = registerStorageRef("video/mp4", 1024L, "internal-hash");
        Product product = createReadyProductWithStorage(
                "t1", "p1", ProductType.PREVIEW, ref.storageReferenceId());

        Optional<PreviewArtifactResponse> result = service.findByProductId(product.productId());
        assertTrue(result.isPresent());

        // Response should not include provider type, bucket, key
        PreviewArtifactResponse r = result.get();
        assertNull(extractFieldFromRecord(r, "providerType"),
                "Response must not expose provider type");
        assertNull(extractFieldFromRecord(r, "bucket"),
                "Response must not expose bucket");
        assertNull(extractFieldFromRecord(r, "key"),
                "Response must not expose key");
    }

    // ─── Edge Cases ───

    @Test
    void multipleDependencyEdgesAreTracked() {
        // A depends on B, C, D
        Product a = createReadyProduct("t1", "p1", ProductType.PREVIEW, "multi-a");
        Product b = createReadyProduct("t1", "p1", ProductType.RAW_MEDIA, "multi-b");
        Product c = createReadyProduct("t1", "p1", ProductType.RAW_MEDIA, "multi-c");
        Product d = createReadyProduct("t1", "p1", ProductType.RAW_MEDIA, "multi-d");

        productRuntime.linkDependency(a.productId(), b.productId(),
                DependencyType.DERIVED_FROM, "t1", "p1");
        productRuntime.linkDependency(a.productId(), c.productId(),
                DependencyType.DERIVED_FROM, "t1", "p1");
        productRuntime.linkDependency(a.productId(), d.productId(),
                DependencyType.DERIVED_FROM, "t1", "p1");

        Optional<PreviewArtifactResponse> result = service.findByProductId(a.productId());
        assertTrue(result.isPresent());
        assertEquals(3, result.get().upstreamDependencyCount());
        assertEquals(0, result.get().downstreamDependentCount());
        assertEquals(3, result.get().upstreamDependencyIds().size());
    }

    @Test
    void productWithZeroVersionIsValid() {
        Product product = new Product(
                "zero-ver", "t1", "p1", null,
                ProductType.PREVIEW, RepresentationKind.MEDIA_FILE,
                "ffmpeg", "ffmpeg", null,
                ProductStatus.READY, null,
                null, null, "video/mp4", 0,
                "{}", Instant.now(), Instant.now());
        productRepo.save(product);

        Optional<PreviewArtifactResponse> result = service.findByProductId("zero-ver");
        assertTrue(result.isPresent());
        assertEquals(0, result.get().version());
    }

    // ─── Helpers ───

    private StorageReference registerStorageRef(String mimeType, long fileSize, String checksum) {
        StorageReference ref = new StorageReference(
                null, "LOCAL", StorageClass.STANDARD,
                "/tmp/storage", "outputs/test.mp4",
                checksum, checksum, fileSize, mimeType,
                Instant.now(), Instant.now());
        return storageRepo.save(ref);
    }

    private Product createReadyProduct(String tenantId, String projectId,
                                         ProductType type, String idPrefix) {
        String productId = Ids.newId(idPrefix);
        Product product = new Product(
                productId, tenantId, projectId, null,
                type, RepresentationKind.MEDIA_FILE,
                "ffmpeg", "ffmpeg", null,
                ProductStatus.REGISTERED, null,
                null, null, "video/mp4", 1,
                "{}", Instant.now(), Instant.now());
        Product registered = productRuntime.register(product);
        return productRuntime.markReady(registered.productId());
    }

    private Product createReadyProductWithStorage(String tenantId, String projectId,
                                                    ProductType type, String storageRefId) {
        String productId = Ids.newId("prod");
        Product product = new Product(
                productId, tenantId, projectId, null,
                type, RepresentationKind.MEDIA_FILE,
                "ffmpeg", "ffmpeg", null,
                ProductStatus.REGISTERED, storageRefId,
                null, null, "video/mp4", 1,
                "{}", Instant.now(), Instant.now());
        Product registered = productRuntime.register(product);
        return productRuntime.markReady(registered.productId());
    }

    private Product createReadyProductWithStorageAndAsset(String tenantId, String projectId,
                                                            String assetId, ProductType type,
                                                            String storageRefId) {
        String productId = Ids.newId("prod");
        Product product = new Product(
                productId, tenantId, projectId, assetId,
                type, RepresentationKind.MEDIA_FILE,
                "ffmpeg", "ffmpeg", null,
                ProductStatus.REGISTERED, storageRefId,
                null, null, "video/mp4", 1,
                "{}", Instant.now(), Instant.now());
        Product registered = productRuntime.register(product);
        return productRuntime.markReady(registered.productId());
    }

    private Product createReadyProductWithStorageAndRevision(String tenantId, String projectId,
                                                               String timelineRevisionId,
                                                               ProductType type,
                                                               String storageRefId) {
        String productId = Ids.newId("prod");
        Product product = new Product(
                productId, tenantId, projectId, null,
                type, RepresentationKind.MEDIA_FILE,
                "ffmpeg", "ffmpeg", timelineRevisionId,
                ProductStatus.REGISTERED, storageRefId,
                null, null, "video/mp4", 1,
                "{}", Instant.now(), Instant.now());
        Product registered = productRuntime.register(product);
        return productRuntime.markReady(registered.productId());
    }

    private Product createFailedProduct(String tenantId, String projectId,
                                          ProductType type, String idPrefix) {
        String productId = Ids.newId(idPrefix);
        Product product = new Product(
                productId, tenantId, projectId, null,
                type, RepresentationKind.MEDIA_FILE,
                "ffmpeg", "ffmpeg", null,
                ProductStatus.REGISTERED, null,
                null, null, null, 1,
                "{\"error\":\"test failure\"}", Instant.now(), Instant.now());
        Product registered = productRuntime.register(product);
        return productRuntime.markFailed(registered.productId());
    }

    /**
     * Reflection helper to verify that a record field does NOT exist.
     * Used to check architecture boundary — response must not expose
     * storage/internal fields.
     */
    private Object extractFieldFromRecord(Object record, String fieldName) {
        try {
            var method = record.getClass().getMethod(fieldName);
            return method.invoke(record);
        } catch (NoSuchMethodException e) {
            return null; // Field does not exist — good
        } catch (Exception e) {
            return null;
        }
    }

    // ─── In-memory repositories (shared with RenderOutputRegistrationServiceTest) ───

    static class InMemoryStorageReferenceRepository extends StorageReferenceRepository {
        private final Map<String, StorageReference> store = new ConcurrentHashMap<>();

        public InMemoryStorageReferenceRepository() { }

        @Override
        public StorageReference save(StorageReference r) {
            String id = r.storageReferenceId() != null ? r.storageReferenceId() : "stor-" + UUID.randomUUID();
            StorageReference saved = new StorageReference(id, r.providerType(), r.storageClass(),
                    r.rootPath(), r.relativePath(), r.checksum(), r.contentHash(),
                    r.fileSize(), r.mimeType(), r.createdAt(), r.updatedAt());
            store.put(id, saved);
            return saved;
        }

        @Override
        public Optional<StorageReference> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<StorageReference> findByContentHash(String hash) {
            return store.values().stream().filter(r -> hash.equals(r.contentHash())).findFirst();
        }

        @Override
        public boolean exists(String id) { return store.containsKey(id); }

        @Override
        public void delete(String id) { store.remove(id); }
    }

    static class InMemoryProductRepository extends ProductRepository {
        private final Map<String, Product> store = new ConcurrentHashMap<>();
        private final Map<String, List<Product>> byProject = new ConcurrentHashMap<>();
        private final Map<String, List<Product>> byAsset = new ConcurrentHashMap<>();
        private final Map<String, Map<ProductType, Product>> latestByAssetAndType = new ConcurrentHashMap<>();
        private final Map<String, List<Product>> byTimelineRevision = new ConcurrentHashMap<>();

        public InMemoryProductRepository() { }

        @Override
        public Product save(Product p) {
            String id = p.productId() != null ? p.productId() : "prod-" + UUID.randomUUID();
            Product saved = new Product(id, p.tenantId(), p.projectId(), p.ownerAssetId(),
                    p.productType(), p.representationKind(), p.producerType(), p.producerId(),
                    p.sourceTimelineRevisionId(), p.status(), p.storageReferenceId(),
                    p.checksum(), p.contentHash(), p.mimeType(), p.version(),
                    p.metadataJson(), p.createdAt(), p.updatedAt());
            store.put(id, saved);
            if (p.projectId() != null) {
                List<Product> list = byProject.computeIfAbsent(p.projectId(), k -> new ArrayList<>());
                list.removeIf(existing -> existing.productId().equals(id));
                list.add(saved);
            }
            if (p.ownerAssetId() != null) {
                List<Product> list = byAsset.computeIfAbsent(p.ownerAssetId(), k -> new ArrayList<>());
                list.removeIf(existing -> existing.productId().equals(id));
                list.add(saved);
            }
            if (p.ownerAssetId() != null) {
                latestByAssetAndType.computeIfAbsent(p.ownerAssetId(), k -> new ConcurrentHashMap<>())
                        .put(p.productType(), saved);
            }
            if (p.sourceTimelineRevisionId() != null) {
                List<Product> list = byTimelineRevision.computeIfAbsent(p.sourceTimelineRevisionId(), k -> new ArrayList<>());
                list.removeIf(existing -> existing.productId().equals(id));
                list.add(saved);
            }
            return saved;
        }

        @Override
        public Optional<Product> findById(String id) { return Optional.ofNullable(store.get(id)); }

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

        @Override
        public List<Product> findBySourceTimelineRevisionId(String timelineRevisionId) {
            return byTimelineRevision.getOrDefault(timelineRevisionId, List.of());
        }
    }

    static class InMemoryProductDependencyRepository extends ProductDependencyRepository {
        private final Map<String, ProductDependency> store = new ConcurrentHashMap<>();

        public InMemoryProductDependencyRepository() { }

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
        public void delete(String dependencyId) { store.remove(dependencyId); }
    }
}
