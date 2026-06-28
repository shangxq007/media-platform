package com.example.platform.render.app.input;

import com.example.platform.render.app.output.RenderOutputRegistrationService;
import com.example.platform.render.app.output.RenderProductProvenance;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.app.timeline.InternalTimelineWriter;
import com.example.platform.render.app.timeline.TimelineRenderJobMapper;
import com.example.platform.render.domain.product.*;
import com.example.platform.render.domain.storage.*;
import com.example.platform.render.domain.timeline.*;
import com.example.platform.render.infrastructure.product.ProductDependencyRepository;
import com.example.platform.render.infrastructure.product.ProductRepository;
import com.example.platform.render.infrastructure.storage.StorageReferenceRepository;
import com.example.platform.render.testsupport.R2FixtureGenerator;
import com.example.platform.shared.Ids;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Backend R4 — Render Input Materialization Smoke Test.
 *
 * <p>Proves the full chain: input media file → StorageRuntime registration
 * → Product registration → StorageRuntime materialization → FFmpeg/libass
 * baseline render → output Product → READY → queryable with input provenance.</p>
 *
 * <p>This test migrates from direct local input files to the platform-standard
 * input materialization path: input Product/StorageReference →
 * StorageRuntimeService.materialize() → local render input path.</p>
 *
 * <p>If FFmpeg is not available on PATH, the test is explicitly skipped.</p>
 *
 * <p>Architecture compliance:
 * <ul>
 *   <li>Input media enters render as Product/StorageReference</li>
 *   <li>StorageRuntime owns physical input materialization</li>
 *   <li>FFmpeg/libass receives local path only after StorageRuntime materialization</li>
 *   <li>ProductRuntime owns input/output Product lifecycle</li>
 *   <li>Render code does not resolve StorageReference paths directly</li>
 *   <li>No signed URLs persisted</li>
 *   <li>No absolute local paths exposed in public output Product metadata</li>
 *   <li>Output registered through RenderOutputRegistrationService</li>
 *   <li>FFmpeg/libass remains baseline subtitle burn-in</li>
 *   <li>Remotion/OpenCue not required</li>
 *   <li>MinIO/S3 not required</li>
 * </ul>
 */
class RenderInputMaterializationSmokeTest {

    @TempDir
    Path tempDir;

    private StorageRuntimeService storageRuntime;
    private ProductRuntimeService productRuntime;
    private RenderOutputRegistrationService registrationService;
    private RenderInputMaterializationService materializationService;
    private TimelineRenderJobMapper mapper;

    private Path mediaDir;
    private Path subtitleDir;
    private Path storageRoot;

    @BeforeEach
    void setUp() {
        mediaDir = tempDir.resolve("media");
        subtitleDir = tempDir.resolve("subtitles");
        storageRoot = tempDir.resolve("storage");

        StorageReferenceRepository storageRepo = new InMemoryStorageReferenceRepository();
        ProductRepository productRepo = new InMemoryProductRepository();
        ProductDependencyRepository depRepo = new InMemoryProductDependencyRepository();
        storageRuntime = new StorageRuntimeService(storageRepo);
        productRuntime = new ProductRuntimeService(productRepo, depRepo);
        registrationService = new RenderOutputRegistrationService(storageRuntime, productRuntime, storageRoot);
        materializationService = new RenderInputMaterializationService(storageRuntime, productRuntime);

        TimelineExtensionsReader extensionsReader = new TimelineExtensionsReader();
        TimelineScriptParser parser = new TimelineScriptParser(extensionsReader);
        InternalTimelineWriter writer = new InternalTimelineWriter(extensionsReader);
        mapper = new TimelineRenderJobMapper(parser, writer);
    }

    // ─── R4: Input materialization smoke tests ───

