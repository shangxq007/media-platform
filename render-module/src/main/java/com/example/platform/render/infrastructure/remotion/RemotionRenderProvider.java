package com.example.platform.render.infrastructure.remotion;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.infrastructure.ExternalRenderScriptParser;
import com.example.platform.render.infrastructure.ProviderStatus;
import com.example.platform.render.infrastructure.ProviderType;
import com.example.platform.render.infrastructure.RenderJob;
import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.render.infrastructure.font.FontPreflightResult;
import com.example.platform.render.infrastructure.font.RenderJobFontPreflight;
import com.example.platform.shared.Ids;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
 * Does NOT replace FFmpeg/libass for baseline subtitle burn-in.
 * Fonts must use unified font asset management, no system font dependency.
 * Subtitle line breaks and timeline must be provided by upstream RenderJob, Remotion only renders.
 * Output passed to FFmpeg for final normalization.</p>
 */
public class RemotionRenderProvider implements RenderProvider {

    private static final Logger log = LoggerFactory.getLogger(RemotionRenderProvider.class);

    private final ProcessToolRunner processToolRunner;
    private final RemotionRenderProviderProperties properties;
    private final TimelineScriptParser timelineScriptParser;
    private final RenderJobFontPreflight fontPreflight;

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    public RemotionRenderProvider(ProcessToolRunner processToolRunner,
                                  RemotionRenderProviderProperties properties,
                                  TimelineScriptParser timelineScriptParser,
                                  RenderJobFontPreflight fontPreflight) {
        this.processToolRunner = processToolRunner;
        this.properties = properties;
        this.timelineScriptParser = timelineScriptParser;
        this.fontPreflight = fontPreflight;
    }

    /**
     * Render a Remotion template with font readiness preflight.
     *
     * <p>Before rendering, performs a font readiness check on the RenderJob.
     * If any font asset is not ready, render is rejected with an explicit error.
     */
    @Override
    public RenderResult render(String jobId, String aiScript, String profile) {
        log.info("RemotionRenderProvider: job={} profile={}", jobId, profile);
        try {
            RenderJob renderJob = extractRenderJob(aiScript);
            if (renderJob != null) {
                FontPreflightResult fontResult = fontPreflight.preflight(renderJob);
                if (!fontResult.passed()) {
                    throw new IllegalStateException(
                            "Font preflight failed for Remotion render job=" + jobId
                            + ": " + fontResult.errors());
                }
                if (!fontResult.warnings().isEmpty()) {
                    log.warn("Font preflight warnings for job={}: {}", jobId, fontResult.warnings());
                }
            }

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

    /**
     * Extract a RenderJob from the AI script JSON for font preflight.
     */
    private RenderJob extractRenderJob(String aiScript) {
        if (aiScript == null || aiScript.isBlank() || aiScript.equals("{}")) {
            return null;
        }
        try {
            String id = extractStringField(aiScript, "id");
            String mode = extractStringField(aiScript, "mode");
            String style = extractStringField(aiScript, "style");
            String captions = extractStringField(aiScript, "captions");
            if (id == null) return null;
            return new RenderJob(id, "captioned_video_export",
                    mode != null ? mode : "production",
                    "1920x1080", List.of(), "{}", captions, style, "mp4",
                    List.of(), null, true, List.of(), List.of());
        } catch (Exception e) {
            log.debug("Could not extract RenderJob from aiScript for font preflight: {}", e.getMessage());
            return null;
        }
    }

    private String extractStringField(String json, String fieldName) {
        int idx = json.indexOf("\"" + fieldName + "\":");
        if (idx < 0) return null;
        int startQuote = json.indexOf("\"", idx + fieldName.length() + 3);
        if (startQuote < 0) return null;
        int endQuote = json.indexOf("\"", startQuote + 1);
        if (endQuote < 0) return null;
        return json.substring(startQuote + 1, endQuote);
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
