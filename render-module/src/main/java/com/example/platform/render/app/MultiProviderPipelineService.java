package com.example.platform.render.app;

import com.example.platform.render.domain.RenderJobStatus;
import com.example.platform.render.domain.SubtitleTrack;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.infrastructure.*;
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

    public MultiProviderPipelineService(RenderProviderRouter providerRouter,
                                         RenderProviderRegistry providerRegistry,
                                         ExportPolicyService exportPolicy,
                                         EffectMappingService effectMapping,
                                         SubtitleRenderService subtitleRender) {
        this.providerRouter = providerRouter;
        this.providerRegistry = providerRegistry;
        this.exportPolicy = exportPolicy;
        this.effectMapping = effectMapping;
        this.subtitleRender = subtitleRender;
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
        log.info("MultiProviderPipelineService: executing pipeline job={} profile={} tier={} format={}",
                jobId, profile, tier, outputFormat);

        long startTime = System.currentTimeMillis();
        List<PipelineStageResult> stageResults = new ArrayList<>();

        try {
            // Step 1: Determine pipeline stages based on profile, tier, and timeline
            List<PipelineStage> stages = planPipeline(timeline, profile, tier, outputFormat);
            log.info("MultiProviderPipelineService: planned {} stages for job={}", stages.size(), jobId);

            // Step 2: Execute each stage
            String currentInput = null;
            for (PipelineStage stage : stages) {
                PipelineStageResult result = executeStage(jobId, stage, timeline, profile, currentInput);
                stageResults.add(result);

                if (!result.success()) {
                    log.error("MultiProviderPipelineService: stage '{}' failed for job={}: {}",
                            stage.name(), jobId, result.errorMessage());
                    return PipelineResult.failed(jobId, stageResults,
                            result.errorCode(), result.errorMessage());
                }

                currentInput = result.outputPath();
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
        List<PipelineStage> stages = new ArrayList<>();

        boolean hasEffects = hasEffects(timeline);
        boolean hasSubtitles = hasSubtitleTracks(timeline);
        boolean isGpuProfile = profile.startsWith("gpu_");
        boolean isRemoteProfile = profile.startsWith("remote_");
        boolean needsPackaging = "dash".equals(outputFormat) || "hls".equals(outputFormat) || "cmaf".equals(outputFormat);

        // Stage 1: Effects (OFX) - if timeline has effects or subtitles
        if (hasEffects || hasSubtitles) {
            String effectProvider = selectEffectProvider(tier, profile);
            stages.add(new PipelineStage("effects", effectProvider,
                    Map.of("subtitleBurnIn", String.valueOf(hasSubtitles))));
        }

        // Stage 2: Transcode (JavaCV or GPU)
        String transcodeProvider = selectTranscodeProvider(tier, profile, isGpuProfile, isRemoteProfile);
        stages.add(new PipelineStage("transcode", transcodeProvider, Map.of()));

        // Stage 3: Packaging (GPAC) - if DASH/HLS output needed
        if (needsPackaging) {
            stages.add(new PipelineStage("packaging", "gpac",
                    Map.of("format", outputFormat)));
        }

        return stages;
    }

    /**
     * Execute a single pipeline stage.
     */
    private PipelineStageResult executeStage(String jobId, PipelineStage stage, TimelineSpec timeline,
                                              String profile, String inputPath) {
        log.info("MultiProviderPipelineService: executing stage '{}' provider='{}' job={}",
                stage.name(), stage.providerKey(), jobId);

        long stageStart = System.currentTimeMillis();

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

            // For the effects/transcode stage, use the provider's render method
            if ("effects".equals(stage.name()) || "transcode".equals(stage.name())) {
                RenderProvider.RenderResult result = provider.render(jobId, stageInput, profile);
                long stageDuration = System.currentTimeMillis() - stageStart;
                return new PipelineStageResult(stage.name(), stage.providerKey(), true,
                        result.storageUri(), stageOutput, null, null, stageDuration);
            }

            // For packaging stage, just pass through (GPAC packaging is handled separately)
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
        if (previousOutput != null) {
            return previousOutput;
        }
        // Serialize timeline to JSON for the first stage
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(timeline);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String buildStageOutput(String jobId, PipelineStage stage) {
        return "/tmp/platform/artifacts/" + jobId + "/" + stage.name() + "-output.mp4";
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
