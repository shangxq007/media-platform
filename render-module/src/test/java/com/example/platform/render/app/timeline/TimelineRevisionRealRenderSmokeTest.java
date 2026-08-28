package com.example.platform.render.app.timeline;

import com.example.platform.timeline.adapter.TimelineRevisionRepository;import com.example.platform.timeline.app.TimelineRevisionQueryService;
import com.example.platform.timeline.app.TimelineRevisionDiffQuery;
import com.example.platform.timeline.app.TimelineImportService;
import com.example.platform.render.app.timeline.TimelineSpecImportAdapter;
import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.extension.domain.ToolExecutionSafetyPolicy;
import com.example.platform.render.api.dto.RenderJobResultResponse;
import com.example.platform.render.api.dto.RenderJobStatusResponse;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.render.app.input.RenderInputMaterializationService;
import com.example.platform.render.app.output.RenderOutputRegistrationService;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.domain.product.*;
import com.example.platform.storage.contract.*;

import com.example.platform.render.infrastructure.product.ProductDependencyRepository;
import com.example.platform.render.infrastructure.product.ProductRepository;
import com.example.platform.render.infrastructure.storage.StorageReferenceRepository;
import com.example.platform.render.testsupport.R2FixtureGenerator;
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
import com.example.platform.render.domain.interchange.TimelineExtensionsReader;
import com.example.platform.render.domain.interchange.TimelineScriptParser;
import com.example.platform.render.domain.interchange.TimelineSpec;

/**
 * R8 retained-denominator smoke proving the removed legacy real-render path
 * fails closed without local FFmpeg execution or R7 publication.
 */
class TimelineRevisionRealRenderSmokeTest {
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
    private TimelineRenderJobMapper mapper;
    private TimelineScriptParser parser;
    private TimelineInputProductResolver inputProductResolver;
    private RenderJobStatusService statusService;

    private InMemoryTimelineRevisionRepository revisionRepo;
    private InMemoryTimelineSnapshotService snapshotService;

    @BeforeEach
    void setUp() {
        StorageReferenceRepository storageRepo = new InMemoryStorageReferenceRepository();
        ProductRepository productRepo = new InMemoryProductRepository();
        ProductDependencyRepository depRepo = new InMemoryProductDependencyRepository();
        storageRuntime = new StorageRuntimeService(storageRepo, mockProvider(null));
        productRuntime = new ProductRuntimeService(productRepo, depRepo);
        registrationService = new RenderOutputRegistrationService(storageRuntime, productRuntime, tempDir, mockProvider(null), mockProvider(null));

        TimelineExtensionsReader extensionsReader = new TimelineExtensionsReader();
        parser = new TimelineScriptParser(extensionsReader);
        TimelineSpecImportAdapter importAdapter = new TimelineSpecImportAdapter(extensionsReader);
        TimelineImportService importService = new TimelineImportService();
        mapper = new TimelineRenderJobMapper(parser, importAdapter, importService);

        revisionRepo = new InMemoryTimelineRevisionRepository();
        snapshotService = new InMemoryTimelineSnapshotService();

        inputProductResolver = new TimelineInputProductResolver(productRuntime);
        statusService = new RenderJobStatusService(productRuntime, depRepo);
    }

