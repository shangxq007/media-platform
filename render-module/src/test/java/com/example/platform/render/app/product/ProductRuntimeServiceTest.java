package com.example.platform.render.app.product;

import com.example.platform.render.domain.product.*;
import com.example.platform.render.infrastructure.product.ProductDependencyRepository;
import com.example.platform.render.infrastructure.product.ProductRepository;
import java.time.Instant;
import java.util.*;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VS.1 Product/Storage boundary tests for {@link ProductRuntimeService}.
 *
 * <p>Tests product lifecycle operations and dependency management.
 * Uses hand-written fakes — no Mockito, no database, no H2.
 */
class ProductRuntimeServiceTest {

    private FakeProductRepository fakeRepo;
    private FakeDependencyRepository fakeDepRepo;
    private ProductRuntimeService service;

    @BeforeEach
    void setUp() {
        fakeRepo = new FakeProductRepository();
        fakeDepRepo = new FakeDependencyRepository();
        service = new ProductRuntimeService(fakeRepo, fakeDepRepo);
    }

    // ========== Registration ==========

    @Nested
    @DisplayName("Product registration")
    class Registration {

        @Test
        @DisplayName("register() succeeds for REGISTERED product with provenance")
        void registerSucceedsWithProvenance() {
            Product product = sampleProduct("prod-1", ProductStatus.REGISTERED, "asset-1");

            Product result = service.register(product);

            assertNotNull(result);
            assertEquals("prod-1", result.productId());
            assertEquals(ProductStatus.REGISTERED, result.status());
            assertEquals(1, fakeRepo.saveCalls);
        }

        @Test
        @DisplayName("register() rejects non-REGISTERED status")
        void registerRejectsNonRegisteredStatus() {
            Product product = sampleProduct("prod-1", ProductStatus.READY, "asset-1");

            assertThrows(IllegalArgumentException.class, () -> service.register(product));
        }

        @Test
        @DisplayName("register() rejects product without provenance")
        void registerRejectsNoProvenance() {
            Product product = new Product(
                    "prod-1", "t-1", "proj-1", null,
                    ProductType.FINAL_RENDER, RepresentationKind.MEDIA_FILE,
                    null, null, null,
                    ProductStatus.REGISTERED, null, null, null, null, 1,
                    null, Instant.now(), Instant.now());

            assertThrows(IllegalArgumentException.class, () -> service.register(product));
        }

        @Test
        @DisplayName("register() accepts product with ownerAssetId provenance")
        void registerAcceptsOwnerAssetProvenance() {
            Product product = sampleProduct("prod-1", ProductStatus.REGISTERED, "asset-1");

            Product result = service.register(product);

            assertEquals("asset-1", result.ownerAssetId());
        }

        @Test
        @DisplayName("register() accepts product with producerId provenance")
        void registerAcceptsProducerProvenance() {
            Product product = new Product(
                    "prod-1", "t-1", "proj-1", null,
                    ProductType.FINAL_RENDER, RepresentationKind.MEDIA_FILE,
                    "render-pipeline", "rp:exec-1", null,
                    ProductStatus.REGISTERED, null, null, null, null, 1,
                    null, Instant.now(), Instant.now());

            Product result = service.register(product);

            assertEquals("rp:exec-1", result.producerId());
        }

        @Test
        @DisplayName("register() accepts product with sourceTimelineRevisionId provenance")
        void registerAcceptsTimelineRevisionProvenance() {
            Product product = new Product(
                    "prod-1", "t-1", "proj-1", null,
                    ProductType.TIMELINE_REVISION, RepresentationKind.TIMELINE_REVISION,
                    null, null, "rev-123",
                    ProductStatus.REGISTERED, null, null, null, null, 1,
                    null, Instant.now(), Instant.now());

            Product result = service.register(product);

            assertEquals("rev-123", result.sourceTimelineRevisionId());
        }
    }

    // ========== Lifecycle transitions ==========

    @Nested
    @DisplayName("Product lifecycle transitions")
    class LifecycleTransitions {

        @Test
        @DisplayName("markReady() transitions REGISTERED → READY")
        void markReadyTransitions() {
            Product registered = sampleProduct("prod-1", ProductStatus.REGISTERED, "asset-1");
            fakeRepo.products.put("prod-1", registered);

            Product result = service.markReady("prod-1");

            assertEquals(ProductStatus.READY, result.status());
        }

