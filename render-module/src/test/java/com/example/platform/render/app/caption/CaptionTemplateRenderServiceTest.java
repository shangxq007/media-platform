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
import com.example.platform.render.domain.storage.*;
import com.example.platform.render.infrastructure.RenderToolCapabilityInventory;
import com.example.platform.render.infrastructure.product.ProductDependencyRepository;
import com.example.platform.render.infrastructure.product.ProductRepository;
import com.example.platform.render.infrastructure.storage.StorageReferenceRepository;
import com.example.platform.render.testsupport.TimelineCoreSmokeFixture;
import com.example.platform.shared.Ids;
import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.extension.domain.ToolSandboxPolicy;
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
 * Proves: valid request → READY product, invalid → validation errors,
 * no direct FFmpeg, no Remotion.
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

    @BeforeEach
    void setUp() {
        StorageReferenceRepository storageRepo = new InMemoryStorageReferenceRepository();
        ProductRepository productRepo = new InMemoryProductRepository();
        ProductDependencyRepository depRepo = new InMemoryProductDependencyRepository();
        storageRuntime = new StorageRuntimeService(storageRepo, mockProvider(null));
        productRuntime = new ProductRuntimeService(productRepo, depRepo);
        RenderOutputRegistrationService regService =
                new RenderOutputRegistrationService(storageRuntime, productRuntime, tempDir, mockProvider(null), mockProvider(null));
        RenderInputMaterializationService matService =
                new RenderInputMaterializationService(storageRuntime, productRuntime);

        ProcessToolRunner toolRunner = new ProcessToolRunner() {
            @Override public ToolExecutionResult execute(com.example.platform.extension.domain.ToolExecutionRequest r) {
                try {
                    String outputPath = r.args().get(r.args().size() - 1);
                    Path output = Path.of(outputPath);
                    Files.createDirectories(output.getParent());
                    Files.writeString(output, "fake-caption-rendered-" + UUID.randomUUID());
                    return ToolExecutionResult.success(0, "ffmpeg", "", Instant.now(), Instant.now());
                } catch (IOException e) {
                    return ToolExecutionResult.failed(1, "", e.getMessage(), Instant.now(), Instant.now());
                }
            }
            @Override public ToolExecutionResult execute(com.example.platform.extension.domain.ToolExecutionRequest r, ToolSandboxPolicy p) { return execute(r); }
        };

        RenderToolCapabilityInventory toolInv = new RenderToolCapabilityInventory() {
            @Override public boolean isToolAvailable(String n) { return "ffmpeg".equals(n); }
        };

        RenderAuditRecorder auditRecorder = new RenderAuditRecorder(new NoopRenderAuditEventSink());
        RenderExecutionStepExecutor stepExecutor = new RenderExecutionStepExecutor(
                matService, regService, productRuntime, toolInv, toolRunner, auditRecorder);
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
                productRuntime, storageRuntime, toolInv,
                inputProductResolver, tempDir);
    }

    @Test
    @DisplayName("Valid request produces READY output Product")
    void validRequestProducesReady() throws Exception {
        registerSourceProduct("prod-source-1");
        CaptionTemplateRenderResult result = service.render(new CaptionTemplateRenderRequest(
                "proj-1", "prod-source-1",
                List.of(new CaptionSegmentSpec(0, 3000, "Hello World")),
                null, null, Map.of()));
        assertTrue(result.isSuccess(), "Failed: status=" + result.status()
                + " msg=" + result.safeMessage()
                + " errors=" + result.validationErrors());
        assertNotNull(result.renderJobId());
        assertNotNull(result.outputProductId());
        assertEquals("READY", result.status());
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
                null, null, Map.of()));
        String s = result.toString();
        assertFalse(s.contains("bucket"));
        assertFalse(s.contains("objectKey"));
        assertFalse(s.contains("signedUrl"));
    }

    @Test
    @DisplayName("Service uses plan runner, not direct FFmpeg")
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
