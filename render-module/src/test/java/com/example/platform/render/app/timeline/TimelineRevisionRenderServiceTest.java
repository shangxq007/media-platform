package com.example.platform.render.app.timeline;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.extension.domain.ToolSandboxPolicy;
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
 * Tests for {@link TimelineRevisionRenderService}.
 *
 * <p>Covers:
 * <ul>
 *   <li>R6: successful render of TimelineRevision to READY Product</li>
 *   <li>R6: provenance metadata in output Product</li>
 *   <li>R6: failure path tests (missing revision, wrong project, etc.)</li>
 *   <li>R6.1: input Product resolution from timeline assets</li>
 *   <li>R6.1: FFmpeg uses materialized input (no testsrc/lavfi)</li>
 *   <li>R6.1: formal ProductDependency lineage</li>
 *   <li>R6.1: fail-closed for missing/unready inputs</li>
 *   <li>Architecture boundaries (no provider/backend/environment exposure)</li>
 * </ul>
 */
class TimelineRevisionRenderServiceTest {

    @TempDir
    Path tempDir;

    private StorageRuntimeService storageRuntime;
    private ProductRuntimeService productRuntime;
    private RenderOutputRegistrationService registrationService;
    private TimelineRenderJobMapper mapper;
    private TimelineScriptParser parser;
    private TimelineInputProductResolver inputProductResolver;
    private TimelineRevisionRenderService renderService;

    // In-memory test doubles
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

        TimelineExtensionsReader extensionsReader = new TimelineExtensionsReader();
        parser = new TimelineScriptParser(extensionsReader);
        InternalTimelineWriter writer = new InternalTimelineWriter(extensionsReader);
        mapper = new TimelineRenderJobMapper(parser, writer);

        revisionRepo = new InMemoryTimelineRevisionRepository();
        snapshotService = new InMemoryTimelineSnapshotService();

        inputProductResolver = new TimelineInputProductResolver(productRuntime);

        // Create a mock ProcessToolRunner that succeeds
        ProcessToolRunner toolRunner = new ProcessToolRunner() {
            @Override
            public ToolExecutionResult execute(ToolExecutionRequest request) {
                try {
                    // Simulate FFmpeg creating an output file
                    List<String> args = request.args();
                    String outputPath = args.get(args.size() - 1);
                    Path output = Path.of(outputPath);
                    Files.createDirectories(output.getParent());
                    Files.writeString(output, "fake-rendered-content-" + UUID.randomUUID());
                    return ToolExecutionResult.success(0, "ffmpeg", "", Instant.now(), Instant.now());
                } catch (IOException e) {
                    return ToolExecutionResult.failed(1, "", e.getMessage(), Instant.now(), Instant.now());
                }
            }

            @Override
            public ToolExecutionResult execute(ToolExecutionRequest request,
                    ToolSandboxPolicy policy) {
                return execute(request);
            }
        };

