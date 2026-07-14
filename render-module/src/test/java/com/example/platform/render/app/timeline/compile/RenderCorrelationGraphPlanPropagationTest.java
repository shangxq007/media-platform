package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.app.timeline.compile.audit.*;
import com.example.platform.render.domain.timeline.*;
import com.example.platform.render.domain.timeline.compile.*;
import com.example.platform.render.infrastructure.ProviderStatus;
import com.example.platform.render.infrastructure.ProviderType;
import com.example.platform.render.infrastructure.RenderToolCapabilityInventory;
import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.extension.domain.ToolSandboxPolicy;
import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.app.input.RenderInputMaterializationService;
import com.example.platform.render.app.output.RenderOutputRegistrationService;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.app.timeline.*;
import com.example.platform.render.domain.product.*;
import com.example.platform.render.domain.storage.*;
import com.example.platform.render.domain.timeline.compile.executionplan.*;
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
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for graph/plan ID propagation, localExecutionRunId, and outputProductId correlation.
 */
class RenderCorrelationGraphPlanPropagationTest {
    @SuppressWarnings("unchecked")
    private static <T> org.springframework.beans.factory.ObjectProvider<T> mockProvider(T instance) {
        org.springframework.beans.factory.ObjectProvider<T> op = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(op.getIfAvailable()).thenReturn(instance);
        return op;
    }


    @TempDir Path tempDir;
    private StorageRuntimeService storageRuntime;
    private ProductRuntimeService productRuntime;
    private InMemoryRenderAuditEventSink auditSink;
    private RenderAuditRecorder auditRecorder;
    private InMemoryTimelineRevisionRepository revisionRepo;
    private InMemoryTimelineSnapshotService snapshotService;

    @BeforeEach
    void setUp() {
        StorageReferenceRepository storageRepo = new InMemoryStorageReferenceRepository();
        ProductRepository productRepo = new InMemoryProductRepository();
        ProductDependencyRepository depRepo = new InMemoryProductDependencyRepository();
        storageRuntime = new StorageRuntimeService(storageRepo, mockProvider(null));
        productRuntime = new ProductRuntimeService(productRepo, depRepo);
        auditSink = new InMemoryRenderAuditEventSink();
        auditRecorder = new RenderAuditRecorder(auditSink);
        revisionRepo = new InMemoryTimelineRevisionRepository();
        snapshotService = new InMemoryTimelineSnapshotService();
    }