    @Test
    @DisplayName("R4: input media registered as RAW_MEDIA Product, materialized, rendered, output READY")
    void timelineRenderUsesMaterializedInputProductAndRegistersReadyOutput() throws Exception {
        R2FixtureGenerator.assumeFfmpegAvailable();

        // 1. Generate test media file
        Path inputVideo = R2FixtureGenerator.generateTestVideo(mediaDir, 2.0, 320, 180, 30);
        assertTrue(Files.exists(inputVideo), "Test video must exist");
        assertTrue(Files.size(inputVideo) > 0, "Test video must be non-empty");

        // 2. Copy video to storage root for registration
        Path storageInput = storageRoot.resolve("inputs/test-input.mp4");
        Files.createDirectories(storageInput.getParent());
        Files.copy(inputVideo, storageInput, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        String inputChecksum = computeSha256(storageInput);
        StorageReference inputStorageRef = new StorageReference(
                null,
                StorageProviderType.LOCAL.name(),
                StorageClass.STANDARD,
                storageRoot.toString(),
                "inputs/test-input.mp4",
                inputChecksum,
                inputChecksum,
                Files.size(storageInput),
                "video/mp4",
                Instant.now(),
                Instant.now());
        StorageReference registeredInputRef = storageRuntime.register(inputStorageRef);
        assertNotNull(registeredInputRef.storageReferenceId());
        assertTrue(storageRuntime.verifyChecksum(registeredInputRef.storageReferenceId()));

        // 3. Register input as RAW_MEDIA Product (must be REGISTERED first)
        String inputProductId = Ids.newId("prod-in");
        Product inputProduct = new Product(
                inputProductId, "ten_r4_001", "prj_r4_001", null,
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "upload", "upload-service", null,
                ProductStatus.REGISTERED, registeredInputRef.storageReferenceId(),
                inputChecksum, inputChecksum, "video/mp4", 1,
                "{\"source\":\"test-fixture\"}", Instant.now(), Instant.now());
        Product registeredInput = productRuntime.register(inputProduct);
        Product readyInput = productRuntime.markReady(registeredInput.productId());
        assertEquals(ProductStatus.READY, readyInput.status());
        assertEquals(ProductType.RAW_MEDIA, readyInput.productType());

        // 4. Materialize input Product through StorageRuntime
        RenderInputMaterialization materialization = materializationService.materialize(
                readyInput.productId(), "ast_r4_001", "clip_r4_001");
        assertTrue(materialization.valid(), "Materialization must succeed: " + materialization.failureReason());
        assertNotNull(materialization.materializedPath());
        assertTrue(Files.exists(materialization.materializedPath()), "Materialized file must exist");
        assertTrue(Files.size(materialization.materializedPath()) > 0, "Materialized file must be non-empty");
        assertEquals(readyInput.productId(), materialization.inputProductId());
        assertEquals(registeredInputRef.storageReferenceId(), materialization.storageReferenceId());

        // 5. Create timeline referencing the input asset
        TimelineAssetRef assetRef = new TimelineAssetRef(
                "ast_r4_001", "asset://ast_r4_001", "mp4", 2, 320, 180, Map.of());
        TimelineClip clip = TimelineClip.of("clip_r4_001", assetRef, 0.0, 0.0, 2.0);
        TimelineTrack videoTrack = new TimelineTrack(
                "trk_r4_v1", "Video 1", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);

        TimelineOutputSpec outputSpec = new TimelineOutputSpec(
                "mp4", "320x180", 30, "h264", 1000,
                TimelineAudioSpec.aacDefault(), "yuv420p");

        TimelineSpec spec = new TimelineSpec(
                "tl_r4_001", "R4 Materialized Input Timeline", "R4 smoke",
                List.of(videoTrack), List.of(), outputSpec, 2.0,
                Map.of("tenantId", "ten_r4_001", "projectId", "prj_r4_001"));

        // 6. Map to RenderJob request
        var mappingResult = mapper.toRenderJobRequest(
                "ten_r4_001", "prj_r4_001", spec, "default_720p", "rev_r4_001", "snap_r4_001");
        assertNotNull(mappingResult.request());

        // 7. Generate subtitle fixture
        Path subtitleFile = R2FixtureGenerator.generateTestSubtitle(subtitleDir);

        // 8. Invoke real FFmpeg render using materialized input path
        // Output must be within storage root for RenderOutputRegistrationService
        Path renderedOutput = storageRoot.resolve("outputs/rendered-r4.mp4");
        invokeRealFfmpegRender(materialization.materializedPath(), subtitleFile, renderedOutput, 320, 180);

        assertTrue(Files.exists(renderedOutput), "Rendered output must exist");
        assertTrue(Files.size(renderedOutput) > 0, "Rendered output must be non-empty");

        // 9. Build provenance with input Product reference
        RenderProductProvenance provenance = mappingResult.toProvenanceBuilder()
                .renderJobId("rj_r4_001")
                .baselineRenderer("ffmpeg-libass")
                .renderMode("baseline-subtitle-burn-in")
                .inputProductIds(materialization.inputProductIdList())
                .build();

        // 10. Register output through RenderOutputRegistrationService
        String outputRelativePath = storageRoot.relativize(renderedOutput).toString();
        Product outputProduct = registrationService.registerOutput(
                "rj_r4_001", "ten_r4_001", "prj_r4_001", "ffmpeg", outputRelativePath, provenance);

        // 11. Verify output Product
        assertNotNull(outputProduct);
        assertEquals(ProductType.FINAL_RENDER, outputProduct.productType());
        assertEquals(RepresentationKind.MEDIA_FILE, outputProduct.representationKind());
        assertEquals(ProductStatus.READY, outputProduct.status());
        assertNotNull(outputProduct.storageReferenceId());
        assertNotNull(outputProduct.checksum());
        assertEquals("video/mp4", outputProduct.mimeType());
        assertEquals("ten_r4_001", outputProduct.tenantId());
        assertEquals("prj_r4_001", outputProduct.projectId());
        assertEquals("rev_r4_001", outputProduct.sourceTimelineRevisionId());

        // 12. Verify output Product is queryable
        Optional<Product> found = productRuntime.find(outputProduct.productId());
        assertTrue(found.isPresent());
        assertEquals(ProductStatus.READY, found.get().status());

        List<Product> byProject = productRuntime.findByProject("prj_r4_001", 50);
        assertTrue(byProject.stream().anyMatch(p -> p.productId().equals(outputProduct.productId())));

        // 13. Verify storage reference
        assertTrue(storageRuntime.exists(outputProduct.storageReferenceId()));
        assertTrue(storageRuntime.verifyChecksum(outputProduct.storageReferenceId()));

        // 14. Verify full provenance metadata including input references
        String metadata = outputProduct.metadataJson();
        assertNotNull(metadata);

        // Timeline provenance
        assertTrue(metadata.contains("\"timelineId\":\"tl_r4_001\""), "Metadata must contain timelineId");
        assertTrue(metadata.contains("\"timelineRevisionId\":\"rev_r4_001\""), "Metadata must contain timelineRevisionId");
        assertTrue(metadata.contains("\"snapshotId\":\"snap_r4_001\""), "Metadata must contain snapshotId");

        // Render provenance
        assertTrue(metadata.contains("\"renderJobId\":\"rj_r4_001\""), "Metadata must contain renderJobId");
        assertTrue(metadata.contains("\"baselineRenderer\":\"ffmpeg-libass\""), "Metadata must contain baselineRenderer");
        assertTrue(metadata.contains("\"renderMode\":\"baseline-subtitle-burn-in\""), "Metadata must contain renderMode");

        // Input Product reference
        assertTrue(metadata.contains("\"inputProductIds\":[\"" + readyInput.productId() + "\"]"),
                "Metadata must contain inputProductIds referencing the input RAW_MEDIA Product");

        // Source asset
        assertTrue(metadata.contains("\"sourceAssetIds\":[\"ast_r4_001\"]"), "Metadata must contain sourceAssetIds");

        // Dimensions
        assertTrue(metadata.contains("\"width\":320"), "Metadata must contain width");
        assertTrue(metadata.contains("\"height\":180"), "Metadata must contain height");

        // Negative checks — no sensitive data
        assertFalse(metadata.contains("signedUrl"), "No signed URL in metadata");
        assertFalse(metadata.contains("signed-url"), "No signed URL in metadata");
        assertFalse(metadata.contains("presign"), "No presign in metadata");
        assertFalse(metadata.contains(storageRoot.toString()), "No absolute storage path in metadata");
        assertFalse(metadata.contains(materialization.materializedPath().toString()),
                "No absolute materialized input path in metadata");
    }

    @Test
    @DisplayName("R4: input Product missing fails materialization")
    void inputProductMissingFailsMaterialization() {
        RenderInputMaterialization result = materializationService.materialize(
                "nonexistent-product", null, null);
        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("not found"));
    }

