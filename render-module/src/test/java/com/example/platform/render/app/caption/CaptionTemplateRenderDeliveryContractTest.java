package com.example.platform.render.app.caption;

import com.example.platform.render.api.dto.*;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.domain.caption.*;
import com.example.platform.render.domain.product.*;
import com.example.platform.render.infrastructure.product.ProductDependencyRepository;
import com.example.platform.render.infrastructure.product.ProductRepository;
import com.example.platform.render.testsupport.TimelineCoreSmokeFixture;
import com.example.platform.shared.Ids;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for safe delivery/result lookup contract.
 * Proves: READY product lookup, not-found, not-deliverable, no storage internals.
 */
class CaptionTemplateRenderDeliveryContractTest {

    @TempDir Path tempDir;
    private ProductRuntimeService productRuntime;
    private CaptionTemplateResultLookupService lookupService;

    @BeforeEach
    void setUp() {
        ProductRepository productRepo = new InMemoryProductRepository();
        ProductDependencyRepository depRepo = new InMemoryProductDependencyRepository();
        productRuntime = new ProductRuntimeService(productRepo, depRepo);
        lookupService = new CaptionTemplateResultLookupService(productRuntime);
    }

    @Test
    @DisplayName("READY FINAL_RENDER product returns ready response")
    void readyProductReturnsReady() {
        String productId = registerProduct(ProductType.FINAL_RENDER, ProductStatus.READY);

        CaptionTemplateRenderResultLookupResponse response = lookupService.lookup(productId);

        assertEquals(CaptionTemplateDeliveryStatus.READY, response.status());
        assertTrue(response.ready());
        assertEquals(productId, response.outputProductId());
        assertEquals("FINAL_RENDER", response.productType());
    }

    @Test
    @DisplayName("v0.2: downloadAvailable=false, previewAvailable=false")
    void v02DownloadNotAvailable() {
        String productId = registerProduct(ProductType.FINAL_RENDER, ProductStatus.READY);

        CaptionTemplateRenderResultLookupResponse response = lookupService.lookup(productId);

        assertFalse(response.downloadAvailable());
        assertFalse(response.previewAvailable());
        assertEquals("OUTPUT_PRODUCT_ID_ONLY", response.deliveryMode());
    }

    @Test
    @DisplayName("Missing product returns NOT_FOUND")
    void missingProductNotFound() {
        CaptionTemplateRenderResultLookupResponse response = lookupService.lookup("nonexistent");

        assertEquals(CaptionTemplateDeliveryStatus.NOT_FOUND, response.status());
        assertFalse(response.ready());
        assertNull(response.outputProductId());
    }

    @Test
    @DisplayName("Null product ID returns NOT_FOUND")
    void nullProductIdNotFound() {
        assertEquals(CaptionTemplateDeliveryStatus.NOT_FOUND, lookupService.lookup(null).status());
        assertEquals(CaptionTemplateDeliveryStatus.NOT_FOUND, lookupService.lookup("").status());
        assertEquals(CaptionTemplateDeliveryStatus.NOT_FOUND, lookupService.lookup("  ").status());
    }

    @Test
    @DisplayName("Non-FINAL_RENDER product returns NOT_DELIVERABLE")
    void nonFinalRenderNotDeliverable() {
        String productId = registerProduct(ProductType.RAW_MEDIA, ProductStatus.READY);

        CaptionTemplateRenderResultLookupResponse response = lookupService.lookup(productId);

        assertEquals(CaptionTemplateDeliveryStatus.NOT_DELIVERABLE, response.status());
        assertFalse(response.ready());
    }

    @Test
    @DisplayName("FAILED product returns FAILED status")
    void failedProductReturnsFailed() {
        String productId = registerProduct(ProductType.FINAL_RENDER, ProductStatus.FAILED);

        CaptionTemplateRenderResultLookupResponse response = lookupService.lookup(productId);

        assertEquals(CaptionTemplateDeliveryStatus.FAILED, response.status());
        assertFalse(response.ready());
    }

