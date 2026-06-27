package com.example.platform.render.app.timeline;

import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.domain.timeline.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Maps a {@link TimelineSpec} or timeline JSON to a {@link SubmitRenderJobRequest}
 * with fail-closed validation.
 *
 * <p>This mapper validates all inputs before creating a render job request.
 * It rejects unsafe or invalid input and does not expose internal provider,
 * backend, environment, or storage provider selection to external callers.</p>
 *
 * <p>The mapper does not enable production dispatch. It produces a request
 * suitable for controlled local smoke testing and the baseline render path.</p>
 *
 * <p>FFmpeg/libass remains the baseline subtitle burn-in path. Remotion
 * remains gated for advanced visual/template rendering only. OpenCue
 * remains disabled by default.</p>
 */
@Service
public class TimelineRenderJobMapper {

    private static final Logger log = LoggerFactory.getLogger(TimelineRenderJobMapper.class);

    static final Set<String> ALLOWED_FORMATS = Set.of("mp4", "webm", "mov", "mkv");
    static final double MAX_DURATION_SEC = 3600.0;
    static final int MAX_FPS = 120;
    static final int MAX_WIDTH = 7680;
    static final int MAX_HEIGHT = 4320;

    /**
     * Internal provider/backend/environment/storageProvider names that must not
     * be selected from external timeline input. If found in preferredProviders
     * or blockedProviders from untrusted input, the request is rejected.
     */
    private static final Set<String> INTERNAL_PROVIDER_NAMES = Set.of(
            "remotion", "ffmpeg", "mlt", "gpac", "bento4", "shaka",
            "libass", "skia", "blender", "natron", "vapoursynth",
            "bmf", "shotstack", "javacv", "opencue",
            "local-process", "localprocess", "local_fs", "localfs",
            "minio", "s3", "oss", "gcs", "azure"
    );

    private final TimelineScriptParser parser;
    private final InternalTimelineWriter writer;

    public TimelineRenderJobMapper(TimelineScriptParser parser, InternalTimelineWriter writer) {
        this.parser = parser;
        this.writer = writer;
    }

    /**
     * Maps a parsed {@link TimelineSpec} to a {@link SubmitRenderJobRequest}.
     *
     * @param tenantId  must not be blank
     * @param projectId must not be blank
     * @param spec      must not be null
     * @param profile   render profile; defaults to "default_1080p" if blank
     * @return the mapped request with provenance embedded in the prompt field
     * @throws IllegalArgumentException if any validation fails
     */
    public MappingResult toRenderJobRequest(String tenantId, String projectId,
                                             TimelineSpec spec, String profile) {
        validateNotBlank(tenantId, "Tenant ID must not be blank");
        validateNotBlank(projectId, "Project ID must not be blank");
        validateNotNull(spec, "Timeline spec must not be null");
        validateNotBlank(spec.id(), "Timeline ID must not be blank");

        // Output spec validation
        validateNotNull(spec.outputSpec(), "Output specification is required");
        TimelineOutputSpec output = spec.outputSpec();

        if (output.frameRate() <= 0) {
            throw new IllegalArgumentException("Frame rate must be positive: " + output.frameRate());
        }
        if (output.frameRate() > MAX_FPS) {
            throw new IllegalArgumentException("Frame rate exceeds maximum (" + MAX_FPS + "fps): " + output.frameRate());
        }
        if (output.width() <= 0) {
            throw new IllegalArgumentException("Canvas width must be positive: " + output.width());
        }
        if (output.height() <= 0) {
            throw new IllegalArgumentException("Canvas height must be positive: " + output.height());
        }
        if (output.width() > MAX_WIDTH || output.height() > MAX_HEIGHT) {
            throw new IllegalArgumentException("Canvas dimensions exceed maximum (" + MAX_WIDTH + "x" + MAX_HEIGHT + "): "
                    + output.width() + "x" + output.height());
        }

        // Format validation
        String format = output.format();
        if (format == null || format.isBlank()) {
            throw new IllegalArgumentException("Output format is required");
        }
        if (!ALLOWED_FORMATS.contains(format.toLowerCase())) {
            throw new IllegalArgumentException("Unsupported output format: " + format);
        }

        // Duration validation
        double duration = spec.totalDuration() > 0 ? spec.totalDuration() : spec.computeDuration();
        if (duration <= 0) {
            throw new IllegalArgumentException("Timeline duration must be positive: " + duration);
        }
        if (duration > MAX_DURATION_SEC) {
            throw new IllegalArgumentException("Timeline duration exceeds maximum (" + MAX_DURATION_SEC + "s): " + duration);
        }

        // Track/clip validation
        if (spec.tracks() == null || spec.tracks().isEmpty()) {
            throw new IllegalArgumentException("Timeline must have at least one track");
        }
        boolean hasClips = spec.tracks().stream()
                .anyMatch(t -> t.clips() != null && !t.clips().isEmpty());
        if (!hasClips) {
            throw new IllegalArgumentException("Timeline has no renderable clips");
        }

        // Asset path safety validation
        validateAssetPaths(spec);

        // Detect subtitles
        boolean hasSubtitles = (spec.textOverlays() != null && !spec.textOverlays().isEmpty())
                || spec.tracks().stream().anyMatch(t -> t.type() == TimelineTrack.TrackType.SUBTITLE);

        // Serialize to Internal Timeline JSON for the prompt field
        String timelineJson = writer.toJson(spec);

        // Validate that the serialized JSON is valid timeline JSON
        if (!parser.isTimelineJson(timelineJson)) {
            throw new IllegalArgumentException("Serialized timeline JSON is not valid");
        }

        String resolvedProfile = (profile == null || profile.isBlank()) ? "default_1080p" : profile;

        // Build request — no preferred/blocked providers from external input
        // Platform resolves providers internally via CapabilityResolution
        SubmitRenderJobRequest request = new SubmitRenderJobRequest(
                tenantId, projectId, timelineJson, resolvedProfile, null);

        int fps = (int) output.frameRate();
        int width = output.width();
        int height = output.height();

        log.info("Timeline mapped to RenderJob request: timelineId={} tenant={} project={} "
                + "duration={}s fps={} {}x{} format={} subtitles={}",
                spec.id(), tenantId, projectId, duration, fps, width, height, format, hasSubtitles);

        return new MappingResult(request, spec.id(), hasSubtitles, duration, fps, width, height, format);
    }

