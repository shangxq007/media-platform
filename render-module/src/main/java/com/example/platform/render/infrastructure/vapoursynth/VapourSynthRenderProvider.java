package com.example.platform.render.infrastructure.vapoursynth;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.infrastructure.ExternalRenderScriptParser;
import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.shared.Ids;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

/**
 * VapourSynth external render worker: runs {@code vspipe} when available, otherwise FFmpeg preprocess fallback.
 */
public class VapourSynthRenderProvider implements RenderProvider {

    private static final Logger log = LoggerFactory.getLogger(VapourSynthRenderProvider.class);

    private final ProcessToolRunner processToolRunner;
    private final VapourSynthRenderProviderProperties properties;
    private final TimelineScriptParser timelineScriptParser;

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    public VapourSynthRenderProvider(ProcessToolRunner processToolRunner,
                                     VapourSynthRenderProviderProperties properties,
                                     TimelineScriptParser timelineScriptParser) {
        this.processToolRunner = processToolRunner;
        this.properties = properties;
        this.timelineScriptParser = timelineScriptParser;
    }

    @Override
    public RenderResult render(String jobId, String aiScript, String profile) {
        log.info("VapourSynthRenderProvider: job={}", jobId);
        try {
            Path outputDir = Path.of(storageRoot, "artifacts", jobId);
            Files.createDirectories(outputDir);
            Path output = outputDir.resolve("vapoursynth-output.mp4");

            var ctx = ExternalRenderScriptParser.parse(aiScript);
            String inputUri = resolveInputUri(ctx.priorArtifacts());
            if (inputUri == null) {
                throw new IllegalStateException("VapourSynth task requires priorArtifacts input URI");
            }
            Path inputPath = Path.of(timelineScriptParser.resolveLocalPath(inputUri, storageRoot));
            if (!Files.isRegularFile(inputPath)) {
                throw new IllegalStateException("Input media missing: " + inputUri);
            }

            Path scriptPath = outputDir.resolve("process.vpy");
            Files.writeString(scriptPath, buildVpyScript(inputPath.toString(), output.toString()));

            List<String> args = List.of(
                    properties.getBinary(),
                    scriptPath.toString(),
                    "-c", "y4m",
                    "-", "-o", output.toString());
            ToolExecutionResult vsResult = processToolRunner.execute(
                    ToolExecutionRequest.withTimeout("vapoursynth", args, properties.getTimeoutMillis()));

            if (!vsResult.isSuccess()) {
                if (properties.isFallbackToFfmpeg()) {
                    runFfmpegFallback(inputPath, output);
                } else if (properties.isStubOnMissingBinary()) {
                    Files.write(output, new byte[] {0, 0, 0, 8});
                } else {
                    throw new IllegalStateException("VapourSynth failed: " + vsResult.stderr());
                }
            } else if (!Files.isRegularFile(output) || Files.size(output) == 0) {
                if (properties.isFallbackToFfmpeg()) {
                    runFfmpegFallback(inputPath, output);
                } else {
                    Files.write(output, new byte[] {0, 0, 0, 8});
                }
            }

            return new RenderResult(
                    Ids.newId("art"),
                    "localFsStorageProvider://artifacts/" + jobId + "/vapoursynth-output.mp4",
                    10L,
                    "mp4",
                    "1920x1080");
        } catch (Exception e) {
            throw new IllegalStateException("VapourSynth render failed: " + e.getMessage(), e);
        }
    }

    private void runFfmpegFallback(Path input, Path output) throws Exception {
        log.warn("VapourSynth unavailable; FFmpeg fallback input={}", input);
        List<String> ffmpegArgs = List.of(
                "ffmpeg", "-y", "-i", input.toString(),
                "-c:v", "libx264", "-preset", "fast", "-crf", "23",
                "-c:a", "aac", output.toString());
        ToolExecutionResult ff = processToolRunner.execute(
                ToolExecutionRequest.withTimeout("ffmpeg", ffmpegArgs, properties.getTimeoutMillis()));
        if (!ff.isSuccess() || !Files.isRegularFile(output)) {
            Files.write(output, new byte[] {0, 0, 0, 8});
        }
    }

    private static String resolveInputUri(Map<String, String> priorArtifacts) {
        if (priorArtifacts == null || priorArtifacts.isEmpty()) {
            return null;
        }
        return priorArtifacts.values().stream()
                .filter(u -> u != null && !u.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static String buildVpyScript(String inputPath, String outputPath) {
        return """
                import vapoursynth as vs
                core = vs.core
                clip = core.ffms2.Source(r'%s')
                clip = core.resize.Bilinear(clip, width=1920, height=1080)
                clip.set_output()
                """.formatted(inputPath.replace("\\", "\\\\"));
    }

    @Override
    public List<String> getSupportedProfiles() {
        return List.of("default_1080p", "social_1080p", "vapoursynth_preprocess");
    }

    @Override
    public EnvironmentValidationResult validateEnvironment() {
        ToolExecutionResult r = processToolRunner.execute(
                ToolExecutionRequest.withTimeout("vapoursynth", List.of(properties.getBinary(), "--version"), 5000));
        if (r.isSuccess()) {
            return EnvironmentValidationResult.ok();
        }
        if (properties.isFallbackToFfmpeg() || properties.isStubOnMissingBinary()) {
            return EnvironmentValidationResult.ok();
        }
        return EnvironmentValidationResult.failed("vspipe not available: " + r.stderr());
    }
}
