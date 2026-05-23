package com.example.platform.render.infrastructure.ofx;

import com.example.platform.render.domain.timeline.TimelineClip;
import com.example.platform.render.domain.timeline.TimelineClipEffect;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineTrack;
import com.example.platform.render.infrastructure.ffmpeg.FfmpegDualInputOverlayService;
import com.example.platform.render.infrastructure.libass.LibassSubtitleCompositor;
import com.example.platform.render.infrastructure.popcornfx.PopcornFxAssetResolver;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * OFX compositor path: real media + PopcornFX dual-input overlay and optional libass burn-in via FFmpeg.
 */
@Service
public class OfxFfmpegCompositeService {

    private static final Logger log = LoggerFactory.getLogger(OfxFfmpegCompositeService.class);
    private static final String PARTICLE_OVERLAY = "video.particle_overlay";

    private final TimelineScriptParser timelineScriptParser;
    private final PopcornFxAssetResolver popcornFxAssetResolver;
    private final FfmpegDualInputOverlayService overlayService;
    private final LibassSubtitleCompositor libassCompositor;

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    @Value("${render.subtitle.libass.enabled:true}")
    private boolean libassEnabled;

    public OfxFfmpegCompositeService(TimelineScriptParser timelineScriptParser,
                                     PopcornFxAssetResolver popcornFxAssetResolver,
                                     FfmpegDualInputOverlayService overlayService,
                                     LibassSubtitleCompositor libassCompositor) {
        this.timelineScriptParser = timelineScriptParser;
        this.popcornFxAssetResolver = popcornFxAssetResolver;
        this.overlayService = overlayService;
        this.libassCompositor = libassCompositor;
    }

    public Optional<Path> tryCompose(String jobId, String aiScript, Path finalOutput) {
        Optional<TimelineSpec> specOpt = timelineScriptParser.parse(aiScript);
        if (specOpt.isEmpty()) {
            return Optional.empty();
        }
        TimelineSpec spec = specOpt.get();
        Optional<VideoClipContext> videoCtx = findPrimaryVideoClip(spec);
        if (videoCtx.isEmpty()) {
            return Optional.empty();
        }

        Path basePath = Path.of(timelineScriptParser.resolveLocalPath(
                videoCtx.get().clip().assetRef().storageUri(), storageRoot));
        if (!basePath.toFile().isFile()) {
            log.debug("OFX FFmpeg path skipped: base media not on disk {}", basePath);
            return Optional.empty();
        }

        Path working = finalOutput.getParent().resolve("ofx-ffmpeg-work.mp4");
        Path current = basePath;

        Optional<TimelineClipEffect> particle = findEffect(videoCtx.get().clip(), PARTICLE_OVERLAY);
        if (particle.isPresent()) {
            Optional<Path> overlayPath = popcornFxAssetResolver.resolveOverlayPath(particle.get().parameters());
            if (overlayPath.isEmpty()) {
                log.warn("particle_overlay requested but asset missing for job={}", jobId);
                return Optional.empty();
            }
            Map<String, Object> p = particle.get().parameters();
            int x = ((Number) p.getOrDefault("x", 0)).intValue();
            int y = ((Number) p.getOrDefault("y", 0)).intValue();
            double opacity = ((Number) p.getOrDefault("opacity", 1.0)).doubleValue();
            var overlayResult = overlayService.compose(current, overlayPath.get(), working, x, y, opacity);
            if (!overlayResult.success()) {
                log.warn("OFX particle overlay failed: {}", overlayResult.errorMessage());
                return Optional.empty();
            }
            current = overlayResult.outputPath();
        } else if (!hasOnlyRealMedia(spec, videoCtx.get())) {
            return Optional.empty();
        }

        Path result = current;
        if (libassEnabled && hasLibassTargets(spec)) {
            Path withSubs = finalOutput.getParent().resolve("ofx-ffmpeg-subs.mp4");
            var subResult = libassCompositor.applyTextOverlays(current, withSubs, spec);
            if (subResult.success()) {
                result = subResult.outputPath();
            }
        }

        try {
            if (!result.equals(finalOutput)) {
                java.nio.file.Files.copy(result, finalOutput,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return Optional.of(finalOutput);
        } catch (Exception e) {
            log.error("OFX FFmpeg composite copy failed", e);
            return Optional.empty();
        }
    }

    private boolean hasOnlyRealMedia(TimelineSpec spec, VideoClipContext ctx) {
        return findEffect(ctx.clip(), PARTICLE_OVERLAY).isEmpty()
                && spec.tracks().stream()
                .filter(t -> t.type() == TimelineTrack.TrackType.VIDEO)
                .flatMap(t -> t.clips().stream())
                .anyMatch(c -> c.assetRef() != null);
    }

    private boolean hasLibassTargets(TimelineSpec spec) {
        return spec.textOverlays() != null && !spec.textOverlays().isEmpty();
    }

    private Optional<VideoClipContext> findPrimaryVideoClip(TimelineSpec spec) {
        for (TimelineTrack track : spec.tracks()) {
            if (track.type() != TimelineTrack.TrackType.VIDEO || track.clips() == null) {
                continue;
            }
            for (TimelineClip clip : track.clips()) {
                if (clip.assetRef() != null && clip.assetRef().storageUri() != null
                        && !clip.assetRef().storageUri().isBlank()) {
                    return Optional.of(new VideoClipContext(track, clip));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<TimelineClipEffect> findEffect(TimelineClip clip, String effectKey) {
        if (clip.effects() == null) {
            return Optional.empty();
        }
        return clip.effects().stream()
                .filter(e -> effectKey.equals(e.effectKey()))
                .findFirst();
    }

    private record VideoClipContext(TimelineTrack track, TimelineClip clip) {}
}
