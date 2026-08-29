package com.example.platform.render.app.caption;

import com.example.platform.render.api.CaptionTemplateRenderApiMapper;
import com.example.platform.render.api.CaptionTemplateRenderController;
import com.example.platform.render.api.dto.*;
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
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E fail-closed delivery contract after removal of legacy Provider execution.
 * Proves no fabricated outputProductId, lookup handoff, or storage publication.
 */
class CaptionTemplateRenderDeliveryE2ESmokeTest {
    @SuppressWarnings("unchecked")
    private static <T> org.springframework.beans.factory.ObjectProvider<T> mockProvider(T instance) {
        org.springframework.beans.factory.ObjectProvider<T> op = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(op.getIfAvailable()).thenReturn(instance);
        return op;
    }


    @TempDir Path tempDir;
    private StorageRuntimeService storageRuntime;
    private ProductRuntimeService productRuntime;
    private CaptionTemplateRenderController controller;
    private InMemoryRenderAuditEventSink auditSink;
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

        auditSink = new InMemoryRenderAuditEventSink();
        RenderAuditRecorder auditRecorder = new RenderAuditRecorder(auditSink);

        RenderExecutionStepExecutor stepExecutor = new RenderExecutionStepExecutor(
                matService, regService, productRuntime, auditRecorder);
        LocalExecutionPlanRunner planRunner = new LocalExecutionPlanRunner(
                new RenderPlanPolicyGuard(), stepExecutor);

        CaptionTemplateRenderService renderService = new CaptionTemplateRenderService(
                new CaptionTemplateRenderContractValidator(),
                new CaptionTemplateTimelineAdapter(),
                new CaptionTemplateRenderResultMapper(),
                new TimelineNormalizationService(), new ArtifactGraphCompiler(),
                new CapabilityGraphCompiler(), new ProviderBindingCompiler(),
                new ProviderExecutionDocumentDraftCompiler(), new RenderExecutionPlanCompiler(),
                new RenderPlanPolicyGuard(), planRunner, matService, regService,
                productRuntime, storageRuntime,
                new TimelineInputProductResolver(productRuntime), tempDir);

        CaptionTemplateResultLookupService lookupService =
                new CaptionTemplateResultLookupService(productRuntime);

