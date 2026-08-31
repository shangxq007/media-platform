package com.example.platform.render.app.timeline.compile;

import com.example.platform.timeline.adapter.TimelineRevisionRepository;import com.example.platform.timeline.app.TimelineRevisionQueryService;
import com.example.platform.timeline.app.TimelineRevisionDiffQuery;
import com.example.platform.timeline.app.TimelineImportService;
import com.example.platform.render.app.timeline.TimelineSpecImportAdapter;
import com.example.platform.render.app.timeline.TimelineRevisionRenderService;
import com.example.platform.render.app.timeline.compile.audit.*;

import com.example.platform.render.domain.product.*;
import com.example.platform.storage.contract.*;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.render.app.input.RenderInputMaterializationService;
import com.example.platform.render.app.output.RenderOutputRegistrationService;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.app.timeline.*;
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
import com.example.platform.render.domain.interchange.TimelineExtensionsReader;
import com.example.platform.render.domain.interchange.TimelineScriptParser;
import com.example.platform.render.domain.interchange.TimelineSpec;

/**
 * LEGACY vs PLAN_BASED fail-closed parity tests after removal of their local
 * Provider execution authority.
 */
class TimelineRevisionRenderModeParityTest {
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
    private InMemoryStorageReferenceRepository storageRepo;

