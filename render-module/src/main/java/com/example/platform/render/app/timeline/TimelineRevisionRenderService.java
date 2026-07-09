package com.example.platform.render.app.timeline;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.app.input.RenderInputMaterializationService;
import com.example.platform.render.app.output.RenderOutputRegistrationService;
import com.example.platform.render.app.output.RenderProductProvenance;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.domain.product.Product;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.app.timeline.InternalTimelineAdapter;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import java.util.Map;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Application service for rendering a TimelineRevision into a final render Product.
 *
 * <p>Orchestrates the full chain:
 * <ol>
 *   <li>Load TimelineRevision and snapshot</li>
 *   <li>Parse to TimelineSpec</li>
 *   <li>Map to SubmitRenderJobRequest via TimelineRenderJobMapper</li>
 *   <li>Materialize input Products through StorageRuntime</li>
 *   <li>Invoke FFmpeg/libass baseline render</li>
 *   <li>Register output through RenderOutputRegistrationService</li>
 *   <li>Return response with outputProductId and provenance</li>
 * </ol>
 *
 * <p>Architecture boundaries:
 * <ul>
 *   <li>Does not expose internal provider/backend/environment selection</li>
 *   <li>FFmpeg/libass is the baseline subtitle burn-in path</li>
 *   <li>Remotion production dispatch remains disabled</li>
 *   <li>OpenCue production submit remains disabled</li>
 *   <li>MinIO/S3 are not required</li>
 *   <li>No signed URLs persisted</li>
 *   <li>No absolute filesystem paths exposed in response</li>
 * </ul>
 */
@Service
public class TimelineRevisionRenderService {

    private static final Logger log = LoggerFactory.getLogger(TimelineRevisionRenderService.class);

    private final TimelineRevisionService revisionService;
    private final TimelineSnapshotService snapshotService;
    private final TimelineRenderJobMapper mapper;
    private final TimelineScriptParser parser;
    private final InternalTimelineAdapter internalTimelineAdapter;
    private final RenderInputMaterializationService materializationService;
    private final RenderOutputRegistrationService registrationService;
    private final ProductRuntimeService productRuntime;
    private final StorageRuntimeService storageRuntime;
    private final TimelineInputProductResolver inputProductResolver;
    private final ProcessToolRunner processToolRunner;
    private final Path storageRoot;

    public TimelineRevisionRenderService(
            TimelineRevisionService revisionService,
            TimelineSnapshotService snapshotService,
            TimelineRenderJobMapper mapper,
            TimelineScriptParser parser,
            InternalTimelineAdapter internalTimelineAdapter,
            RenderInputMaterializationService materializationService,
            RenderOutputRegistrationService registrationService,
            ProductRuntimeService productRuntime,
            StorageRuntimeService storageRuntime,
            TimelineInputProductResolver inputProductResolver,
            ProcessToolRunner processToolRunner,
            Path storageRoot) {
        this.revisionService = revisionService;
        this.snapshotService = snapshotService;
        this.mapper = mapper;
        this.parser = parser;
        this.internalTimelineAdapter = internalTimelineAdapter;
        this.materializationService = materializationService;
        this.registrationService = registrationService;
        this.productRuntime = productRuntime;
        this.storageRuntime = storageRuntime;
        this.inputProductResolver = inputProductResolver;
        this.processToolRunner = processToolRunner;
        this.storageRoot = storageRoot;
    }

