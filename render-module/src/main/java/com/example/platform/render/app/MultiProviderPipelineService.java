package com.example.platform.render.app;

import com.example.platform.render.app.planner.IncrementalPipelineSupport;
import com.example.platform.render.app.planner.PipelineExecutionPlan;
import com.example.platform.render.app.planner.PipelineTask;
import com.example.platform.render.app.planner.PipelineTaskType;
import com.example.platform.render.app.timeline.SegmentPipelinePayloadBuilder;
import com.example.platform.render.app.timeline.SegmentStitchComposeService;
import com.example.platform.render.domain.timeline.FinalComposerHint;
import com.example.platform.render.domain.RenderJobStatus;
import com.example.platform.render.domain.SubtitleTrack;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.infrastructure.*;
import com.example.platform.render.infrastructure.bento4.Bento4PackagingProvider;
import com.example.platform.render.infrastructure.gpac.GPACPackagingProvider;
import com.example.platform.render.infrastructure.gpac.PackagingProvider;
import com.example.platform.render.infrastructure.gpac.PackagingRequest;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Multi-provider pipeline orchestration service.
 *
 * <p>Chains multiple render providers to produce a single artifact:</p>
 * <pre>
 * Input Timeline → [OFX Effects] → [JavaCV Transcode] → [GPAC Packaging] → Output Artifact
 * </pre>
 *
 * <p>Pipeline stages:</p>
 * <ol>
 *   <li><strong>Effect Stage</strong> (OFX): Apply filters, transitions, subtitle burn-in</li>
 *   <li><strong>Transcode Stage</strong> (JavaCV/GPU): Encode to target format</li>
 *   <li><strong>Packaging Stage</strong> (GPAC): DASH/HLS packaging if needed</li>
 * </ol>
 */
@Service
public class MultiProviderPipelineService {

    private static final Logger log = LoggerFactory.getLogger(MultiProviderPipelineService.class);

    private final RenderProviderRouter providerRouter;
    private final RenderProviderRegistry providerRegistry;
    private final ExportPolicyService exportPolicy;
    private final EffectMappingService effectMapping;
    private final SubtitleRenderService subtitleRender;
    private final TimelineExecutorService timelineExecutor;
    private final SegmentStitchComposeService segmentStitchComposeService;
    private final java.util.Map<String, PackagingProvider> packagingProviders;

    public MultiProviderPipelineService(RenderProviderRouter providerRouter,
                                         RenderProviderRegistry providerRegistry,
                                         ExportPolicyService exportPolicy,
                                         EffectMappingService effectMapping,
                                         SubtitleRenderService subtitleRender,
                                         TimelineExecutorService timelineExecutor,
                                         SegmentStitchComposeService segmentStitchComposeService,
                                         java.util.Optional<GPACPackagingProvider> gpacPackagingProvider,
                                         java.util.Optional<Bento4PackagingProvider> bento4PackagingProvider,
                                         java.util.Optional<com.example.platform.render.infrastructure.shaka.ShakaPackagingProvider> shakaPackagingProvider) {
        this.providerRouter = providerRouter;
        this.providerRegistry = providerRegistry;
        this.exportPolicy = exportPolicy;
        this.effectMapping = effectMapping;
        this.subtitleRender = subtitleRender;
        this.timelineExecutor = timelineExecutor;
        this.segmentStitchComposeService = segmentStitchComposeService;
        java.util.Map<String, PackagingProvider> providers = new java.util.LinkedHashMap<>();
        gpacPackagingProvider.ifPresent(p -> providers.put("gpac", p));
        bento4PackagingProvider.ifPresent(p -> providers.put("bento4", p));
        shakaPackagingProvider.ifPresent(p -> providers.put("shaka", p));
        this.packagingProviders = java.util.Map.copyOf(providers);
    }

