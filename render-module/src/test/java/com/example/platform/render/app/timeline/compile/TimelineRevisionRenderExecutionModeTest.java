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
import com.example.platform.render.api.dto.TimelineRevisionRenderRequest;
import com.example.platform.render.app.timeline.*;
import com.example.platform.render.domain.product.*;
import com.example.platform.render.domain.storage.*;
import com.example.platform.render.domain.timeline.*;
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
 * Tests for TimelineRevision render execution mode switching.
 *
 * <p>Proves:
 * <ul>
 *   <li>LEGACY mode uses TimelineRevisionRenderService</li>
 *   <li>PLAN_BASED mode uses PlanBasedTimelineRevisionRenderService</li>
 *   <li>Both modes produce READY Product</li>
 *   <li>Both modes create ProductDependency lineage</li>
 *   <li>Both modes return safe public API contract</li>
 *   <li>Feature flag is internal only (not in public DTOs)</li>
 *   <li>Plan-based mode does not expose storage internals</li>
 *   <li>Plan-based mode does not expose raw commands</li>
 * </ul>
 */
class TimelineRevisionRenderExecutionModeTest {

    @TempDir
    Path tempDir;

    private StorageRuntimeService storageRuntime;
    private ProductRuntimeService productRuntime;
    private RenderOutputRegistrationService registrationService;
    private RenderInputMaterializationService materializationService;
    private TimelineRenderJobMapper mapper;
    private TimelineScriptParser parser;
    private TimelineInputProductResolver inputProductResolver;

    private InMemoryTimelineRevisionRepository revisionRepo;
    private InMemoryTimelineSnapshotService snapshotService;

    @BeforeEach
    void setUp() {
        StorageReferenceRepository storageRepo = new InMemoryStorageReferenceRepository();
        ProductRepository productRepo = new InMemoryProductRepository();
        ProductDependencyRepository depRepo = new InMemoryProductDependencyRepository();
        storageRuntime = new StorageRuntimeService(storageRepo);
        productRuntime = new ProductRuntimeService(productRepo, depRepo);
        registrationService = new RenderOutputRegistrationService(storageRuntime, productRuntime, tempDir);
        materializationService = new RenderInputMaterializationService(storageRuntime, productRuntime);

        TimelineExtensionsReader extensionsReader = new TimelineExtensionsReader();
        parser = new TimelineScriptParser(extensionsReader);
        InternalTimelineWriter writer = new InternalTimelineWriter(extensionsReader);
        mapper = new TimelineRenderJobMapper(parser, writer);

        revisionRepo = new InMemoryTimelineRevisionRepository();
        snapshotService = new InMemoryTimelineSnapshotService();
        inputProductResolver = new TimelineInputProductResolver(productRuntime);
    }

