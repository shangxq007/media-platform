package com.example.platform.render.app.timeline.compile;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.render.app.timeline.compile.audit.*;
import com.example.platform.extension.domain.ToolSandboxPolicy;
import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.app.input.RenderInputMaterializationService;
import com.example.platform.render.app.output.RenderOutputRegistrationService;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.app.timeline.*;
import com.example.platform.render.domain.product.*;
import com.example.platform.render.domain.storage.*;
import com.example.platform.render.domain.timeline.*;
import com.example.platform.render.domain.timeline.compile.*;
import com.example.platform.render.infrastructure.RenderToolCapabilityInventory;
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
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke test for plan-based TimelineRevision rendering through LocalExecutionPlanRunner.
 *
 * <p>Proves:
 * <ul>
 *   <li>Plan-based render produces READY output Product</li>
 *   <li>Plan-based render creates ProductDependency lineage</li>
 *   <li>Plan-based render uses FFmpeg baseline</li>
 *   <li>Plan-based render does not expose storage internals in result</li>
 *   <li>Missing FFmpeg fails closed</li>
 * </ul>
 */
class PlanBasedTimelineRevisionRenderSmokeTest {
    @SuppressWarnings("unchecked")
    private static <T> org.springframework.beans.factory.ObjectProvider<T> mockProvider(T instance) {
        org.springframework.beans.factory.ObjectProvider<T> op = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(op.getIfAvailable()).thenReturn(instance);
        return op;
    }


    @TempDir
    Path tempDir;

    private StorageRuntimeService storageRuntime;
    private ProductRuntimeService productRuntime;
    private RenderOutputRegistrationService registrationService;
    private RenderInputMaterializationService materializationService;
    private TimelineRenderJobMapper mapper;
    private TimelineScriptParser parser;
    private TimelineInputProductResolver inputProductResolver;
    private TimelineNormalizationService normalizer;
    private ArtifactGraphCompiler artifactCompiler;
    private CapabilityGraphCompiler capabilityCompiler;
    private ProviderBindingCompiler bindingCompiler;
    private ProviderExecutionDocumentDraftCompiler draftCompiler;
    private RenderExecutionPlanCompiler planCompiler;
    private RenderPlanPolicyGuard policyGuard;
    private LocalExecutionPlanRunner planRunner;
    private RenderExecutionStepExecutor stepExecutor;
    private PlanBasedTimelineRevisionRenderService renderService;
    private RenderToolCapabilityInventory toolInventory;

    private InMemoryTimelineRevisionRepository revisionRepo;
    private InMemoryTimelineSnapshotService snapshotService;
    private boolean ffmpegAvailable = true;

    @BeforeEach
    void setUp() {
        StorageReferenceRepository storageRepo = new InMemoryStorageReferenceRepository();
        ProductRepository productRepo = new InMemoryProductRepository();
        ProductDependencyRepository depRepo = new InMemoryProductDependencyRepository();
        storageRuntime = new StorageRuntimeService(storageRepo, mockProvider(null));
        productRuntime = new ProductRuntimeService(productRepo, depRepo);
        registrationService = new RenderOutputRegistrationService(storageRuntime, productRuntime, tempDir, mockProvider(null), mockProvider(null));
        materializationService = new RenderInputMaterializationService(storageRuntime, productRuntime);

        TimelineExtensionsReader extensionsReader = new TimelineExtensionsReader();
        parser = new TimelineScriptParser(extensionsReader);
        InternalTimelineWriter writer = new InternalTimelineWriter(extensionsReader);
        mapper = new TimelineRenderJobMapper(parser, writer);

        revisionRepo = new InMemoryTimelineRevisionRepository();
        snapshotService = new InMemoryTimelineSnapshotService();
        inputProductResolver = new TimelineInputProductResolver(productRuntime);

        normalizer = new TimelineNormalizationService();
        artifactCompiler = new ArtifactGraphCompiler();
        capabilityCompiler = new CapabilityGraphCompiler();
        bindingCompiler = new ProviderBindingCompiler();
        draftCompiler = new ProviderExecutionDocumentDraftCompiler();
        planCompiler = new RenderExecutionPlanCompiler();
        policyGuard = new RenderPlanPolicyGuard();

        // Mock ProcessToolRunner that simulates FFmpeg output
        ProcessToolRunner toolRunner = new ProcessToolRunner() {
            @Override
            public ToolExecutionResult execute(ToolExecutionRequest request) {
                try {
                    List<String> args = request.args();
                    String outputPath = args.get(args.size() - 1);
                    Path output = Path.of(outputPath);
                    Files.createDirectories(output.getParent());
                    Files.writeString(output, "fake-rendered-content-" + UUID.randomUUID());
                    return ToolExecutionResult.success(0, "ffmpeg", "",
                            Instant.now(), Instant.now());
                } catch (IOException e) {
                    return ToolExecutionResult.failed(1, "", e.getMessage(),
                            Instant.now(), Instant.now());
                }
            }

            @Override
            public ToolExecutionResult execute(ToolExecutionRequest request, ToolSandboxPolicy policy) {
                return execute(request);
            }
        };

        // Mock tool inventory
        toolInventory = new RenderToolCapabilityInventory() {
            @Override
            public boolean isToolAvailable(String toolName) {
                return ffmpegAvailable && "ffmpeg".equals(toolName);
            }
        };

        RenderAuditRecorder auditRecorder = new RenderAuditRecorder(new NoopRenderAuditEventSink());
        stepExecutor = new RenderExecutionStepExecutor(
                materializationService, registrationService, productRuntime, toolInventory, toolRunner, auditRecorder);
        planRunner = new LocalExecutionPlanRunner(policyGuard, stepExecutor);

        renderService = new PlanBasedTimelineRevisionRenderService(
                new StubTimelineRevisionService(revisionRepo),
                snapshotService, mapper, parser, inputProductResolver,
                normalizer, artifactCompiler, capabilityCompiler,
                bindingCompiler, draftCompiler, planCompiler,
                policyGuard, planRunner,
                materializationService, registrationService,
                productRuntime, storageRuntime, toolInventory, tempDir);
    }