        @Test
        @DisplayName("markReady() is idempotent for READY product")
        void markReadyIdempotent() {
            Product ready = sampleProduct("prod-1", ProductStatus.READY, "asset-1");
            fakeRepo.products.put("prod-1", ready);
            int savesBefore = fakeRepo.saveCalls;

            Product result = service.markReady("prod-1");

            assertEquals(ProductStatus.READY, result.status());
            assertEquals(savesBefore, fakeRepo.saveCalls, "Should not save when already READY");
        }

        @Test
        @DisplayName("markFailed() transitions to FAILED")
        void markFailedTransitions() {
            Product registered = sampleProduct("prod-1", ProductStatus.REGISTERED, "asset-1");
            fakeRepo.products.put("prod-1", registered);

            Product result = service.markFailed("prod-1");

            assertEquals(ProductStatus.FAILED, result.status());
        }

        @Test
        @DisplayName("markReady() throws when product not found")
        void markReadyThrowsWhenNotFound() {
            assertThrows(NoSuchElementException.class, () -> service.markReady("prod-missing"));
        }
    }

    // ========== Dependency management ==========

    @Nested
    @DisplayName("Dependency management")
    class DependencyManagement {

        @Test
        @DisplayName("linkDependency() creates dependency edge")
        void linkDependencyCreates() {
            ProductDependency result = service.linkDependency("prod-1", "prod-2",
                    DependencyType.DERIVED_FROM, "t-1", "proj-1");

            assertNotNull(result);
            assertEquals("prod-1", result.productId());
            assertEquals("prod-2", result.dependsOnProductId());
            assertEquals(DependencyType.DERIVED_FROM, result.dependencyType());
        }

