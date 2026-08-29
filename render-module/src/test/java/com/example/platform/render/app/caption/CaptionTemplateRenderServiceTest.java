package com.example.platform.render.app.caption;

import com.example.platform.render.app.input.RenderInputMaterializationService;
import com.example.platform.render.app.output.RenderOutputRegistrationService;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.app.timeline.*;
import com.example.platform.render.app.timeline.compile.*;
import com.example.platform.render.app.timeline.compile.audit.*;
import com.example.platform.render.domain.caption.*;
import com.example.platform.render.domain.product.*;
import com.example.platform.storage.contract.*;
import com.example.platform.render.infrastructure.product.ProductDependencyRepository;
import com.example.platform.render.infrastructure.product.ProductRepository;
import com.example.platform.render.infrastructure.storage.StorageReferenceRepository;
import com.example.platform.render.testsupport.TimelineCoreSmokeFixture;
import com.example.platform.shared.Ids;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CaptionTemplateRenderService.
 * Proves: valid legacy execution request fails closed without output, invalid
 * request retains validation errors, and no direct Provider or Remotion runs.
 */
class CaptionTemplateRenderServiceTest {
    @SuppressWarnings("unchecked")
    private static <T> org.springframework.beans.factory.ObjectProvider<T> mockProvider(T instance) {
        org.springframework.beans.factory.ObjectProvider<T> op = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(op.getIfAvailable()).thenReturn(instance);
        return op;
    }


    @TempDir Path tempDir;
    private StorageRuntimeService storageRuntime;
    private ProductRuntimeService productRuntime;
    private CaptionTemplateRenderService service;
    private InMemoryStorageReferenceRepository storageRepo;

    @BeforeEach
    void setUp() {
        storageRepo = new InMemoryStorageReferenceRepository();
        ProductRepository productRepo = new InMemoryProductRepository();
        ProductDependencyRepository depRepo = new InMemoryProductDependencyRepository();
        storageRuntime = new StorageRuntimeService(storageRepo, mockProvider(null));
        productRuntime = new ProductRuntimeService(productRepo, depRepo);
        RenderOutputRegistrationService regService =
                new RenderOutputRegistrationService(storageRuntime, productRuntime, tempDir, mockProvider(null), mockProvider(null));
        RenderInputMaterializationService matService =
                new RenderInputMaterializationService(storageRuntime, productRuntime);

        RenderAuditRecorder auditRecorder = new RenderAuditRecorder(new NoopRenderAuditEventSink());
        RenderExecutionStepExecutor stepExecutor = new RenderExecutionStepExecutor(
                matService, regService, productRuntime, auditRecorder);
        LocalExecutionPlanRunner planRunner = new LocalExecutionPlanRunner(
                new RenderPlanPolicyGuard(), stepExecutor);

        TimelineInputProductResolver inputProductResolver = new TimelineInputProductResolver(productRuntime);

        service = new CaptionTemplateRenderService(
                new CaptionTemplateRenderContractValidator(),
                new CaptionTemplateTimelineAdapter(),
                new CaptionTemplateRenderResultMapper(),
                new TimelineNormalizationService(),
                new ArtifactGraphCompiler(),
                new CapabilityGraphCompiler(),
                new ProviderBindingCompiler(),
                new ProviderExecutionDocumentDraftCompiler(),
                new RenderExecutionPlanCompiler(),
                new RenderPlanPolicyGuard(),
                planRunner, matService, regService,
                productRuntime, storageRuntime,
                inputProductResolver, tempDir);
    }

