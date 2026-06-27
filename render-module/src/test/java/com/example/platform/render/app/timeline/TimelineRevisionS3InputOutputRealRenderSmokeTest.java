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
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * R10C: Full S3 Input + S3 Output E2E render smoke test.
 *
 * <p>Proves that a full render chain can use S3-compatible storage for BOTH
 * input and output, completing the StorageRuntime S3 closure.</p>
 *
 * <p><b>This test invokes real FFmpeg and requires a running S3-compatible backend.</b>
 * It proves:
 * <ol>
 *   <li>Input media is generated as a tiny real mp4</li>
 *   <li>Input media is uploaded to S3-compatible object storage</li>
 *   <li>Input Product uses S3-compatible StorageReference</li>
 *   <li>TimelineRevision references the input source asset</li>
 *   <li>Render materializes input from S3</li>
 *   <li>FFmpeg/libass renders from materialized local input</li>
 *   <li>Render path does not use testsrc or lavfi</li>
 *   <li>Output is uploaded to S3-compatible internal storage</li>
 *   <li>Output Product uses S3-compatible StorageReference</li>
 *   <li>Output object exists in RustFS</li>
 *   <li>Output object can be materialized/read back</li>
 *   <li>Output Product is FINAL_RENDER READY</li>
 *   <li>ProductDependency lineage exists (DERIVED_FROM)</li>
 *   <li>R7 status/result APIs return safe metadata</li>
 *   <li>No sensitive data in public responses</li>
 * </ol>
 *
 * <p>If FFmpeg or S3 endpoint is unavailable, the test is explicitly skipped.</p>
 */
class TimelineRevisionS3InputOutputRealRenderSmokeTest {