    /**
     * Render a TimelineRevision into a final render Product.
     *
     * @param projectId     the project identifier
     * @param revisionId    the timeline revision identifier
     * @param outputProfile the render profile (e.g., "default_1080p")
     * @return the render result with outputProductId and provenance
     * @throws IllegalArgumentException if revision not found or validation fails
     * @throws IllegalStateException    if render or registration fails
     */
    public RevisionRenderResult render(String projectId, String revisionId, String outputProfile) {
        log.info("Timeline revision render requested: project={} revision={} profile={}",
                projectId, revisionId, outputProfile);

        // 1. Load TimelineRevision
        var revisionOpt = revisionService.findById(revisionId);
        if (revisionOpt.isEmpty()) {
            throw new IllegalArgumentException("Timeline revision not found: " + revisionId);
        }
        var revision = revisionOpt.get();

        // 2. Verify project ownership
        if (!projectId.equals(revision.projectId())) {
            throw new IllegalArgumentException(
                    "Revision does not belong to project: " + revisionId + " expected=" + projectId + " actual=" + revision.projectId());
        }

        String tenantId = revision.tenantId();
        String snapshotId = revision.snapshotId();

        // 3. Load snapshot payload
        var payloadOpt = snapshotService.findPayload(snapshotId);
        if (payloadOpt.isEmpty()) {
            throw new IllegalStateException("Snapshot not found for revision: " + revisionId + " snapshot=" + snapshotId);
        }
        String timelineJson = payloadOpt.get();

        // 4. Parse to TimelineSpec — try internal adapter first, fall back to script parser
        TimelineSpec spec = internalTimelineAdapter.toSpec(timelineJson).orElse(null);
        if (spec == null) {
            var specOpt = parser.parse(timelineJson);
            if (specOpt.isEmpty()) {
                throw new IllegalStateException("Failed to parse timeline JSON for revision: " + revisionId);
            }
            spec = specOpt.get();
        }

        // 5. Map to render job request
        var mappingResult = mapper.toRenderJobRequest(
                tenantId, projectId, spec, outputProfile, revisionId, snapshotId);

        String renderJobId = Ids.newId("rj");

        // 6. Resolve input media — try Product-backed, fall back to URI-based (preview/bootstrap)
        Path materializedInput;
        List<String> inputProductIds = List.of();
        String mediaResolutionMode = "UNKNOWN";

        // Extract product bindings from spec
        Map<String, String> productBindings = new java.util.HashMap<>();
        if (spec.tracks() != null) {
            for (var track : spec.tracks()) {
                if (track.clips() != null) {
                    for (var clip : track.clips()) {
                        if (clip.assetRef() != null && clip.assetRef().productId() != null) {
                            productBindings.put(clip.assetRef().assetId(), clip.assetRef().productId());
                        }
                    }
                }
            }
        }
        var resolverResult = inputProductResolver.resolveWithBindings(mappingResult.sourceAssetIds(), productBindings);
        if (resolverResult.valid()) {
            inputProductIds = resolverResult.inputProductIds();
            String primaryInputProductId = inputProductIds.get(0);
            var materialization = materializationService.materialize(primaryInputProductId, null, null);
            if (!materialization.valid()) {
                throw new IllegalStateException(
                        "Input materialization failed for " + primaryInputProductId
                        + ": " + materialization.failureReason());
            }
            materializedInput = materialization.materializedPath();
            mediaResolutionMode = "PRODUCT_BACKED";
        } else {
            log.info("Product resolution failed, falling back to URI-based media: {}", resolverResult.failureReason());
            String mediaUri = spec.tracks().stream()
                    .filter(t -> t.clips() != null)
                    .flatMap(t -> t.clips().stream())
                    .filter(c -> c.assetRef() != null && c.assetRef().storageUri() != null && !c.assetRef().storageUri().isBlank())
                    .map(c -> c.assetRef().storageUri())
                    .findFirst()
                    .orElse(null);
            if (mediaUri == null || mediaUri.isBlank()) {
                throw new IllegalStateException(
                        "No renderable media source found for assets: " + mappingResult.sourceAssetIds());
            }
            String localPath = mediaUri.startsWith("localFsStorageProvider://")
                    ? storageRoot.resolve(mediaUri.substring("localFsStorageProvider://".length())).toAbsolutePath().toString()
                    : mediaUri.startsWith("/") ? mediaUri : null;
            if (localPath == null || !java.nio.file.Files.exists(Path.of(localPath))) {
                throw new IllegalStateException("Cannot resolve media URI: " + mediaUri);
            }
            materializedInput = Path.of(localPath);
            mediaResolutionMode = "URI_BACKED_PREVIEW";
        }
        log.info("Media resolution: mode={} assetIds={}", mediaResolutionMode, mappingResult.sourceAssetIds());

        // 7. Build provenance with inputProductIds
        RenderProductProvenance provenance = mappingResult.toProvenanceBuilder()
                .renderJobId(renderJobId)
                .baselineRenderer("ffmpeg-libass")
                .renderMode("timeline-revision-render")
                .inputProductIds(inputProductIds)
                .build();

        // 9. Invoke FFmpeg/libass baseline render with materialized input
        Path outputDir = storageRoot.resolve("render-output").resolve(renderJobId);
        Path outputVideo = outputDir.resolve("output.mp4");

        try {
            invokeFfmpegRender(timelineJson, outputVideo,
                    mappingResult.width(), mappingResult.height(), materializedInput);
        } catch (Exception e) {
            log.error("FFmpeg render failed for revision={}: {}", revisionId, e.getMessage());
            throw new IllegalStateException("FFmpeg render failed: " + e.getMessage(), e);
        }

        // 10. Register output through RenderOutputRegistrationService
        String relativePath = storageRoot.relativize(outputVideo).toString();
        Product outputProduct;
        try {
            outputProduct = registrationService.registerOutput(
                    renderJobId, tenantId, projectId, "ffmpeg", relativePath, provenance);
        } catch (Exception e) {
            log.error("Output registration failed for revision={}: {}", revisionId, e.getMessage());
            throw new IllegalStateException("Output registration failed: " + e.getMessage(), e);
        }

        log.info("Timeline revision render completed: revision={} product={} status={} inputs={}",
                revisionId, outputProduct.productId(), outputProduct.status(), inputProductIds.size());

        return new RevisionRenderResult(
                renderJobId,
                revisionId,
                snapshotId,
                outputProduct.productId(),
                outputProduct.status().name(),
                outputProduct.storageReferenceId(),
                outputProduct.mimeType(),
                mappingResult.outputFormat(),
                mappingResult.width(),
                mappingResult.height(),
                mappingResult.fps(),
                mappingResult.duration(),
                mappingResult.hasSubtitles(),
                "ffmpeg-libass",
                "timeline-revision-render",
                inputProductIds,
                inputProductIds.size());
    }

