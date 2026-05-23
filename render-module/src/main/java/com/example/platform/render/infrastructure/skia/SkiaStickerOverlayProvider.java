package com.example.platform.render.infrastructure.skia;

import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineStickerReader;
import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.render.infrastructure.RenderProviderCapability;
import com.example.platform.shared.Ids;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** L6 Skia-compatible sticker overlay (Java2D raster + FFmpeg overlay). */
@Component
@ConditionalOnProperty(prefix = "render.providers.skia", name = "enabled", havingValue = "true")
public class SkiaStickerOverlayProvider implements RenderProvider {

    private static final Logger log = LoggerFactory.getLogger(SkiaStickerOverlayProvider.class);

    private final TimelineScriptParser timelineScriptParser;
    private final TimelineStickerReader stickerReader;
    private final StickerOverlayCompositor compositor;

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    public SkiaStickerOverlayProvider(TimelineScriptParser timelineScriptParser,
                                      TimelineStickerReader stickerReader,
                                      StickerOverlayCompositor compositor) {
        this.timelineScriptParser = timelineScriptParser;
        this.stickerReader = stickerReader;
        this.compositor = compositor;
    }

    @Override
    public RenderResult render(String jobId, String aiScript, String profile) {
        log.info("SkiaStickerOverlayProvider: job={}", jobId);
        TimelineSpec spec = timelineScriptParser.parse(aiScript)
                .orElseThrow(() -> new IllegalArgumentException("Invalid timeline for skia overlay"));
        if (!stickerReader.requiresSkiaOverlay(spec)) {
            throw new IllegalStateException("No stickers on timeline");
        }
        try {
            Path outputDir = Path.of(storageRoot, "artifacts", jobId);
            Files.createDirectories(outputDir);
            Path input = resolveInput(jobId, spec);
            Path output = outputDir.resolve("skia-output.mp4");
            var result = compositor.applyStickers(input, output, spec);
            if (!result.success()) {
                throw new IllegalStateException(result.errorMessage());
            }
            if (result.skipped()) {
                Files.copy(input, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return new RenderResult(
                    Ids.newId("art"),
                    "localFsStorageProvider://artifacts/" + jobId + "/skia-output.mp4",
                    30L,
                    "mp4",
                    spec.outputSpec() != null
                            ? spec.outputSpec().width() + "x" + spec.outputSpec().height()
                            : "1920x1080");
        } catch (Exception e) {
            throw new IllegalStateException("Skia overlay failed: " + e.getMessage(), e);
        }
    }

    private Path resolveInput(String jobId, TimelineSpec spec) {
        Path transcode = Path.of(storageRoot, "artifacts", jobId, "transcode-output.mp4");
        if (Files.isRegularFile(transcode)) {
            return transcode;
        }
        Path libass = Path.of(storageRoot, "artifacts", jobId, "libass-output.mp4");
        if (Files.isRegularFile(libass)) {
            return libass;
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
        throw new IllegalStateException("No input for skia overlay");
    }

    public RenderProviderCapability getCapability() {
        return new RenderProviderCapability(
                "skia",
                Set.of("mp4"),
                Set.of("h264", "aac"),
                Set.of("video.sticker_image", "video.sticker_animated"),
                Set.of(),
                Set.of("overlay"),
                "3840x2160",
                true,
                false,
                false,
                Set.of("skia_1080p", "default_1080p"));
    }

    @Override
    public List<String> getSupportedProfiles() {
        return List.of("skia_1080p", "default_1080p");
    }

    @Override
    public EnvironmentValidationResult validateEnvironment() {
        return EnvironmentValidationResult.ok();
    }
}