    /**
     * Execute a multi-provider render pipeline.
     *
     * @param jobId        render job ID
     * @param timeline     parsed OTIO timeline
     * @param profile      render profile (e.g., "default_1080p", "gpu_h265")
     * @param tier         user tier (FREE/PRO/TEAM/ENTERPRISE/EXPERIMENTAL)
     * @param outputFormat target output format (mp4/dash/hls/webm)
     * @return pipeline execution result
     */
    public PipelineResult executePipeline(String jobId, TimelineSpec timeline, String profile,
                                           String tier, String outputFormat) {
        return executePipeline(jobId, timeline, profile, tier, outputFormat, null);
    }

    /**
     * Executes the multi-provider pipeline, optionally driven by a pre-built incremental plan.
     */
    public PipelineResult executePipeline(String jobId, TimelineSpec timeline, String profile,
                                           String tier, String outputFormat,
                                           PipelineExecutionPlan executionPlan) {
        log.info("MultiProviderPipelineService: executing pipeline job={} profile={} tier={} format={} incremental={}",
                jobId, profile, tier, outputFormat, executionPlan != null);

        long startTime = System.currentTimeMillis();
        List<PipelineStageResult> stageResults = new ArrayList<>();

        try {
            List<PipelineStage> stages = executionPlan != null
                    ? timelineExecutor.stagesFromPlan(executionPlan)
                    : planPipeline(timeline, profile, tier, outputFormat);
            log.info("MultiProviderPipelineService: planned {} stages for job={}", stages.size(), jobId);

            List<String> segmentOrder = extractSegmentOrder(executionPlan);
            Map<String, String> segmentArtifacts = new LinkedHashMap<>();
            preloadReuseSegmentArtifacts(executionPlan, segmentArtifacts);

            String currentInput = null;
            for (PipelineStage stage : stages) {
                PipelineStageResult result = executeStage(
                        jobId, stage, timeline, profile, currentInput, segmentOrder, segmentArtifacts, executionPlan);
                stageResults.add(result);

                if (!result.success()) {
                    log.error("MultiProviderPipelineService: stage '{}' failed for job={}: {}",
                            stage.name(), jobId, result.errorMessage());
                    return PipelineResult.failed(jobId, stageResults,
                            result.errorCode(), result.errorMessage());
                }

                currentInput = result.storageUri() != null ? result.storageUri() : result.outputPath();
                if (stage.name().startsWith("seg_") && result.storageUri() != null) {
                    segmentArtifacts.put(stage.name(), result.storageUri());
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("MultiProviderPipelineService: pipeline complete job={} duration={}ms stages={}",
                    jobId, duration, stageResults.size());

            return PipelineResult.success(jobId, stageResults, duration);

        } catch (PlatformException e) {
            throw e;
        } catch (Exception e) {
            log.error("MultiProviderPipelineService: pipeline failed for job={}", jobId, e);
            throw new PlatformException(
                    new ConfigurableErrorCode("RENDER-500-001", 500101,
                            Map.of("en", "Multi-provider pipeline failed", "zh", "多Provider流水线失败"),
                            "render", 500),
                    e.getMessage(),
                    Map.of("jobId", jobId, "profile", profile, "error", e.getClass().getSimpleName()),
                    "en"
            );
        }
    }

    /**
     * Plan the pipeline stages based on the timeline, profile, tier, and output format.
     */
    public List<PipelineStage> planPipeline(TimelineSpec timeline, String profile, String tier, String outputFormat) {
        TimelineExecutorService.TimelineExecutionPlan plan =
                timelineExecutor.plan(timeline, profile, tier, outputFormat);
        log.debug("TimelineExecutor planned {} stages for timeline {}", plan.stages().size(), plan.timelineId());
        return new ArrayList<>(plan.stages());
    }

    /**
     * Execute a single pipeline stage.
     */
    private PipelineStageResult executeStage(String jobId, PipelineStage stage, TimelineSpec timeline,
                                              String profile, String inputPath,
                                              List<String> segmentOrder,
                                              Map<String, String> segmentArtifacts,
                                              PipelineExecutionPlan executionPlan) {
        long stageStart = System.currentTimeMillis();

        if (IncrementalPipelineSupport.shouldReuse(stage)) {
            String uri = IncrementalPipelineSupport.reuseUri(stage);
            String effectiveUri = uri != null ? uri : IncrementalPipelineSupport.effectiveOutput(stage);
            log.info("MultiProviderPipelineService: skipping stage '{}' (incremental reuse) uri={}",
                    stage.name(), effectiveUri);
            long stageDuration = System.currentTimeMillis() - stageStart;
            return new PipelineStageResult(stage.name(), stage.providerKey(), true,
                    effectiveUri, effectiveUri, null, null, stageDuration);
        }

        if ("final_compose".equals(stage.name()) && !segmentArtifacts.isEmpty() && !segmentOrder.isEmpty()) {
            try {
                Map<String, String> ordered = SegmentPipelinePayloadBuilder.orderedSegmentArtifacts(
                        segmentOrder, segmentArtifacts);
                FinalComposerHint composer = executionPlan != null
                        ? executionPlan.finalComposer()
                        : FinalComposerHint.FFMPEG;
                SegmentStitchComposeService.StitchResult stitch =
                        segmentStitchComposeService.stitch(jobId, ordered, profile, composer);
                long stageDuration = System.currentTimeMillis() - stageStart;
                log.info("MultiProviderPipelineService: stitched {} segments for job={} backend={}",
                        stitch.segmentCount(), jobId, stitch.backend());
                return new PipelineStageResult(stage.name(), stitch.backend(), true,
                        stitch.storageUri(), stitch.storageUri(), null, null, stageDuration);
            } catch (Exception e) {
                long stageDuration = System.currentTimeMillis() - stageStart;
                return new PipelineStageResult(stage.name(), "ffmpeg", false,
                        null, null, "RENDER-500-005", e.getMessage(), stageDuration);
            }
        }

        log.info("MultiProviderPipelineService: executing stage '{}' provider='{}' job={}",
                stage.name(), stage.providerKey(), jobId);

        try {
            RenderProvider provider = providerRegistry.getProvider(stage.providerKey())
                    .orElseThrow(() -> new PlatformException(
                            new ConfigurableErrorCode("RENDER-404-001", 500404,
                                    Map.of("en", "Provider not found", "zh", "Provider不存在"),
                                    "render", 404),
                            "Provider not found: " + stage.providerKey(),
                            Map.of("jobId", jobId, "provider", stage.providerKey()),
                            "en"
                    ));

            // Build stage-specific parameters
            String stageInput = buildStageInput(jobId, stage, timeline, inputPath);
            String stageOutput = buildStageOutput(jobId, stage);
            String renderJobId = stage.name().startsWith("seg_") ? jobId + "-" + stage.name() : jobId;

            if ("effects".equals(stage.name()) || "transcode".equals(stage.name())
                    || stage.name().startsWith("seg_")
                    || "mlt_multitrack".equals(stage.name()) || "subtitles".equals(stage.name())
                    || "skia_overlay".equals(stage.name())
                    || "final_compose".equals(stage.name())) {
                if ("subtitles".equals(stage.name()) && timeline.textOverlays() != null
                        && !timeline.textOverlays().isEmpty()) {
                    subtitleRender.prepareLibassStage(jobId, timeline);
                }
                RenderProvider.RenderResult result = provider.render(renderJobId, stageInput, profile);
                long stageDuration = System.currentTimeMillis() - stageStart;
                String storageUri = result.storageUri() != null ? result.storageUri()
                        : "localFsStorageProvider://artifacts/" + renderJobId + "/output.mp4";
                return new PipelineStageResult(stage.name(), stage.providerKey(), true,
                        storageUri, stageOutput, null, null, stageDuration);
            }

            if ("packaging".equals(stage.name())) {
                return executePackagingStage(jobId, stage, inputPath, stageOutput, stageStart);
            }

            long stageDuration = System.currentTimeMillis() - stageStart;
            return new PipelineStageResult(stage.name(), stage.providerKey(), true,
                    null, stageOutput, null, null, stageDuration);

        } catch (PlatformException e) {
            long stageDuration = System.currentTimeMillis() - stageStart;
            return new PipelineStageResult(stage.name(), stage.providerKey(), false,
                    null, null, "RENDER-500-001", e.getMessage(), stageDuration);
        } catch (Exception e) {
            long stageDuration = System.currentTimeMillis() - stageStart;
            return new PipelineStageResult(stage.name(), stage.providerKey(), false,
                    null, null, "RENDER-500-001", e.getMessage(), stageDuration);
        }
    }

    private String selectEffectProvider(String tier, String profile) {
        if ("PRO".equals(tier) || "TEAM".equals(tier) || "ENTERPRISE".equals(tier) || "EXPERIMENTAL".equals(tier)) {
            if (providerRegistry.getProvider("ofx").isPresent()) {
                return "ofx";
            }
        }
        return "javacv";
    }

    private String selectTranscodeProvider(String tier, String profile, boolean isGpu, boolean isRemote) {
        if (isRemote && providerRegistry.getProvider("remote-javacv").isPresent()) {
            return "remote-javacv";
        }
        if (isGpu) {
            // Try GPU-capable provider first
            for (RenderProviderCapability cap : providerRegistry.getAllCapabilities()) {
                if (cap.requiresGpu()) {
                    RenderProviderHealthCheck health = providerRegistry.getHealthCheck(cap.providerKey());
                    if (health == null || health.healthy()) {
                        return cap.providerKey();
                    }
                }
            }
            // Fallback warning
            log.warn("MultiProviderPipelineService: no healthy GPU provider found, falling back to CPU");
        }
        return "javacv";
    }

    private String buildStageInput(String jobId, PipelineStage stage, TimelineSpec timeline, String previousOutput) {
        if (stage.name().startsWith("seg_") && stage.parameters() != null) {
            try {
                int startFrame = Integer.parseInt(stage.parameters().getOrDefault("startFrame", "0"));
                int durationFrames = Integer.parseInt(
                        stage.parameters().getOrDefault("durationFrames", "120"));
                int fps = timeline.outputSpec() != null
                        ? (int) timeline.outputSpec().frameRate() : 30;
                return SegmentPipelinePayloadBuilder.segmentRenderPayload(
                        timeline, stage.name(), startFrame, durationFrames, fps, previousOutput);
            } catch (Exception e) {
                log.warn("Failed to build segment payload for {}: {}", stage.name(), e.getMessage());
            }
        }
        if (previousOutput != null) {
            return previousOutput;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(timeline);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildStageOutput(String jobId, PipelineStage stage) {
        String artifactJob = stage.name().startsWith("seg_") ? jobId + "-" + stage.name() : jobId;
        return "/tmp/platform/artifacts/" + artifactJob + "/output.mp4";
    }

    private static List<String> extractSegmentOrder(PipelineExecutionPlan plan) {
        if (plan == null) {
            return List.of();
        }
        return plan.tasks().stream()
                .filter(t -> t.type() == PipelineTaskType.SEGMENT_RENDER)
                .map(PipelineTask::taskId)
                .toList();
    }

    private static void preloadReuseSegmentArtifacts(PipelineExecutionPlan plan,
                                                     Map<String, String> segmentArtifacts) {
        if (plan == null) {
            return;
        }
        for (PipelineTask task : plan.tasks()) {
            if (task.type() != PipelineTaskType.SEGMENT_RENDER || task.parameters() == null) {
                continue;
            }
            if ("reuse".equalsIgnoreCase(task.parameters().get("incrementalMode"))
                    || "true".equalsIgnoreCase(task.parameters().get("skipExecution"))) {
                String uri = task.parameters().get("reuseArtifactUri");
                if (uri != null && !uri.isBlank()) {
                    segmentArtifacts.put(task.taskId(), uri);
                }
            }
        }
    }

    private String selectPackagingProviderKey(String outputFormat) {
        return selectPackagingProviderKey(outputFormat, Map.of());
    }

    private String selectPackagingProviderKey(String outputFormat, Map<String, String> stageParams) {
        if (stageParams != null && stageParams.containsKey("packager")) {
            String packager = stageParams.get("packager").toLowerCase();
            if (packagingProviders.containsKey(packager)) {
                return packager;
            }
        }
        if ("dash_drm".equals(outputFormat) && packagingProviders.containsKey("bento4")) {
            return "bento4";
        }
        if (packagingProviders.containsKey("gpac")) {
            return "gpac";
        }
        return packagingProviders.keySet().stream().findFirst().orElse("gpac");
    }

    private PipelineStageResult executePackagingStage(String jobId, PipelineStage stage, String inputPath,
                                                       String stageOutput, long stageStart) {
        PackagingProvider provider = packagingProviders.get(stage.providerKey());
        if (provider == null) {
            long stageDuration = System.currentTimeMillis() - stageStart;
            return new PipelineStageResult(stage.name(), stage.providerKey(), false,
                    null, null, "RENDER-404-002",
                    "Packaging provider not available: " + stage.providerKey(), stageDuration);
        }
        String format = stage.parameters().getOrDefault("format", "dash");
        String inputUri = inputPath != null ? inputPath : buildStageOutput(jobId,
                new PipelineStage("transcode", "javacv", Map.of()));
        String outputBase = "/tmp/platform/artifacts/" + jobId + "/packaged";
        PackagingRequest request = switch (format.toLowerCase()) {
            case "hls" -> PackagingRequest.hls(inputUri, outputBase, 4);
            case "cmaf" -> new PackagingRequest(inputUri, outputBase, "cmaf", 4, List.of(), Map.of());
            default -> PackagingRequest.dash(inputUri, outputBase, 4);
        };
        var result = provider.packageMedia(request);
        long stageDuration = System.currentTimeMillis() - stageStart;
        if (result.success()) {
            return new PipelineStageResult(stage.name(), stage.providerKey(), true,
                    result.manifestUri(), stageOutput, null, null, stageDuration);
        }
        return new PipelineStageResult(stage.name(), stage.providerKey(), false,
                null, null, "RENDER-500-003", result.errorMessage(), stageDuration);
    }

    private boolean hasEffects(TimelineSpec timeline) {
        return timeline.tracks().stream()
                .anyMatch(track -> track.clips().stream()
                        .anyMatch(clip -> clip.effects() != null && !clip.effects().isEmpty()));
    }

    private boolean hasSubtitleTracks(TimelineSpec timeline) {
        return timeline.tracks().stream()
                .anyMatch(track -> "SUBTITLE".equalsIgnoreCase(track.type().name()));
    }

    /**
     * A single stage in the multi-provider pipeline.
     */
    public record PipelineStage(
            String name,
            String providerKey,
            Map<String, String> parameters
    ) {}

    /**
     * Result of executing a single pipeline stage.
     */
    public record PipelineStageResult(
            String stageName,
            String providerKey,
            boolean success,
            String storageUri,
            String outputPath,
            String errorCode,
            String errorMessage,
            long durationMs
    ) {}

    /**
     * Overall pipeline execution result.
     */
    public record PipelineResult(
            String jobId,
            boolean success,
            List<PipelineStageResult> stages,
            String errorCode,
            String errorMessage,
            long totalDurationMs,
            String artifactId,
            String storageUri
    ) {
        public static PipelineResult success(String jobId, List<PipelineStageResult> stages, long duration) {
            String artifactId = Ids.newId("art");
            String storageUri = stages.isEmpty() ? null :
                    stages.get(stages.size() - 1).storageUri();
            return new PipelineResult(jobId, true, stages, null, null, duration, artifactId, storageUri);
        }

        public static PipelineResult failed(String jobId, List<PipelineStageResult> stages,
                                             String errorCode, String errorMessage) {
            return new PipelineResult(jobId, false, stages, errorCode, errorMessage,
                    0, null, null);
        }
    }
}
