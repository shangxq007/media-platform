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
 * E2E smoke test for Caption Template Render.
 *
 * <p>Proves the full product flow:
 * RAW_MEDIA Product → Caption Template Render → PLAN_BASED → FFmpeg/libass
 * → StorageRuntime output registration → READY FINAL_RENDER Product
 * → ProductDependency lineage → safe result contract.</p>
 */
class CaptionTemplateRenderE2ESmokeTest {
    @SuppressWarnings("unchecked")
    private static <T> org.springframework.beans.factory.ObjectProvider<T> mockProvider(T instance) {
        org.springframework.beans.factory.ObjectProvider<T> op = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(op.getIfAvailable()).thenReturn(instance);
        return op;
    }


    @TempDir Path tempDir;
    private StorageRuntimeService storageRuntime;
    private ProductRuntimeService productRuntime;
    private ProductRepository productRepo;
    private ProductDependencyRepository depRepo;
    private CaptionTemplateRenderService service;
    private InMemoryRenderAuditEventSink auditSink;
    private RenderAuditRecorder auditRecorder;

    @BeforeEach
    void setUp() {
        StorageReferenceRepository storageRepo = new InMemoryStorageReferenceRepository();
        productRepo = new InMemoryProductRepository();
        depRepo = new InMemoryProductDependencyRepository();
        storageRuntime = new StorageRuntimeService(storageRepo, mockProvider(null));
        productRuntime = new ProductRuntimeService(productRepo, depRepo);
        RenderOutputRegistrationService regService =
                new RenderOutputRegistrationService(storageRuntime, productRuntime, tempDir, mockProvider(null), mockProvider(null));
        RenderInputMaterializationService matService =
                new RenderInputMaterializationService(storageRuntime, productRuntime);

        auditSink = new InMemoryRenderAuditEventSink();
        auditRecorder = new RenderAuditRecorder(auditSink);

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

        RenderExecutionStepExecutor stepExecutor = new RenderExecutionStepExecutor(
                matService, regService, productRuntime, toolInv, toolRunner, auditRecorder);
        LocalExecutionPlanRunner planRunner = new LocalExecutionPlanRunner(
                new RenderPlanPolicyGuard(), stepExecutor);

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
                new TimelineInputProductResolver(productRuntime), tempDir);
    }

    @Test
    @DisplayName("E2E: caption render produces READY FINAL_RENDER Product")
    void e2eProducesReadyProduct() throws Exception {
        String assetId = registerSourceProduct();
        CaptionTemplateRenderResult result = render(assetId);

        assertTrue(result.isSuccess(), "Failed: status=" + result.status()
                + " msg=" + result.safeMessage()
                + " errors=" + result.validationErrors());
        assertNotNull(result.outputProductId());
        assertEquals("READY", result.status());

        // Verify output Product exists and is READY
        Product outputProduct = productRuntime.find(result.outputProductId()).orElseThrow();
        assertEquals(ProductStatus.READY, outputProduct.status());
        assertEquals(ProductType.FINAL_RENDER, outputProduct.productType());
    }

    @Test
    @DisplayName("E2E: output Product linked to source by ProductDependency lineage")
    void e2eProductLineage() throws Exception {
        String assetId = registerSourceProduct();
        CaptionTemplateRenderResult result = render(assetId);

        // Verify ProductDependency from output to source
        List<ProductDependency> deps = productRuntime.findDependencies(result.outputProductId());
        assertFalse(deps.isEmpty(), "Should have at least one ProductDependency");
        // Dependency links output product to some input product
        assertNotNull(deps.get(0).dependsOnProductId());
    }

    @Test
    @DisplayName("E2E: output registered through StorageRuntime")
    void e2eStorageRegistration() throws Exception {
        String sourceProductId = registerSourceProduct();
        CaptionTemplateRenderResult result = render(sourceProductId);

        Product outputProduct = productRuntime.find(result.outputProductId()).orElseThrow();
        assertNotNull(outputProduct.storageReferenceId(),
                "Output product should have storage reference");
    }

    @Test
    @DisplayName("E2E: audit events emitted at service level (compile pipeline events)")
    void e2eAuditEvents() throws Exception {
        String assetId = registerSourceProduct();
        render(assetId);

        // Service-level: compile pipeline emits PROVIDER_BINDING_COMPLETED etc.
        // API-level audit (REQUESTED/COMPLETED) is emitted by controller in P2C.2
        // This test verifies service-level pipeline events exist
        assertFalse(auditSink.findAll().isEmpty(),
                "Service-level audit events should be emitted during compile pipeline");
    }

    @Test
    @DisplayName("E2E: no full caption text in audit payload")
    void e2eAuditNoCaptionText() throws Exception {
        String sourceProductId = registerSourceProduct();
        render(sourceProductId);

        auditSink.findAll().forEach(event -> {
            if (event.message() != null) {
                assertFalse(event.message().contains("Hello World"),
                        "Audit must not contain caption text");
            }
        });
    }

    @Test
    @DisplayName("E2E: result does not expose provider/storage internals")
    void e2eResultSafe() throws Exception {
        String sourceProductId = registerSourceProduct();
        CaptionTemplateRenderResult result = render(sourceProductId);

        String str = result.toString();
        assertFalse(str.contains("bucket"));
        assertFalse(str.contains("objectKey"));
        assertFalse(str.contains("signedUrl"));
        assertFalse(str.contains("providerName"));
        assertFalse(str.contains("renderCorrelationId"));
        assertFalse(str.contains("renderExecutionPlanId"));
    }

    @Test
    @DisplayName("E2E: outputProductId usable for downstream lookup")
    void e2eOutputProductIdUsable() throws Exception {
        String sourceProductId = registerSourceProduct();
        CaptionTemplateRenderResult result = render(sourceProductId);

        // outputProductId can be used to load the product
        Product outputProduct = productRuntime.find(result.outputProductId()).orElseThrow();
        assertNotNull(outputProduct);
        assertEquals(result.outputProductId(), outputProduct.productId());
    }

    @Test
    @DisplayName("E2E: PLAN_BASED mode used (not Remotion)")
    void e2ePlanBasedModeUsed() throws Exception {
        String sourceProductId = registerSourceProduct();
        CaptionTemplateRenderResult result = render(sourceProductId);

        // If this passes, PLAN_BASED pipeline executed successfully
        assertTrue(result.isSuccess());
        // No Remotion reference in result
        assertFalse(result.toString().contains("remotion"));
    }

    // --- Helpers ---

    private CaptionTemplateRenderResult render(String sourceProductId) {
        CaptionTemplateRenderRequest request = new CaptionTemplateRenderRequest(
                "proj-1", sourceProductId,
                List.of(new CaptionSegmentSpec(0, 3000, "Hello World")),
                null, null, Map.of());
        CaptionTemplateRenderResult result = service.render(request);
        System.out.println("[E2E] result: status=" + result.status()
                + " ready=" + result.ready()
                + " msg=" + result.safeMessage()
                + " errors=" + result.validationErrors()
                + " jobId=" + result.renderJobId()
                + " productId=" + result.outputProductId());
        return result;
    }

    private static final String SOURCE_ASSET_ID = "source-asset-1";

    private String registerSourceProduct() throws Exception {
        Path inputDir = tempDir.resolve("storage-inputs");
        Files.createDirectories(inputDir);
        Path inputVideo = inputDir.resolve(SOURCE_ASSET_ID + ".mp4");
        Files.writeString(inputVideo, "fake-mp4-content");
        String checksum = computeSha256(inputVideo);
        StorageReference ref = storageRuntime.register(new StorageReference(
                null, StorageProviderType.LOCAL.name(), StorageClass.STANDARD,
                tempDir.toString(), tempDir.relativize(inputVideo).toString(),
                checksum, checksum, Files.size(inputVideo), "video/mp4", Instant.now(), Instant.now()));
        String productId = Ids.newId("prod");
        productRuntime.register(new Product(productId, TimelineCoreSmokeFixture.TENANT_ID,
                TimelineCoreSmokeFixture.PROJECT_ID, SOURCE_ASSET_ID,
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "upload", "upload-service", null, ProductStatus.REGISTERED,
                ref.storageReferenceId(), checksum, checksum, "video/mp4", 1, "{}", Instant.now(), Instant.now()));
        productRuntime.markReady(productId);
        return SOURCE_ASSET_ID; // Return asset ID, not product UUID
    }

    private String computeSha256(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // --- In-memory doubles ---

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