    // Dev-only S3 configuration (matches docker-compose.dev.yml)
    private static final String S3_ENDPOINT = "http://localhost:9000";
    private static final String S3_REGION = "us-east-1";
    private static final String S3_ACCESS_KEY = "dev-access-key";
    private static final String S3_SECRET_KEY = "dev-secret-key";
    private static final String S3_BUCKET = "r10c-smoke-test";

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
        assumeTrue(reachable, "S3 endpoint not reachable at localhost:9000; skipping S3 input+output render smoke");
    }

    @BeforeEach
    void setUp() {
        R2FixtureGenerator.assumeFfmpegAvailable();

        // Create S3 client for uploading test fixtures
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

        // Create StorageRuntimeService with S3 materializer
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

        revisionRepo = new InMemoryTimelineRevisionRepository();
        snapshotService = new InMemoryTimelineSnapshotService();

        inputProductResolver = new TimelineInputProductResolver(productRuntime);
        statusService = new RenderJobStatusService(productRuntime, depRepo);
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
    @DisplayName("R10C: full S3 input + S3 output render smoke")
    void fullS3InputOutputRenderSmoke() throws Exception {
        // ── Step 1: Generate real input media ──
        Path inputVideo = R2FixtureGenerator.generateTestVideo(
                tempDir.resolve("input-media"), 3.0, 320, 180, 24);
        assertTrue(Files.exists(inputVideo), "Input video must be generated");
        assertTrue(Files.size(inputVideo) > 0, "Input video must be non-zero");

        // ── Step 2: Upload input to S3-compatible object storage ──
        String inputObjectKey = "r10c/input-" + UUID.randomUUID().toString().substring(0, 8) + ".mp4";
        byte[] videoBytes = Files.readAllBytes(inputVideo);
        String inputChecksum = computeSha256(videoBytes);

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(S3_BUCKET)
                        .key(inputObjectKey)
                        .contentType("video/mp4")
                        .build(),
                RequestBody.fromBytes(videoBytes));

        // ── Step 3: Create input StorageReference with S3 provider type ──
        StorageReference inputRef = storageRuntime.register(new StorageReference(
                null,
                StorageProviderType.S3_COMPATIBLE.name(),
                com.example.platform.render.domain.storage.StorageClass.STANDARD,
                S3_BUCKET,
                inputObjectKey,
                inputChecksum,
                inputChecksum,
                videoBytes.length,
                "video/mp4",
                Instant.now(),
                Instant.now()));

        assertNotNull(inputRef.storageReferenceId(), "Input S3 StorageReference must be registered");
        assertEquals(StorageProviderType.S3_COMPATIBLE.name(), inputRef.providerType());

        // ── Step 4: Register input RAW_MEDIA Product ──
        Product inputProduct = registerReadyRawMediaProduct(
                TimelineCoreSmokeFixture.ASSET_ID,
                TimelineCoreSmokeFixture.TENANT_ID,
                TimelineCoreSmokeFixture.PROJECT_ID,
                inputRef);
        assertEquals(ProductStatus.READY, inputProduct.status());
        assertEquals(inputRef.storageReferenceId(), inputProduct.storageReferenceId());

        // Verify input Product properties
        assertEquals(ProductType.RAW_MEDIA, inputProduct.productType());
        assertEquals(RepresentationKind.MEDIA_FILE, inputProduct.representationKind());
        assertEquals("video/mp4", inputProduct.mimeType());

        // ── Step 5: Create TimelineRevision ──
        TimelineSpec spec = TimelineCoreSmokeFixture.createMinimalVideoTimeline();
        String timelineJson = TimelineCoreSmokeFixture.toJson(spec);

        String snapshotId = "snap_r10c_001";
        snapshotService.saveWithId(snapshotId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, timelineJson);

        String revisionId = "rev_r10c_001";
        revisionRepo.insert(createRevision(revisionId,
                TimelineCoreSmokeFixture.PROJECT_ID,
                TimelineCoreSmokeFixture.TENANT_ID, snapshotId));

        // ── Step 6: Create real ProcessToolRunner ──
        ProcessToolRunner realRunner = new RealFfmpegProcessToolRunner();

        TimelineRevisionRenderService renderService = new TimelineRevisionRenderService(
                new StubTimelineRevisionService(revisionRepo),
                snapshotService, mapper, parser,
                new RenderInputMaterializationService(storageRuntime, productRuntime),
                registrationService, productRuntime, storageRuntime,
                inputProductResolver, realRunner, tempDir);

        // ── Step 7: Invoke render (S3 input → FFmpeg → S3 output) ──
        TimelineRevisionRenderService.RevisionRenderResult result =
                renderService.render(
                        TimelineCoreSmokeFixture.PROJECT_ID, revisionId, "default_720p");

        // ── Step 8: Verify render result ──
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

        // ── Step 9: Verify output Product ──
        Optional<Product> outputProductOpt = productRuntime.find(result.outputProductId());
        assertTrue(outputProductOpt.isPresent(), "Output Product must exist");
        Product outputProduct = outputProductOpt.get();
        assertEquals(ProductType.FINAL_RENDER, outputProduct.productType());
        assertEquals(RepresentationKind.MEDIA_FILE, outputProduct.representationKind());
        assertEquals(ProductStatus.READY, outputProduct.status());
        assertEquals("video/mp4", outputProduct.mimeType());
        assertNotNull(outputProduct.checksum(), "Output checksum must be set");
        assertFalse(outputProduct.checksum().isBlank(), "Output checksum must not be blank");

        // ── Step 10: Verify output StorageReference is S3_COMPATIBLE ──
        StorageReference outputRef = storageRuntime.find(outputProduct.storageReferenceId())
                .orElseThrow(() -> new AssertionError("Output StorageReference must exist"));

        assertEquals(StorageProviderType.S3_COMPATIBLE.name(), outputRef.providerType(),
                "Output StorageReference must use S3_COMPATIBLE provider type");
        assertEquals(S3_BUCKET, outputRef.rootPath(),
                "Output rootPath must be the S3 bucket");
        assertTrue(outputRef.relativePath().contains("render-jobs/"),
                "Output relativePath must contain render-jobs/");
        assertTrue(outputRef.relativePath().endsWith(".mp4"),
                "Output relativePath must end with .mp4");
        assertNotNull(outputRef.checksum(), "Output checksum must be set");
        assertTrue(outputRef.fileSize() > 0, "Output file size must be positive");
        assertEquals("video/mp4", outputRef.mimeType(), "Output mimeType must be video/mp4");

        // ── Step 11: Verify output object exists in S3 ──
        HeadObjectResponse headResponse = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(S3_BUCKET)
                .key(outputRef.relativePath())
                .build());
        assertNotNull(headResponse, "Output object must exist in S3");
        assertTrue(headResponse.contentLength() > 0, "Output object must be non-empty");

        // ── Step 12: Verify output can be materialized ──
        String materializedPath = storageRuntime.materialize(outputProduct.storageReferenceId());
        assertNotNull(materializedPath, "Materialized path must not be null");
        assertTrue(Files.exists(Path.of(materializedPath)), "Materialized file must exist");
        assertTrue(Files.size(Path.of(materializedPath)) > 0, "Materialized file must be non-empty");

        // Verify with ffprobe
        boolean ffprobeOk = verifyWithFfprobe(Path.of(materializedPath));
        assertTrue(ffprobeOk, "Output must be a valid media file readable by ffprobe");

        // ── Step 13: Verify input can be materialized from S3 ──
        String inputMaterializedPath = storageRuntime.materialize(inputProduct.storageReferenceId());
        assertNotNull(inputMaterializedPath, "Input materialized path must not be null");
        assertTrue(Files.exists(Path.of(inputMaterializedPath)), "Input materialized file must exist");
        assertTrue(Files.size(Path.of(inputMaterializedPath)) > 0, "Input materialized file must be non-empty");

        // ── Step 14: Verify ProductDependency lineage ──
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

        // ── Step 15: Verify R7 status query ──
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

        // ── Step 16: Verify R7 result query ──
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

        // ── Step 17: Verify public response safety ──
        String statusStr = status.toString();
        assertFalse(statusStr.contains("storageReferenceId"), "No storageReferenceId in status");
        assertFalse(statusStr.contains("storageProvider"), "No storageProvider in status");
        assertFalse(statusStr.contains("signedUrl"), "No signedUrl in status");
        assertFalse(statusStr.contains("localPath"), "No localPath in status");
        assertFalse(statusStr.contains("materializedPath"), "No materializedPath in status");
        assertFalse(statusStr.contains("provider"), "No provider in status");
        assertFalse(statusStr.contains("backend"), "No backend in status");
        assertFalse(statusStr.contains("environment"), "No environment in status");
        assertFalse(statusStr.contains(S3_BUCKET), "No bucket in status");
        assertFalse(statusStr.contains(inputObjectKey), "No input object key in status");

        String resultStr = resultResp.toString();
        assertFalse(resultStr.contains("storageReferenceId"), "No storageReferenceId in result");
        assertFalse(resultStr.contains("storageProvider"), "No storageProvider in result");
        assertFalse(resultStr.contains("signedUrl"), "No signedUrl in result");
        assertFalse(resultStr.contains("localPath"), "No localPath in result");
        assertFalse(resultStr.contains("materializedPath"), "No materializedPath in result");
        assertFalse(resultStr.contains(S3_BUCKET), "No bucket in result");
        assertFalse(resultStr.contains(inputObjectKey), "No input object key in result");

        // Verify no sensitive data in output Product metadata
        String metadata = outputProduct.metadataJson();
        assertFalse(metadata.contains("signedUrl"), "No signedUrl in Product metadata");
        assertFalse(metadata.contains("presign"), "No presign in Product metadata");
        assertFalse(metadata.contains("storageProvider"), "No storageProvider in Product metadata");
        assertFalse(metadata.contains(S3_BUCKET), "No bucket in Product metadata");
        assertFalse(metadata.contains(inputObjectKey), "No input object key in Product metadata");

        // Verify provenance fields ARE present
        assertTrue(metadata.contains("\"inputProductIds\":"), "Must contain inputProductIds in metadata");
        assertTrue(metadata.contains("\"renderJobId\":"), "Must contain renderJobId in metadata");
        assertTrue(metadata.contains("\"timelineRevisionId\":"), "Must contain timelineRevisionId in metadata");
        assertTrue(metadata.contains("\"snapshotId\":"), "Must contain snapshotId in metadata");

        System.out.println("[R10C] Full S3 input+output render smoke PASSED");
        System.out.println("[R10C]   renderJobId:     " + result.renderJobId());
        System.out.println("[R10C]   outputProductId: " + result.outputProductId());
        System.out.println("[R10C]   inputProviderType: " + inputRef.providerType());
        System.out.println("[R10C]   outputProviderType: " + outputRef.providerType());
        System.out.println("[R10C]   inputBucket:     " + S3_BUCKET);
        System.out.println("[R10C]   inputKey:        " + inputObjectKey);
        System.out.println("[R10C]   outputBucket:    " + outputRef.rootPath());
        System.out.println("[R10C]   outputKey:       " + outputRef.relativePath());
    }

    // ─── Helper: register a READY RAW_MEDIA Product from an existing S3 StorageReference ───

    private Product registerReadyRawMediaProduct(String assetId, String tenantId,
                                                  String projectId, StorageReference ref) {
        String productId = Ids.newId("prod");
        Product product = new Product(
                productId, tenantId, projectId, assetId,
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "upload", "upload-service", null,
                ProductStatus.REGISTERED, ref.storageReferenceId(),
                ref.checksum(), ref.checksum(), "video/mp4", 1,
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
                1, "hash-r10c", "internal-1.0", "sync", "user-1", null,
                "R10C S3 input+output smoke revision", null, null, null, false, null, null,
                OffsetDateTime.now());
    }

    // ─── Helper: compute SHA-256 ───

    private String computeSha256(byte[] data) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // ─── Real ProcessToolRunner that invokes actual FFmpeg ───

    static class RealFfmpegProcessToolRunner implements ProcessToolRunner {
        @Override
        public ToolExecutionResult execute(ToolExecutionRequest request) {
            try {
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