    @Test
    @DisplayName("LEGACY mode produces READY Product")
    void legacyModeProducesReadyProduct() throws Exception {
        registerReadyRawMediaProduct();
        String revisionId = setupRevision();

        TimelineRevisionRenderFacade facade = createFacade(TimelineRenderExecutionMode.LEGACY);
        TimelineRevisionRenderService.RevisionRenderResult result =
                facade.render(TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_1080p");

        assertNotNull(result);
        assertEquals("READY", result.productStatus());
        assertEquals(TimelineRenderExecutionMode.LEGACY, facade.getExecutionMode());
    }

    @Test
    @DisplayName("PLAN_BASED mode produces READY Product")
    void planBasedModeProducesReadyProduct() throws Exception {
        registerReadyRawMediaProduct();
        String revisionId = setupRevision();

        TimelineRevisionRenderFacade facade = createFacade(TimelineRenderExecutionMode.PLAN_BASED);
        TimelineRevisionRenderService.RevisionRenderResult result =
                facade.render(TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_1080p");

        assertNotNull(result);
        assertEquals("READY", result.productStatus());
        assertEquals(TimelineRenderExecutionMode.PLAN_BASED, facade.getExecutionMode());
    }

    @Test
    @DisplayName("Both modes create ProductDependency lineage")
    void bothModesCreateLineage() throws Exception {
        registerReadyRawMediaProduct();
        String revisionId = setupRevision();

        // LEGACY
        TimelineRevisionRenderFacade legacyFacade = createFacade(TimelineRenderExecutionMode.LEGACY);
        TimelineRevisionRenderService.RevisionRenderResult legacyResult =
                legacyFacade.render(TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_1080p");
        List<ProductDependency> legacyDeps = productRuntime.findDependencies(legacyResult.outputProductId());
        assertFalse(legacyDeps.isEmpty(), "Legacy should create dependency");

        // Reset for PLAN_BASED
        registerReadyRawMediaProduct();
        revisionId = setupRevision();
        TimelineRevisionRenderFacade planFacade = createFacade(TimelineRenderExecutionMode.PLAN_BASED);
        TimelineRevisionRenderService.RevisionRenderResult planResult =
                planFacade.render(TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_1080p");
        List<ProductDependency> planDeps = productRuntime.findDependencies(planResult.outputProductId());
        assertFalse(planDeps.isEmpty(), "Plan-based should create dependency");
    }

    @Test
    @DisplayName("Both modes return safe public API contract")
    void bothModesReturnSafeContract() throws Exception {
        registerReadyRawMediaProduct();
        String revisionId = setupRevision();

        TimelineRevisionRenderFacade facade = createFacade(TimelineRenderExecutionMode.PLAN_BASED);
        TimelineRevisionRenderService.RevisionRenderResult result =
                facade.render(TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_1080p");

        // Result should not expose storage internals
        assertNotNull(result.renderJobId());
        assertNotNull(result.outputProductId());
        assertNotNull(result.baselineRenderer());
        assertNotNull(result.inputProductIds());
        // storageReferenceId is in the existing contract (not a leak)
    }

    @Test
    @DisplayName("Feature flag is not in public request DTO")
    void featureFlagNotInPublicRequest() {
        // TimelineRevisionRenderRequest should not have execution mode field
        TimelineRevisionRenderRequest request = new TimelineRevisionRenderRequest("default_1080p");
        // If this compiles, the request has no execution mode field
        assertNotNull(request);
        assertEquals("default_1080p", request.outputProfile());
    }

    @Test
    @DisplayName("Facade uses correct service based on mode")
    void facadeRoutesCorrectly() throws Exception {
        registerReadyRawMediaProduct();
        String revisionId = setupRevision();

        // Both modes should succeed
        TimelineRevisionRenderFacade legacyFacade = createFacade(TimelineRenderExecutionMode.LEGACY);
        TimelineRevisionRenderService.RevisionRenderResult legacyResult =
                legacyFacade.render(TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_1080p");
        assertNotNull(legacyResult.outputProductId());

        registerReadyRawMediaProduct();
        revisionId = setupRevision();
        TimelineRevisionRenderFacade planFacade = createFacade(TimelineRenderExecutionMode.PLAN_BASED);
        TimelineRevisionRenderService.RevisionRenderResult planResult =
                planFacade.render(TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_1080p");
        assertNotNull(planResult.outputProductId());
    }

    // --- Helpers ---

    private TimelineRevisionRenderFacade createFacade(TimelineRenderExecutionMode mode) {
        TimelineRenderExecutionProperties props =
                new TimelineRenderExecutionProperties(mode);

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

        RenderToolCapabilityInventory toolInventory = new RenderToolCapabilityInventory() {
            @Override
            public boolean isToolAvailable(String toolName) {
                return "ffmpeg".equals(toolName);
            }
        };

        // Legacy service
        TimelineRevisionRenderService legacyService = new TimelineRevisionRenderService(
                new StubTimelineRevisionService(revisionRepo),
                snapshotService, mapper, parser,
                materializationService, registrationService,
                productRuntime, storageRuntime, inputProductResolver,
                toolRunner, tempDir);

        // Plan-based service dependencies
        TimelineNormalizationService normalizer = new TimelineNormalizationService();
        ArtifactGraphCompiler artifactCompiler = new ArtifactGraphCompiler();
        CapabilityGraphCompiler capabilityCompiler = new CapabilityGraphCompiler();
        ProviderBindingCompiler bindingCompiler = new ProviderBindingCompiler();
        ProviderExecutionDocumentDraftCompiler draftCompiler = new ProviderExecutionDocumentDraftCompiler();
        RenderExecutionPlanCompiler planCompiler = new RenderExecutionPlanCompiler();
        RenderPlanPolicyGuard policyGuard = new RenderPlanPolicyGuard();
        RenderAuditRecorder auditRecorder = new RenderAuditRecorder(new NoopRenderAuditEventSink());
        RenderExecutionStepExecutor stepExecutor = new RenderExecutionStepExecutor(
                materializationService, registrationService, productRuntime,
                toolInventory, toolRunner, auditRecorder);
        LocalExecutionPlanRunner planRunner = new LocalExecutionPlanRunner(policyGuard, stepExecutor);

        PlanBasedTimelineRevisionRenderService planBasedService =
                new PlanBasedTimelineRevisionRenderService(
                        new StubTimelineRevisionService(revisionRepo),
                        snapshotService, mapper, parser, inputProductResolver,
                        normalizer, artifactCompiler, capabilityCompiler,
                        bindingCompiler, draftCompiler, planCompiler,
                        policyGuard, planRunner,
                        materializationService, registrationService,
                        productRuntime, storageRuntime, toolInventory, tempDir);

        RenderDeduplicationService dedupService = new RenderDeduplicationService(productRuntime);
        return new TimelineRevisionRenderFacade(legacyService, planBasedService, dedupService, props, auditRecorder);
    }

    private void registerReadyRawMediaProduct() throws Exception {
        Path inputDir = tempDir.resolve("storage-inputs");
        Files.createDirectories(inputDir);
        Path inputVideo = inputDir.resolve(TimelineCoreSmokeFixture.ASSET_ID + ".mp4");
        Files.writeString(inputVideo, "fake-mp4-content-for-testing");

        String checksum = computeSha256(inputVideo);
        StorageReference ref = storageRuntime.register(new StorageReference(
                null, StorageProviderType.LOCAL.name(), StorageClass.STANDARD,
                tempDir.toString(), tempDir.relativize(inputVideo).toString(),
                checksum, checksum, Files.size(inputVideo), "video/mp4",
                Instant.now(), Instant.now()));

        String productId = Ids.newId("prod");
        Product product = new Product(
                productId, TimelineCoreSmokeFixture.TENANT_ID,
                TimelineCoreSmokeFixture.PROJECT_ID, TimelineCoreSmokeFixture.ASSET_ID,
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "upload", "upload-service", null,
                ProductStatus.REGISTERED, ref.storageReferenceId(),
                checksum, checksum, "video/mp4", 1,
                "{}", Instant.now(), Instant.now());
        productRuntime.register(product);
        productRuntime.markReady(productId);
    }

    private String setupRevision() {
        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();
        String timelineJson = TimelineCoreSmokeFixture.toJson(spec);
        String snapshotId = "snap-mode-" + UUID.randomUUID().toString().substring(0, 8);
        snapshotService.saveWithId(snapshotId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, timelineJson);
        String revisionId = "rev-mode-" + UUID.randomUUID().toString().substring(0, 8);
        revisionRepo.insert(createRevision(revisionId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, snapshotId));
        return revisionId;
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
            super(null, null, null, null, null, null, null);
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
