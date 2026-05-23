package com.example.platform.render.infrastructure.natron;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.render.infrastructure.RenderPreset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

/**
 * Natron worker POC: applies {@link NatronRenderProviderProperties#getPocEffectKey()} via
 * {@code poc-render.sh} (FFmpeg vignette fallback until .ntp templates are wired).
 */
public class NatronRenderProvider implements RenderProvider {

    private static final Logger log = LoggerFactory.getLogger(NatronRenderProvider.class);

    private final ProcessToolRunner processToolRunner;
    private final NatronPocJobExtractor jobExtractor;
    private final NatronPocCommandBuilder commandBuilder;
    private final NatronBatchScriptGenerator batchScriptGenerator;
    private final NatronRenderDurationResolver durationResolver;
    private final NatronRenderProviderProperties properties;

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    public NatronRenderProvider(ProcessToolRunner processToolRunner,
                                NatronPocJobExtractor jobExtractor,
                                NatronPocCommandBuilder commandBuilder,
                                NatronBatchScriptGenerator batchScriptGenerator,
                                NatronRenderDurationResolver durationResolver,
                                NatronRenderProviderProperties properties) {
        this.processToolRunner = processToolRunner;
        this.jobExtractor = jobExtractor;
        this.commandBuilder = commandBuilder;
        this.batchScriptGenerator = batchScriptGenerator;
        this.durationResolver = durationResolver;
        this.properties = properties;
    }

    void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    @Override
    public RenderResult render(String jobId, String aiScript, String profile) {
        log.info("NatronRenderProvider: job={}, profile={}, effects={}", jobId, profile,
                properties.getSupportedEffectKeys());

        try {
            Path outputDir = Path.of(storageRoot, "artifacts", jobId);
            Files.createDirectories(outputDir);
            String outputPath = outputDir.resolve("output.mp4").toString();

            Optional<NatronPocJob> job = jobExtractor.extract(
                    aiScript, properties.getSupportedEffectKeys(), storageRoot, outputPath);
            if (job.isEmpty()) {
                throw new PlatformException(
                        new ConfigurableErrorCode("RENDER-400-010", 400010,
                                Map.of("en", "Timeline missing Natron POC effect or input media",
                                        "zh", "时间线缺少 Natron POC 特效或可渲染素材"),
                                "render", 400),
                        "Expected one of " + properties.getSupportedEffectKeys()
                                + " on a video clip with local media",
                        Map.of("jobId", jobId, "provider", "natron"),
                        "en");
            }

            Path natronDir = outputDir.resolve("natron");
            String batchScriptPath = null;
            if (!properties.isFallbackToFfmpeg()) {
                batchScriptPath = batchScriptGenerator.generate(job.get(), natronDir).toString();
            }

            List<String> args = commandBuilder.buildArgs(
                    job.get(),
                    properties.isFallbackToFfmpeg(),
                    batchScriptPath,
                    properties.getReaderNodeName(),
                    properties.getWriterNodeName());
            ToolExecutionRequest request = new ToolExecutionRequest(
                    NatronPocCommandBuilder.TOOL_KEY,
                    args,
                    Map.of(
                            "NATRON_INTENSITY", String.valueOf(job.get().intensity()),
                            "NATRON_SATURATION", String.valueOf(job.get().saturation())),
                    null,
                    properties.getTimeoutMillis());
            ToolExecutionResult result = processToolRunner.execute(request);

            if (!result.isSuccess()) {
                throw new PlatformException(
                        new ConfigurableErrorCode("RENDER-500-010", 500510,
                                Map.of("en", "Natron POC render failed", "zh", "Natron POC 渲染失败"),
                                "render", 500),
                        "natron-poc-render failed: " + result.stderr(),
                        Map.of("jobId", jobId, "provider", "natron",
                                "exitCode", String.valueOf(result.exitCode())),
                        "en");
            }

            if (!Files.isRegularFile(Path.of(outputPath))) {
                throw new PlatformException(
                        new ConfigurableErrorCode("RENDER-500-010", 500510,
                                Map.of("en", "Natron POC produced no output", "zh", "Natron POC 未生成输出文件"),
                                "render", 500),
                        "Missing output: " + outputPath,
                        Map.of("jobId", jobId),
                        "en");
            }

            RenderPreset preset = RenderPreset.fromProfile(profile);
            String artifactId = Ids.newId("art");
            String resolution = preset.width() + "x" + preset.height();
            log.info("NatronRenderProvider: complete artifact={}", artifactId);

            long durationSec = durationResolver.resolveDurationSeconds(jobId, outputPath);

            return new RenderResult(
                    artifactId,
                    "localFsStorageProvider://artifacts/" + jobId + "/output.mp4",
                    durationSec,
                    "mp4",
                    resolution);
        } catch (PlatformException e) {
            throw e;
        } catch (Exception e) {
            log.error("NatronRenderProvider: failed job={}", jobId, e);
            throw new PlatformException(
                    new ConfigurableErrorCode("RENDER-500-010", 500510,
                            Map.of("en", "Natron render failed", "zh", "Natron 渲染失败"),
                            "render", 500),
                    e.getMessage(),
                    Map.of("jobId", jobId, "provider", "natron"),
                    "en");
        }
    }

    @Override
    public List<String> getSupportedProfiles() {
        return List.of("natron_poc_1080p", "natron_poc_720p");
    }

    @Override
    public EnvironmentValidationResult validateEnvironment() {
        if (properties.isFallbackToFfmpeg()) {
            return EnvironmentValidationResult.ok();
        }
        Path renderer = Path.of(properties.getRendererBinary());
        if (renderer.isAbsolute() && Files.isExecutable(renderer)) {
            return EnvironmentValidationResult.ok();
        }
        return EnvironmentValidationResult.failed(
                "Natron renderer not executable: " + properties.getRendererBinary()
                        + " (enable render.providers.natron.fallback-to-ffmpeg for POC)");
    }
}