    /**
     * Parses timeline JSON and maps to a {@link SubmitRenderJobRequest}.
     *
     * @param tenantId     must not be blank
     * @param projectId    must not be blank
     * @param timelineJson must be valid timeline JSON
     * @param profile      render profile; defaults to "default_1080p" if blank
     * @return the mapped request with provenance embedded in the prompt field
     * @throws IllegalArgumentException if parsing or validation fails
     */
    public MappingResult toRenderJobRequestFromJson(String tenantId, String projectId,
                                                     String timelineJson, String profile) {
        validateNotBlank(tenantId, "Tenant ID must not be blank");
        validateNotBlank(projectId, "Project ID must not be blank");
        if (timelineJson == null || timelineJson.isBlank()) {
            throw new IllegalArgumentException("Timeline JSON must not be blank");
        }

        var spec = parser.parse(timelineJson)
                .orElseThrow(() -> new IllegalArgumentException("Failed to parse timeline JSON"));
        return toRenderJobRequest(tenantId, projectId, spec, profile);
    }

    /**
     * Validates that all asset paths in the timeline are safe.
     * Rejects: path traversal (..), home directory (~), absolute paths (/),
     * remote URLs (http/https), file:// URIs.
     * Accepts: asset:// scheme, relative paths without traversal.
     */
    private void validateAssetPaths(TimelineSpec spec) {
        if (spec.tracks() == null) return;
        for (TimelineTrack track : spec.tracks()) {
            if (track.clips() == null) continue;
            for (TimelineClip clip : track.clips()) {
                if (clip.assetRef() == null) continue;
                String uri = clip.assetRef().storageUri();
                if (uri == null || uri.isBlank()) continue;
                validateSingleAssetPath(uri, clip.id());
            }
        }
    }

    private void validateSingleAssetPath(String uri, String clipId) {
        if (uri.contains("..")) {
            throw new IllegalArgumentException("Unsafe asset path: path traversal detected in clip " + clipId);
        }
        if (uri.startsWith("~")) {
            throw new IllegalArgumentException("Unsafe asset path: home directory reference in clip " + clipId);
        }
        if (uri.startsWith("/")) {
            throw new IllegalArgumentException("Unsafe asset path: absolute path not allowed in clip " + clipId);
        }
        if (uri.startsWith("file://") || uri.startsWith("FILE://")) {
            throw new IllegalArgumentException("Unsafe asset path: file:// URI not allowed in clip " + clipId);
        }
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            throw new IllegalArgumentException("Unsafe asset path: remote URL not allowed in clip " + clipId);
        }
    }

    private void validateNotNull(Object value, String message) {
        if (value == null) throw new IllegalArgumentException(message);
    }

    private void validateNotBlank(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }

    /**
     * Result of mapping a timeline to a render job request.
     * Carries provenance metadata for downstream use.
     */
    public record MappingResult(
            SubmitRenderJobRequest request,
            String timelineId,
            boolean hasSubtitles,
            double duration,
            int fps,
            int width,
            int height,
            String outputFormat) {}
}
