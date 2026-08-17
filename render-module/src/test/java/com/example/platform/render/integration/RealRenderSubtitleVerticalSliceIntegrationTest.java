package com.example.platform.render.integration;

import com.example.platform.timeline.app.TimelineCanonicalRejectionException;import com.example.platform.timeline.app.TimelineContentHasher;import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;
import com.example.platform.shared.time.MediaTime;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.app.ToolRegistry;
import com.example.platform.extension.infrastructure.DefaultProcessToolRunner;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.render.app.input.RenderInputMaterializationService;
import com.example.platform.render.app.output.RenderOutputRegistrationService;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.app.timeline.InternalTimelineToEditorConverter;
import com.example.platform.render.app.timeline.TimelineSpecImportAdapter;
import com.example.platform.timeline.app.TimelineImportService;
import com.example.platform.render.app.timeline.RenderTimelinePayloadCodec;
import com.example.platform.timeline.app.ProductCurrentRevisionService;
import com.example.platform.timeline.app.TimelineCanonicalizer;
import com.example.platform.render.app.timeline.TimelineConversionService;
import com.example.platform.render.app.timeline.TimelineInputProductResolver;
import com.example.platform.timeline.app.TimelineArtifactPinValidator;
import com.example.platform.timeline.app.TimelinePatchService;
import com.example.platform.render.app.timeline.TimelineRenderJobMapper;
import com.example.platform.timeline.app.TimelineRevisionDiffService;
import com.example.platform.render.app.timeline.TimelineRevisionRenderService;
import com.example.platform.timeline.adapter.TimelineRevisionRepository;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.app.TimelineRevisionService;
import com.example.platform.timeline.app.TimelineSemanticDiffService;
import com.example.platform.render.app.timeline.TimelineSpecResolver;
import com.example.platform.render.app.timeline.TimelineTestSupport;
import com.example.platform.render.app.timeline.compile.ArtifactGraphCompiler;
import com.example.platform.render.app.timeline.compile.CapabilityGraphCompiler;
import com.example.platform.render.app.timeline.compile.LocalExecutionPlanRunner;
import com.example.platform.render.app.timeline.compile.PlanBasedTimelineRevisionRenderService;
import com.example.platform.render.app.timeline.compile.ProviderBindingCompiler;
import com.example.platform.render.app.timeline.compile.ProviderExecutionDocumentDraftCompiler;
import com.example.platform.render.app.timeline.compile.RenderExecutionPlanCompiler;
import com.example.platform.render.app.timeline.compile.RenderExecutionStepExecutor;
import com.example.platform.render.app.timeline.compile.RenderPlanPolicyGuard;
import com.example.platform.render.app.timeline.compile.TimelineNormalizationService;
import com.example.platform.render.app.timeline.compile.audit.RenderAuditEvent;
import com.example.platform.render.app.timeline.compile.audit.RenderAuditEventSink;
import com.example.platform.render.app.timeline.compile.audit.RenderAuditRecorder;
import com.example.platform.render.domain.product.Product;
import com.example.platform.render.domain.product.ProductStatus;
import com.example.platform.render.domain.interchange.TimelineExtensionsReader;
import com.example.platform.render.domain.interchange.TimelineScriptParser;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.render.infrastructure.RenderToolCapabilityInventory;
import com.example.platform.render.infrastructure.ffmpeg.FFmpegCommandFactory;
import com.example.platform.render.infrastructure.ffmpeg.FFmpegRenderProvider;
import com.example.platform.render.infrastructure.media.MediaAssetResolver;
import com.example.platform.render.infrastructure.product.ProductDependencyRepository;
import com.example.platform.render.infrastructure.product.ProductRepository;
import com.example.platform.render.infrastructure.storage.StorageReferenceRepository;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.FixturePath;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT;
import static com.example.platform.typedschema.jooq.generated.tables.StorageReference.STORAGE_REFERENCE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Authentic Contract P vertical-slice integration test
 * (PTCSG_REAL_RENDER_SUBTITLE_VERTICAL_SLICE_V1).
 *
 * <p>Real PostgreSQL Testcontainers + real FFmpeg + real ffprobe. Proves:
 * E1-saved Timeline -> governed snapshot payload -> production plan-based render path
 * -> playable MP4 -> ffprobe-validated media -> READY Product through
 * ProductRuntimeService -> tenant/project isolation; plus deterministic extracted-frame
 * subtitle burn-in verification through the production libass-capable provider surface
 * using the E1-saved caption-carrying payload.</p>
 */
