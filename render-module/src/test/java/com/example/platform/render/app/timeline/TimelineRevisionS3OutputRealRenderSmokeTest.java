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
import com.example.platform.render.app.storage.RenderOutputStorageProperties;
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
import com.example.platform.storage.infrastructure.S3ObjectMaterializer;
import com.example.platform.storage.infrastructure.S3ObjectWriter;
import com.example.platform.storage.infrastructure.StorageS3Properties;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * R10B: S3-backed render output registration smoke test.
 *
 * <p>Proves that render outputs can be uploaded to S3-compatible internal storage
 * and registered with S3_COMPATIBLE provider type, while keeping input as LOCAL.</p>
 *
 * <p><b>This test invokes real FFmpeg and requires a running S3-compatible backend.</b>
 * It proves:
 * <ol>
 *   <li>Input media is LOCAL (generated with FFmpeg testsrc)</li>
 *   <li>Render output is uploaded to S3-compatible internal storage</li>
 *   <li>Output StorageReference uses S3_COMPATIBLE provider type</li>
 *   <li>Object exists in S3 after render</li>
 *   <li>Object can be materialized/read back</li>
 *   <li>Output Product is FINAL_RENDER READY</li>
 *   <li>ProductDependency lineage exists (DERIVED_FROM)</li>
 *   <li>R7 status/result APIs are safe (no bucket/key/path/signed URL exposure)</li>
 * </ol>
 *
 * <p>If FFmpeg or S3 endpoint is unavailable, the test is explicitly skipped.</p>
 */
class TimelineRevisionS3OutputRealRenderSmokeTest {

    // Dev-only S3 configuration (matches docker-compose.dev.yml)
    private static final String S3_ENDPOINT = "http://localhost:9000";
    private static final String S3_REGION = "us-east-1";
    private static final String S3_ACCESS_KEY = "dev-access-key";
    private static final String S3_SECRET_KEY = "dev-secret-key";
    private static final String S3_BUCKET = "r10b-smoke-test";

    @TempDir
    Path tempDir;

    private StorageRuntimeService storageRuntime;
    private ProductRuntimeService productRuntime;
    private RenderOutputRegistrationService registrationService;
    private TimelineRenderJobMapper mapper;
    private TimelineScriptParser parser;
    private TimelineInputProductResolver inputProductResolver;
    private RenderJobStatusService statusService;
    private S3ObjectMaterializer s3Materializer;
    private S3ObjectWriter s3Writer;
    private S3Client s3Client;

    private InMemoryTimelineRevisionRepository revisionRepo;
    private InMemoryTimelineSnapshotService snapshotService;

    @BeforeAll
    static void requireS3Endpoint() {
        boolean reachable = false;
        try (Socket socket = new Socket("localhost", 9000)) {
            reachable = true;
        } catch (IOException ignored) {}
        assumeTrue(reachable, "S3 endpoint not reachable at localhost:9000; skipping S3-output render smoke");
    }

