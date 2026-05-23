package com.example.platform.render.infrastructure.libass;

import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.render.infrastructure.RenderProviderCapability;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * L6 libass subtitle burn-in provider (ASS + FFmpeg {@code ass=} filter).
 */
@Component
@ConditionalOnProperty(prefix = "render.providers.libass", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LibassOverlayRenderProvider implements RenderProvider {

    private static final Logger log = LoggerFactory.getLogger(LibassOverlayRenderProvider.class);

    private final TimelineScriptParser timelineScriptParser;
    private final LibassSubtitleCompositor compositor;

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    public LibassOverlayRenderProvider(TimelineScriptParser timelineScriptParser,
                                       LibassSubtitleCompositor compositor) {
        this.timelineScriptParser = timelineScriptParser;
        this.compositor = compositor;
    }

    @Override
    public RenderResult render(String jobId, String aiScript, String profile) {
        log.info("LibassOverlayRenderProvider: job={}", jobId);
        Optional<TimelineSpec> specOpt = timelineScriptParser.parse(aiScript);
        if (specOpt.isEmpty()) {
            throw new PlatformException(
                    new ConfigurableErrorCode("RENDER-400-030", 400030,
                            Map.of("en", "Invalid timeline for libass", "zh", "libass 时间线无效"),
                            "render", 400),
                    "Cannot parse timeline",
                    Map.of("jobId", jobId),
                    "en");
        }
        TimelineSpec spec = specOpt.get();
        try {
            Path outputDir = Path.of(storageRoot, "artifacts", jobId);
            Files.createDirectories(outputDir);
            Path input = resolveInputVideo(jobId, spec);
            Path output = outputDir.resolve("libass-output.mp4");
            var result = compositor.applyTextOverlays(input, output, spec);
            if (!result.success() || result.wasSkipped()) {
                throw new IllegalStateException("libass stage produced no output: "
                        + (result.errorMessage() != null ? result.errorMessage() : "skipped"));
            }
            return new RenderResult(
                    Ids.newId("art"),
                    "localFsStorageProvider://artifacts/" + jobId + "/libass-output.mp4",
                    30L,
                    "mp4",
                    spec.outputSpec() != null
                            ? spec.outputSpec().width() + "x" + spec.outputSpec().height()
                            : "1920x1080");
        } catch (PlatformException e) {
            throw e;
        } catch (Exception e) {
            log.error("Libass render failed job={}", jobId, e);
            throw new PlatformException(
                    new ConfigurableErrorCode("RENDER-500-030", 500030,
                            Map.of("en", "Libass burn-in failed", "zh", "libass 烧录失败"),
                            "render", 500),
                    e.getMessage(),
                    Map.of("jobId", jobId),
                    "en");
        }
    }

    private Path resolveInputVideo(String jobId, TimelineSpec spec) {
        Path transcode = Path.of(storageRoot, "artifacts", jobId, "transcode-output.mp4");
        if (Files.isRegularFile(transcode)) {
            return transcode;
        }
        Path effects = Path.of(storageRoot, "artifacts", jobId, "effects-output.mp4");
        if (Files.isRegularFile(effects)) {
            return effects;
        }
        for (var track : spec.tracks()) {
            if (track.type() != com.example.platform.render.domain.timeline.TimelineTrack.TrackType.VIDEO) {
                continue;
            }
            for (var clip : track.clips()) {
                if (clip.assetRef() != null && clip.assetRef().storageUri() != null) {
                    return Path.of(timelineScriptParser.resolveLocalPath(
                            clip.assetRef().storageUri(), storageRoot));
                }
            }
        }
        throw new IllegalStateException("No input video for libass stage");
    }

    public RenderProviderCapability getCapability() {
        return new RenderProviderCapability(
                "libass",
                Set.of("mp4"),
                Set.of("h264", "aac"),
                Set.of("text.subtitle_burn_in", "text.overlay"),
                Set.of(),
                Set.of("burn_in"),
                "3840x2160",
                true,
                false,
                false,
                Set.of("libass_1080p", "default_1080p"));
    }

    @Override
    public List<String> getSupportedProfiles() {
        return List.of("libass_1080p", "default_1080p");
    }

    @Override
    public EnvironmentValidationResult validateEnvironment() {
        return EnvironmentValidationResult.ok();
    }
}