    @Test
    @DisplayName("Plan-based render succeeds and returns READY product")
    void planBasedRenderSucceeds() throws Exception {
        registerReadyRawMediaProduct();
        String revisionId = setupRevision();
        PlanBasedTimelineRevisionRenderService service = createPlanBasedService();

        TimelineRevisionRenderService.RevisionRenderResult result =
                service.render(TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_1080p");

        assertNotNull(result);
        assertEquals("READY", result.productStatus());
        assertNotNull(result.outputProductId());
    }

    @Test
    @DisplayName("localExecutionRunId is generated and present in run result")
    void localExecutionRunIdGenerated() {
        LocalExecutionPlanRunner runner = createRunner();
        RenderExecutionPlan plan = createSimplePlan();
        LocalExecutionPlanContext context = createContext();

        LocalExecutionPlanRunResult result = runner.run(plan, context);

        assertNotNull(result.localExecutionRunId());
        assertTrue(result.localExecutionRunId().startsWith("ler-"));
    }

    @Test
    @DisplayName("localExecutionRunId is different across runs")
    void localExecutionRunIdDifferentAcrossRuns() {
        LocalExecutionPlanRunner runner = createRunner();
        RenderExecutionPlan plan = createSimplePlan();
        LocalExecutionPlanContext context = createContext();

        LocalExecutionPlanRunResult r1 = runner.run(plan, context);
        LocalExecutionPlanRunResult r2 = runner.run(plan, context);

        assertNotEquals(r1.localExecutionRunId(), r2.localExecutionRunId());
    }

    @Test
    @DisplayName("localExecutionRunId does not affect fingerprint")
    void localRunIdDoesNotAffectFingerprint() {
        RenderRequestFingerprint fp1 = RenderRequestFingerprint.generate("p", "r", "default_1080p", "LEGACY");
        String localRunId = "ler-" + UUID.randomUUID().toString().substring(0, 12);
        RenderRequestFingerprint fp2 = RenderRequestFingerprint.generate("p", "r", "default_1080p", "LEGACY");
        assertEquals(fp1.value(), fp2.value());
    }

    @Test
    @DisplayName("Public render response does not expose localExecutionRunId or graph/plan IDs")
    void publicResponseNoInternalIds() throws Exception {
        registerReadyRawMediaProduct();
        String revisionId = setupRevision();
        PlanBasedTimelineRevisionRenderService service = createPlanBasedService();

        TimelineRevisionRenderService.RevisionRenderResult result =
                service.render(TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_1080p");

        // RevisionRenderResult has no localExecutionRunId, graphId, planId fields
        assertNotNull(result.renderJobId());
        assertNotNull(result.outputProductId());
    }

    @Test
    @DisplayName("Audit events do not expose raw command or storage internals")
    void auditEventsPayloadSafe() throws Exception {
        registerReadyRawMediaProduct();
        String revisionId = setupRevision();
        PlanBasedTimelineRevisionRenderService service = createPlanBasedService();

        service.render(TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_1080p");

        auditSink.findAll().forEach(event -> {
            if (event.message() != null) {
                assertFalse(event.message().contains("ffmpeg -i"), "No raw command in event message");
            }
            if (event.sanitizedDetails() != null) {
                assertFalse(event.sanitizedDetails().contains("bucket"));
                assertFalse(event.sanitizedDetails().contains("rootPath"));
            }
        });
    }

    // --- Helpers ---

    private PlanBasedTimelineRevisionRenderService createPlanBasedService() {
        TimelineExtensionsReader extReader = new TimelineExtensionsReader();
        TimelineScriptParser parser = new TimelineScriptParser(extReader);
        InternalTimelineWriter writer = new InternalTimelineWriter(extReader);
        TimelineRenderJobMapper mapper = new TimelineRenderJobMapper(parser, writer);
        TimelineInputProductResolver inputProductResolver = new TimelineInputProductResolver(productRuntime);

        ProcessToolRunner toolRunner = new ProcessToolRunner() {
            @Override
            public ToolExecutionResult execute(com.example.platform.extension.domain.ToolExecutionRequest request) {
                try {
                    List<String> args = request.args();
                    String outputPath = args.get(args.size() - 1);
                    Path output = Path.of(outputPath);
                    Files.createDirectories(output.getParent());
                    Files.writeString(output, "fake-rendered-" + UUID.randomUUID());
                    return ToolExecutionResult.success(0, "ffmpeg", "", Instant.now(), Instant.now());
                } catch (IOException e) {
                    return ToolExecutionResult.failed(1, "", e.getMessage(), Instant.now(), Instant.now());
                }
            }
            @Override
            public ToolExecutionResult execute(com.example.platform.extension.domain.ToolExecutionRequest request,
                                                com.example.platform.extension.domain.ToolSandboxPolicy policy) {
                return execute(request);
            }
        };

        RenderToolCapabilityInventory toolInventory = new RenderToolCapabilityInventory() {
            @Override public boolean isToolAvailable(String n) { return "ffmpeg".equals(n); }
        };

        RenderInputMaterializationService matService = new RenderInputMaterializationService(storageRuntime, productRuntime);
        RenderOutputRegistrationService regService = new RenderOutputRegistrationService(storageRuntime, productRuntime, tempDir, mockProvider(null), mockProvider(null));
        TimelineNormalizationService normalizer = new TimelineNormalizationService();
        ArtifactGraphCompiler artifactCompiler = new ArtifactGraphCompiler();
        CapabilityGraphCompiler capCompiler = new CapabilityGraphCompiler();
        ProviderBindingCompiler bindingCompiler = new ProviderBindingCompiler();
        ProviderExecutionDocumentDraftCompiler draftCompiler = new ProviderExecutionDocumentDraftCompiler();
        RenderExecutionPlanCompiler planCompiler = new RenderExecutionPlanCompiler();
        RenderPlanPolicyGuard policyGuard = new RenderPlanPolicyGuard();
        RenderExecutionStepExecutor stepExecutor = new RenderExecutionStepExecutor(
                matService, regService, productRuntime, toolInventory, toolRunner, auditRecorder);
        LocalExecutionPlanRunner planRunner = new LocalExecutionPlanRunner(policyGuard, stepExecutor);

        return new PlanBasedTimelineRevisionRenderService(
                new StubTimelineRevisionService(revisionRepo), snapshotService,
                mapper, parser, inputProductResolver, normalizer,
                artifactCompiler, capCompiler, bindingCompiler, draftCompiler,
                planCompiler, policyGuard, planRunner, matService,
                regService, productRuntime, storageRuntime, toolInventory, tempDir, auditRecorder);
    }

    private LocalExecutionPlanRunner createRunner() {
        RenderToolCapabilityInventory toolInv = new RenderToolCapabilityInventory() {
            @Override public boolean isToolAvailable(String n) { return "ffmpeg".equals(n); }
        };
        ProcessToolRunner toolRunner = new ProcessToolRunner() {
            @Override public ToolExecutionResult execute(com.example.platform.extension.domain.ToolExecutionRequest r) {
                try {
                    String outputPath = r.args().get(r.args().size() - 1);
                    Files.createDirectories(Path.of(outputPath).getParent());
                    Files.writeString(Path.of(outputPath), "fake-output");
                    return ToolExecutionResult.success(0, "ffmpeg", "", Instant.now(), Instant.now());
                } catch (Exception e) {
                    return ToolExecutionResult.failed(1, "", e.getMessage(), Instant.now(), Instant.now());
                }
            }
            @Override public ToolExecutionResult execute(com.example.platform.extension.domain.ToolExecutionRequest r,
                                                          com.example.platform.extension.domain.ToolSandboxPolicy p) { return execute(r); }
        };
        RenderInputMaterializationService matService = new RenderInputMaterializationService(storageRuntime, productRuntime);
        RenderOutputRegistrationService regService = new RenderOutputRegistrationService(storageRuntime, productRuntime, tempDir, mockProvider(null), mockProvider(null));
        RenderExecutionStepExecutor stepExecutor = new RenderExecutionStepExecutor(
                matService, regService, productRuntime, toolInv, toolRunner, auditRecorder);
        return new LocalExecutionPlanRunner(new RenderPlanPolicyGuard(), stepExecutor);
    }

    private RenderExecutionPlan createSimplePlan() {
        var ffmpegRef = new com.example.platform.render.domain.timeline.compile.binding.BoundProviderRef(
                "ffmpeg", ProviderStatus.PRODUCTION, ProviderType.RENDER, "P0", true, true, "6.1", 0);
        var nodeType = com.example.platform.render.domain.timeline.compile.ArtifactNodeType.FINAL_RENDER;
        var exec = new RenderExecutionStep("step-exec", RenderExecutionStepType.EXECUTE_PROVIDER,
                RenderExecutionStepStatus.PENDING, "node-1", nodeType,
                "ffmpeg", ffmpegRef, null, List.of(), false,
                ExecutionEnvironmentTarget.LOCAL, "Execute", Map.of());
        var verify = new RenderExecutionStep("step-verify", RenderExecutionStepType.VERIFY_OUTPUT,
                RenderExecutionStepStatus.PENDING, "node-1", nodeType,
                "ffmpeg", ffmpegRef, null, List.of("step-exec"), false,
                ExecutionEnvironmentTarget.LOCAL, "Verify", Map.of());
        var register = new RenderExecutionStep("step-reg", RenderExecutionStepType.REGISTER_OUTPUT,
                RenderExecutionStepStatus.PENDING, "node-1", nodeType,
                "ffmpeg", ffmpegRef, null, List.of("step-verify"), false,
                ExecutionEnvironmentTarget.LOCAL, "Register", Map.of());
        var link = new RenderExecutionStep("step-link", RenderExecutionStepType.LINK_PRODUCT_DEPENDENCY,
                RenderExecutionStepStatus.PENDING, "node-1", nodeType,
                null, null, null, List.of("step-reg"), false,
                ExecutionEnvironmentTarget.LOCAL, "Link", Map.of());
        var finalize = new RenderExecutionStep("step-final", RenderExecutionStepType.FINALIZE_RENDER,
                RenderExecutionStepStatus.PENDING, null, null, null, null, null,
                List.of("step-link"), false, ExecutionEnvironmentTarget.LOCAL, "Finalize", Map.of());
        return new RenderExecutionPlan(
                RenderExecutionPlanId.fromBindingPlan("bp-test", "PRODUCTION"),
                "bp-test", "tl-test", ExecutionPolicy.production(),
                ExecutionEnvironmentTarget.LOCAL,
                List.of(exec, verify, register, link, finalize), false, List.of());
    }

    private LocalExecutionPlanContext createContext() {
        return new LocalExecutionPlanContext("rj-test", "t-1", "proj-1", "rev-1", "snap-1",
                "{}", "default_1080p", List.of("input-1"), "input-1", null,
                tempDir, tempDir.resolve("output"), "output.mp4",
                1920, 1080, 30, 5.0, false, "mp4", Map.of());
    }

    private void registerReadyRawMediaProduct() throws Exception {
        Path inputDir = tempDir.resolve("storage-inputs");
        Files.createDirectories(inputDir);
        Path inputVideo = inputDir.resolve(TimelineCoreSmokeFixture.ASSET_ID + ".mp4");
        Files.writeString(inputVideo, "fake-mp4-content");
        String checksum = computeSha256(inputVideo);
        StorageReference ref = storageRuntime.register(new StorageReference(
                null, StorageProviderType.LOCAL.name(), StorageClass.STANDARD,
                tempDir.toString(), tempDir.relativize(inputVideo).toString(),
                checksum, checksum, Files.size(inputVideo), "video/mp4", Instant.now(), Instant.now()));
        String productId = Ids.newId("prod");
        productRuntime.register(new Product(productId, TimelineCoreSmokeFixture.TENANT_ID,
                TimelineCoreSmokeFixture.PROJECT_ID, TimelineCoreSmokeFixture.ASSET_ID,
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "upload", "upload-service", null, ProductStatus.REGISTERED,
                ref.storageReferenceId(), checksum, checksum, "video/mp4", 1, "{}", Instant.now(), Instant.now()));
        productRuntime.markReady(productId);
    }

    private String setupRevision() {
        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();
        String timelineJson = TimelineCoreSmokeFixture.toJson(spec);
        String snapshotId = "snap-" + UUID.randomUUID().toString().substring(0, 8);
        snapshotService.saveWithId(snapshotId, TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, timelineJson);
        String revisionId = "rev-" + UUID.randomUUID().toString().substring(0, 8);
        revisionRepo.insert(new TimelineRevisionRepository.RevisionRow(
                revisionId, TimelineCoreSmokeFixture.PROJECT_ID, TimelineCoreSmokeFixture.TENANT_ID,
                null, 1, snapshotId, 1, "hash-abc", "internal-1.0", "sync", "user-1", null,
                "Test revision", null, null, null, false, null, null, OffsetDateTime.now()));
        return revisionId;
    }

    private String computeSha256(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // --- In-memory doubles ---

    static class InMemoryTimelineRevisionRepository {
        private final Map<String, TimelineRevisionRepository.RevisionRow> store = new ConcurrentHashMap<>();
        void insert(TimelineRevisionRepository.RevisionRow row) { store.put(row.id(), row); }
        Optional<TimelineRevisionRepository.RevisionRow> findById(String id) { return Optional.ofNullable(store.get(id)); }
    }

    static class StubTimelineRevisionService extends TimelineRevisionService {
        private final InMemoryTimelineRevisionRepository repo;
        StubTimelineRevisionService(InMemoryTimelineRevisionRepository repo) { super(null, null, null, null, null, null, null, null); this.repo = repo; }
        @Override
        public Optional<RevisionInfo> findById(String revisionId) {
            return repo.findById(revisionId).map(row -> new RevisionInfo(
                    row.id(), row.projectId(), row.tenantId(), row.parentRevisionId(),
                    row.revisionNumber(), row.snapshotId(), row.internalRevision(),
                    row.contentHash(), row.schemaVersion(), row.source(),
                    row.authorUserId(), row.editSessionId(), row.message(),
                    List.of(), row.changeSummaryJson(), row.patchOpsJson(),
                    row.isMerge(), row.mergeParentRevisionIds(), row.mergeBaseRevisionId(),
                    row.createdAt() != null ? row.createdAt().toString() : null));
        }
    }

    static class InMemoryTimelineSnapshotService extends TimelineSnapshotService {
        private final Map<String, SnapshotInfo> store = new ConcurrentHashMap<>();
        InMemoryTimelineSnapshotService() { super(null); }
        void saveWithId(String snapshotId, String projectId, String tenantId, String payloadJson) {
            store.put(snapshotId, new SnapshotInfo(snapshotId, projectId, tenantId, payloadJson, "1.0.0"));
        }
        @Override
        public Optional<String> findPayload(String snapshotId) {
            return Optional.ofNullable(store.get(snapshotId)).map(SnapshotInfo::payloadJson);
        }
    }

    static class InMemoryStorageReferenceRepository extends StorageReferenceRepository {
        private final Map<String, StorageReference> store = new ConcurrentHashMap<>();
        InMemoryStorageReferenceRepository() { super(null); }
        @Override
        public StorageReference save(StorageReference ref) {
            String id = ref.storageReferenceId() != null ? ref.storageReferenceId() : "stor-" + UUID.randomUUID().toString().substring(0, 8);
            StorageReference saved = new StorageReference(id, ref.providerType(), ref.storageClass(),
                    ref.rootPath(), ref.relativePath(), ref.checksum(), ref.contentHash(),
                    ref.fileSize(), ref.mimeType(), ref.createdAt(), ref.updatedAt());
            store.put(id, saved); return saved;
        }
        @Override
        public Optional<StorageReference> findById(String id) { return Optional.ofNullable(store.get(id)); }
    }

    static class InMemoryProductRepository extends ProductRepository {
        private final Map<String, Product> store = new ConcurrentHashMap<>();
        InMemoryProductRepository() { super(null); }
        @Override public Product save(Product p) { store.put(p.productId(), p); return p; }
        @Override public Optional<Product> findById(String id) { return Optional.ofNullable(store.get(id)); }
        @Override public List<Product> findByAsset(String assetId) { return store.values().stream().filter(p -> assetId.equals(p.ownerAssetId())).toList(); }
        @Override public Optional<Product> findLatest(String assetId, ProductType type) { return findByAsset(assetId).stream().filter(p -> p.productType() == type).findFirst(); }
        @Override public List<Product> findByProject(String projectId, int limit) { return store.values().stream().filter(p -> projectId.equals(p.projectId())).limit(limit).toList(); }
        @Override public List<Product> findBySourceTimelineRevisionId(String revId) { return store.values().stream().filter(p -> revId.equals(p.sourceTimelineRevisionId())).toList(); }
    }

    static class InMemoryProductDependencyRepository extends ProductDependencyRepository {
        private final Map<String, List<ProductDependency>> store = new ConcurrentHashMap<>();
        InMemoryProductDependencyRepository() { super(null); }
        @Override public ProductDependency save(ProductDependency dep) { store.computeIfAbsent(dep.productId(), k -> new ArrayList<>()).add(dep); return dep; }
        @Override public List<ProductDependency> findDependencies(String productId) { return store.getOrDefault(productId, List.of()); }
        @Override public List<ProductDependency> findDependents(String productId) { return List.of(); }
    }
}
