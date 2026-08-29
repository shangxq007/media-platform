package com.example.platform.render.api;

import com.example.platform.render.api.dto.*;
import com.example.platform.render.app.caption.*;
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
 * API-level E2E smoke test for Caption Template Render.
 *
 * <p>Proves the full API boundary fails closed after removal of the legacy
 * plan-based Provider authority, without product/storage publication or leaks.</p>
 *
 * <p>Uses real service wiring (not mock service) to verify the full product loop
 * through the API boundary.</p>
 */
class CaptionTemplateRenderApiE2ESmokeTest {
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
    private RenderAuditRecorder auditRecorder;
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
        auditRecorder = new RenderAuditRecorder(auditSink);

        RenderExecutionStepExecutor stepExecutor = new RenderExecutionStepExecutor(
                matService, regService, productRuntime, auditRecorder);
        LocalExecutionPlanRunner planRunner = new LocalExecutionPlanRunner(
                new RenderPlanPolicyGuard(), stepExecutor);

        CaptionTemplateRenderService service = new CaptionTemplateRenderService(
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
                new TimelineInputProductResolver(productRuntime), tempDir);

        controller = new CaptionTemplateRenderController(
                service, null, new CaptionTemplateRenderApiMapper(), auditRecorder);
    }

    @Test
    @DisplayName("API E2E: valid legacy request fails closed without outputProductId")
    void apiE2eSuccess() throws Exception {
        registerSourceProduct();

        CaptionTemplateRenderApiRequest request = apiRequest();
        ResponseEntity<CaptionTemplateRenderApiResponse> response =
                controller.render("tenant-1", "proj-1", request);

        assertFailClosedResponse(response);
    }

    @Test
    @DisplayName("API E2E: fail-closed render creates no READY FINAL_RENDER Product")
    void apiE2eOutputProduct() throws Exception {
        registerSourceProduct();

        ResponseEntity<CaptionTemplateRenderApiResponse> response =
                controller.render("tenant-1", "proj-1", apiRequest());

        assertFailClosedResponse(response);
        assertNoOutputCommit();
    }

    @Test
    @DisplayName("API E2E: fail-closed render creates no fabricated output lineage")
    void apiE2eLineage() throws Exception {
        registerSourceProduct();

        ResponseEntity<CaptionTemplateRenderApiResponse> response =
                controller.render("tenant-1", "proj-1", apiRequest());

        assertFailClosedResponse(response);
        assertNoOutputCommit();
    }

    @Test
    @DisplayName("API E2E: fail-closed render does not commit storage")
    void apiE2eStorageRegistration() throws Exception {
        registerSourceProduct();

        ResponseEntity<CaptionTemplateRenderApiResponse> response =
                controller.render("tenant-1", "proj-1", apiRequest());

        assertFailClosedResponse(response);
        assertEquals(1, storageRepo.size(), "Only the source storage reference may exist");
        assertNoOutputCommit();
    }

    @Test
    @DisplayName("API E2E: audit emits REQUESTED and FAILED but never COMPLETED")
    void apiE2eAuditEvents() throws Exception {
        registerSourceProduct();

        controller.render("tenant-1", "proj-1", apiRequest());

        assertTrue(auditSink.findAll().stream()
                .anyMatch(e -> e.eventType() == RenderAuditEventType.CAPTION_TEMPLATE_RENDER_REQUESTED));
        assertTrue(auditSink.findAll().stream()
                .anyMatch(e -> e.eventType() == RenderAuditEventType.CAPTION_TEMPLATE_RENDER_FAILED));
        assertFalse(auditSink.findAll().stream()
                .anyMatch(e -> e.eventType() == RenderAuditEventType.CAPTION_TEMPLATE_RENDER_COMPLETED));
    }

    @Test
    @DisplayName("API E2E: failed audit exposes no outputProductId")
    void apiE2eAuditIncludesOutputProductId() throws Exception {
        registerSourceProduct();

        controller.render("tenant-1", "proj-1", apiRequest());

        var failures = auditSink.findAll().stream()
                .filter(e -> e.eventType() == RenderAuditEventType.CAPTION_TEMPLATE_RENDER_FAILED)
                .toList();
        assertEquals(1, failures.size());
        assertNull(failures.getFirst().outputProductId());
    }

    @Test
    @DisplayName("API E2E: service and controller audit retain fail-closed trail")
    void apiE2eServiceAuditEvents() throws Exception {
        registerSourceProduct();

        controller.render("tenant-1", "proj-1", apiRequest());

        assertFalse(auditSink.findAll().isEmpty(), "Audit events should be emitted");
        assertTrue(auditSink.findAll().size() >= 2,
                "At least REQUESTED + FAILED events expected");
        assertFalse(auditSink.findAll().stream()
                .anyMatch(e -> e.eventType() == RenderAuditEventType.CAPTION_TEMPLATE_RENDER_COMPLETED));
    }

    @Test
    @DisplayName("API E2E: validation failure returns 400 with safe errors")
    void apiE2eValidationFailure() {
        CaptionTemplateRenderApiRequest invalidRequest = new CaptionTemplateRenderApiRequest(
                null, List.of(), null, null, null);

        ResponseEntity<CaptionTemplateRenderApiResponse> response =
                controller.render("tenant-1", "proj-1", invalidRequest);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("VALIDATION_FAILED", response.getBody().status());
        assertFalse(response.getBody().ready());
    }

    @Test
    @DisplayName("API E2E: validation failure emits VALIDATION_FAILED audit event")
    void apiE2eValidationAudit() {
        CaptionTemplateRenderApiRequest invalidRequest = new CaptionTemplateRenderApiRequest(
                null, List.of(), null, null, null);

        controller.render("tenant-1", "proj-1", invalidRequest);

        assertTrue(auditSink.findAll().stream()
                .anyMatch(e -> e.eventType() == RenderAuditEventType.CAPTION_TEMPLATE_RENDER_VALIDATION_FAILED));
    }

    @Test
    @DisplayName("API E2E: response does not expose provider/storage internals")
    void apiE2eResponseSafe() throws Exception {
        registerSourceProduct();

        ResponseEntity<CaptionTemplateRenderApiResponse> response =
                controller.render("tenant-1", "proj-1", apiRequest());

        assertFailClosedResponse(response);
        String str = response.getBody().toString();
        assertFalse(str.contains("bucket"));
        assertFalse(str.contains("objectKey"));
        assertFalse(str.contains("signedUrl"));
        assertFalse(str.contains("providerName"));
        assertFalse(str.contains("renderCorrelationId"));
        assertFalse(str.contains("renderExecutionPlanId"));
        assertFalse(str.contains("artifactGraphId"));
    }

    @Test
    @DisplayName("API E2E: no full caption text in audit payload")
    void apiE2eAuditNoCaptionText() throws Exception {
        registerSourceProduct();

        controller.render("tenant-1", "proj-1", apiRequest());

        auditSink.findAll().forEach(event -> {
            if (event.message() != null) {
                assertFalse(event.message().contains("Hello World"),
                        "Audit must not contain caption text");
            }
        });
    }

    @Test
    @DisplayName("API E2E: fail-closed controller calls neither Remotion nor local Provider")
    void apiE2eNoRemotion() throws Exception {
        registerSourceProduct();

        ResponseEntity<CaptionTemplateRenderApiResponse> response =
                controller.render("tenant-1", "proj-1", apiRequest());

        assertFailClosedResponse(response);
        assertFalse(response.getBody().toString().contains("remotion"));
    }

    @Test
    @DisplayName("API E2E: fail-closed response cannot hand off a fabricated product")
    void apiE2eOutputProductIdUsable() throws Exception {
        registerSourceProduct();

        ResponseEntity<CaptionTemplateRenderApiResponse> response =
                controller.render("tenant-1", "proj-1", apiRequest());

        assertFailClosedResponse(response);
        assertNoOutputCommit();
    }

    // --- Download contract ---

    @Test
    @DisplayName("API E2E: fail-closed response exposes no download handoff or storage internals")
    void apiE2eDownloadContract() throws Exception {
        registerSourceProduct();

        ResponseEntity<CaptionTemplateRenderApiResponse> response =
                controller.render("tenant-1", "proj-1", apiRequest());

        assertFailClosedResponse(response);
        String str = response.getBody().toString();
        assertFalse(str.contains("downloadUrl"));
        assertFalse(str.contains("signedUrl"));
        assertFalse(str.contains("presign"));
    }

    private void assertFailClosedResponse(ResponseEntity<CaptionTemplateRenderApiResponse> response) {
        assertEquals(500, response.getStatusCode().value());
        CaptionTemplateRenderApiResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("FAILED", body.status());
        assertFalse(body.ready());
        assertNull(body.outputProductId());
        assertNull(body.renderJobId());
        assertTrue(body.validationErrors().isEmpty());
        assertEquals("Policy guard rejected plan: Plan has 1 policy violations", body.message());
    }

    private void assertNoOutputCommit() {
        assertTrue(productRuntime.findByProject("proj-1", 100).stream()
                .noneMatch(product -> product.productType() == ProductType.FINAL_RENDER
                        || product.status() == ProductStatus.FAILED));
        assertFalse(auditSink.findAll().stream()
                .anyMatch(event -> event.eventType() == RenderAuditEventType.CAPTION_TEMPLATE_RENDER_COMPLETED));
    }

    // --- Helpers ---

    private CaptionTemplateRenderApiRequest apiRequest() {
        return new CaptionTemplateRenderApiRequest(
                SOURCE_ASSET_ID,
                List.of(new CaptionTemplateSegmentDto(0L, 3000L, "Hello World")),
                new CaptionTemplateDto("tpl-1", "Basic",
                        new CaptionTemplateStyleDto("BOTTOM_CENTER",
                                new CaptionFontStyleDto("DejaVu Sans", 400, "#FFFFFF", "#000000", 2, null),
                                24, 2, 1.4, "CENTER")),
                new CaptionOutputProfileDto(1920, 1080, 30.0, "mp4"),
                Map.of("requestSource", "api-test"));
    }

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
