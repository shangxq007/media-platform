package com.example.platform.render.infrastructure.remotion;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.infrastructure.ExternalRenderScriptParser;
import com.example.platform.render.infrastructure.ProviderStatus;
import com.example.platform.render.infrastructure.ProviderType;
import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.shared.Ids;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

/**
 * Remotion subtitle and template render provider.
 *
 * <p>Status: POC / P1. Specialized subtitle and template render provider.
 * For subtitle fonts, subtitle effects, word-by-word highlighting, TikTok/short video style subtitles,
 * React template-based video, brand packaging, title cards.
 * Frontend can preview via Remotion Player, backend outputs via Remotion Renderer.
 * Does NOT handle video trim, transcode, audio extraction, format repair.
 * Fonts must use unified font asset management, no system font dependency.
 * Subtitle line breaks and timeline must be provided by upstream RenderJob, Remotion only renders.
 * Output passed to FFmpeg for final normalization.</p>
 */
public class RemotionRenderProvider implements RenderProvider {

    private static final Logger log = LoggerFactory.getLogger(RemotionRenderProvider.class);

    private final ProcessToolRunner processToolRunner;
    private final RemotionRenderProviderProperties properties;
    private final TimelineScriptParser timelineScriptParser;

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    public RemotionRenderProvider(ProcessToolRunner processToolRunner,
                                  RemotionRenderProviderProperties properties,
                                  TimelineScriptParser timelineScriptParser) {
        this.processToolRunner = processToolRunner;
        this.properties = properties;
        this.timelineScriptParser = timelineScriptParser;
    }

    @Override
    public RenderResult render(String jobId, String aiScript, String profile) {
        log.info("RemotionRenderProvider: job={} profile={}", jobId, profile);
        try {
            Path outputDir = Path.of(storageRoot, "artifacts", jobId);
            Files.createDirectories(outputDir);
            Path output = outputDir.resolve("remotion-output.mp4");
            Path propsFile = outputDir.resolve("remotion-props.json");
            Files.writeString(propsFile, aiScript != null ? aiScript : "{}");

            ExternalRenderScriptParser.ExternalRenderContext ctx =
                    ExternalRenderScriptParser.parse(aiScript);
            List<String> args = buildRenderCommand(propsFile, output, ctx);
            ToolExecutionResult result = processToolRunner.execute(
                    ToolExecutionRequest.withTimeout("remotion", args, properties.getTimeoutMillis()));

            if (!result.isSuccess()) {
                if (properties.isStubOnMissingCli()) {
                    Files.write(output, new byte[] {0, 0, 0, 8});
                    log.warn("Remotion CLI failed; stub output written for job={}", jobId);
                } else {
                    throw new IllegalStateException("Remotion failed: " + result.stderr());
                }
            }

            long duration = timelineScriptParser.parse(aiScript)
                    .map(TimelineSpec::totalDuration)
                    .filter(d -> d > 0)
                    .map(d -> Math.max(1L, Math.round(d)))
                    .orElse(30L);

            return new RenderResult(
                    Ids.newId("art"),
                    "localFsStorageProvider://artifacts/" + jobId + "/remotion-output.mp4",
                    duration,
                    "mp4",
                    "1920x1080");
        } catch (Exception e) {
            log.error("Remotion render failed job={}", jobId, e);
            throw new IllegalStateException("Remotion render failed: " + e.getMessage(), e);
        }
    }

    List<String> buildRenderCommand(Path propsFile, Path output,
                                   ExternalRenderScriptParser.ExternalRenderContext ctx) {
        List<String> args = new ArrayList<>();
        args.add(properties.getCli());
        if (properties.getRemotionArgs() != null && !properties.getRemotionArgs().isBlank()) {
            for (String part : properties.getRemotionArgs().split("\\s+")) {
                if (!part.isBlank()) {
                    args.add(part);
                }
            }
        }
        args.add("render");
        String composition = ctx != null && ctx.compositionId() != null && !ctx.compositionId().isBlank()
                ? ctx.compositionId()
                : (ctx != null && ctx.templateId() != null && !ctx.templateId().isBlank()
                        ? ctx.templateId()
                        : properties.getCompositionId());
        args.add(composition);
        args.add(output.toString());
        args.add("--props");
        args.add(propsFile.toString());
        if (ctx != null && ctx.projectDir() != null && !ctx.projectDir().isBlank()) {
            args.add("--root");
            args.add(ctx.projectDir());
        }
        return args;
    }

    @Override
    public List<String> getSupportedProfiles() {
        return List.of("remotion_1080p", "remotion_social");
    }

    @Override
    public EnvironmentValidationResult validateEnvironment() {
        return EnvironmentValidationResult.ok();
    }

    @Override
    public ProviderStatus getStatus() {
        return ProviderStatus.POC;
    }

    @Override
    public String getPriority() {
        return "P1";
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.RENDER;
    }

    @Override
    public String getPurpose() {
        return "Subtitle and template render provider: subtitle fonts, effects, word highlighting, React templates, brand packaging";
    }

    @Override
    public List<String> getLimitations() {
        return List.of(
                "Does NOT handle video trim, transcode, audio extraction, format repair",
                "Fonts must use unified font asset management, no system font dependency",
                "Subtitle line breaks and timeline must be provided by upstream RenderJob",
                "Output should be passed to FFmpeg for final normalization"
        );
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("caption_effects", "caption_burn_in", "template_render", "subtitle_fonts", "word_highlighting");
    }

    @Override
    public boolean isAutoDispatch() {
        return true;
    }
}