        controller = new CaptionTemplateRenderController(
                renderService, lookupService, new CaptionTemplateRenderApiMapper(), auditRecorder);
    }

    @Test
    @DisplayName("E2E: fail-closed render exposes no outputProductId for lookup")
    void renderThenLookup() throws Exception {
        registerSourceProduct();

        // Render
        CaptionTemplateRenderApiRequest request = new CaptionTemplateRenderApiRequest(
                SOURCE_ASSET_ID,
                List.of(new CaptionTemplateSegmentDto(0L, 3000L, "Hello")),
                new CaptionTemplateDto("tpl-inline", "inline",
                        new CaptionTemplateStyleDto("BOTTOM_CENTER",
                                new CaptionFontStyleDto("DejaVu Sans", 400, "#FFFFFF", "#000000", 2, null),
                                24, 2, 1.4, "center")),
                null, Map.of());
        ResponseEntity<CaptionTemplateRenderApiResponse> renderResponse =
                controller.render("tenant-1", "proj-1", request);
        assertFailClosedRender(renderResponse);
        assertNoOutputCommit();
    }

    @Test
    @DisplayName("E2E: fail-closed render publishes no download contract")
    void renderThenLookupDownloadContract() throws Exception {
        registerSourceProduct();

        CaptionTemplateRenderApiRequest request = new CaptionTemplateRenderApiRequest(
                SOURCE_ASSET_ID,
                List.of(new CaptionTemplateSegmentDto(0L, 3000L, "Hello")),
                new CaptionTemplateDto("tpl-inline", "inline",
                        new CaptionTemplateStyleDto("BOTTOM_CENTER",
                                new CaptionFontStyleDto("DejaVu Sans", 400, "#FFFFFF", "#000000", 2, null),
                                24, 2, 1.4, "center")),
                null, Map.of());
        ResponseEntity<CaptionTemplateRenderApiResponse> renderResponse =
                controller.render("tenant-1", "proj-1", request);
        assertFailClosedRender(renderResponse);
        assertNull(renderResponse.getBody().outputProductId());
        assertNoOutputCommit();
    }

    @Test
    @DisplayName("E2E: fail-closed render response contains no storage internals")
    void renderThenLookupNoInternals() throws Exception {
        registerSourceProduct();

        CaptionTemplateRenderApiRequest request = new CaptionTemplateRenderApiRequest(
                SOURCE_ASSET_ID,
                List.of(new CaptionTemplateSegmentDto(0L, 3000L, "Hello")),
                new CaptionTemplateDto("tpl-inline", "inline",
                        new CaptionTemplateStyleDto("BOTTOM_CENTER",
                                new CaptionFontStyleDto("DejaVu Sans", 400, "#FFFFFF", "#000000", 2, null),
                                24, 2, 1.4, "center")),
                null, Map.of());
        ResponseEntity<CaptionTemplateRenderApiResponse> renderResponse =
                controller.render("tenant-1", "proj-1", request);
        assertFailClosedRender(renderResponse);
        String str = renderResponse.getBody().toString();
        assertFalse(str.contains("bucket"));
        assertFalse(str.contains("signedUrl"));
        assertFalse(str.contains("storageReferenceId"));
    }

    @Test
    @DisplayName("E2E: lookup nonexistent product returns 404")
    void lookupNonexistentReturns404() {
        ResponseEntity<CaptionTemplateRenderResultLookupResponse> response =
                controller.lookupResult("tenant-1", "proj-1", "nonexistent-product");
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    @DisplayName("E2E: failed render audit has no completion or lookup publication")
    void auditEventsForRenderAndLookup() throws Exception {
        registerSourceProduct();

        CaptionTemplateRenderApiRequest request = new CaptionTemplateRenderApiRequest(
                SOURCE_ASSET_ID,
                List.of(new CaptionTemplateSegmentDto(0L, 3000L, "Hello")),
                new CaptionTemplateDto("tpl-inline", "inline",
                        new CaptionTemplateStyleDto("BOTTOM_CENTER",
                                new CaptionFontStyleDto("DejaVu Sans", 400, "#FFFFFF", "#000000", 2, null),
                                24, 2, 1.4, "center")),
                null, Map.of());
        ResponseEntity<CaptionTemplateRenderApiResponse> renderResponse =
                controller.render("tenant-1", "proj-1", request);
        assertFailClosedRender(renderResponse);

        assertTrue(auditSink.findAll().stream()
                .anyMatch(e -> e.eventType() == RenderAuditEventType.CAPTION_TEMPLATE_RENDER_REQUESTED));
        assertTrue(auditSink.findAll().stream()
                .anyMatch(e -> e.eventType() == RenderAuditEventType.CAPTION_TEMPLATE_RENDER_FAILED));
        assertFalse(auditSink.findAll().stream()
                .anyMatch(e -> e.eventType() == RenderAuditEventType.CAPTION_TEMPLATE_RENDER_COMPLETED
                        || e.eventType() == RenderAuditEventType.CAPTION_TEMPLATE_RESULT_LOOKUP_COMPLETED));
    }

    @Test
    @DisplayName("E2E: fail-closed render creates no fabricated ProductDependency lineage")
    void lineageStillValid() throws Exception {
        registerSourceProduct();

        CaptionTemplateRenderApiRequest request = new CaptionTemplateRenderApiRequest(
                SOURCE_ASSET_ID,
                List.of(new CaptionTemplateSegmentDto(0L, 3000L, "Hello")),
                new CaptionTemplateDto("tpl-inline", "inline",
                        new CaptionTemplateStyleDto("BOTTOM_CENTER",
                                new CaptionFontStyleDto("DejaVu Sans", 400, "#FFFFFF", "#000000", 2, null),
                                24, 2, 1.4, "center")),
                null, Map.of());
        ResponseEntity<CaptionTemplateRenderApiResponse> renderResponse =
                controller.render("tenant-1", "proj-1", request);
        assertFailClosedRender(renderResponse);
        assertNoOutputCommit();
    }

    private void assertFailClosedRender(
            ResponseEntity<CaptionTemplateRenderApiResponse> renderResponse) {
        assertEquals(500, renderResponse.getStatusCode().value());
        assertNotNull(renderResponse.getBody());
        assertEquals("FAILED", renderResponse.getBody().status());
        assertFalse(renderResponse.getBody().ready());
        assertNull(renderResponse.getBody().renderJobId());
        assertNull(renderResponse.getBody().outputProductId());
        assertEquals("Policy guard rejected plan: Plan has 1 policy violations", renderResponse.getBody().message());
    }

    private void assertNoOutputCommit() {
        assertEquals(1, storageRepo.size(), "Only source storage may be committed");
        assertTrue(productRuntime.findByProject("proj-1", 100).stream()
                .noneMatch(product -> product.productType() == ProductType.FINAL_RENDER));
    }

    // --- Helpers ---

    private static final String SOURCE_ASSET_ID = "source-asset-1";

    private void registerSourceProduct() throws Exception {
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
            String id = ref.storageReferenceId() != null ? ref.storageReferenceId() : "stor-" + UUID.randomUUID().toString().substring(0, 8);
            StorageReference saved = new StorageReference(id, ref.providerType(), ref.storageClass(),
                    ref.rootPath(), ref.relativePath(), ref.checksum(), ref.contentHash(),
                    ref.fileSize(), ref.mimeType(), ref.createdAt(), ref.updatedAt());
            store.put(id, saved); return saved;
        }
        @Override public java.util.Optional<StorageReference> findById(String id) { return java.util.Optional.ofNullable(store.get(id)); }
        int size() { return store.size(); }
    }

    static class InMemoryProductRepository extends ProductRepository {
        private final Map<String, Product> store = new ConcurrentHashMap<>();
        InMemoryProductRepository() { super(null); }
        @Override public Product save(Product p) { store.put(p.productId(), p); return p; }
        @Override public java.util.Optional<Product> findById(String id) { return java.util.Optional.ofNullable(store.get(id)); }
        @Override public List<Product> findByAsset(String a) { return store.values().stream().filter(p -> a.equals(p.ownerAssetId())).toList(); }
        @Override public java.util.Optional<Product> findLatest(String a, ProductType t) { return findByAsset(a).stream().filter(p -> p.productType() == t).findFirst(); }
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