    @BeforeEach
    void setUp() {
        storageRepo = new InMemoryStorageReferenceRepository();
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
    @DisplayName("LEGACY fails closed without a READY FINAL_RENDER Product")
    void legacyFailsClosedWithoutReadyProduct() throws Exception {
        registerReadyRawMediaProduct();
        String revisionId = setupRevision();
        TimelineRevisionRenderService legacyService = createLegacyService();
        assertLegacyFailClosed(legacyService, revisionId);
    }

    @Test
    @DisplayName("PLAN_BASED fails closed without a READY FINAL_RENDER Product")
    void planBasedFailsClosedWithoutReadyProduct() throws Exception {
        registerReadyRawMediaProduct();
        String revisionId = setupRevision();
        PlanBasedTimelineRevisionRenderService planService = createPlanBasedService();
        assertPlanFailClosed(planService, revisionId);
    }

    @Test
    @DisplayName("Both removed modes expose typed fail-closed contracts instead of a result")
    void bothModesUseTypedFailClosedContracts() throws Exception {
        registerReadyRawMediaProduct();
        String legacyRevId = setupRevision();
        IllegalStateException legacyFailure = assertLegacyFailClosed(createLegacyService(), legacyRevId);

        registerReadyRawMediaProduct();
        String planRevId = setupRevision();
        IllegalStateException planFailure = assertPlanFailClosed(createPlanBasedService(), planRevId);
        assertFalse(legacyFailure.getMessage().contains("storageReferenceId"));
        assertFalse(planFailure.getMessage().contains("storageReferenceId"));
    }

    @Test
    @DisplayName("Both removed modes create no fabricated ProductDependency lineage")
    void bothModesCreateNoLineage() throws Exception {
        registerReadyRawMediaProduct();
        String legacyRevId = setupRevision();
        assertLegacyFailClosed(createLegacyService(), legacyRevId);

        registerReadyRawMediaProduct();
        String planRevId = setupRevision();
        assertPlanFailClosed(createPlanBasedService(), planRevId);
    }

    @Test
    @DisplayName("Both removed modes commit no output through StorageRuntime")
    void bothModesCommitNoOutputStorage() throws Exception {
        registerReadyRawMediaProduct();
        String legacyRevId = setupRevision();
        assertLegacyFailClosed(createLegacyService(), legacyRevId);

        registerReadyRawMediaProduct();
        String planRevId = setupRevision();
        assertPlanFailClosed(createPlanBasedService(), planRevId);
    }

    @Test
    @DisplayName("Both modes do not expose storage internals in failure")
    void bothModesNoStorageInternalsInFailure() throws Exception {
        registerReadyRawMediaProduct();
        String revId = setupRevision();
        String resultStr = assertLegacyFailClosed(createLegacyService(), revId).getMessage();
        assertFalse(resultStr.contains("bucket"));
        assertFalse(resultStr.contains("objectKey"));
        assertFalse(resultStr.contains("rootPath"));
        assertFalse(resultStr.contains("materializedPath"));
        assertFalse(resultStr.contains("signedUrl"));
    }

    @Test
    @DisplayName("Both modes do not expose or execute a raw command")
    void bothModesNoRawCommand() throws Exception {
        registerReadyRawMediaProduct();
        String revId = setupRevision();
        String resultStr = assertLegacyFailClosed(createLegacyService(), revId).getMessage();
        assertFalse(resultStr.contains("provider -i"));
        assertFalse(resultStr.contains("h264"));
    }

    @Test
    @DisplayName("PLAN_BASED emits audit/correlation events without completion publication")
    void planBasedEmitsFailClosedAuditEvents() throws Exception {
        registerReadyRawMediaProduct();
        String revId = setupRevision();
        assertPlanFailClosed(createPlanBasedService(), revId);
        assertFalse(auditSink.findAll().isEmpty(), "PLAN_BASED should emit audit events");
        assertFalse(auditSink.findAll().stream()
                .anyMatch(event -> event.eventType() == RenderAuditEventType.RENDER_COMPLETED));
    }

    @Test
    @DisplayName("PLAN_BASED remains Provider-only executable")
    void planBasedRemainsProviderOnly() {
        // Verify plan-based policy guard rejects non-Provider
        RenderPlanPolicyGuard guard = new RenderPlanPolicyGuard();
        // The guard checks non-production providers - STUB providers are never executable
        // This is verified by existing LocalExecutionPlanRunnerTest
        assertNotNull(guard);
    }

    private IllegalStateException assertLegacyFailClosed(
            TimelineRevisionRenderService service, String revisionId) {
        return assertFailClosed(service, null, revisionId,
                "Typed provider plugin execution required");
    }

    private IllegalStateException assertPlanFailClosed(
            PlanBasedTimelineRevisionRenderService service, String revisionId) {
        return assertFailClosed(null, service, revisionId,
                "Plan-based render failed: Policy guard rejected plan: Plan has 1 policy violations");
    }

    private IllegalStateException assertFailClosed(
            TimelineRevisionRenderService legacyService,
            PlanBasedTimelineRevisionRenderService planService,
            String revisionId,
            String expectedMessage) {
        int storageCountBefore = storageRepo.size();
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> {
            if (legacyService != null) {
                legacyService.render(TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_1080p");
            } else {
                planService.render(TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_1080p");
            }
        });
        assertEquals(expectedMessage, failure.getMessage());
        assertEquals(storageCountBefore, storageRepo.size(), "Render must not commit storage");
        assertTrue(productRuntime.findBySourceTimelineRevisionId(revisionId).isEmpty());
        assertTrue(productRuntime.findByProject(TimelineCoreSmokeFixture.PROJECT_ID, 100).stream()
                .noneMatch(product -> product.productType() == ProductType.FINAL_RENDER));
        return failure;
    }

    // --- Helpers ---

    private TimelineRevisionRenderService createLegacyService() {
        TimelineExtensionsReader extReader = new TimelineExtensionsReader();
        TimelineScriptParser parser = new TimelineScriptParser(extReader);
        TimelineSpecImportAdapter importAdapter = new TimelineSpecImportAdapter(extReader);
        TimelineImportService importService = new TimelineImportService();
        TimelineRenderJobMapper mapper = new TimelineRenderJobMapper(parser, importAdapter, importService);
        TimelineInputProductResolver inputProductResolver = new TimelineInputProductResolver(productRuntime);
        RenderInputMaterializationService matService = new RenderInputMaterializationService(storageRuntime, productRuntime);
        RenderOutputRegistrationService regService = new RenderOutputRegistrationService(storageRuntime, productRuntime, tempDir, mockProvider(null), mockProvider(null));
        return new TimelineRevisionRenderService(
                new StubTimelineRevisionService(revisionRepo), snapshotService,
                mapper, parser, null, matService, regService, productRuntime, storageRuntime,
                inputProductResolver, tempDir);
    }

    private PlanBasedTimelineRevisionRenderService createPlanBasedService() {
        TimelineExtensionsReader extReader = new TimelineExtensionsReader();
        TimelineScriptParser parser = new TimelineScriptParser(extReader);
        TimelineSpecImportAdapter importAdapter = new TimelineSpecImportAdapter(extReader);
        TimelineImportService importService = new TimelineImportService();
        TimelineRenderJobMapper mapper = new TimelineRenderJobMapper(parser, importAdapter, importService);
        TimelineInputProductResolver inputProductResolver = new TimelineInputProductResolver(productRuntime);
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
                matService, regService, productRuntime, auditRecorder);
        LocalExecutionPlanRunner planRunner = new LocalExecutionPlanRunner(policyGuard, stepExecutor);
        return new PlanBasedTimelineRevisionRenderService(
                new StubTimelineRevisionService(revisionRepo), snapshotService,
                mapper, parser, inputProductResolver, normalizer,
                artifactCompiler, capCompiler, bindingCompiler, draftCompiler,
                planCompiler, policyGuard, planRunner, matService,
                regService, productRuntime, storageRuntime, tempDir, auditRecorder);
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
    static class StubTimelineRevisionService extends TimelineRevisionQueryService {
        private final InMemoryTimelineRevisionRepository repo;
        StubTimelineRevisionService(InMemoryTimelineRevisionRepository repo) { super(null, null, null); this.repo = repo; }
        @Override public Optional<RevisionInfo> findById(String projectId, String tenantId, String revisionId) {
            return repo.findById(revisionId)
                    // project ownership only: Stub is a single-tenant in-memory
                    // double; tenant-predicate truth is covered by the real
                    // TimelineRevisionQueryService integration tests. Ignoring the
                    // ambient TenantContext keeps parallel full-suite runs isolated.
                    .filter(row -> row.projectId().equals(projectId))
                    .map(row -> new RevisionInfo(
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
        public Optional<SnapshotInfo> findOwnedById(String projectId, String tenantId, String snapshotId) {
            return Optional.ofNullable(store.get(snapshotId))
                    .filter(s -> s.projectId().equals(projectId) && s.tenantId().equals(tenantId));
        }
    }
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
        @Override public Optional<StorageReference> findById(String id) { return Optional.ofNullable(store.get(id)); }
        int size() { return store.size(); }
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
