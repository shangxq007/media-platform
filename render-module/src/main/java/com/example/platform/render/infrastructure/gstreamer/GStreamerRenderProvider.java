package com.example.platform.render.infrastructure.gstreamer;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.render.domain.timeline.TimelineClip;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.render.infrastructure.RenderPreset;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * GStreamer-based render provider for pipeline-based video processing.
 */
@Component
@ConditionalOnProperty(prefix = "render.providers.gstreamer", name = "enabled", havingValue = "true")
public class GStreamerRenderProvider implements RenderProvider {

    private static final Logger log = LoggerFactory.getLogger(GStreamerRenderProvider.class);

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    private final ProcessToolRunner processToolRunner;
    private final GStreamerCommandFactory commandFactory;
    private final TimelineScriptParser timelineScriptParser;

    public GStreamerRenderProvider(ProcessToolRunner processToolRunner,
                                    GStreamerCommandFactory commandFactory,
                                    TimelineScriptParser timelineScriptParser) {
        this.processToolRunner = processToolRunner;
        this.commandFactory = commandFactory;
        this.timelineScriptParser = timelineScriptParser;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    @Override
    public RenderResult render(String jobId, String aiScript, String profile) {
        log.info("GStreamerRenderProvider: rendering job={}, profile={}", jobId, profile);

        try {
            Path outputDir = Path.of(storageRoot, "artifacts", jobId);
            outputDir.toFile().mkdirs();
            String outputPath = outputDir.resolve("output.mp4").toString();
            RenderPreset preset = RenderPreset.fromProfile(profile);

            List<String> pipelineArgs = buildPipeline(aiScript, outputPath, preset);

            ToolExecutionRequest request = ToolExecutionRequest.withTimeout(
                    "gst-launch-1.0", pipelineArgs, 600_000);
            ToolExecutionResult result = processToolRunner.execute(request);

            if (result.isSuccess()) {
                String artifactId = Ids.newId("art");
                String resolution = preset.width() + "x" + preset.height();
                log.info("GStreamerRenderProvider: render complete, artifact={}", artifactId);
                return new RenderResult(
                        artifactId,
                        "localFsStorageProvider://artifacts/" + jobId + "/output.mp4",
                        30L,
                        "mp4",
                        resolution);
            }
            throw new PlatformException(
                    new ConfigurableErrorCode("RENDER-500-005", 500505,
                            Map.of("en", "GStreamer rendering failed", "zh", "GStreamer渲染失败"),
                            "render", 500),
                    "gst-launch-1.0 failed: " + result.stderr(),
                    Map.of("jobId", jobId, "provider", "gstreamer",
                            "exitCode", String.valueOf(result.exitCode())),
                    "en");
        } catch (PlatformException e) {
            throw e;
        } catch (Exception e) {
            log.error("GStreamerRenderProvider: render failed for job={}", jobId, e);
            throw new PlatformException(
                    new ConfigurableErrorCode("RENDER-500-001", 500101,
                            Map.of("en", "GStreamer render failed", "zh", "GStreamer渲染失败"),
                            "render", 500),
                    e.getMessage(),
                    Map.of("jobId", jobId, "provider", "gstreamer"),
                    "en");
        }
    }

    private List<String> buildPipeline(String aiScript, String outputPath, RenderPreset preset) {
        Optional<TimelineSpec> timeline = timelineScriptParser.parse(aiScript);
        if (timeline.isPresent()) {
            Optional<TimelineClip> clip = timelineScriptParser.firstVideoClip(timeline.get());
            if (clip.isPresent() && clip.get().assetRef() != null) {
                String uri = clip.get().assetRef().storageUri();
                if (timelineScriptParser.mediaFileExists(uri, storageRoot)) {
                    String sourcePath = timelineScriptParser.resolveLocalPath(uri, storageRoot);
                    return commandFactory.buildTranscodePipeline(sourcePath, outputPath, preset);
                }
            }
        }
        return commandFactory.buildTestSourcePipeline(outputPath, preset);
    }

    @Override
    public List<String> getSupportedProfiles() {
        return List.of("default_1080p", "default_720p", "social_1080p", "social_720p",
                "gstreamer_1080p", "gstreamer_720p");
    }

    @Override
    public boolean supports(String capability) {
        return switch (capability) {
            case "pipeline", "real-time", "streaming", "multi-track", "compositing",
                 "subtitle-overlay", "filter-graph", "transcode" -> true;
            default -> getSupportedProfiles().contains(capability);
        };
    }

    @Override
    public EnvironmentValidationResult validateEnvironment() {
        try {
            ToolExecutionRequest request = ToolExecutionRequest.withTimeout(
                    "gst-launch-1.0", List.of("--version"), 10_000);
            ToolExecutionResult result = processToolRunner.execute(request);
            if (result.isSuccess()) {
                return EnvironmentValidationResult.ok();
            }
            return EnvironmentValidationResult.failed("gst-launch-1.0 returned non-zero exit code");
        } catch (Exception e) {
            return EnvironmentValidationResult.failed("gst-launch-1.0 not available: " + e.getMessage());
        }
    }
}
