package com.example.platform.render.app.timeline;

import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.domain.product.*;
import com.example.platform.render.infrastructure.product.ProductDependencyRepository;
import com.example.platform.render.infrastructure.product.ProductRepository;
import com.example.platform.shared.Ids;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TimelineInputProductResolver}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Resolution of sourceAssetIds to READY RAW_MEDIA Products</li>
 *   <li>De-duplication of identical sourceAssetIds</li>
 *   <li>Fail-closed behavior for missing/unready Products</li>
 *   <li>Unsafe sourceAssetId rejection (paths, URLs, provider hints)</li>
 *   <li>Exact-match provider hint rejection (not substring)</li>
 * </ul>
 */
class TimelineInputProductResolverTest {

    private ProductRuntimeService productRuntime;
    private TimelineInputProductResolver resolver;

    @BeforeEach
    void setUp() {
        ProductRepository productRepo = new InMemoryProductRepository();
        ProductDependencyRepository depRepo = new InMemoryProductDependencyRepository();
        productRuntime = new ProductRuntimeService(productRepo, depRepo);
        resolver = new TimelineInputProductResolver(productRuntime);
    }

    // ─── Success cases ───

    @Test
    @DisplayName("Resolves product from matching ownerAssetId")
    void resolvesProductFromMatchingAssetId() {
        Product input = createReadyRawMediaProduct("ast_001", "ten_1", "prj_1");

        var result = resolver.resolve(List.of("ast_001"));

        assertTrue(result.valid(), result.failureReason());
        assertEquals(1, result.inputProductIds().size());
        assertEquals(input.productId(), result.inputProductIds().get(0));
        assertEquals(List.of("ast_001"), result.sourceAssetIds());
    }

    @Test
    @DisplayName("De-duplicates input Product IDs from repeated asset IDs")
    void deDuplicatesInputProductIds() {
        Product input = createReadyRawMediaProduct("ast_001", "ten_1", "prj_1");

        var result = resolver.resolve(List.of("ast_001", "ast_001"));

        assertTrue(result.valid(), result.failureReason());
        assertEquals(1, result.inputProductIds().size(),
                "Repeated identical sourceAssetIds must produce one de-duplicated inputProductId");
        assertEquals(input.productId(), result.inputProductIds().get(0));
    }

    @Test
    @DisplayName("Repeated identical sourceAssetIds do not create duplicate dependency edges")
    void repeatedIdenticalSourceAssetIdsProduceOneProductId() {
        createReadyRawMediaProduct("ast_dup", "ten_1", "prj_1");

        var result = resolver.resolve(List.of("ast_dup", "ast_dup", "ast_dup"));

        assertTrue(result.valid());
        // De-duplicated: only 1 Product ID
        assertEquals(1, result.inputProductIds().size(),
                "Three identical asset IDs must resolve to one Product ID");
        // This means downstream dependency linking creates exactly 1 edge, not 3
    }

    @Test
    @DisplayName("Resolves multiple distinct assets to multiple Products")
    void resolvesMultipleDistinctAssets() {
        Product input1 = createReadyRawMediaProduct("ast_001", "ten_1", "prj_1");
        Product input2 = createReadyRawMediaProduct("ast_002", "ten_1", "prj_1");

        var result = resolver.resolve(List.of("ast_001", "ast_002"));

        assertTrue(result.valid(), result.failureReason());
        assertEquals(2, result.inputProductIds().size());
        assertTrue(result.inputProductIds().contains(input1.productId()));
        assertTrue(result.inputProductIds().contains(input2.productId()));
    }

    @Test
    @DisplayName("Preserves sourceAssetIds in result")
    void preservesSourceAssetIds() {
        createReadyRawMediaProduct("ast_a", "ten_1", "prj_1");

        var result = resolver.resolve(List.of("ast_a"));

        assertTrue(result.valid());
        assertEquals(List.of("ast_a"), result.sourceAssetIds());
    }

    // ─── Failure: no Product mapping ───

