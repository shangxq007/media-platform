package com.example.platform.render.app.timeline;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.extension.domain.ToolSandboxPolicy;
import com.example.platform.render.api.dto.RenderJobResultResponse;
import com.example.platform.render.api.dto.RenderJobStatusResponse;
import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.app.input.RenderInputMaterializationService;
import com.example.platform.render.app.output.RenderOutputRegistrationService;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.domain.product.*;
import com.example.platform.render.domain.storage.*;
import com.example.platform.render.domain.timeline.*;
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

/**
 * R8: Local real-render smoke harness proving the full R6.1 + R7 backend chain
 * with a real media file and real FFmpeg/libass execution.
 *
 * <p><b>This test invokes real FFmpeg to render video.</b> It proves the full chain:
 * <ol>
 *   <li>TimelineRevision → input RAW_MEDIA Product</li>
 *   <li>StorageRuntime materialize</li>
 *   <li>FFmpeg/libass real render (no testsrc/lavfi in render path)</li>
 *   <li>output FINAL_RENDER Product READY</li>
 *   <li>ProductDependency lineage (DERIVED_FROM)</li>
 *   <li>R7 render job status query</li>
 *   <li>R7 render job result query</li>
 * </ol>
 *
 * <p>If FFmpeg is not available on PATH, the test is explicitly skipped with
 * a clear message. It does NOT silently fall back.</p>
 *
 * <p>Architecture compliance:
 * <ul>
 *   <li>FFmpeg/libass is the baseline subtitle burn-in path (not Remotion)</li>
 *   <li>Remotion production dispatch remains disabled</li>
 *   <li>OpenCue production submit remains disabled</li>
 *   <li>MinIO/S3 are not required (LOCAL storage only)</li>
 *   <li>No Artifact Runtime introduced</li>
 *   <li>No signed URLs persisted</li>
 *   <li>No internal provider/backend/environment exposed to external callers</li>
 * </ul>
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
        InternalTimelineWriter writer = new InternalTimelineWriter(extensionsReader);
        mapper = new TimelineRenderJobMapper(parser, writer);

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
    @DisplayName("R8: full real render smoke — TimelineRevision → FFmpeg → Product → R7 status/result")
    void fullRealRenderSmoke() throws Exception {
        R2FixtureGenerator.assumeFfmpegAvailable();

        // ── Step 1: Generate a tiny real input mp4 (3 seconds, 320x180) ──
        Path inputVideo = R2FixtureGenerator.generateTestVideo(
                tempDir.resolve("input-media"), 3.0, 320, 180, 24);
        assertTrue(Files.exists(inputVideo), "Input video must be generated");
        assertTrue(Files.size(inputVideo) > 0, "Input video must be non-zero");

        // ── Step 2: Register input as StorageReference + READY RAW_MEDIA Product ──
        Product inputProduct = registerReadyRawMediaProduct(
                TimelineCoreSmokeFixture.ASSET_ID,
                TimelineCoreSmokeFixture.TENANT_ID,
                TimelineCoreSmokeFixture.PROJECT_ID,
                inputVideo);
        assertEquals(ProductStatus.READY, inputProduct.status());
        assertNotNull(inputProduct.storageReferenceId());

        // ── Step 3: Create TimelineRevision referencing the source asset ──
        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();
        String timelineJson = TimelineCoreSmokeFixture.toJson(spec);
        String snapshotId = "snap_r8_001";
        snapshotService.saveWithId(snapshotId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, timelineJson);

        String revisionId = "rev_r8_001";
        revisionRepo.insert(createRevision(revisionId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, snapshotId));

        // ── Step 4: Create real ProcessToolRunner that invokes FFmpeg ──
        ProcessToolRunner realRunner = new RealFfmpegProcessToolRunner();

        TimelineRevisionRenderService renderService = new TimelineRevisionRenderService(
                new StubTimelineRevisionService(revisionRepo),
                snapshotService, mapper, parser,
                null,
                new RenderInputMaterializationService(storageRuntime, productRuntime),
                registrationService, productRuntime, storageRuntime,
                inputProductResolver, realRunner, tempDir);

        // ── Step 5: Invoke render ──
        TimelineRevisionRenderService.RevisionRenderResult result =
                renderService.render(
                        TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_720p");

        // ── Step 6: Verify output Product ──
        assertNotNull(result, "Render result must not be null");
        assertNotNull(result.outputProductId(), "outputProductId must be present");
        assertEquals("READY", result.productStatus(), "output Product must be READY");
        assertNotNull(result.renderJobId(), "renderJobId must be present");
        assertEquals(revisionId, result.timelineRevisionId());
        assertEquals(snapshotId, result.snapshotId());

        // Verify inputProductIds
        assertNotNull(result.inputProductIds(), "inputProductIds must be present");
        assertEquals(1, result.inputProductIds().size(), "Must have 1 input Product");
        assertEquals(inputProduct.productId(), result.inputProductIds().get(0));
        assertEquals(1, result.inputDependencyCount());

        // Verify output Product type
        Optional<Product> outputProductOpt = productRuntime.find(result.outputProductId());
        assertTrue(outputProductOpt.isPresent(), "Output Product must exist");
        Product outputProduct = outputProductOpt.get();
        assertEquals(ProductType.FINAL_RENDER, outputProduct.productType());
        assertEquals(RepresentationKind.MEDIA_FILE, outputProduct.representationKind());
        assertEquals(ProductStatus.READY, outputProduct.status());

        // ── Step 7: Verify output file is a real playable mp4 via ffprobe ──
        StorageReference outputRef = storageRuntime.find(outputProduct.storageReferenceId())
                .orElseThrow(() -> new AssertionError("Output StorageReference must exist"));
        Path outputVideo = Path.of(outputRef.rootPath()).resolve(outputRef.relativePath());
        assertTrue(Files.exists(outputVideo), "Output video file must exist");
        assertTrue(Files.size(outputVideo) > 0, "Output video must be non-zero");

        // Verify with ffprobe
        boolean ffprobeOk = verifyWithFfprobe(outputVideo);
        assertTrue(ffprobeOk, "Output must be a valid media file readable by ffprobe");

        // ── Step 8: Verify ProductDependency lineage ──
        List<ProductDependency> deps = productRuntime.findDependencies(result.outputProductId());
        assertEquals(1, deps.size(), "Output must have exactly 1 dependency edge");

        ProductDependency dep = deps.get(0);
        assertEquals(result.outputProductId(), dep.productId());
        assertEquals(inputProduct.productId(), dep.dependsOnProductId());
        assertEquals(DependencyType.DERIVED_FROM, dep.dependencyType());
        assertEquals(TimelineCoreSmokeFixture.TENANT_ID, dep.tenantId());
        assertEquals(TimelineCoreSmokeFixture.PROJECT_ID, dep.projectId());

        // Verify upstream/downstream queries
        List<String> upstream = productRuntime.findUpstream(result.outputProductId());
        assertEquals(1, upstream.size());
        assertEquals(inputProduct.productId(), upstream.get(0));

        List<String> downstream = productRuntime.findDownstream(inputProduct.productId());
        assertEquals(1, downstream.size());
        assertEquals(result.outputProductId(), downstream.get(0));

        // ── Step 9: Verify R7 status query ──
        Optional<RenderJobStatusResponse> statusOpt = statusService.findStatus(
                TimelineCoreSmokeFixture.PROJECT_ID, revisionId, result.renderJobId());
        assertTrue(statusOpt.isPresent(), "R7 status query must return a result");

        RenderJobStatusResponse status = statusOpt.get();
        assertEquals(result.renderJobId(), status.renderJobId());
        assertEquals(TimelineCoreSmokeFixture.PROJECT_ID, status.projectId());
        assertEquals(revisionId, status.timelineRevisionId());
        assertEquals("READY", status.status());
        assertTrue(status.resultAvailable());
        assertEquals(result.outputProductId(), status.outputProductId());
        assertEquals("READY", status.productStatus());
        assertNotNull(status.inputProductIds());
        assertEquals(1, status.inputProductIds().size());
        assertEquals(inputProduct.productId(), status.inputProductIds().get(0));
        assertEquals(1, status.inputDependencyCount());

        // ── Step 10: Verify R7 result query ──
        Optional<RenderJobResultResponse> resultOpt = statusService.findResult(
                TimelineCoreSmokeFixture.PROJECT_ID, revisionId, result.renderJobId());
        assertTrue(resultOpt.isPresent(), "R7 result query must return a result");

        RenderJobResultResponse resultResp = resultOpt.get();
        assertEquals(result.renderJobId(), resultResp.renderJobId());
        assertEquals(TimelineCoreSmokeFixture.PROJECT_ID, resultResp.projectId());
        assertEquals(revisionId, resultResp.timelineRevisionId());
        assertEquals(result.outputProductId(), resultResp.outputProductId());
        assertEquals("READY", resultResp.productStatus());
        assertEquals("video/mp4", resultResp.mimeType());
        assertEquals("mp4", resultResp.outputFormat());
        assertTrue(resultResp.width() > 0, "width must be positive");
        assertTrue(resultResp.height() > 0, "height must be positive");
        assertTrue(resultResp.fps() > 0, "fps must be positive");
        assertTrue(resultResp.durationSeconds() > 0, "duration must be positive");
        assertEquals("ffmpeg-libass", resultResp.baselineRenderer());
        assertEquals("timeline-revision-render", resultResp.renderMode());
        assertNotNull(resultResp.inputProductIds());
        assertEquals(1, resultResp.inputProductIds().size());
        assertEquals(1, resultResp.inputDependencyCount());

        // ── Step 11: Verify public response safety ──
        // Verify no sensitive data in status response
        String statusStr = status.toString();
        assertFalse(statusStr.contains("storageReferenceId"), "No storageReferenceId in status");
        assertFalse(statusStr.contains("storageProvider"), "No storageProvider in status");
        assertFalse(statusStr.contains("signedUrl"), "No signedUrl in status");
        assertFalse(statusStr.contains("localPath"), "No localPath in status");
        assertFalse(statusStr.contains("materializedPath"), "No materializedPath in status");
        assertFalse(statusStr.contains("provider"), "No provider in status");
        assertFalse(statusStr.contains("backend"), "No backend in status");
        assertFalse(statusStr.contains("environment"), "No environment in status");

        // Verify no sensitive data in result response
        String resultStr = resultResp.toString();
        assertFalse(resultStr.contains("storageReferenceId"), "No storageReferenceId in result");
        assertFalse(resultStr.contains("storageProvider"), "No storageProvider in result");
        assertFalse(resultStr.contains("signedUrl"), "No signedUrl in result");
        assertFalse(resultStr.contains("localPath"), "No localPath in result");
        assertFalse(resultStr.contains("materializedPath"), "No materializedPath in result");

        // Verify no sensitive data in output Product metadata
        String metadata = outputProduct.metadataJson();
        assertFalse(metadata.contains("signedUrl"), "No signedUrl in Product metadata");
        assertFalse(metadata.contains("presign"), "No presign in Product metadata");
        assertFalse(metadata.contains(tempDir.toString()), "No absolute path in Product metadata");
        assertFalse(metadata.contains("storageProvider"), "No storageProvider in Product metadata");

        // Verify inputProductIds IS present in metadata
        assertTrue(metadata.contains("\"inputProductIds\":"), "Must contain inputProductIds in metadata");
        assertTrue(metadata.contains("\"renderJobId\":"), "Must contain renderJobId in metadata");
        assertTrue(metadata.contains("\"timelineRevisionId\":"), "Must contain timelineRevisionId in metadata");
        assertTrue(metadata.contains("\"snapshotId\":"), "Must contain snapshotId in metadata");

        System.out.println("[R8] Real render smoke PASSED");
        System.out.println("[R8]   renderJobId:     " + result.renderJobId());
        System.out.println("[R8]   outputProductId: " + result.outputProductId());
        System.out.println("[R8]   outputVideo:     " + outputVideo);
        System.out.println("[R8]   outputSize:      " + Files.size(outputVideo) + " bytes");
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
        public ToolExecutionResult execute(ToolExecutionRequest request, ToolSandboxPolicy policy) {
            return execute(request);
        }
    }

    // ─── Stub TimelineRevisionService ───

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
        public Optional<String> findPayload(String snapshotId) {
            return Optional.ofNullable(store.get(snapshotId)).map(SnapshotInfo::payloadJson);
        }

        @Override
        public Optional<SnapshotInfo> findById(String snapshotId) {
            return Optional.ofNullable(store.get(snapshotId));
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