        renderService = new TimelineRevisionRenderService(
                new StubTimelineRevisionService(revisionRepo),
                snapshotService,
                mapper,
                parser,
                new RenderInputMaterializationService(storageRuntime, productRuntime),
                registrationService,
                productRuntime,
                storageRuntime,
                inputProductResolver,
                toolRunner,
                tempDir);
    }

    // ─── R6: Original tests ───

    @Test
    @DisplayName("R6: missing revision throws IllegalArgumentException")
    void missingRevisionThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> renderService.render("prj_1", "nonexistent-rev", "default_1080p"));
    }

    @Test
    @DisplayName("R6: wrong project throws IllegalArgumentException")
    void wrongProjectThrowsException() {
        String snapshotId = "snap_r6_002";
        snapshotService.saveWithId(snapshotId, "prj_correct", "ten_1", "{}");

        String revisionId = "rev_r6_002";
        revisionRepo.insert(new TimelineRevisionRepository.RevisionRow(
                revisionId, "prj_correct", "ten_1", null, 1, snapshotId,
                1, "hash", "internal-1.0", "sync", "user-1", null, "msg",
                null, null, null, false, null, null, OffsetDateTime.now()));

        assertThrows(IllegalArgumentException.class,
                () -> renderService.render("prj_wrong", revisionId, "default_1080p"));
    }

    @Test
    @DisplayName("R6: missing snapshot throws IllegalStateException")
    void missingSnapshotThrowsException() {
        String revisionId = "rev_r6_003";
        revisionRepo.insert(new TimelineRevisionRepository.RevisionRow(
                revisionId, "prj_1", "ten_1", null, 1, "nonexistent-snap",
                1, "hash", "internal-1.0", "sync", "user-1", null, "msg",
                null, null, null, false, null, null, OffsetDateTime.now()));

        assertThrows(IllegalStateException.class,
                () -> renderService.render("prj_1", revisionId, "default_1080p"));
    }

    // ─── R6.1: Input Product resolution tests ───

    @Test
    @DisplayName("R6.1: render resolves input Product and uses materialized input path")
    void r61RenderUsesMaterializedInputNotTestsrc() throws Exception {
        R2FixtureGenerator.assumeFfmpegAvailable();

        // 1. Register a READY RAW_MEDIA Product matching the timeline's asset ID
        Product inputProduct = registerReadyRawMediaProduct(
                TimelineCoreSmokeFixture.ASSET_ID,
                TimelineCoreSmokeFixture.TENANT_ID,
                TimelineCoreSmokeFixture.PROJECT_ID);

        // 2. Create snapshot + revision with the fixture timeline
        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();
        String timelineJson = TimelineCoreSmokeFixture.toJson(spec);
        String snapshotId = "snap_r61_001";
        snapshotService.saveWithId(snapshotId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, timelineJson);

        String revisionId = "rev_r61_001";
        revisionRepo.insert(createRevision(revisionId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, snapshotId));

        // 3. Use a capturing ProcessToolRunner that copies materialized input to output
        List<List<String>> capturedArgs = new ArrayList<>();
        ProcessToolRunner capturingRunner = createCapturingAndCopyingToolRunner(capturedArgs);

        TimelineRevisionRenderService serviceWithCapturing = new TimelineRevisionRenderService(
                new StubTimelineRevisionService(revisionRepo),
                snapshotService, mapper, parser,
                new RenderInputMaterializationService(storageRuntime, productRuntime),
                registrationService, productRuntime, storageRuntime,
                inputProductResolver, capturingRunner, tempDir);

        // 4. Render
        TimelineRevisionRenderService.RevisionRenderResult result =
                serviceWithCapturing.render(
                        TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_720p");

        // 5. Verify result
        assertNotNull(result);
        assertNotNull(result.outputProductId());
        assertEquals("READY", result.productStatus());

        // 6. Verify inputProductIds populated
        assertNotNull(result.inputProductIds());
        assertEquals(1, result.inputProductIds().size());
        assertEquals(inputProduct.productId(), result.inputProductIds().get(0));
        assertEquals(1, result.inputDependencyCount());

        // 7. Verify FFmpeg command used materialized input (no testsrc/lavfi)
        assertFalse(capturedArgs.isEmpty(), "FFmpeg must have been invoked");
        List<String> ffmpegArgs = capturedArgs.get(0);

        // Find the -i argument and verify it points to a real file
        int iIndex = ffmpegArgs.indexOf("-i");
        assertTrue(iIndex >= 0, "FFmpeg args must contain -i");
        String inputPath = ffmpegArgs.get(iIndex + 1);
        assertFalse(inputPath.contains("testsrc"), "FFmpeg must NOT use testsrc");
        assertFalse(inputPath.contains("lavfi"), "FFmpeg must NOT use lavfi");
        assertTrue(Files.exists(Path.of(inputPath)),
                "Materialized input path must exist: " + inputPath);

        // Verify full args do not contain lavfi or testsrc anywhere
        String argsString = String.join(" ", ffmpegArgs);
        assertFalse(argsString.contains("lavfi"), "FFmpeg args must not contain lavfi");
        assertFalse(argsString.contains("testsrc"), "FFmpeg args must not contain testsrc");
    }

    @Test
    @DisplayName("R6.1: render creates formal ProductDependency edges")
    void r61ResolvesInputProductIdsAndCreatesDependency() throws Exception {
        R2FixtureGenerator.assumeFfmpegAvailable();

        Product inputProduct = registerReadyRawMediaProduct(
                TimelineCoreSmokeFixture.ASSET_ID,
                TimelineCoreSmokeFixture.TENANT_ID,
                TimelineCoreSmokeFixture.PROJECT_ID);

        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();
        String timelineJson = TimelineCoreSmokeFixture.toJson(spec);
        String snapshotId = "snap_r61_002";
        snapshotService.saveWithId(snapshotId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, timelineJson);

        String revisionId = "rev_r61_002";
        revisionRepo.insert(createRevision(revisionId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, snapshotId));

        List<List<String>> capturedArgs = new ArrayList<>();
        ProcessToolRunner capturingRunner = createCapturingAndCopyingToolRunner(capturedArgs);

        TimelineRevisionRenderService serviceWithCapturing = new TimelineRevisionRenderService(
                new StubTimelineRevisionService(revisionRepo),
                snapshotService, mapper, parser,
                new RenderInputMaterializationService(storageRuntime, productRuntime),
                registrationService, productRuntime, storageRuntime,
                inputProductResolver, capturingRunner, tempDir);

        TimelineRevisionRenderService.RevisionRenderResult result =
                serviceWithCapturing.render(
                        TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_720p");

        // Verify dependency edges
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
    }

    @Test
    @DisplayName("R6.1: missing input Product fails closed")
    void r61MissingInputProductFailsClosed() {
        // Timeline has sourceAssetId = "ast_smoke_001" but no Product registered
        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();
        String timelineJson = TimelineCoreSmokeFixture.toJson(spec);
        String snapshotId = "snap_r61_003";
        snapshotService.saveWithId(snapshotId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, timelineJson);

        String revisionId = "rev_r61_003";
        revisionRepo.insert(createRevision(revisionId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, snapshotId));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> renderService.render(
                        TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_720p"));

        assertTrue(ex.getMessage().contains("Input product resolution failed"),
                "Error must indicate resolution failure: " + ex.getMessage());
    }

    @Test
    @DisplayName("R6.1: input Product not READY fails closed")
    void r61InputProductNotReadyFailsClosed() {
        // Register Product but don't mark READY
        String productId = Ids.newId("prod");
        Product product = new Product(
                productId, TimelineCoreSmokeFixture.TENANT_ID,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.ASSET_ID,
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "upload", "upload-service", null,
                ProductStatus.REGISTERED, "stor-test",
                null, null, "video/mp4", 1,
                "{}", Instant.now(), Instant.now());
        productRuntime.register(product);

        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();
        String timelineJson = TimelineCoreSmokeFixture.toJson(spec);
        String snapshotId = "snap_r61_004";
        snapshotService.saveWithId(snapshotId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, timelineJson);

        String revisionId = "rev_r61_004";
        revisionRepo.insert(createRevision(revisionId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, snapshotId));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> renderService.render(
                        TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_720p"));

        assertTrue(ex.getMessage().contains("Input product resolution failed"),
                "Error must indicate resolution failure: " + ex.getMessage());
    }

    @Test
    @DisplayName("R6.1: input Product missing StorageReference fails closed")
    void r61InputProductMissingStorageReferenceFailsClosed() {
        // Register READY RAW_MEDIA Product but with no storageReferenceId
        String productId = Ids.newId("prod");
        Product product = new Product(
                productId, TimelineCoreSmokeFixture.TENANT_ID,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.ASSET_ID,
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "upload", "upload-service", null,
                ProductStatus.REGISTERED, null, // no storageReferenceId
                null, null, "video/mp4", 1,
                "{}", Instant.now(), Instant.now());
        Product registered = productRuntime.register(product);
        productRuntime.markReady(registered.productId());

        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();
        String timelineJson = TimelineCoreSmokeFixture.toJson(spec);
        String snapshotId = "snap_r61_005";
        snapshotService.saveWithId(snapshotId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, timelineJson);

        String revisionId = "rev_r61_005";
        revisionRepo.insert(createRevision(revisionId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, snapshotId));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> renderService.render(
                        TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_720p"));

        assertTrue(ex.getMessage().contains("Input materialization failed"),
                "Error must indicate materialization failure: " + ex.getMessage());
    }

    @Test
    @DisplayName("R6.1: response excludes sensitive data")
    void r61ResponseExcludesSensitiveData() throws Exception {
        R2FixtureGenerator.assumeFfmpegAvailable();

        registerReadyRawMediaProduct(
                TimelineCoreSmokeFixture.ASSET_ID,
                TimelineCoreSmokeFixture.TENANT_ID,
                TimelineCoreSmokeFixture.PROJECT_ID);

        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();
        String timelineJson = TimelineCoreSmokeFixture.toJson(spec);
        String snapshotId = "snap_r61_006";
        snapshotService.saveWithId(snapshotId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, timelineJson);

        String revisionId = "rev_r61_006";
        revisionRepo.insert(createRevision(revisionId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, snapshotId));

        List<List<String>> capturedArgs = new ArrayList<>();
        ProcessToolRunner capturingRunner = createCapturingAndCopyingToolRunner(capturedArgs);

        TimelineRevisionRenderService serviceWithCapturing = new TimelineRevisionRenderService(
                new StubTimelineRevisionService(revisionRepo),
                snapshotService, mapper, parser,
                new RenderInputMaterializationService(storageRuntime, productRuntime),
                registrationService, productRuntime, storageRuntime,
                inputProductResolver, capturingRunner, tempDir);

        TimelineRevisionRenderService.RevisionRenderResult result =
                serviceWithCapturing.render(
                        TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_720p");

        // Verify Product metadata excludes sensitive data
        Optional<Product> outputProduct = productRuntime.find(result.outputProductId());
        assertTrue(outputProduct.isPresent());
        String metadata = outputProduct.get().metadataJson();

        assertFalse(metadata.contains("signedUrl"), "No signed URL in metadata");
        assertFalse(metadata.contains("signed-url"), "No signed URL in metadata");
        assertFalse(metadata.contains("presign"), "No presign in metadata");
        assertFalse(metadata.contains(tempDir.toString()), "No absolute path in metadata");
        assertFalse(metadata.contains("storageProvider"), "No storageProvider in metadata");
        assertFalse(metadata.contains("remotion"), "No remotion in metadata");
        assertFalse(metadata.contains("opencue"), "No opencue in metadata");

        // Verify inputProductIds IS present
        assertTrue(metadata.contains("\"inputProductIds\":"), "Must contain inputProductIds");
    }

    // ─── Helper: register a READY RAW_MEDIA Product ───

    private Product registerReadyRawMediaProduct(String assetId, String tenantId, String projectId)
            throws Exception {
        // Generate tiny real mp4 using FFmpeg testsrc (fixture only, not render input)
        Path inputVideo = R2FixtureGenerator.generateTestVideo(
                tempDir.resolve("input-media"), 2.0, 320, 180, 30);

        // Copy to storage location
        Path storageInput = tempDir.resolve("storage-inputs").resolve(assetId + ".mp4");
        Files.createDirectories(storageInput.getParent());
        Files.copy(inputVideo, storageInput, StandardCopyOption.REPLACE_EXISTING);

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

    // ─── Helper: create capturing and copying ProcessToolRunner ───

    private ProcessToolRunner createCapturingAndCopyingToolRunner(
            List<List<String>> capturedArgs) {
        return new ProcessToolRunner() {
            @Override
            public ToolExecutionResult execute(ToolExecutionRequest request) {
                capturedArgs.add(List.copyOf(request.args()));
                try {
                    // Find materialized input path (arg after "-i")
                    String inputPath = null;
                    List<String> args = request.args();
                    for (int i = 0; i < args.size() - 1; i++) {
                        if ("-i".equals(args.get(i))) {
                            inputPath = args.get(i + 1);
                            break;
                        }
                    }
                    // Output path is last arg
                    String outputPath = args.get(args.size() - 1);
                    Path output = Path.of(outputPath);
                    Files.createDirectories(output.getParent());

                    if (inputPath != null && Files.exists(Path.of(inputPath))) {
                        // Copy real mp4 to output path (valid media for registration)
                        Files.copy(Path.of(inputPath), output,
                                StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        Files.writeString(output, "fallback-content-" + UUID.randomUUID());
                    }

                    return ToolExecutionResult.success(
                            0, "ffmpeg", "", Instant.now(), Instant.now());
                } catch (IOException e) {
                    return ToolExecutionResult.failed(
                            1, "", e.getMessage(), Instant.now(), Instant.now());
                }
            }

            @Override
            public ToolExecutionResult execute(ToolExecutionRequest request,
                    ToolSandboxPolicy policy) {
                return execute(request);
            }
        };
    }

    // ─── Helper: create revision row ───

    private TimelineRevisionRepository.RevisionRow createRevision(
            String revisionId, String projectId, String tenantId, String snapshotId) {
        return new TimelineRevisionRepository.RevisionRow(
                revisionId, projectId, tenantId, null, 1, snapshotId,
                1, "hash-abc", "internal-1.0", "sync", "user-1", null,
                "Test revision", null, null, null, false, null, null,
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
            if (p.projectId() != null) byProject.computeIfAbsent(p.projectId(), k -> new ArrayList<>()).add(saved);
            if (p.ownerAssetId() != null) byAsset.computeIfAbsent(p.ownerAssetId(), k -> new ArrayList<>()).add(saved);
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
                    .filter(p -> timelineRevisionId.equals(p.sourceTimelineRevisionId()))
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