    @Test
    @DisplayName("REGISTERED (not READY) product returns NOT_DELIVERABLE")
    void registeredNotReadyNotDeliverable() {
        String productId = registerProduct(ProductType.FINAL_RENDER, ProductStatus.REGISTERED);

        CaptionTemplateRenderResultLookupResponse response = lookupService.lookup(productId);

        assertEquals(CaptionTemplateDeliveryStatus.NOT_DELIVERABLE, response.status());
        assertFalse(response.ready());
    }

    @Test
    @DisplayName("Response does not expose storage internals")
    void responseNoStorageInternals() {
        String productId = registerProduct(ProductType.FINAL_RENDER, ProductStatus.READY);

        CaptionTemplateRenderResultLookupResponse response = lookupService.lookup(productId);

        String str = response.toString();
        assertFalse(str.contains("bucket"));
        assertFalse(str.contains("objectKey"));
        assertFalse(str.contains("rootPath"));
        assertFalse(str.contains("signedUrl"));
        assertFalse(str.contains("storageReferenceId"));
    }

    @Test
    @DisplayName("Response does not expose provider internals")
    void responseNoProviderInternals() {
        String productId = registerProduct(ProductType.FINAL_RENDER, ProductStatus.READY);

        CaptionTemplateRenderResultLookupResponse response = lookupService.lookup(productId);

        String str = response.toString();
        assertFalse(str.contains("providerName"));
        assertFalse(str.contains("backendName"));
    }

    @Test
    @DisplayName("Response does not expose graph/plan IDs")
    void responseNoGraphPlanIds() {
        String productId = registerProduct(ProductType.FINAL_RENDER, ProductStatus.READY);

        CaptionTemplateRenderResultLookupResponse response = lookupService.lookup(productId);

        String str = response.toString();
        assertFalse(str.contains("renderCorrelationId"));
        assertFalse(str.contains("renderExecutionPlanId"));
        assertFalse(str.contains("artifactGraphId"));
    }

    // --- Helpers ---

    private String registerProduct(ProductType type, ProductStatus status) {
        String productId = Ids.newId("prod");
        Product product = new Product(productId, TimelineCoreSmokeFixture.TENANT_ID,
                TimelineCoreSmokeFixture.PROJECT_ID, "asset-1",
                type, RepresentationKind.MEDIA_FILE,
                "ffmpeg", "ffmpeg-libass", "rev-1",
                ProductStatus.REGISTERED, "stor-1",
                "checksum", "checksum", "video/mp4", 1,
                "{}", Instant.now(), Instant.now());
        productRuntime.register(product);
        if (status == ProductStatus.READY) productRuntime.markReady(productId);
        else if (status == ProductStatus.FAILED) productRuntime.markFailed(productId);
        return productId;
    }

    // --- In-memory doubles ---

    static class InMemoryProductRepository extends ProductRepository {
        private final Map<String, Product> store = new ConcurrentHashMap<>();
        InMemoryProductRepository() { super(null); }
        @Override public Product save(Product p) { store.put(p.productId(), p); return p; }
        @Override public Optional<Product> findById(String id) { return Optional.ofNullable(store.get(id)); }
        @Override public List<Product> findByAsset(String a) { return store.values().stream().filter(p -> a.equals(p.ownerAssetId())).toList(); }
        @Override public Optional<Product> findLatest(String a, ProductType t) { return findByAsset(a).stream().filter(p -> p.productType() == t).findFirst(); }
        @Override public List<Product> findByProject(String pid, int lim) { return store.values().stream().filter(p -> pid.equals(p.projectId())).limit(lim).toList(); }
        @Override public List<Product> findBySourceTimelineRevisionId(String r) { return store.values().stream().filter(p -> r.equals(p.sourceTimelineRevisionId())).toList(); }
    }

    static class InMemoryProductDependencyRepository extends ProductDependencyRepository {
        private final Map<String, List<ProductDependency>> store = new ConcurrentHashMap<>();
        InMemoryProductDependencyRepository() { super(null); }
        @Override public ProductDependency save(ProductDependency dep) { store.computeIfAbsent(dep.productId(), k -> new ArrayList<>()).add(dep); return dep; }
        @Override public List<ProductDependency> findDependencies(String pid) { return store.getOrDefault(pid, List.of()); }
        @Override public List<ProductDependency> findDependents(String pid) { return List.of(); }
    }
}