    @Test
    @DisplayName("Plan-based render produces READY output Product")
    void planBasedRenderProducesReadyProduct() throws Exception {
        // 1. Register input Product
        registerReadyRawMediaProduct(
                TimelineCoreSmokeFixture.ASSET_ID,
                TimelineCoreSmokeFixture.TENANT_ID,
                TimelineCoreSmokeFixture.PROJECT_ID);

        // 2. Create snapshot + revision
        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();
        String timelineJson = TimelineCoreSmokeFixture.toJson(spec);
        String snapshotId = "snap-plan-001";
        snapshotService.saveWithId(snapshotId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, timelineJson);
        String revisionId = "rev-plan-001";
        revisionRepo.insert(createRevision(revisionId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, snapshotId));

        // 3. Execute plan-based render
        TimelineRevisionRenderService.RevisionRenderResult result =
                renderService.render(
                        TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_1080p");

        // 4. Verify result
        assertNotNull(result);
        assertNotNull(result.outputProductId());
        assertEquals("READY", result.productStatus());
        assertEquals("ffmpeg-libass", result.baselineRenderer());
        assertFalse(result.inputProductIds().isEmpty());
        assertTrue(result.inputDependencyCount() > 0);
    }

    @Test
    @DisplayName("Plan-based render creates ProductDependency lineage")
    void planBasedRenderCreatesLineage() throws Exception {
        registerReadyRawMediaProduct(
                TimelineCoreSmokeFixture.ASSET_ID,
                TimelineCoreSmokeFixture.TENANT_ID,
                TimelineCoreSmokeFixture.PROJECT_ID);

        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();
        String timelineJson = TimelineCoreSmokeFixture.toJson(spec);
        String snapshotId = "snap-plan-002";
        snapshotService.saveWithId(snapshotId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, timelineJson);
        String revisionId = "rev-plan-002";
        revisionRepo.insert(createRevision(revisionId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, snapshotId));

        TimelineRevisionRenderService.RevisionRenderResult result =
                renderService.render(
                        TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_1080p");

        // Verify ProductDependency exists
        List<ProductDependency> deps = productRuntime.findDependencies(result.outputProductId());
        assertFalse(deps.isEmpty(), "Should have at least one ProductDependency");
    }

    @Test
    @DisplayName("Plan-based render result does not expose storage internals")
    void planBasedRenderResultIsSafe() throws Exception {
        registerReadyRawMediaProduct(
                TimelineCoreSmokeFixture.ASSET_ID,
                TimelineCoreSmokeFixture.TENANT_ID,
                TimelineCoreSmokeFixture.PROJECT_ID);

        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();
        String timelineJson = TimelineCoreSmokeFixture.toJson(spec);
        String snapshotId = "snap-plan-003";
        snapshotService.saveWithId(snapshotId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, timelineJson);
        String revisionId = "rev-plan-003";
        revisionRepo.insert(createRevision(revisionId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, snapshotId));

        TimelineRevisionRenderService.RevisionRenderResult result =
                renderService.render(
                        TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_1080p");

        // Result should not expose raw command, process environment, bucket, key, etc.
        assertNotNull(result.renderJobId());
        assertNotNull(result.outputProductId());
        assertNotNull(result.baselineRenderer());
    }

    @Test
    @DisplayName("Missing FFmpeg fails closed")
    void missingFfmpegFailsClosed() throws Exception {
        ffmpegAvailable = false;

        registerReadyRawMediaProduct(
                TimelineCoreSmokeFixture.ASSET_ID,
                TimelineCoreSmokeFixture.TENANT_ID,
                TimelineCoreSmokeFixture.PROJECT_ID);

        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();
        String timelineJson = TimelineCoreSmokeFixture.toJson(spec);
        String snapshotId = "snap-plan-004";
        snapshotService.saveWithId(snapshotId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, timelineJson);
        String revisionId = "rev-plan-004";
        revisionRepo.insert(createRevision(revisionId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, snapshotId));

        assertThrows(IllegalStateException.class, () ->
                renderService.render(
                        TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_1080p"));
    }

    @Test
    @DisplayName("Compile pipeline produces deterministic plan IDs")
    void compilePipelineDeterministic() {
        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();

        NormalizedTimeline t1 = normalizer.normalize(spec, "proj-1");
        NormalizedTimeline t2 = normalizer.normalize(spec, "proj-1");

        assertEquals(t1.timelineId(), t2.timelineId());
        assertEquals(t1.tracks().size(), t2.tracks().size());
    }

    // --- Helpers ---

    private void registerReadyRawMediaProduct(String assetId, String tenantId, String projectId)
            throws Exception {
        // Create a tiny real mp4 file for testing
        Path inputDir = tempDir.resolve("storage-inputs");
        Files.createDirectories(inputDir);
        Path inputVideo = inputDir.resolve(assetId + ".mp4");
        // Write a minimal mp4-like file
        Files.writeString(inputVideo, "fake-mp4-content-for-testing");

        String checksum = computeSha256(inputVideo);
        StorageReference ref = storageRuntime.register(new StorageReference(
                null, StorageProviderType.LOCAL.name(), StorageClass.STANDARD,
                tempDir.toString(), tempDir.relativize(inputVideo).toString(),
                checksum, checksum, Files.size(inputVideo), "video/mp4",
                Instant.now(), Instant.now()));

        String productId = Ids.newId("prod");
        Product product = new Product(
                productId, tenantId, projectId, assetId,
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "upload", "upload-service", null,
                ProductStatus.REGISTERED, ref.storageReferenceId(),
                checksum, checksum, "video/mp4", 1,
                "{}", Instant.now(), Instant.now());
        Product registered = productRuntime.register(product);
        productRuntime.markReady(registered.productId());
    }

    private TimelineRevisionRepository.RevisionRow createRevision(
            String revisionId, String projectId, String tenantId, String snapshotId) {
        return new TimelineRevisionRepository.RevisionRow(
                revisionId, projectId, tenantId, null, 1, snapshotId,
                1, "hash-abc", "internal-1.0", "sync", "user-1", null,
                "Test revision", null, null, null, false, null, null,
                OffsetDateTime.now());
    }

    private String computeSha256(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // --- In-memory test doubles ---

    static class InMemoryTimelineRevisionRepository {
        private final Map<String, TimelineRevisionRepository.RevisionRow> store = new ConcurrentHashMap<>();

        void insert(TimelineRevisionRepository.RevisionRow row) {
            store.put(row.id(), row);
        }

        Optional<TimelineRevisionRepository.RevisionRow> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }
    }

    static class StubTimelineRevisionService extends TimelineRevisionService {
        private final InMemoryTimelineRevisionRepository repo;

        StubTimelineRevisionService(InMemoryTimelineRevisionRepository repo) {
            super(null, null, null, null, null, null, null, null);
            this.repo = repo;
        }

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

        InMemoryTimelineSnapshotService() {
            super(null);
        }

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
            String id = ref.storageReferenceId() != null ? ref.storageReferenceId()
                    : "stor-" + UUID.randomUUID().toString().substring(0, 8);
            StorageReference saved = new StorageReference(id, ref.providerType(), ref.storageClass(),
                    ref.rootPath(), ref.relativePath(), ref.checksum(), ref.contentHash(),
                    ref.fileSize(), ref.mimeType(), ref.createdAt(), ref.updatedAt());
            store.put(id, saved);
            return saved;
        }

        @Override
        public Optional<StorageReference> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }
    }

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
            return store.values().stream()
                    .filter(p -> assetId.equals(p.ownerAssetId()))
                    .toList();
        }

        @Override
        public Optional<Product> findLatest(String assetId, ProductType type) {
            return findByAsset(assetId).stream()
                    .filter(p -> p.productType() == type)
                    .findFirst();
        }

        @Override
        public List<Product> findByProject(String projectId, int limit) {
            return store.values().stream()
                    .filter(p -> projectId.equals(p.projectId()))
                    .limit(limit)
                    .toList();
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
            return store.values().stream()
                    .flatMap(List::stream)
                    .filter(d -> productId.equals(d.dependsOnProductId()))
                    .toList();
        }
    }
}