    @BeforeEach
    void setUp() {
        R2FixtureGenerator.assumeFfmpegAvailable();

        // Create S3 client for cleanup
        StorageS3Properties s3Props = new StorageS3Properties();
        s3Props.setEnabled(true);
        s3Props.setEndpoint(S3_ENDPOINT);
        s3Props.setRegion(S3_REGION);
        s3Props.setAccessKey(S3_ACCESS_KEY);
        s3Props.setSecretKey(S3_SECRET_KEY);
        s3Props.setPathStyleAccess(true);
        s3Props.setDefaultBucket(S3_BUCKET);

        s3Client = S3Client.builder()
                .region(Region.of(S3_REGION))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .endpointOverride(java.net.URI.create(S3_ENDPOINT))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(S3_ACCESS_KEY, S3_SECRET_KEY)))
                .build();

        s3Materializer = new S3ObjectMaterializer(s3Props);
        s3Writer = new S3ObjectWriter(s3Props);

        // Ensure bucket exists
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(S3_BUCKET).build());
        } catch (Exception ignored) {
            // Bucket may already exist
        }

        // Set up in-memory repositories
        StorageReferenceRepository storageRepo = new InMemoryStorageReferenceRepository();
        ProductRepository productRepo = new InMemoryProductRepository();
        ProductDependencyRepository depRepo = new InMemoryProductDependencyRepository();
        storageRuntime = new StorageRuntimeService(storageRepo, s3Materializer);
        productRuntime = new ProductRuntimeService(productRepo, depRepo);

        // Configure S3 output storage
        RenderOutputStorageProperties outputProps = new RenderOutputStorageProperties();
        outputProps.setProvider("s3-compatible");
        outputProps.setS3Bucket(S3_BUCKET);
        outputProps.setS3KeyPrefix("projects");

        // Create registration service with S3 output support
        registrationService = new RenderOutputRegistrationService(
                storageRuntime, productRuntime, tempDir, s3Writer, outputProps);

        TimelineExtensionsReader extensionsReader = new TimelineExtensionsReader();
        parser = new TimelineScriptParser(extensionsReader);
        InternalTimelineWriter writer = new InternalTimelineWriter(extensionsReader);
        mapper = new TimelineRenderJobMapper(parser, writer);
        inputProductResolver = new TimelineInputProductResolver(productRuntime);
        statusService = new RenderJobStatusService(productRuntime, depRepo);

        revisionRepo = new InMemoryTimelineRevisionRepository();
        snapshotService = new InMemoryTimelineSnapshotService();
    }

    @AfterEach
    void tearDown() {
        // Clean up S3 objects created during test
        if (s3Client != null) {
            try {
                ListObjectsV2Response objects = s3Client.listObjectsV2(
                        ListObjectsV2Request.builder().bucket(S3_BUCKET).build());
                if (objects.contents() != null) {
                    for (S3Object obj : objects.contents()) {
                        try {
                            s3Client.deleteObject(DeleteObjectRequest.builder()
                                    .bucket(S3_BUCKET).key(obj.key()).build());
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("R10B: render output uploaded to S3 with S3_COMPATIBLE provider type")
    void renderOutputUploadedToS3() throws Exception {
        // ── Step 1: Generate real input media (LOCAL) ──
        Path inputVideo = R2FixtureGenerator.generateTestVideo(
                tempDir.resolve("input-media"), 2.0, 320, 180, 24);
        assertTrue(Files.exists(inputVideo), "Input video must be generated");
        assertTrue(Files.size(inputVideo) > 0, "Input video must be non-empty");

        // ── Step 2: Register input as LOCAL RAW_MEDIA Product ──
        Path storageInput = tempDir.resolve("storage-inputs").resolve("input.mp4");
        Files.createDirectories(storageInput.getParent());
        Files.copy(inputVideo, storageInput, StandardCopyOption.REPLACE_EXISTING);

        String inputChecksum = computeSha256(storageInput);
        StorageReference inputRef = new StorageReference(
                null, StorageProviderType.LOCAL.name(), com.example.platform.render.domain.storage.StorageClass.STANDARD,
                tempDir.toString(), tempDir.relativize(storageInput).toString(),
                inputChecksum, inputChecksum, Files.size(storageInput), "video/mp4",
                Instant.now(), Instant.now());
        StorageReference registeredInputRef = storageRuntime.register(inputRef);

        String inputProductId = Ids.newId("prod-input");
        Product inputProduct = new Product(
                inputProductId, "tenant_1", "prj_1", TimelineCoreSmokeFixture.ASSET_ID,
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "upload", "upload-service", null,
                ProductStatus.REGISTERED, registeredInputRef.storageReferenceId(),
                inputChecksum, inputChecksum, "video/mp4", 1,
                "{}", Instant.now(), Instant.now());
        productRuntime.register(inputProduct);
        productRuntime.markReady(inputProductId);

        // ── Step 3: Create TimelineRevision ──
        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();
        String timelineJson = TimelineCoreSmokeFixture.toJson(spec);

        String snapshotId = "snap_r10b_001";
        snapshotService.save(snapshotId, "prj_1", "tenant_1", timelineJson, "1.0.0");

        String revisionId = "rev_r10b_001";
        revisionRepo.insert(new InMemoryTimelineRevisionRepository.RevisionRow(
                revisionId, "prj_1", "tenant_1", snapshotId, "user_1",
                "R10B smoke test revision", OffsetDateTime.now()));

        // ── Step 4: Render with S3 output registration ──
        TimelineRevisionRenderService renderService = createRenderService();

        TimelineRevisionRenderService.RevisionRenderResult result =
                renderService.render("prj_1", revisionId, "default_720p");

        // ── Step 5: Verify render result ──
        assertNotNull(result, "Render result must not be null");
        assertNotNull(result.outputProductId(), "Output product ID must be set");
        assertEquals("READY", result.productStatus(), "Output product must be READY");

        // ── Step 6: Verify output StorageReference is S3_COMPATIBLE ──
        Product outputProduct = productRuntime.find(result.outputProductId()).orElseThrow();
        StorageReference outputRef = storageRuntime.find(outputProduct.storageReferenceId()).orElseThrow();

        assertEquals(StorageProviderType.S3_COMPATIBLE.name(), outputRef.providerType(),
                "Output StorageReference must use S3_COMPATIBLE provider type");
        assertEquals(S3_BUCKET, outputRef.rootPath(),
                "Output rootPath must be the S3 bucket");
        assertTrue(outputRef.relativePath().contains("render-jobs/"),
                "Output relativePath must contain render-jobs/");
        assertTrue(outputRef.relativePath().endsWith(".mp4"),
                "Output relativePath must end with .mp4");

        // ── Step 7: Verify object exists in S3 ──
        HeadObjectResponse headResponse = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(S3_BUCKET)
                .key(outputRef.relativePath())
                .build());
        assertNotNull(headResponse, "Object must exist in S3");
        assertTrue(headResponse.contentLength() > 0, "Object must be non-empty");

        // ── Step 8: Verify object can be materialized ──
        String materializedPath = storageRuntime.materialize(outputProduct.storageReferenceId());
        assertNotNull(materializedPath, "Materialized path must not be null");
        assertTrue(Files.exists(Path.of(materializedPath)), "Materialized file must exist");
        assertTrue(Files.size(Path.of(materializedPath)) > 0, "Materialized file must be non-empty");

        // ── Step 9: Verify output Product properties ──
        assertEquals(ProductType.FINAL_RENDER, outputProduct.productType());
        assertEquals(RepresentationKind.MEDIA_FILE, outputProduct.representationKind());
        assertEquals("video/mp4", outputProduct.mimeType());
        assertNotNull(outputProduct.checksum(), "Checksum must be set");
        assertFalse(outputProduct.checksum().isBlank(), "Checksum must not be blank");

        // ── Step 10: Verify ProductDependency lineage ──
        List<ProductDependency> dependencies = productRuntime.findDependencies(result.outputProductId());
        assertFalse(dependencies.isEmpty(), "Output must have at least one dependency");
        boolean hasInputDep = dependencies.stream()
                .anyMatch(d -> d.dependsOnProductId().equals(inputProductId)
                        && d.dependencyType() == DependencyType.DERIVED_FROM);
        assertTrue(hasInputDep, "Output must have DERIVED_FROM dependency on input");

        // ── Step 11: Verify R7 status query ──
        Optional<RenderJobStatusResponse> statusOpt = statusService.findStatus(
                "prj_1", revisionId, result.renderJobId());
        assertTrue(statusOpt.isPresent(), "R7 status query must return a result");
        RenderJobStatusResponse status = statusOpt.get();
        assertEquals("READY", status.status());
        assertTrue(status.resultAvailable());
        assertNotNull(status.inputProductIds());
        assertFalse(status.inputProductIds().isEmpty());

        // ── Step 12: Verify R7 result query ──
        Optional<RenderJobResultResponse> resultOpt = statusService.findResult(
                "prj_1", revisionId, result.renderJobId());
        assertTrue(resultOpt.isPresent(), "R7 result query must return a result");
        RenderJobResultResponse resultResp = resultOpt.get();
        assertEquals("video/mp4", resultResp.mimeType());
        assertNotNull(resultResp.renderJobId());
        assertNotNull(resultResp.outputProductId());

        // ── Step 13: Verify public response safety ──
        // R7 responses must not expose bucket/key/path/signed URL
        String statusJson = status.toString();
        String resultJson = resultResp.toString();
        for (String sensitive : List.of(S3_BUCKET, "localhost:9000", "signedUrl", "storageReferenceId",
                "rootPath", "relativePath", "materializedPath", "localPath")) {
            assertFalse(statusJson.contains(sensitive),
                    "Status response must not contain: " + sensitive);
            assertFalse(resultJson.contains(sensitive),
                    "Result response must not contain: " + sensitive);
        }

        System.out.println("R10B S3 output smoke PASSED:");
        System.out.println("  renderJobId=" + result.renderJobId());
        System.out.println("  outputProductId=" + result.outputProductId());
        System.out.println("  storageProviderType=" + outputRef.providerType());
        System.out.println("  bucket=" + outputRef.rootPath());
        System.out.println("  objectKey=" + outputRef.relativePath());
    }

    private TimelineRevisionRenderService createRenderService() {
        // Create a mock ProcessToolRunner that generates real video
        ProcessToolRunner toolRunner = new ProcessToolRunner() {
            @Override
            public ToolExecutionResult execute(ToolExecutionRequest request) {
                try {
                    List<String> args = request.args();
                    // Find output path from ffmpeg args
                    String outputPath = args.get(args.size() - 1);
                    Path output = Path.of(outputPath);
                    Files.createDirectories(output.getParent());

                    // Generate a real video using ffmpeg
                    List<String> cmd = new ArrayList<>();
                    cmd.add("ffmpeg");
                    cmd.add("-y");
                    cmd.add("-f");
                    cmd.add("lavfi");
                    cmd.add("-i");
                    cmd.add("testsrc=duration=2:size=320x180:rate=24");
                    cmd.add("-f");
                    cmd.add("lavfi");
                    cmd.add("-i");
                    cmd.add("sine=frequency=440:duration=2:sample_rate=48000");
                    cmd.add("-c:v");
                    cmd.add("libx264");
                    cmd.add("-preset");
                    cmd.add("ultrafast");
                    cmd.add("-pix_fmt");
                    cmd.add("yuv420p");
                    cmd.add("-c:a");
                    cmd.add("aac");
                    cmd.add("-b:a");
                    cmd.add("64k");
                    cmd.add("-shortest");
                    cmd.add(output.toAbsolutePath().toString());

                    ProcessBuilder pb = new ProcessBuilder(cmd);
                    pb.redirectErrorStream(true);
                    Process p = pb.start();
                    int exitCode = p.waitFor();

                    if (exitCode == 0 && Files.exists(output) && Files.size(output) > 0) {
                        return ToolExecutionResult.success(0, "", "", Instant.now(), Instant.now());
                    } else {
                        return ToolExecutionResult.failed(exitCode, "", "ffmpeg failed", Instant.now(), Instant.now());
                    }
                } catch (Exception e) {
                    return ToolExecutionResult.failed(-1, "", e.getMessage(), Instant.now(), Instant.now());
                }
            }

            @Override
            public ToolExecutionResult execute(ToolExecutionRequest request, ToolSandboxPolicy policy) {
                return execute(request);
            }
        };

        return new TimelineRevisionRenderService(
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

    private String computeSha256(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // ─── Stub services ───

    static class StubTimelineRevisionService extends TimelineRevisionService {
        private final InMemoryTimelineRevisionRepository repo;

        StubTimelineRevisionService(InMemoryTimelineRevisionRepository repo) {
            super(null, null, null, null, null, null, null);
            this.repo = repo;
        }

        @Override
        public Optional<RevisionInfo> findById(String revisionId) {
            return repo.findById(revisionId).map(row -> new RevisionInfo(
                    row.id(), row.projectId(), row.tenantId(), null,
                    1, row.snapshotId(), 1,
                    null, "1.0.0", "test",
                    row.userId(), null, row.message(),
                    List.of(), null, null,
                    false, null, null,
                    row.createdAt() != null ? row.createdAt().toString() : null));
        }
    }

    // ─── In-memory test doubles ───

    static class InMemoryTimelineRevisionRepository {
        private final Map<String, RevisionRow> store = new ConcurrentHashMap<>();

        void insert(RevisionRow row) {
            store.put(row.id(), row);
        }

        Optional<RevisionRow> findById(String id) {
            return Optional.ofNullable(store.get(id));
        }

        record RevisionRow(String id, String projectId, String tenantId, String snapshotId,
                           String userId, String message, OffsetDateTime createdAt) {}
    }

    static class InMemoryTimelineSnapshotService extends TimelineSnapshotService {
        private final Map<String, SnapshotInfo> store = new ConcurrentHashMap<>();

        InMemoryTimelineSnapshotService() {
            super(null);
        }

        void save(String snapshotId, String projectId, String tenantId, String payloadJson, String schemaVersion) {
            store.put(snapshotId, new SnapshotInfo(snapshotId, projectId, tenantId, payloadJson, schemaVersion));
        }

        @Override
        public Optional<String> findPayload(String snapshotId) {
            SnapshotInfo info = store.get(snapshotId);
            return info != null ? Optional.of(info.payloadJson()) : Optional.empty();
        }
    }

    static class InMemoryStorageReferenceRepository extends StorageReferenceRepository {
        private final Map<String, StorageReference> store = new ConcurrentHashMap<>();

        @Override
        public StorageReference save(StorageReference ref) {
            String id = ref.storageReferenceId() != null ? ref.storageReferenceId() : Ids.newId("stor");
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

        @Override
        public boolean exists(String id) {
            return store.containsKey(id);
        }

        @Override
        public void delete(String id) {
            store.remove(id);
        }
    }

    static class InMemoryProductRepository extends ProductRepository {
        private final Map<String, Product> store = new ConcurrentHashMap<>();
        private final Map<String, List<Product>> byProject = new ConcurrentHashMap<>();
        private final Map<String, List<Product>> byAsset = new ConcurrentHashMap<>();

        @Override
        public Product save(Product p) {
            String id = p.productId() != null ? p.productId() : Ids.newId("prod");
            Product saved = new Product(id, p.tenantId(), p.projectId(), p.ownerAssetId(),
                    p.productType(), p.representationKind(), p.producerType(), p.producerId(),
                    p.sourceTimelineRevisionId(), p.status(), p.storageReferenceId(),
                    p.checksum(), p.contentHash(), p.mimeType(), p.version(),
                    p.metadataJson(), p.createdAt(), p.updatedAt());
            store.put(id, saved);
            // Update byProject: replace existing entry with same ID, or add new
            List<Product> projectList = byProject.computeIfAbsent(p.projectId(), k -> new ArrayList<>());
            projectList.removeIf(existing -> existing.productId().equals(id));
            projectList.add(saved);
            if (p.ownerAssetId() != null) {
                List<Product> assetList = byAsset.computeIfAbsent(p.ownerAssetId(), k -> new ArrayList<>());
                assetList.removeIf(existing -> existing.productId().equals(id));
                assetList.add(saved);
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
                    .filter(p -> timelineRevisionId.equals(p.sourceTimelineRevisionId()))
                    .toList();
        }
    }

    static class InMemoryProductDependencyRepository extends ProductDependencyRepository {
        private final Map<String, ProductDependency> store = new ConcurrentHashMap<>();

        @Override
        public ProductDependency save(ProductDependency d) {
            String id = d.dependencyId() != null ? d.dependencyId() : Ids.newId("dep");
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
            return findDependencies(productId).stream()
                    .anyMatch(d -> d.dependsOnProductId().equals(dependsOnId));
        }

        @Override
        public void delete(String dependencyId) {
            store.remove(dependencyId);
        }
    }
}