    @Test
    @DisplayName("R4: input Product not READY fails materialization")
    void inputProductNotReadyFailsMaterialization() {
        // Register a Product but don't mark it READY
        Product product = new Product(
                Ids.newId("prod"), "ten_1", "prj_1", null,
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "upload", "upload-service", null,
                ProductStatus.REGISTERED, "stor-1",
                null, null, "video/mp4", 1,
                "{}", Instant.now(), Instant.now());
        Product registered = productRuntime.register(product);
        assertEquals(ProductStatus.REGISTERED, registered.status());

        RenderInputMaterialization result = materializationService.materialize(
                registered.productId(), null, null);
        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("not READY"));
    }

    @Test
    @DisplayName("R4: input Product with no storageReferenceId fails materialization")
    void inputProductNoStorageReferenceIdFailsMaterialization() {
        Product product = new Product(
                Ids.newId("prod"), "ten_1", "prj_1", null,
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "upload", "upload-service", null,
                ProductStatus.REGISTERED, null, // no storageReferenceId
                null, null, "video/mp4", 1,
                "{}", Instant.now(), Instant.now());
        Product registered = productRuntime.register(product);
        productRuntime.markReady(registered.productId());

        RenderInputMaterialization result = materializationService.materialize(
                registered.productId(), null, null);
        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("no storageReferenceId"));
    }

    @Test
    @DisplayName("R4: input Product not MEDIA_FILE fails materialization")
    void inputProductNotMediaFileFailsMaterialization() {
        Product product = new Product(
                Ids.newId("prod"), "ten_1", "prj_1", null,
                ProductType.TRANSCRIPT, RepresentationKind.JSON_DOCUMENT,
                "asr", "whisper", null,
                ProductStatus.REGISTERED, "stor-1",
                null, null, "application/json", 1,
                "{}", Instant.now(), Instant.now());
        Product registered = productRuntime.register(product);
        productRuntime.markReady(registered.productId());

        RenderInputMaterialization result = materializationService.materialize(
                registered.productId(), null, null);
        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("not MEDIA_FILE"));
    }

    @Test
    @DisplayName("R4: missing StorageReference fails materialization")
    void missingStorageReferenceFailsMaterialization() {
        // Register a Product referencing a non-existent StorageReference
        Product product = new Product(
                Ids.newId("prod"), "ten_1", "prj_1", null,
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "upload", "upload-service", null,
                ProductStatus.REGISTERED, "stor-nonexistent",
                null, null, "video/mp4", 1,
                "{}", Instant.now(), Instant.now());
        Product registered = productRuntime.register(product);
        productRuntime.markReady(registered.productId());

        RenderInputMaterialization result = materializationService.materialize(
                registered.productId(), null, null);
        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("not found") || result.failureReason().contains("not materialized"));
    }

    @Test
    @DisplayName("R4: zero-byte materialized file fails validation")
    void zeroByteMaterializedFileFailsValidation() throws Exception {
        // Create a zero-byte file in storage
        Path emptyFile = storageRoot.resolve("inputs/empty.mp4");
        Files.createDirectories(emptyFile.getParent());
        Files.writeString(emptyFile, "");

        StorageReference ref = new StorageReference(
                null, StorageProviderType.LOCAL.name(), StorageClass.STANDARD,
                storageRoot.toString(), "inputs/empty.mp4",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                0, "video/mp4", Instant.now(), Instant.now());
        StorageReference registered = storageRuntime.register(ref);

        Product product = new Product(
                Ids.newId("prod"), "ten_1", "prj_1", null,
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "upload", "upload-service", null,
                ProductStatus.REGISTERED, registered.storageReferenceId(),
                null, null, "video/mp4", 1,
                "{}", Instant.now(), Instant.now());
        Product registeredProduct = productRuntime.register(product);
        productRuntime.markReady(registeredProduct.productId());

        RenderInputMaterialization result = materializationService.materialize(
                registeredProduct.productId(), null, null);
        assertFalse(result.valid());
        assertTrue(result.failureReason().contains("zero-byte"));
    }

    @Test
    @DisplayName("R4: no signed URL is persisted in output Product")
    void noSignedUrlIsPersistedInOutput() throws Exception {
        R2FixtureGenerator.assumeFfmpegAvailable();

        // Generate and register input
        Path inputVideo = R2FixtureGenerator.generateTestVideo(mediaDir, 1.0, 320, 180, 24);
        Path storageInput = storageRoot.resolve("inputs/nosign-input.mp4");
        Files.createDirectories(storageInput.getParent());
        Files.copy(inputVideo, storageInput, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        String checksum = computeSha256(storageInput);
        StorageReference ref = storageRuntime.register(new StorageReference(
                null, StorageProviderType.LOCAL.name(), StorageClass.STANDARD,
                storageRoot.toString(), "inputs/nosign-input.mp4",
                checksum, checksum, Files.size(storageInput), "video/mp4",
                Instant.now(), Instant.now()));

        Product inputProduct = productRuntime.register(new Product(
                Ids.newId("prod"), "ten_1", "prj_1", null,
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "upload", "upload", null,
                ProductStatus.REGISTERED, ref.storageReferenceId(),
                checksum, checksum, "video/mp4", 1, "{}",
                Instant.now(), Instant.now()));
        productRuntime.markReady(inputProduct.productId());

        // Materialize and render
        RenderInputMaterialization mat = materializationService.materialize(
                inputProduct.productId(), null, null);
        assertTrue(mat.valid());

        // Output must be within storage root
        Path renderedOutput = storageRoot.resolve("outputs/rendered-nosign.mp4");
        invokeRealFfmpegRender(mat.materializedPath(), null, renderedOutput, 320, 180);

        // Register output
        String relPath = storageRoot.relativize(renderedOutput).toString();
        Product output = registrationService.registerOutput(
                "job-nosign", "ten_1", "prj_1", "ffmpeg", relPath);

        String metadata = output.metadataJson();
        assertFalse(metadata.contains("signedUrl"), "No signed URL in metadata");
        assertFalse(metadata.contains("signed-url"), "No signed URL in metadata");
        assertFalse(metadata.contains("presign"), "No presign in metadata");
    }

    @Test
    @DisplayName("R4: no absolute local path exposed in output metadata")
    void noAbsoluteLocalPathExposedInOutputMetadata() throws Exception {
        R2FixtureGenerator.assumeFfmpegAvailable();

        Path inputVideo = R2FixtureGenerator.generateTestVideo(mediaDir, 1.0, 320, 180, 24);
        Path storageInput = storageRoot.resolve("inputs/nopath-input.mp4");
        Files.createDirectories(storageInput.getParent());
        Files.copy(inputVideo, storageInput, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        String checksum = computeSha256(storageInput);
        StorageReference ref = storageRuntime.register(new StorageReference(
                null, StorageProviderType.LOCAL.name(), StorageClass.STANDARD,
                storageRoot.toString(), "inputs/nopath-input.mp4",
                checksum, checksum, Files.size(storageInput), "video/mp4",
                Instant.now(), Instant.now()));

        Product inputProduct = productRuntime.register(new Product(
                Ids.newId("prod"), "ten_1", "prj_1", null,
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "upload", "upload", null,
                ProductStatus.REGISTERED, ref.storageReferenceId(),
                checksum, checksum, "video/mp4", 1, "{}",
                Instant.now(), Instant.now()));
        productRuntime.markReady(inputProduct.productId());

        RenderInputMaterialization mat = materializationService.materialize(
                inputProduct.productId(), null, null);
        assertTrue(mat.valid());

        // Output must be within storage root
        Path renderedOutput = storageRoot.resolve("outputs/rendered-nopath.mp4");
        invokeRealFfmpegRender(mat.materializedPath(), null, renderedOutput, 320, 180);

        String relPath = storageRoot.relativize(renderedOutput).toString();
        Product output = registrationService.registerOutput(
                "job-nopath", "ten_1", "prj_1", "ffmpeg", relPath);

        String metadata = output.metadataJson();
        assertFalse(metadata.contains(storageRoot.toString()),
                "Metadata must not expose absolute storage root path");
        assertFalse(metadata.contains(mat.materializedPath().toString()),
                "Metadata must not expose absolute materialized input path");
    }

    @Test
    @DisplayName("R4: MinIO/S3 not required — LOCAL storage only")
    void minioS3NotRequired() throws Exception {
        R2FixtureGenerator.assumeFfmpegAvailable();

        Path inputVideo = R2FixtureGenerator.generateTestVideo(mediaDir, 1.0, 320, 180, 24);
        Path storageInput = storageRoot.resolve("inputs/local-input.mp4");
        Files.createDirectories(storageInput.getParent());
        Files.copy(inputVideo, storageInput, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        String checksum = computeSha256(storageInput);
        StorageReference ref = storageRuntime.register(new StorageReference(
                null, StorageProviderType.LOCAL.name(), StorageClass.STANDARD,
                storageRoot.toString(), "inputs/local-input.mp4",
                checksum, checksum, Files.size(storageInput), "video/mp4",
                Instant.now(), Instant.now()));

        Product inputProduct = productRuntime.register(new Product(
                Ids.newId("prod"), "ten_1", "prj_1", null,
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "upload", "upload", null,
                ProductStatus.REGISTERED, ref.storageReferenceId(),
                checksum, checksum, "video/mp4", 1, "{}",
                Instant.now(), Instant.now()));
        productRuntime.markReady(inputProduct.productId());

        RenderInputMaterialization mat = materializationService.materialize(
                inputProduct.productId(), null, null);
        assertTrue(mat.valid());

        // Verify LOCAL provider
        Optional<StorageReference> storedRef = storageRuntime.find(ref.storageReferenceId());
        assertTrue(storedRef.isPresent());
        assertEquals(StorageProviderType.LOCAL.name(), storedRef.get().providerType());
    }

    // ─── R5: Product dependency edge tests ───

    @Test
    @DisplayName("R5: materialized input render creates Product dependency edge")
    void materializedInputRenderCreatesProductDependencyEdge() throws Exception {
        R2FixtureGenerator.assumeFfmpegAvailable();

        // 1. Generate test media file
        Path inputVideo = R2FixtureGenerator.generateTestVideo(mediaDir, 2.0, 320, 180, 30);
        assertTrue(Files.exists(inputVideo), "Test video must exist");

        // 2. Copy video to storage root for registration
        Path storageInput = storageRoot.resolve("inputs/r5-input.mp4");
        Files.createDirectories(storageInput.getParent());
        Files.copy(inputVideo, storageInput, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        String inputChecksum = computeSha256(storageInput);
        StorageReference inputStorageRef = new StorageReference(
                null, StorageProviderType.LOCAL.name(), StorageClass.STANDARD,
                storageRoot.toString(), "inputs/r5-input.mp4",
                inputChecksum, inputChecksum, Files.size(storageInput),
                "video/mp4", Instant.now(), Instant.now());
        StorageReference registeredInputRef = storageRuntime.register(inputStorageRef);

        // 3. Register input as RAW_MEDIA Product
        String inputProductId = Ids.newId("prod-r5-in");
        Product inputProduct = new Product(
                inputProductId, "ten_r5_001", "prj_r5_001", null,
                ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                "upload", "upload-service", null,
                ProductStatus.REGISTERED, registeredInputRef.storageReferenceId(),
                inputChecksum, inputChecksum, "video/mp4", 1,
                "{\"source\":\"test-fixture\"}", Instant.now(), Instant.now());
        Product registeredInput = productRuntime.register(inputProduct);
        Product readyInput = productRuntime.markReady(registeredInput.productId());
        assertEquals(ProductStatus.READY, readyInput.status());

        // 4. Materialize input Product
        RenderInputMaterialization materialization = materializationService.materialize(
                readyInput.productId(), "ast_r5_001", "clip_r5_001");
        assertTrue(materialization.valid(), "Materialization must succeed: " + materialization.failureReason());

        // 5. Create timeline
        TimelineAssetRef assetRef = new TimelineAssetRef(
                "ast_r5_001", "asset://ast_r5_001", "mp4", 2, 320, 180, Map.of());
        TimelineClip clip = TimelineClip.of("clip_r5_001", assetRef, 0.0, 0.0, 2.0);
        TimelineTrack videoTrack = new TimelineTrack(
                "trk_r5_v1", "Video 1", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
        TimelineOutputSpec outputSpec = new TimelineOutputSpec(
                "mp4", "320x180", 30, "h264", 1000,
                TimelineAudioSpec.aacDefault(), "yuv420p");
        TimelineSpec spec = new TimelineSpec(
                "tl_r5_001", "R5 Dependency Edge Timeline", "R5 smoke",
                List.of(videoTrack), List.of(), outputSpec, 2.0,
                Map.of("tenantId", "ten_r5_001", "projectId", "prj_r5_001"));

        // 6. Map to RenderJob request
        var mappingResult = mapper.toRenderJobRequest(
                "ten_r5_001", "prj_r5_001", spec, "default_720p", "rev_r5_001", "snap_r5_001");

        // 7. Generate subtitle fixture
        Path subtitleFile = R2FixtureGenerator.generateTestSubtitle(subtitleDir);

        // 8. Invoke real FFmpeg render
        Path renderedOutput = storageRoot.resolve("outputs/rendered-r5.mp4");
        invokeRealFfmpegRender(materialization.materializedPath(), subtitleFile, renderedOutput, 320, 180);

        // 9. Build provenance with input Product reference
        RenderProductProvenance provenance = mappingResult.toProvenanceBuilder()
                .renderJobId("rj_r5_001")
                .baselineRenderer("ffmpeg-libass")
                .renderMode("baseline-subtitle-burn-in")
                .inputProductIds(materialization.inputProductIdList())
                .build();

        // 10. Register output through RenderOutputRegistrationService
        String outputRelativePath = storageRoot.relativize(renderedOutput).toString();
        Product outputProduct = registrationService.registerOutput(
                "rj_r5_001", "ten_r5_001", "prj_r5_001", "ffmpeg", outputRelativePath, provenance);

        // 11. Verify output Product is READY
        assertEquals(ProductStatus.READY, outputProduct.status());
        assertEquals(ProductType.FINAL_RENDER, outputProduct.productType());

        // 12. Verify formal Product dependency edge exists
        List<ProductDependency> deps = productRuntime.findDependencies(outputProduct.productId());
        assertFalse(deps.isEmpty(), "Output Product must have dependency edges");
        assertEquals(1, deps.size(), "Output must have exactly 1 dependency edge");

        ProductDependency dep = deps.get(0);
        assertEquals(outputProduct.productId(), dep.productId(), "Dependency source must be output Product");
        assertEquals(readyInput.productId(), dep.dependsOnProductId(), "Dependency target must be input Product");
        assertEquals(DependencyType.DERIVED_FROM, dep.dependencyType(), "Dependency type must be DERIVED_FROM");
        assertEquals("ten_r5_001", dep.tenantId());
        assertEquals("prj_r5_001", dep.projectId());

        // 13. Verify upstream query works
        List<String> upstream = productRuntime.findUpstream(outputProduct.productId());
        assertEquals(1, upstream.size());
        assertEquals(readyInput.productId(), upstream.get(0));

        // 14. Verify downstream query works
        List<String> downstream = productRuntime.findDownstream(readyInput.productId());
        assertEquals(1, downstream.size());
        assertEquals(outputProduct.productId(), downstream.get(0));

        // 15. Verify metadata still contains inputProductIds
        String metadata = outputProduct.metadataJson();
        assertTrue(metadata.contains("\"inputProductIds\":[\"" + readyInput.productId() + "\"]"),
                "Metadata must contain inputProductIds");

        // 16. Verify no signed URLs or local paths
        assertFalse(metadata.contains("signedUrl"), "No signed URL in metadata");
        assertFalse(metadata.contains(storageRoot.toString()), "No absolute storage path in metadata");
    }

    // ─── Helper: invoke real FFmpeg ───

    private void invokeRealFfmpegRender(Path inputVideo, Path subtitleFile,
                                          Path outputVideo, int width, int height) throws IOException {
        Files.createDirectories(outputVideo.getParent());

        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(inputVideo.toAbsolutePath().toString());

        if (subtitleFile != null && Files.exists(subtitleFile)) {
            String subPath = subtitleFile.toAbsolutePath().toString();
            cmd.add("-vf");
            cmd.add("subtitles=" + escapeFfmpegPath(subPath) + ":force_style='FontSize=24,PrimaryColour=&HFFFFFF',format=yuv420p");
        }

        cmd.add("-c:v");
        cmd.add("libx264");
        cmd.add("-preset");
        cmd.add("ultrafast");
        cmd.add("-c:a");
        cmd.add("aac");
        cmd.add("-b:a");
        cmd.add("64k");
        cmd.add(outputVideo.toAbsolutePath().toString());

        R2FixtureGenerator.ProcessResult result = R2FixtureGenerator.executeCommand(cmd);
        if (!result.success()) {
            throw new IOException("FFmpeg render failed: " + result.stderr());
        }

        if (!Files.exists(outputVideo) || Files.size(outputVideo) == 0) {
            throw new IOException("FFmpeg render produced no output or zero-byte output: " + outputVideo);
        }
    }

    private static String escapeFfmpegPath(String path) {
        return path.replace("'", "'\\''").replace(":", "\\:");
    }

    private String computeSha256(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // ─── In-memory test doubles ───

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
            return List.of();
        }

        @Override
        public Optional<Product> findLatest(String assetId, ProductType type) {
            return Optional.empty();
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
            // Returns what this product depends ON (upstream)
            return store.values().stream()
                    .filter(d -> d.productId().equals(productId))
                    .toList();
        }

        @Override
        public List<ProductDependency> findDependents(String productId) {
            // Returns what depends ON this product (downstream)
            return store.values().stream()
                    .filter(d -> d.dependsOnProductId().equals(productId))
                    .toList();
        }

        @Override
        public boolean exists(String productId, String dependsOnId) {
            return false;
        }

        @Override
        public void delete(String dependencyId) {
            store.remove(dependencyId);
        }
    }
}
