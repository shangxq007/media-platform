package com.example.platform.render.app.timeline.compile;

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
 * Tests for {@link RenderDeduplicationService}.
 *
 * <p>Proves:
 * <ul>
 *   <li>READY product is reused when exact match exists</li>
 *   <li>READY product from different project is NOT reused</li>
 *   <li>READY product with different profile is NOT reused</li>
 *   <li>No existing render proceeds with new render</li>
 *   <li>Failed previous render allows retry</li>
 *   <li>Fingerprint is stable for same inputs</li>
 * </ul>
 */
class RenderDeduplicationServiceTest {

    private ProductRuntimeService productRuntime;
    private RenderDeduplicationService dedupService;

    @BeforeEach
    void setUp() {
        ProductRepository productRepo = new InMemoryProductRepository();
        ProductDependencyRepository depRepo = new InMemoryProductDependencyRepository();
        productRuntime = new ProductRuntimeService(productRepo, depRepo);
        dedupService = new RenderDeduplicationService(productRuntime);
    }

    @Test
    @DisplayName("READY product is reused when exact match exists")
    void readyProductIsReused() {
        // Register a READY FINAL_RENDER product for the revision
        registerProduct("rev-1", "proj-1", ProductStatus.READY,
                "{\"outputProfile\":\"default_1080p\"}");

        RenderDeduplicationDecision decision = dedupService.check(
                "proj-1", "rev-1", "default_1080p", "LEGACY");

        assertTrue(decision.shouldReuse());
        assertNotNull(decision.reusedResult());
        assertEquals("READY", decision.reusedResult().productStatus());
    }

    @Test
    @DisplayName("READY product from different project is NOT reused")
    void differentProjectNotReused() {
        registerProduct("rev-1", "proj-other", ProductStatus.READY,
                "{\"outputProfile\":\"default_1080p\"}");

        RenderDeduplicationDecision decision = dedupService.check(
                "proj-1", "rev-1", "default_1080p", "LEGACY");

        assertTrue(decision.shouldProceed());
    }

    @Test
    @DisplayName("READY product with different profile is NOT reused")
    void differentProfileNotReused() {
        registerProduct("rev-1", "proj-1", ProductStatus.READY,
                "{\"outputProfile\":\"default_720p\"}");

        RenderDeduplicationDecision decision = dedupService.check(
                "proj-1", "rev-1", "default_1080p", "LEGACY");

        assertTrue(decision.shouldProceed());
    }

    @Test
    @DisplayName("No existing render proceeds with new render")
    void noExistingRenderProceeds() {
        RenderDeduplicationDecision decision = dedupService.check(
                "proj-1", "rev-1", "default_1080p", "LEGACY");

        assertTrue(decision.shouldProceed());
        assertEquals(RenderDeduplicationReason.NO_EXISTING_RENDER, decision.reason());
    }

    @Test
    @DisplayName("Failed previous render allows retry")
    void failedRenderAllowsRetry() {
        registerProduct("rev-1", "proj-1", ProductStatus.FAILED,
                "{\"outputProfile\":\"default_1080p\"}");

        RenderDeduplicationDecision decision = dedupService.check(
                "proj-1", "rev-1", "default_1080p", "LEGACY");

        assertTrue(decision.shouldProceed());
        assertEquals(RenderDeduplicationReason.FAILED_PREVIOUS_ATTEMPT, decision.reason());
    }

    @Test
    @DisplayName("Fingerprint is stable for same inputs")
    void fingerprintIsStable() {
        RenderDeduplicationDecision d1 = dedupService.check(
                "proj-1", "rev-1", "default_1080p", "LEGACY");
        RenderDeduplicationDecision d2 = dedupService.check(
                "proj-1", "rev-1", "default_1080p", "LEGACY");

        assertEquals(d1.fingerprint().value(), d2.fingerprint().value());
    }

    @Test
    @DisplayName("READY product without metadata uses default profile match")
    void readyProductWithoutMetadataUsesDefault() {
        registerProduct("rev-1", "proj-1", ProductStatus.READY, null);

        RenderDeduplicationDecision decision = dedupService.check(
                "proj-1", "rev-1", "default_1080p", "LEGACY");

        // null metadata → assumes default_1080p → matches
        assertTrue(decision.shouldReuse());
    }

    @Test
    @DisplayName("Non-FINAL_RENDER product is not reused")
    void nonFinalRenderProductNotReused() {
        // Register a RAW_MEDIA product (not FINAL_RENDER)
        Product rawProduct = new Product(
                "prod-raw", "tenant-1", "proj-1", "asset-1",
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "upload", "upload-service", "rev-1",
                ProductStatus.REGISTERED, "stor-1",
                "checksum", "checksum", "video/mp4", 1,
                "{\"outputProfile\":\"default_1080p\"}",
                Instant.now(), Instant.now());
        productRuntime.register(rawProduct);
        productRuntime.markReady(rawProduct.productId());

        RenderDeduplicationDecision decision = dedupService.check(
                "proj-1", "rev-1", "default_1080p", "LEGACY");

        assertTrue(decision.shouldProceed());
    }

    // --- Helpers ---

    private void registerProduct(String revisionId, String projectId, ProductStatus status,
                                  String metadataJson) {
        String productId = "prod-" + UUID.randomUUID().toString().substring(0, 8);
        Product product = new Product(
                productId, "tenant-1", projectId, null,
                ProductType.FINAL_RENDER, RepresentationKind.MEDIA_FILE,
                "ffmpeg", "ffmpeg-libass", revisionId,
                ProductStatus.REGISTERED, "stor-" + productId,
                "checksum", "checksum", "video/mp4", 1,
                metadataJson, Instant.now(), Instant.now());
        productRuntime.register(product);
        if (status == ProductStatus.READY) {
            productRuntime.markReady(productId);
        } else if (status == ProductStatus.FAILED) {
            productRuntime.markFailed(productId);
        }
    }

    // --- In-memory doubles ---

    static class InMemoryProductRepository extends ProductRepository {
        private final Map<String, Product> store = new ConcurrentHashMap<>();

        InMemoryProductRepository() { super(null); }

        @Override
        public Product save(Product product) {
            store.put(product.productId(), product);
            return product;
        }

        @Override
        public Optional<Product> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Product> findByAsset(String assetId) {
            return store.values().stream().filter(p -> assetId.equals(p.ownerAssetId())).toList();
        }

        @Override
        public Optional<Product> findLatest(String assetId, ProductType type) {
            return findByAsset(assetId).stream().filter(p -> p.productType() == type).findFirst();
        }

        @Override
        public List<Product> findByProject(String projectId, int limit) {
            return store.values().stream().filter(p -> projectId.equals(p.projectId())).limit(limit).toList();
        }

        @Override
        public List<Product> findBySourceTimelineRevisionId(String timelineRevisionId) {
            return store.values().stream()
                    .filter(p -> timelineRevisionId.equals(p.sourceTimelineRevisionId()))
                    .toList();
        }
    }

    static class InMemoryProductDependencyRepository extends ProductDependencyRepository {
        private final Map<String, List<ProductDependency>> store = new ConcurrentHashMap<>();

        InMemoryProductDependencyRepository() { super(null); }

        @Override
        public ProductDependency save(ProductDependency dep) {
            store.computeIfAbsent(dep.productId(), k -> new ArrayList<>()).add(dep);
            return dep;
        }

        @Override
        public List<ProductDependency> findDependencies(String productId) {
            return store.getOrDefault(productId, List.of());
        }

        @Override
        public List<ProductDependency> findDependents(String productId) {
            return List.of();
        }
    }
}