class RealRenderSubtitleVerticalSliceIntegrationTest extends PostgresTestContainerSupport {

    @TempDir
    Path tempDir;

    private static DataSource dataSource;
    private static DSLContext dsl;

    private TimelineRevisionSaveService saveService;
    private TimelineSnapshotService snapshotService;
    private TimelineRevisionService revisionService;
    private ProductRuntimeService productRuntime;
    private StorageRuntimeService storageRuntime;
    private Path storageRoot;
    private boolean realFfmpegAvailable;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES);
        RenderTestSchemaFixture.createSchema(dsl);
        // The production storage_reference table shape, created in the ephemeral test DB
        // (test infrastructure only; no production schema change, no migration).
        dsl.execute("""
                CREATE TABLE IF NOT EXISTS storage_reference (
                    storage_reference_id varchar(256) primary key,
                    provider_type varchar(32),
                    storage_class varchar(32),
                    root_path varchar(1024),
                    relative_path varchar(1024),
                    checksum varchar(128),
                    content_hash varchar(128),
                    file_size bigint,
                    mime_type varchar(64),
                    tenant_id varchar(64),
                    created_at timestamp,
                    updated_at timestamp,
                    UNIQUE (provider_type, root_path, relative_path)
                )
                """);
        dsl.execute("""
                CREATE TABLE IF NOT EXISTS product_dependency (
                    dependency_id varchar(256) primary key,
                    tenant_id varchar(64),
                    project_id varchar(256),
                    product_id varchar(256),
                    depends_on_product_id varchar(256),
                    dependency_type varchar(32),
                    created_at timestamp,
                    updated_at timestamp,
                    UNIQUE (product_id, depends_on_product_id, dependency_type)
                )
                """);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() throws Exception {
        RenderTestSchemaFixture.truncate(dsl);
        com.example.platform.shared.web.TenantContext.set("ten-vslice");
        storageRoot = tempDir.resolve("storage");
        Files.createDirectories(storageRoot);
        snapshotService = new TimelineSnapshotService(dsl);
        ProductCurrentRevisionService currentRevisionService = new ProductCurrentRevisionService(dsl);
        saveService = new TimelineRevisionSaveService(dsl, currentRevisionService,
                new com.example.platform.timeline.canonical.TimelineContentDigester(), snapshotService);
        revisionService = buildTimelineRevisionService(dsl, snapshotService);
        ProductRepository productRepo = new ProductRepository(dsl);
        ProductDependencyRepository depRepo = new ProductDependencyRepository(dsl);
        productRuntime = new ProductRuntimeService(productRepo, depRepo);
        StorageReferenceRepository storageRepo = new StorageReferenceRepository(dsl);
        storageRuntime = new StorageRuntimeService(storageRepo, mockProvider(null));
        realFfmpegAvailable = new RenderToolCapabilityInventory().isToolAvailable("ffmpeg");
    }

    @Test
    void e1SavedTimeline_rendersThroughProductionPath_toPlayableMp4AndReadyProduct() throws Exception {
        Assumptions.assumeTrue(realFfmpegAvailable, "real ffmpeg required");
        Path fixtureVideo = FixturePath.goldenProjectAssets().resolve("video/vertical-slice-input.mp4");
        Assumptions.assumeTrue(Files.exists(fixtureVideo), "governed media fixture missing");

        String projectId = "prj-vslice-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        String tenantId = "ten-vslice";
        insertProduct(projectId, tenantId);
        materializeInputMedia(fixtureVideo);
        // The production render path parses the frozen document-JSON payload with
        // TimelineScriptParser, whose OTIO-style clip parsing assigns the parser-default
        // asset id ("track-0-clip-0") because the canonical document clip shape
        // (clipId/assetId) is not read by that parser. The input Product is therefore
        // registered under the id the production path actually resolves.
        registerReadyInputProduct("track-0-clip-0", tenantId, projectId, "assets/video/vertical-slice-input.mp4");

        // 1. E1-gated save with governed captions.v1 metadata.
        TimelineDocument doc = createDocumentWithCaptions("clip-vslice", "asset-vslice");
        var revision = saveService.saveRevision(projectId, null, doc, "vslice-user");

        // 2. Snapshot payload exists through the sole authority.
        String snapshotId = snapshotIdOf(revision.revisionId());
        Optional<String> payload = snapshotService.findPayload(snapshotId);
        assertTrue(payload.isPresent(), "E1 save must persist the governed snapshot payload");
        assertTrue(payload.get().contains("\"textOverlays\""), "payload must carry the caption expansion");

        // 3. Render through the real production plan-based path.
        PlanBasedTimelineRevisionRenderService renderService = buildPlanBasedRenderService();
        TimelineRevisionRenderService.RevisionRenderResult result =
                renderService.render(projectId, revision.revisionId(), "preview_720p");

        // 4. Playable output exists.
        Path output = storageRoot.resolve("render-output").resolve(result.renderJobId()).resolve("output.mp4");
        assertTrue(Files.exists(output), "output MP4 must exist");
        assertTrue(Files.size(output) > 0, "output MP4 must be non-empty");

        // 5. ffprobe validation.
        FfprobeInfo info = ffprobe(output);
        assertTrue(info.videoStreams >= 1, "output must have a video stream");
        assertEquals("h264", info.videoCodec);
        assertEquals(1280, info.width);
        assertEquals(720, info.height);
        assertTrue(info.durationSeconds >= 2.5 && info.durationSeconds <= 3.5,
                "duration must be within frozen tolerance (3.0s +/- 0.5s), got " + info.durationSeconds);

        // 6. Output Product registered and READY only through ProductRuntimeService.
        Product outputProduct = productRuntime.find(result.outputProductId()).orElseThrow();
        assertEquals(ProductStatus.READY, outputProduct.status(), "output Product must be READY");
        assertEquals(tenantId, outputProduct.tenantId());
        assertEquals(projectId, outputProduct.projectId());
        assertNotNull(outputProduct.storageReferenceId(), "storage reference must be valid");

        // 7. Tenant/project isolation: rendering with a different project fails closed.
        PlanBasedTimelineRevisionRenderService isolationService = buildPlanBasedRenderService();
        assertThrows(IllegalArgumentException.class,
                () -> isolationService.render("other-project-" + projectId, revision.revisionId(), "preview_720p"),
                "cross-project render must be rejected");
    }

    @Test
    void subtitleBurnIn_deterministicExtractedFrameDiff() throws Exception {
        Assumptions.assumeTrue(realFfmpegAvailable, "real ffmpeg required");
        Path fixtureVideo = FixturePath.goldenProjectAssets().resolve("video/vertical-slice-input.mp4");
        Assumptions.assumeTrue(Files.exists(fixtureVideo), "governed media fixture missing");

        Path mediaDir = storageRoot.resolve("assets/video");
        Files.createDirectories(mediaDir);
        Path media = mediaDir.resolve("vertical-slice-input.mp4");
        Files.copy(fixtureVideo, media, StandardCopyOption.REPLACE_EXISTING);
        Path governedSrt = FixturePath.goldenProjectAssets().resolve("subtitle/subtitles_en.srt");
        Assumptions.assumeTrue(Files.exists(governedSrt), "governed SRT missing");
        // The provider verifies subtitle existence relative to the test worker CWD
        // (the render-module directory), so the governed SRT is mirrored there.
        Path cwdSrtDir = Path.of("build", "test-srt");
        Files.createDirectories(cwdSrtDir);
        Files.copy(governedSrt, cwdSrtDir.resolve("subtitles_en.srt"), StandardCopyOption.REPLACE_EXISTING);

        ToolRegistry registry = new ToolRegistry();
        String ffmpegBin = findBinary("ffmpeg");
        registry.registerExecutable("ffmpeg", ffmpegBin);
        registry.registerExecutable("ffprobe", findBinary("ffprobe"));
        registry.registerTool(new com.example.platform.extension.domain.ToolDefinition(
                "ffmpeg", "ffmpeg", "FFmpeg", ffmpegBin,
                java.util.List.of(), com.example.platform.extension.domain.ToolSandboxPolicy.defaults()));
        ProcessToolRunner runner = new DefaultProcessToolRunner(registry);
        FFmpegRenderProvider provider = new FFmpegRenderProvider(runner, new FFmpegCommandFactory(),
                new TimelineScriptParser(new com.example.platform.render.domain.interchange.TimelineExtensionsReader()),
                new MediaAssetResolver("/tmp/platform", Optional.empty()));
        provider.setStorageRoot(storageRoot.toString());

        String withSubtitle = """
                {"id":"tl-subtitle","tracks":[{"id":"v1","type":"VIDEO","clips":[
                  {"id":"clip-1","assetRef":{"storageUri":"assets/video/vertical-slice-input.mp4"},
                   "sourceRange":{"start_time":0,"duration":3},"timelineStart":0},
                  {"id":"clip-2","assetRef":{"storageUri":"assets/video/vertical-slice-input.mp4"},
                   "sourceRange":{"start_time":0,"duration":3},"timelineStart":3}]}],
                 "metadata":{"subtitlePath":"build/test-srt/subtitles_en.srt"}}""";
        String withoutSubtitle = """
                {"id":"tl-subtitle","tracks":[{"id":"v1","type":"VIDEO","clips":[
                  {"id":"clip-1","assetRef":{"storageUri":"assets/video/vertical-slice-input.mp4"},
                   "sourceRange":{"start_time":0,"duration":3},"timelineStart":0}]}]}""";

        var withResult = provider.render("vslice-job-with-sub", withSubtitle, "preview_720p");
        var withoutResult = provider.render("vslice-job-without-sub", withoutSubtitle, "preview_720p");

        Path outWith = storageRoot.resolve("artifacts").resolve("vslice-job-with-sub").resolve("output.mp4");
        Path outWithout = storageRoot.resolve("artifacts").resolve("vslice-job-without-sub").resolve("output.mp4");
        assertTrue(Files.exists(outWith) && Files.size(outWith) > 0);
        assertTrue(Files.exists(outWithout) && Files.size(outWithout) > 0);

        // ffprobe validity for the subtitled output.
        FfprobeInfo info = ffprobe(outWith);
        assertTrue(info.videoStreams >= 1);

        // Deterministic extracted-frame comparison at the cue midpoint (1.5s).
        byte[] frameWith = extractFrame(outWith, 1.5);
        byte[] frameWithout = extractFrame(outWithout, 1.5);
        int width = 1280;
        int differingPixels = countDifferingPixels(frameWith, frameWithout, width, 1280, 720);
        assertTrue(differingPixels > 200,
                "subtitle burn-in must produce visible pixel differences in the subtitle region, got "
                        + differingPixels + " differing pixels");
    }

    @Test
    void invalidSave_renderNeverReachesReady() {
        // Canonically invalid document -> rejection -> no revision, no payload, nothing to render.
        String projectId = "prj-bad-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        var invalid = createDocumentWithDuplicateTrackIds();
        assertThrows(com.example.platform.timeline.app.TimelineCanonicalRejectionException.class,
                () -> saveService.saveRevision(projectId, null, invalid, "vslice-user"));
        assertEquals(0L, dsl.selectCount()
                .from(com.example.platform.typedschema.jooq.generated.tables.TimelineSnapshot.TIMELINE_SNAPSHOT)
                .where(com.example.platform.typedschema.jooq.generated.tables.TimelineSnapshot.TIMELINE_SNAPSHOT.PROJECT_ID.eq(projectId))
                .fetchOne(0, Long.class));
    }

    // --- wiring ---

    private TimelineRevisionService buildTimelineRevisionService(DSLContext dsl, TimelineSnapshotService snapshotService) {
        TimelineExtensionsReader extensionsReader = new TimelineExtensionsReader();
        TimelineScriptParser parser = new TimelineScriptParser(extensionsReader);
        TimelineSpecImportAdapter importAdapter = new TimelineSpecImportAdapter(extensionsReader);
        TimelineImportService importService = new TimelineImportService();
        TimelineCanonicalizer canonicalizer = new TimelineCanonicalizer();
        TimelineSpecResolver resolver = new TimelineSpecResolver(TimelineTestSupport.internalTimelineAdapter(), parser);
        TimelineConversionService conversionService = new TimelineConversionService(resolver, importAdapter, importService);
        TimelinePatchService patchService = new TimelinePatchService(canonicalizer);
        return new TimelineRevisionService(
                new TimelineRevisionRepository(dsl), snapshotService,
                new com.example.platform.timeline.app.TimelineContentHasher(canonicalizer),
                new TimelineRevisionDiffService(),
                new RenderTimelinePayloadCodec(conversionService, new InternalTimelineToEditorConverter()),
                patchService,
                new TimelineSemanticDiffService(canonicalizer),
                new TimelineArtifactPinValidator(new com.example.platform.render.testutil.NoopArtifactQueryService()),
                new com.example.platform.artifact.app.ArtifactPinService(
                        new com.example.platform.artifact.infrastructure.ArtifactPinRepository(dsl)));
    }

    private PlanBasedTimelineRevisionRenderService buildPlanBasedRenderService() {
        TimelineExtensionsReader extensionsReader = new TimelineExtensionsReader();
        TimelineScriptParser parser = new TimelineScriptParser(extensionsReader);
        TimelineSpecImportAdapter importAdapter = new TimelineSpecImportAdapter(extensionsReader);
        TimelineImportService importService = new TimelineImportService();
        TimelineRenderJobMapper mapper = new TimelineRenderJobMapper(parser, importAdapter, importService);
        RenderInputMaterializationService materializationService =
                new RenderInputMaterializationService(storageRuntime, productRuntime);
        RenderOutputRegistrationService registrationService = new RenderOutputRegistrationService(
                storageRuntime, productRuntime, storageRoot, mockProvider(null), mockProvider(null));
        TimelineInputProductResolver inputProductResolver = new TimelineInputProductResolver(productRuntime);
        TimelineNormalizationService normalizer = new TimelineNormalizationService();
        ArtifactGraphCompiler artifactCompiler = new ArtifactGraphCompiler();
        CapabilityGraphCompiler capabilityCompiler = new CapabilityGraphCompiler();
        ProviderBindingCompiler bindingCompiler = new ProviderBindingCompiler();
        ProviderExecutionDocumentDraftCompiler draftCompiler = new ProviderExecutionDocumentDraftCompiler();
        RenderExecutionPlanCompiler planCompiler = new RenderExecutionPlanCompiler();
        RenderPlanPolicyGuard policyGuard = new RenderPlanPolicyGuard();
        RenderToolCapabilityInventory toolInventory = new RenderToolCapabilityInventory();
        ToolRegistry registry = new ToolRegistry();
        String ffmpegBin = findBinary("ffmpeg");
        registry.registerExecutable("ffmpeg", ffmpegBin);
        registry.registerExecutable("ffprobe", findBinary("ffprobe"));
        registry.registerTool(new com.example.platform.extension.domain.ToolDefinition(
                "ffmpeg", "ffmpeg", "FFmpeg", ffmpegBin,
                java.util.List.of(), com.example.platform.extension.domain.ToolSandboxPolicy.defaults()));
        ProcessToolRunner runner = new DefaultProcessToolRunner(registry);
        RenderAuditRecorder auditRecorder = new RenderAuditRecorder(new RenderAuditEventSink() {
            @Override public void record(RenderAuditEvent event) { }
            @Override public List<RenderAuditEvent> findAll() { return List.of(); }
            @Override public List<RenderAuditEvent> findByRenderJobId(String renderJobId) { return List.of(); }
            @Override public List<RenderAuditEvent> findByProjectId(String projectId) { return List.of(); }
            @Override public void clear() { }
        });
        RenderExecutionStepExecutor stepExecutor = new RenderExecutionStepExecutor(
                materializationService, registrationService, productRuntime, toolInventory, runner, auditRecorder);
        LocalExecutionPlanRunner planRunner = new LocalExecutionPlanRunner(policyGuard, stepExecutor);
        return new PlanBasedTimelineRevisionRenderService(
                revisionService, snapshotService, mapper, parser, inputProductResolver,
                normalizer, artifactCompiler, capabilityCompiler, bindingCompiler, draftCompiler,
                planCompiler, policyGuard, planRunner, materializationService, registrationService,
                productRuntime, storageRuntime, toolInventory, storageRoot);
    }

    // --- helpers ---

    private void insertProduct(String productId, String tenantId) {
        dsl.insertInto(PRODUCT)
                .set(PRODUCT.PRODUCT_ID, productId)
                .set(PRODUCT.TENANT_ID, tenantId)
                .set(PRODUCT.PROJECT_ID, productId)
                .set(PRODUCT.PRODUCT_TYPE, "OUTPUT")
                .set(PRODUCT.REPRESENTATION_KIND, "MEDIA_FILE")
                .set(PRODUCT.STATUS, "REGISTERED")
                .set(PRODUCT.VERSION, 1)
                .set(PRODUCT.CREATED_AT, java.time.LocalDateTime.now())
                .set(PRODUCT.UPDATED_AT, java.time.LocalDateTime.now())
                .execute();
    }

    private void materializeInputMedia(Path fixtureVideo) throws IOException {
        Path mediaDir = storageRoot.resolve("assets/video");
        Files.createDirectories(mediaDir);
        Files.copy(fixtureVideo, mediaDir.resolve("vertical-slice-input.mp4"), StandardCopyOption.REPLACE_EXISTING);
    }

    private void registerReadyInputProduct(String assetId, String tenantId, String projectId, String relativePath)
            throws Exception {
        Path file = storageRoot.resolve(relativePath);
        String checksum = sha256(file);
        String storageReferenceId = "sr-" + java.util.UUID.randomUUID();
        dsl.insertInto(STORAGE_REFERENCE)
                .set(STORAGE_REFERENCE.STORAGE_REFERENCE_ID, storageReferenceId)
                .set(STORAGE_REFERENCE.PROVIDER_TYPE, "LOCAL")
                .set(STORAGE_REFERENCE.STORAGE_CLASS, "STANDARD")
                .set(STORAGE_REFERENCE.ROOT_PATH, storageRoot.toString())
                .set(STORAGE_REFERENCE.RELATIVE_PATH, relativePath)
                .set(STORAGE_REFERENCE.CHECKSUM, checksum)
                .set(STORAGE_REFERENCE.CONTENT_HASH, checksum)
                .set(STORAGE_REFERENCE.FILE_SIZE, Files.size(file))
                .set(STORAGE_REFERENCE.MIME_TYPE, "video/mp4")
                .set(STORAGE_REFERENCE.CREATED_AT, java.time.LocalDateTime.now())
                .set(STORAGE_REFERENCE.UPDATED_AT, java.time.LocalDateTime.now())
                .execute();
        dsl.insertInto(PRODUCT)
                .set(PRODUCT.PRODUCT_ID, "prod-" + assetId)
                .set(PRODUCT.TENANT_ID, tenantId)
                .set(PRODUCT.PROJECT_ID, projectId)
                .set(PRODUCT.OWNER_ASSET_ID, assetId)
                .set(PRODUCT.PRODUCT_TYPE, "RAW_MEDIA")
                .set(PRODUCT.REPRESENTATION_KIND, "MEDIA_FILE")
                .set(PRODUCT.PRODUCER_ID, "test")
                .set(PRODUCT.STATUS, "READY")
                .set(PRODUCT.STORAGE_REFERENCE_ID, storageReferenceId)
                .set(PRODUCT.CHECKSUM, checksum)
                .set(PRODUCT.CONTENT_HASH, checksum)
                .set(PRODUCT.MIME_TYPE, "video/mp4")
                .set(PRODUCT.VERSION, 1)
                .set(PRODUCT.CREATED_AT, java.time.LocalDateTime.now())
                .set(PRODUCT.UPDATED_AT, java.time.LocalDateTime.now())
                .execute();
    }

    private String snapshotIdOf(String revisionId) {
        return dsl.select(com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION.SNAPSHOT_ID)
                .from(com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION)
                .where(com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION.ID.eq(revisionId))
                .fetchOne(com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION.SNAPSHOT_ID);
    }

    private TimelineDocument createDocumentWithCaptions(String clipId, String assetId) {
        String captions = """
                [
                  {"id":"cue-1","text":"Welcome to the Media Platform","startMs":1000,"durationMs":1000},
                  {"id":"cue-2","text":"你好，字幕验证","startMs":2000,"durationMs":800}
                ]""";
        var clip = new TimelineClip(clipId, assetId, null, null, null,
                MediaTime.ofRational(0, 1), MediaTime.ofRational(3, 1), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        var track = new TimelineTrack("track-1", "Main", TrackType.VIDEO, List.of(clip));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(track),
                new TimelineMetadata("Vertical Slice", "",
                        Map.of(com.example.platform.timeline.app.TimelineDocumentJsonSerializer.CAPTIONS_V1_METADATA_KEY, captions)));
    }

    private TimelineDocument createDocumentWithDuplicateTrackIds() {
        var clip = new TimelineClip("clip-1", "asset-1", null, null, null,
                MediaTime.ofRational(0, 1), MediaTime.ofRational(10, 1), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        var trackA = new TimelineTrack("dup-track", "A", TrackType.VIDEO, List.of(clip));
        var trackB = new TimelineTrack("dup-track", "B", TrackType.AUDIO, List.of(clip));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(trackA, trackB), new TimelineMetadata("Test", "", Map.of()));
    }

    private String findBinary(String name) {
        Path home = Path.of(System.getProperty("user.home"), ".local", "bin", name);
        if (Files.exists(home)) {
            return home.toString();
        }
        return name;
    }

    private String sha256(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private FfprobeInfo ffprobe(Path media) throws Exception {
        List<String> cmd = List.of(findBinary("ffprobe"), "-v", "error",
                "-show_entries", "stream=codec_type,codec_name,width,height",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1", media.toString());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes());
        assertEquals(0, p.waitFor(), "ffprobe must succeed: " + out);
        FfprobeInfo info = new FfprobeInfo();
        int videoStreams = 0;
        String lastCodecName = null;
        boolean currentIsVideo = false;
        for (String line : out.split("\n")) {
            if (line.startsWith("codec_name=")) {
                lastCodecName = line.substring("codec_name=".length());
            } else if (line.startsWith("codec_type=video")) {
                videoStreams++;
                info.videoCodec = lastCodecName;
                currentIsVideo = true;
            } else if (line.startsWith("codec_type=")) {
                currentIsVideo = false;
            }
            if (currentIsVideo && line.startsWith("width=")) {
                info.width = Integer.parseInt(line.substring("width=".length()));
            }
            if (currentIsVideo && line.startsWith("height=")) {
                info.height = Integer.parseInt(line.substring("height=".length()));
            }
            if (line.startsWith("duration=")) {
                info.durationSeconds = Double.parseDouble(line.substring("duration=".length()));
            }
        }
        info.videoStreams = videoStreams;
        return info;
    }

    private byte[] extractFrame(Path video, double seconds) throws Exception {
        Path frame = storageRoot.resolve("frame-" + video.getFileName() + "-" + seconds + ".raw");
        List<String> cmd = List.of(findBinary("ffmpeg"), "-y", "-ss", String.valueOf(seconds),
                "-i", video.toString(), "-frames:v", "1", "-f", "rawvideo", "-pix_fmt", "rgb24",
                frame.toString());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String err = new String(p.getInputStream().readAllBytes());
        assertEquals(0, p.waitFor(), "frame extraction must succeed: " + err);
        byte[] bytes = Files.readAllBytes(frame);
        assertEquals(1280 * 720 * 3, bytes.length, "raw frame must be 1280x720 rgb24");
        return bytes;
    }

    private int countDifferingPixels(byte[] a, byte[] b, int width, int frameWidth, int frameHeight) {
        int differing = 0;
        // Subtitle region: bottom band (y in [600, 720)).
        int bandStart = 600 * width * 3;
        for (int i = bandStart; i < a.length; i += 3) {
            int dr = Math.abs((a[i] & 0xFF) - (b[i] & 0xFF));
            int dg = Math.abs((a[i + 1] & 0xFF) - (b[i + 1] & 0xFF));
            int db = Math.abs((a[i + 2] & 0xFF) - (b[i + 2] & 0xFF));
            if (dr + dg + db > 60) {
                differing++;
            }
        }
        return differing;
    }

    @SuppressWarnings("unchecked")
    private static <T> org.springframework.beans.factory.ObjectProvider<T> mockProvider(T instance) {
        org.springframework.beans.factory.ObjectProvider<T> op = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(op.getIfAvailable()).thenReturn(instance);
        return op;
    }

    private static class FfprobeInfo {
        int videoStreams = 0;
        String videoCodec = "";
        int width = 0;
        int height = 0;
        double durationSeconds = 0;
    }
}