        @Test
        @DisplayName("linkDependency() detects self-dependency cycle")
        void linkDependencyDetectsSelfCycle() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.linkDependency("prod-1", "prod-1",
                            DependencyType.DERIVED_FROM, "t-1", "proj-1"));
        }

        @Test
        @DisplayName("linkDependency() detects transitive cycle")
        void linkDependencyDetectsTransitiveCycle() {
            // prod-1 depends on prod-2 (stored under key prod-1 per save() convention)
            fakeDepRepo.dependencies.put("prod-1", new ArrayList<>(List.of(
                    new ProductDependency("dep-1", "t-1", "proj-1", "prod-1", "prod-2",
                            DependencyType.DERIVED_FROM, Instant.now()))));

            // Trying to create prod-2 → prod-1 would create cycle
            assertThrows(IllegalArgumentException.class,
                    () -> service.linkDependency("prod-2", "prod-1",
                            DependencyType.DERIVED_FROM, "t-1", "proj-1"));
        }

        @Test
        @DisplayName("findDependencies() delegates to repository")
        void findDependenciesDelegates() {
            fakeDepRepo.dependencies.put("prod-1", List.of(
                    new ProductDependency("dep-1", "t-1", "proj-1", "prod-1", "prod-2",
                            DependencyType.DERIVED_FROM, Instant.now())));

            List<ProductDependency> result = service.findDependencies("prod-1");

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("findUpstream() returns dependsOnProductIds")
        void findUpstreamReturnsUpstreamIds() {
            fakeDepRepo.dependencies.put("prod-1", List.of(
                    new ProductDependency("dep-1", "t-1", "proj-1", "prod-1", "prod-2",
                            DependencyType.DERIVED_FROM, Instant.now()),
                    new ProductDependency("dep-2", "t-1", "proj-1", "prod-1", "prod-3",
                            DependencyType.DERIVED_FROM, Instant.now())));

            List<String> upstream = service.findUpstream("prod-1");

            assertEquals(2, upstream.size());
            assertTrue(upstream.contains("prod-2"));
            assertTrue(upstream.contains("prod-3"));
        }

        @Test
        @DisplayName("findDownstream() returns dependent productIds")
        void findDownstreamReturnsDownstreamIds() {
            fakeDepRepo.dependents.put("prod-1", List.of(
                    new ProductDependency("dep-1", "t-1", "proj-1", "prod-2", "prod-1",
                            DependencyType.DERIVED_FROM, Instant.now())));

            List<String> downstream = service.findDownstream("prod-1");

            assertEquals(1, downstream.size());
            assertEquals("prod-2", downstream.get(0));
        }
    }

    // ========== Product domain model ==========

    @Nested
    @DisplayName("Product domain model boundaries")
    class ProductDomain {

        @Test
        @DisplayName("Product.withStatus() preserves all fields except status and updatedAt")
        void withStatusPreservesFields() {
            Product original = sampleProduct("prod-1", ProductStatus.REGISTERED, "asset-1");
            Product updated = original.withStatus(ProductStatus.READY);

            assertEquals(ProductStatus.READY, updated.status());
            assertEquals(original.productId(), updated.productId());
            assertEquals(original.tenantId(), updated.tenantId());
            assertEquals(original.projectId(), updated.projectId());
            assertEquals(original.ownerAssetId(), updated.ownerAssetId());
            assertEquals(original.productType(), updated.productType());
            assertEquals(original.storageReferenceId(), updated.storageReferenceId());
        }

        @Test
        @DisplayName("Product.hasProvenance() returns true when ownerAssetId set")
        void hasProvenanceWithOwnerAsset() {
            Product product = sampleProduct("prod-1", ProductStatus.REGISTERED, "asset-1");
            assertTrue(product.hasProvenance());
        }

        @Test
        @DisplayName("Product.hasProvenance() returns true when producerId set")
        void hasProvenanceWithProducer() {
            Product product = new Product(
                    "prod-1", "t-1", "proj-1", null,
                    ProductType.FINAL_RENDER, RepresentationKind.MEDIA_FILE,
                    "renderer", "rp:1", null,
                    ProductStatus.REGISTERED, null, null, null, null, 1,
                    null, Instant.now(), Instant.now());
            assertTrue(product.hasProvenance());
        }

        @Test
        @DisplayName("Product.hasProvenance() returns false when no provenance")
        void hasProvenanceFalseWhenNone() {
            Product product = new Product(
                    "prod-1", "t-1", "proj-1", null,
                    ProductType.FINAL_RENDER, RepresentationKind.MEDIA_FILE,
                    null, null, null,
                    ProductStatus.REGISTERED, null, null, null, null, 1,
                    null, Instant.now(), Instant.now());
            assertFalse(product.hasProvenance());
        }

        @Test
        @DisplayName("Product types cover expected render verticals")
        void productTypesCoverVerticals() {
            assertNotNull(ProductType.FINAL_RENDER);
            assertNotNull(ProductType.PREVIEW);
            assertNotNull(ProductType.TIMELINE_REVISION);
            assertNotNull(ProductType.PROXY);
            assertNotNull(ProductType.THUMBNAIL);
        }
    }

    // ========== Helpers ==========

    private Product sampleProduct(String id, ProductStatus status, String ownerAssetId) {
        return new Product(
                id, "t-1", "proj-1", ownerAssetId,
                ProductType.FINAL_RENDER, RepresentationKind.MEDIA_FILE,
                "render-pipeline", "rp:" + id, null,
                status, "stor-1", "abc123", "abc123", "video/mp4", 1,
                "{}", Instant.now(), Instant.now());
    }

    // ========== Fakes ==========

    static class FakeProductRepository extends ProductRepository {
        int saveCalls = 0;
        final Map<String, Product> products = new HashMap<>();

        FakeProductRepository() { super(); }

        @Override
        public Product save(Product p) {
            saveCalls++;
            String id = p.productId() != null ? p.productId() : "prod-" + UUID.randomUUID();
            Product saved = new Product(id, p.tenantId(), p.projectId(), p.ownerAssetId(),
                    p.productType(), p.representationKind(), p.producerType(), p.producerId(),
                    p.sourceTimelineRevisionId(), p.status(), p.storageReferenceId(),
                    p.checksum(), p.contentHash(), p.mimeType(), p.version(),
                    p.metadataJson(), p.createdAt(), p.updatedAt());
            products.put(id, saved);
            return saved;
        }

        @Override
        public Optional<Product> findById(String productId) {
            return Optional.ofNullable(products.get(productId));
        }
    }

    static class FakeDependencyRepository extends ProductDependencyRepository {
        final Map<String, List<ProductDependency>> dependencies = new HashMap<>();
        final Map<String, List<ProductDependency>> dependents = new HashMap<>();

        FakeDependencyRepository() { super(); }

        @Override
        public ProductDependency save(ProductDependency dep) {
            String id = dep.dependencyId() != null ? dep.dependencyId() : "pdep-" + UUID.randomUUID();
            ProductDependency saved = new ProductDependency(id, dep.tenantId(), dep.projectId(),
                    dep.productId(), dep.dependsOnProductId(), dep.dependencyType(), Instant.now());
            dependencies.computeIfAbsent(dep.productId(), k -> new ArrayList<>()).add(saved);
            dependents.computeIfAbsent(dep.dependsOnProductId(), k -> new ArrayList<>()).add(saved);
            return saved;
        }

        @Override
        public List<ProductDependency> findDependencies(String productId) {
            return dependencies.getOrDefault(productId, List.of());
        }

        @Override
        public List<ProductDependency> findDependents(String productId) {
            return dependents.getOrDefault(productId, List.of());
        }
    }
}