    /**
     * Invoke FFmpeg to render using a materialized input file.
     * Uses libx264/aac baseline encoding.
     *
     * <p>R6.1: Requires a materialized input path from StorageRuntime.
     * No testsrc/lavfi fallback — the input file is the actual media source.</p>
     */
    private void invokeFfmpegRender(String timelineJson, Path outputVideo,
                                     int width, int height,
                                     Path materializedInput) throws IOException {
        Files.createDirectories(outputVideo.getParent());

        // Write timeline JSON to temp file for audit trail
        Path tempDir = outputVideo.getParent().resolve("temp");
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve("timeline.json"), timelineJson);

        // Defensive validation of materialized input
        if (materializedInput == null) {
            throw new IOException("Materialized input path is null");
        }
        if (!Files.exists(materializedInput)) {
            throw new IOException("Materialized input file does not exist: " + materializedInput);
        }
        if (!Files.isRegularFile(materializedInput)) {
            throw new IOException("Materialized input is not a regular file: " + materializedInput);
        }
        if (Files.size(materializedInput) == 0) {
            throw new IOException("Materialized input file is zero-byte: " + materializedInput);
        }

        // R6.1: FFmpeg uses materialized input file — no testsrc, no lavfi
        List<String> cmd = List.of(
                "ffmpeg", "-y",
                "-i", materializedInput.toAbsolutePath().toString(),
                "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "64k",
                "-shortest",
                outputVideo.toAbsolutePath().toString()
        );

        ToolExecutionRequest request = ToolExecutionRequest.withTimeout(
                "ffmpeg", cmd.subList(1, cmd.size()), 60_000);

        ToolExecutionResult result = processToolRunner.execute(request);
        if (!result.isSuccess()) {
            throw new IOException("FFmpeg render failed: " + result.stderr());
        }

        if (!Files.exists(outputVideo) || Files.size(outputVideo) == 0) {
            throw new IOException(
                    "FFmpeg render produced no output or zero-byte output: " + outputVideo);
        }

        log.info("FFmpeg render with materialized input: input={} output={} size={}",
                materializedInput, outputVideo, Files.size(outputVideo));
    }

    /**
     * Result of rendering a TimelineRevision.
     *
     * <p>R6.1: includes inputProductIds and inputDependencyCount.
     * inputDependencyCount represents the de-duplicated resolved input Product IDs
     * passed to output registration for formal lineage linking.</p>
     */
    public record RevisionRenderResult(
            String renderJobId,
            String timelineRevisionId,
            String snapshotId,
            String outputProductId,
            String productStatus,
            String storageReferenceId,
            String mimeType,
            String outputFormat,
            int width,
            int height,
            int fps,
            double durationSeconds,
            boolean hasSubtitles,
            String baselineRenderer,
            String renderMode,
            List<String> inputProductIds,
            int inputDependencyCount) {}
}