    /**
     * Full R8 smoke: real FFmpeg render through TimelineRevisionRenderService,
     * then verify R7 status/result queries and ProductDependency lineage.
     */
    @Test
    @DisplayName("R8: removed direct render authority fails closed without product or publication")
    void fullRealRenderSmokeFailsClosed() throws Exception {
        Path inputVideo = tempDir.resolve("input-media").resolve("input.mp4");
        Files.createDirectories(inputVideo.getParent());
        Files.writeString(inputVideo, "fixture-media");

        Product inputProduct = registerReadyRawMediaProduct(
                TimelineCoreSmokeFixture.ASSET_ID,
                TimelineCoreSmokeFixture.TENANT_ID,
                TimelineCoreSmokeFixture.PROJECT_ID,
                inputVideo);
        assertEquals(ProductStatus.READY, inputProduct.status());

        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();
        String snapshotId = "snap_r8_001";
        snapshotService.saveWithId(snapshotId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID,
                TimelineCoreSmokeFixture.toJson(spec));
        String revisionId = "rev_r8_001";
        revisionRepo.insert(createRevision(revisionId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID,
                snapshotId));

        java.util.concurrent.atomic.AtomicInteger processInvocations =
                new java.util.concurrent.atomic.AtomicInteger();
        ProcessToolRunner forbiddenRunner = new ProcessToolRunner() {
            @Override
            public ToolExecutionResult execute(ToolExecutionRequest request) {
                processInvocations.incrementAndGet();
                return ToolExecutionResult.failed(
                        1, "", "must not execute", Instant.now(), Instant.now());
            }

            @Override
            public ToolExecutionResult execute(
                    ToolExecutionRequest request, ToolExecutionSafetyPolicy policy) {
                return execute(request);
            }
        };
        TimelineRevisionRenderService renderService = new TimelineRevisionRenderService(
                new StubTimelineRevisionService(revisionRepo),
                snapshotService, mapper, parser, null,
                new RenderInputMaterializationService(storageRuntime, productRuntime),
                registrationService, productRuntime, storageRuntime,
                inputProductResolver, forbiddenRunner, tempDir);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> renderService.render(
                        TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_720p"));

        assertEquals("Typed provider plugin execution required", failure.getMessage());
        assertEquals(0, processInvocations.get(), "Removed local FFmpeg authority must not run");
        assertTrue(productRuntime.findBySourceTimelineRevisionId(revisionId).isEmpty());
        assertTrue(productRuntime.findByProject(TimelineCoreSmokeFixture.PROJECT_ID, 100).stream()
                .noneMatch(product -> product.productType() == ProductType.FINAL_RENDER));
        assertFalse(Files.exists(tempDir.resolve("render-output")),
                "Fail-closed render must not stage output artifacts");
        assertTrue(statusService.findStatus(
                TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "unpublished-render-job").isEmpty());
        assertTrue(statusService.findResult(
                TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "unpublished-render-job").isEmpty());
        assertFalse(failure.getMessage().contains("storageReferenceId"));
        assertFalse(failure.getMessage().contains(tempDir.toString()));
        assertFalse(failure.getMessage().contains("ffmpeg -i"));
    }

    // ─── Helper: register a READY RAW_MEDIA Product from a real file ───

    private Product registerReadyRawMediaProduct(String assetId, String tenantId,
                                                  String projectId, Path mediaFile) throws Exception {
        // Copy to storage location
        Path storageInput = tempDir.resolve("storage-inputs").resolve(assetId + ".mp4");
        Files.createDirectories(storageInput.getParent());
        Files.copy(mediaFile, storageInput, StandardCopyOption.REPLACE_EXISTING);

        // Register StorageReference
        String checksum = computeSha256(storageInput);
        StorageReference ref = storageRuntime.register(new StorageReference(
                null, StorageProviderType.LOCAL.name(), StorageClass.STANDARD,
                tempDir.toString(), tempDir.relativize(storageInput).toString(),
                checksum, checksum, Files.size(storageInput), "video/mp4",
                Instant.now(), Instant.now()));

        // Register RAW_MEDIA Product with ownerAssetId matching timeline clip assetId
        String productId = Ids.newId("prod");
        Product product = new Product(
                productId, tenantId, projectId, assetId,
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "upload", "upload-service", null,
                ProductStatus.REGISTERED, ref.storageReferenceId(),
                checksum, checksum, "video/mp4", 1,
                "{}", Instant.now(), Instant.now());
        Product registered = productRuntime.register(product);
        Product ready = productRuntime.markReady(registered.productId());
        assertEquals(ProductStatus.READY, ready.status());
        return ready;
    }

    // ─── Helper: verify file with ffprobe ───

    private boolean verifyWithFfprobe(Path file) {
        try {
            List<String> cmd = List.of("ffprobe", "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    file.toAbsolutePath().toString());
            R2FixtureGenerator.ProcessResult result = R2FixtureGenerator.executeCommand(cmd);
            if (result.success()) {
                double duration = Double.parseDouble(result.stdout().trim());
                return duration > 0;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // ─── Helper: create revision row ───

    private TimelineRevisionRepository.RevisionRow createRevision(
            String revisionId, String projectId, String tenantId, String snapshotId) {
        return new TimelineRevisionRepository.RevisionRow(
                revisionId, projectId, tenantId, null, 1, snapshotId,
                1, "hash-r8", "internal-1.0", "sync", "user-1", null,
                "R8 smoke revision", null, null, null, false, null, null,
                OffsetDateTime.now());
    }

    // ─── Helper: compute SHA-256 ───

    private String computeSha256(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // ─── Real ProcessToolRunner that invokes actual FFmpeg ───

    static class RealFfmpegProcessToolRunner implements ProcessToolRunner {
        @Override
        public ToolExecutionResult execute(ToolExecutionRequest request) {
            try {
                // Prepend the tool key ("ffmpeg") to the args list for ProcessBuilder
                List<String> fullCmd = new ArrayList<>();
                fullCmd.add(request.toolKey());
                fullCmd.addAll(request.args());

                R2FixtureGenerator.ProcessResult procResult = R2FixtureGenerator.executeCommand(fullCmd);
                Instant now = Instant.now();
                if (procResult.success()) {
                    return ToolExecutionResult.success(0, procResult.stdout(), procResult.stderr(), now, now);
                } else {
                    return ToolExecutionResult.failed(1, procResult.stdout(), procResult.stderr(), now, now);
                }
            } catch (Exception e) {
                Instant now = Instant.now();
                return ToolExecutionResult.failed(-1, "", e.getMessage(), now, now);
            }
        }

        @Override
        public ToolExecutionResult execute(ToolExecutionRequest request, ToolExecutionSafetyPolicy policy) {
            return execute(request);
        }
    }

    // ─── Stub TimelineRevisionService ───

    static class StubTimelineRevisionService extends TimelineRevisionQueryService {
        private final InMemoryTimelineRevisionRepository repo;

        StubTimelineRevisionService(InMemoryTimelineRevisionRepository repo) {
            super(null, null, null, null);
            this.repo = repo;
        }

        @Override
        public Optional<RevisionInfo> findById(String projectId, String tenantId, String revisionId) {
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

    // ─── In-memory test doubles ───

    static class InMemoryTimelineRevisionRepository {
        private final Map<String, TimelineRevisionRepository.RevisionRow> store = new ConcurrentHashMap<>();

        void insert(TimelineRevisionRepository.RevisionRow row) {
            store.put(row.id(), row);
        }

        Optional<TimelineRevisionRepository.RevisionRow> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }
    }

    static class InMemoryTimelineSnapshotService extends TimelineSnapshotService {
        private final Map<String, SnapshotInfo> store = new ConcurrentHashMap<>();

        InMemoryTimelineSnapshotService() {
            super(null);
        }

        @Override
        public String save(String projectId, String tenantId, String payloadJson, String schemaVersion) {
            String snapshotId = "snap-" + UUID.randomUUID().toString().substring(0, 8);
            store.put(snapshotId, new SnapshotInfo(snapshotId, projectId, tenantId, payloadJson, schemaVersion));
            return snapshotId;
        }

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
        private final Map<String, StorageReference> byContentHash = new ConcurrentHashMap<>();

        @Override
        public StorageReference save(StorageReference r) {
            String id = r.storageReferenceId() != null ? r.storageReferenceId() : "stor-" + UUID.randomUUID();
            StorageReference saved = new StorageReference(id, r.providerType(), r.storageClass(),
                    r.rootPath(), r.relativePath(), r.checksum(), r.contentHash(),
                    r.fileSize(), r.mimeType(), r.createdAt(), r.updatedAt());
            store.put(id, saved);
            if (r.contentHash() != null) byContentHash.put(r.contentHash(), saved);
            return saved;
        }

        @Override
        public Optional<StorageReference> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<StorageReference> findByContentHash(String hash) {
            return Optional.ofNullable(byContentHash.get(hash));
        }

        @Override
        public boolean exists(String id) {
            return store.containsKey(id);
        }

        @Override
        public void delete(String id) {
            StorageReference ref = store.remove(id);
            if (ref != null && ref.contentHash() != null) byContentHash.remove(ref.contentHash());
        }
    }

    static class InMemoryProductRepository extends ProductRepository {
        private final Map<String, Product> store = new ConcurrentHashMap<>();
        private final Map<String, List<Product>> byProject = new ConcurrentHashMap<>();
        private final Map<String, List<Product>> byAsset = new ConcurrentHashMap<>();

        @Override
        public Product save(Product p) {
            String id = p.productId() != null ? p.productId() : "prod-" + UUID.randomUUID();
            Product saved = new Product(id, p.tenantId(), p.projectId(), p.ownerAssetId(),
                    p.productType(), p.representationKind(), p.producerType(), p.producerId(),
                    p.sourceTimelineRevisionId(), p.status(), p.storageReferenceId(),
                    p.checksum(), p.contentHash(), p.mimeType(), p.version(),
                    p.metadataJson(), p.createdAt(), p.updatedAt());
            store.put(id, saved);
            // Update byProject list: replace existing entry with same ID, or add new
            if (p.projectId() != null) {
                List<Product> list = byProject.computeIfAbsent(p.projectId(), k -> new ArrayList<>());
                list.removeIf(existing -> existing.productId().equals(id));
                list.add(saved);
            }
            if (p.ownerAssetId() != null) {
                List<Product> list = byAsset.computeIfAbsent(p.ownerAssetId(), k -> new ArrayList<>());
                list.removeIf(existing -> existing.productId().equals(id));
                list.add(saved);
            }
            return saved;
        }

        @Override
        public Optional<Product> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Product> findByProject(String projectId, int limit) {
            List<Product> products = byProject.getOrDefault(projectId, List.of());
            return products.size() > limit ? products.subList(0, limit) : products;
        }

        @Override
        public List<Product> findByAsset(String assetId) {
            return byAsset.getOrDefault(assetId, List.of());
        }

        @Override
        public Optional<Product> findLatest(String assetId, ProductType type) {
            return byAsset.getOrDefault(assetId, List.of()).stream()
                    .filter(p -> p.productType() == type)
                    .findFirst();
        }

        @Override
        public List<Product> findBySourceTimelineRevisionId(String timelineRevisionId) {
            return store.values().stream()
                    .filter(product -> timelineRevisionId.equals(product.sourceTimelineRevisionId()))
                    .toList();
        }
    }

    static class InMemoryProductDependencyRepository extends ProductDependencyRepository {
        private final Map<String, ProductDependency> store = new ConcurrentHashMap<>();

        @Override
        public ProductDependency save(ProductDependency d) {
            String id = d.dependencyId() != null ? d.dependencyId() : "dep-" + UUID.randomUUID();
            ProductDependency saved = new ProductDependency(id, d.tenantId(), d.projectId(),
                    d.productId(), d.dependsOnProductId(), d.dependencyType(), d.createdAt());
            store.put(id, saved);
            return saved;
        }

        @Override
        public List<ProductDependency> findDependencies(String productId) {
            return store.values().stream()
                    .filter(d -> d.productId().equals(productId))
                    .toList();
        }

        @Override
        public List<ProductDependency> findDependents(String productId) {
            return store.values().stream()
                    .filter(d -> d.dependsOnProductId().equals(productId))
                    .toList();
        }

        @Override
        public boolean exists(String productId, String dependsOnId) {
            return findDependents(dependsOnId).stream()
                    .anyMatch(d -> d.productId().equals(productId));
        }

        @Override
        public void delete(String dependencyId) {
            store.remove(dependencyId);
        }
    }
}