    @Test
    @DisplayName("Fails when no Product for asset ID")
    void failsWhenNoProductForAssetId() {
        var result = resolver.resolve(List.of("nonexistent_asset"));

        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("No READY RAW_MEDIA Product"),
                "Reason: " + result.failureReason());
    }

    @Test
    @DisplayName("Fails when Product is not READY")
    void failsWhenProductNotReady() {
        createRegisteredNotReadyProduct("ast_notready", "ten_1", "prj_1");

        var result = resolver.resolve(List.of("ast_notready"));

        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("No READY RAW_MEDIA Product"),
                "Reason: " + result.failureReason());
    }

    @Test
    @DisplayName("Fails when Product is not RAW_MEDIA")
    void failsWhenProductNotRawMedia() {
        String productId = Ids.newId("prod");
        Product product = new Product(
                productId, "ten_1", "prj_1", "ast_final",
                ProductType.FINAL_RENDER, RepresentationKind.MEDIA_FILE,
                "ffmpeg", "ffmpeg", null,
                ProductStatus.REGISTERED, "stor-1",
                null, null, "video/mp4", 1,
                "{}", Instant.now(), Instant.now());
        Product registered = productRuntime.register(product);
        productRuntime.markReady(registered.productId());

        var result = resolver.resolve(List.of("ast_final"));

        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("No READY RAW_MEDIA Product"),
                "Reason: " + result.failureReason());
    }

    @Test
    @DisplayName("Fails when no source assets")
    void failsWhenNoSourceAssets() {
        var result = resolver.resolve(List.of());

        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("No source assets"),
                "Reason: " + result.failureReason());
    }

    @Test
    @DisplayName("Fails when sourceAssetIds is null")
    void failsWhenSourceAssetIdsNull() {
        var result = resolver.resolve(null);

        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("No source assets"),
                "Reason: " + result.failureReason());
    }

    // ─── Failure: unsafe sourceAssetIds ───

    @Test
    @DisplayName("Rejects blank sourceAssetId")
    void rejectsBlankSourceAssetId() {
        var result = resolver.resolve(List.of(""));

        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("Blank source asset ID"),
                "Reason: " + result.failureReason());
    }

    @Test
    @DisplayName("Rejects whitespace-only sourceAssetId")
    void rejectsWhitespaceSourceAssetId() {
        var result = resolver.resolve(List.of("   "));

        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("Blank source asset ID"),
                "Reason: " + result.failureReason());
    }

    @Test
    @DisplayName("Rejects absolute path sourceAssetId")
    void rejectsAbsoluteSourceAssetId() {
        var result = resolver.resolve(List.of("/etc/passwd"));

        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("absolute path"),
                "Reason: " + result.failureReason());
    }

    @Test
    @DisplayName("Rejects path traversal sourceAssetId")
    void rejectsPathTraversalSourceAssetId() {
        var result = resolver.resolve(List.of("../secret"));

        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("path traversal"),
                "Reason: " + result.failureReason());
    }

    @Test
    @DisplayName("Rejects home directory sourceAssetId")
    void rejectsHomePathSourceAssetId() {
        var result = resolver.resolve(List.of("~/video.mp4"));

        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("home directory"),
                "Reason: " + result.failureReason());
    }

    @Test
    @DisplayName("Rejects backslash path sourceAssetId")
    void rejectsBackslashSourceAssetId() {
        var result = resolver.resolve(List.of("foo\\bar\\video.mp4"));

        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("backslash"),
                "Reason: " + result.failureReason());
    }

    @Test
    @DisplayName("Rejects file:// URI sourceAssetId")
    void rejectsFileUriSourceAssetId() {
        var result = resolver.resolve(List.of("file:///tmp/video.mp4"));

        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("file://"),
                "Reason: " + result.failureReason());
    }

    @Test
    @DisplayName("Rejects http:// URL sourceAssetId")
    void rejectsHttpUrlSourceAssetId() {
        var result = resolver.resolve(List.of("http://example.com/video.mp4"));

        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("http URL"),
                "Reason: " + result.failureReason());
    }

    @Test
    @DisplayName("Rejects https:// URL sourceAssetId")
    void rejectsHttpsUrlSourceAssetId() {
        var result = resolver.resolve(List.of("https://example.com/video.mp4"));

        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("https URL"),
                "Reason: " + result.failureReason());
    }

    @Test
    @DisplayName("Rejects s3:// URI sourceAssetId")
    void rejectsS3UriSourceAssetId() {
        var result = resolver.resolve(List.of("s3://bucket/video.mp4"));

        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("s3 URL"),
                "Reason: " + result.failureReason());
    }

    @Test
    @DisplayName("Rejects gs:// URI sourceAssetId")
    void rejectsGsUriSourceAssetId() {
        var result = resolver.resolve(List.of("gs://bucket/video.mp4"));

        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("gs URL"),
                "Reason: " + result.failureReason());
    }

    @Test
    @DisplayName("Rejects exact-match internal provider hint")
    void rejectsProviderHintSourceAssetId() {
        var result = resolver.resolve(List.of("ffmpeg"));

        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("provider/backend hint"),
                "Reason: " + result.failureReason());
    }

    @Test
    @DisplayName("Rejects exact-match internal provider hint (case-insensitive)")
    void rejectsProviderHintCaseInsensitive() {
        var result = resolver.resolve(List.of("REMOTION"));

        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("provider/backend hint"),
                "Reason: " + result.failureReason());
    }

    @Test
    @DisplayName("Accepts asset ID containing provider name (not exact match)")
    void acceptsAssetIdContainingProviderName() {
        // "my-ffmpeg-demo-asset" is NOT equal to "ffmpeg" — exact match only
        Product input = createReadyRawMediaProduct("my-ffmpeg-demo-asset", "ten_1", "prj_1");

        var result = resolver.resolve(List.of("my-ffmpeg-demo-asset"));

        assertTrue(result.valid(), result.failureReason());
        assertEquals(1, result.inputProductIds().size());
        assertEquals(input.productId(), result.inputProductIds().get(0));
    }

    @Test
    @DisplayName("Rejects unsafe ID before Product lookup (fail-fast)")
    void rejectsUnsafeIdBeforeLookup() {
        // Even if a Product exists with ownerAssetId "/etc/passwd", the resolver
        // must reject the ID before attempting any Product lookup
        var result = resolver.resolve(List.of("/etc/passwd"));

        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("absolute path"),
                "Must reject before Product lookup: " + result.failureReason());
    }

    // ─── Helpers ───

    private Product createReadyRawMediaProduct(String assetId, String tenantId, String projectId) {
        String productId = Ids.newId("prod");
        Product product = new Product(
                productId, tenantId, projectId, assetId,
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "upload", "upload-service", null,
                ProductStatus.REGISTERED, "stor-" + UUID.randomUUID().toString().substring(0, 8),
                null, null, "video/mp4", 1,
                "{}", Instant.now(), Instant.now());
        Product registered = productRuntime.register(product);
        return productRuntime.markReady(registered.productId());
    }

    private Product createRegisteredNotReadyProduct(String assetId, String tenantId, String projectId) {
        String productId = Ids.newId("prod");
        Product product = new Product(
                productId, tenantId, projectId, assetId,
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "upload", "upload-service", null,
                ProductStatus.REGISTERED, "stor-" + UUID.randomUUID().toString().substring(0, 8),
                null, null, "video/mp4", 1,
                "{}", Instant.now(), Instant.now());
        return productRuntime.register(product);
    }

    // ─── In-memory test doubles ───

    static class InMemoryProductRepository extends ProductRepository {
        private final Map<String, Product> store = new ConcurrentHashMap<>();
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
            if (p.ownerAssetId() != null) {
                byAsset.computeIfAbsent(p.ownerAssetId(), k -> new ArrayList<>()).add(saved);
            }
            return saved;
        }

        @Override
        public Optional<Product> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Product> findByAsset(String assetId) {
            return byAsset.getOrDefault(assetId, List.of());
        }

        @Override
        public List<Product> findByProject(String projectId, int limit) {
            return List.of();
        }

        @Override
        public Optional<Product> findLatest(String assetId, ProductType type) {
            return byAsset.getOrDefault(assetId, List.of()).stream()
                    .filter(p -> p.productType() == type)
                    .findFirst();
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