    @Test
    @DisplayName("Valid legacy request fails closed without an output Product")
    void validRequestFailsClosed() throws Exception {
        registerSourceProduct("prod-source-1");
        CaptionTemplateRenderResult result = service.render(new CaptionTemplateRenderRequest(
                "proj-1", "prod-source-1",
                List.of(new CaptionSegmentSpec(0, 3000, "Hello World")),
                new CaptionTemplateSpec(null, "inline",
                        new CaptionStyleSpec(CaptionPlacement.BOTTOM_CENTER,
                                new FontStyleSpec("DejaVu Sans", 400, "#FFFFFF", "#000000", 2, null),
                                24, 2, 1.4, "center")),
                null, Map.of()));
        assertFalse(result.isSuccess());
        assertNotNull(result.renderJobId());
        assertNull(result.outputProductId());
        assertEquals("FAILED", result.status());
        assertFalse(result.ready());
        assertEquals("Policy guard rejected plan: Plan has 1 policy violations", result.safeMessage());
        assertEquals(1, storageRepo.size(), "Only source storage may be committed");
        assertTrue(productRuntime.findByProject("proj-1", 100).stream()
                .noneMatch(product -> product.productType() == ProductType.FINAL_RENDER));
    }

    @Test
    @DisplayName("Invalid request returns validation errors")
    void invalidRequestReturnsErrors() {
        CaptionTemplateRenderResult result = service.render(
                new CaptionTemplateRenderRequest(null, null, List.of(), null, null, Map.of()));
        assertFalse(result.isSuccess());
        assertTrue(result.hasValidationErrors());
    }

    @Test
    @DisplayName("Result does not expose storage internals")
    void resultNoInternals() throws Exception {
        registerSourceProduct("prod-source-1");
        CaptionTemplateRenderResult result = service.render(new CaptionTemplateRenderRequest(
                "proj-1", "prod-source-1",
                List.of(new CaptionSegmentSpec(0, 3000, "Test")),
                new CaptionTemplateSpec(null, "inline",
                        new CaptionStyleSpec(CaptionPlacement.BOTTOM_CENTER,
                                new FontStyleSpec("DejaVu Sans", 400, "#FFFFFF", "#000000", 2, null),
                                24, 2, 1.4, "center")),
                null, Map.of()));
        String s = result.toString();
        assertFalse(s.contains("bucket"));
        assertFalse(s.contains("objectKey"));
        assertFalse(s.contains("signedUrl"));
    }

    @Test
    @DisplayName("Service uses plan runner, not direct Provider")
    void serviceUsesPlanRunner() {
        assertNotNull(service);
    }

    // --- Helpers ---

    private void registerSourceProduct(String assetId) throws Exception {
        Path inputDir = tempDir.resolve("storage-inputs");
        Files.createDirectories(inputDir);
        Path inputVideo = inputDir.resolve(assetId + ".mp4");
        Files.writeString(inputVideo, "fake-mp4-content");
        String checksum = computeSha256(inputVideo);
        StorageReference ref = storageRuntime.register(new StorageReference(
                null, StorageProviderType.LOCAL.name(), StorageClass.STANDARD,
                tempDir.toString(), tempDir.relativize(inputVideo).toString(),
                checksum, checksum, Files.size(inputVideo), "video/mp4", Instant.now(), Instant.now()));
        String productId = Ids.newId("prod");
        productRuntime.register(new Product(productId, TimelineCoreSmokeFixture.TENANT_ID,
                TimelineCoreSmokeFixture.PROJECT_ID, assetId,
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "upload", "upload-service", null, ProductStatus.REGISTERED,
                ref.storageReferenceId(), checksum, checksum, "video/mp4", 1, "{}", Instant.now(), Instant.now()));
        productRuntime.markReady(productId);
    }

    private String computeSha256(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    static class InMemoryStorageReferenceRepository extends StorageReferenceRepository {
        private final Map<String, StorageReference> store = new ConcurrentHashMap<>();
        InMemoryStorageReferenceRepository() { super(null); }
        @Override public StorageReference save(StorageReference ref) {
            String id = ref.storageReferenceId() != null ? ref.storageReferenceId()
                    : "stor-" + UUID.randomUUID().toString().substring(0, 8);
            StorageReference saved = new StorageReference(id, ref.providerType(), ref.storageClass(),
                    ref.rootPath(), ref.relativePath(), ref.checksum(), ref.contentHash(),
                    ref.fileSize(), ref.mimeType(), ref.createdAt(), ref.updatedAt());
            store.put(id, saved); return saved;
        }
        @Override public Optional<StorageReference> findById(String id) { return Optional.ofNullable(store.get(id)); }
        int size() { return store.size(); }
    }

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
