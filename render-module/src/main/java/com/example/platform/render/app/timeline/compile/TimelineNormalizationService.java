package com.example.platform.render.app.timeline.compile;

import com.example.platform.render.domain.timeline.TimelineAssetRef;
import com.example.platform.render.domain.timeline.TimelineClip;
import com.example.platform.render.domain.timeline.TimelineClipEffect;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineTextOverlay;
import com.example.platform.render.domain.timeline.TimelineTrack;
import com.example.platform.render.domain.timeline.compile.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Normalizes a TimelineSpec into a deterministic NormalizedTimeline for compile.
 *
 * <p>Deterministic: same input → same output (byte-stable).
 * Provider-neutral: no tool-specific logic.
 * Fail-closed: unsupported constructs throw TimelineCompileException.</p>
 */
@Service
public class TimelineNormalizationService {

    private static final Logger log = LoggerFactory.getLogger(TimelineNormalizationService.class);

    /**
     * Normalize a TimelineSpec into a NormalizedTimeline.
     *
     * @param spec      the input TimelineSpec
     * @param projectId the project identifier
     * @return the deterministic NormalizedTimeline
     * @throws TimelineCompileException if the spec contains unsupported or invalid constructs
     */
    public NormalizedTimeline normalize(TimelineSpec spec, String projectId) {
        if (spec == null) {
            throw TimelineCompileException.missingField("TimelineSpec");
        }
        if (spec.tracks() == null || spec.tracks().isEmpty()) {
            throw TimelineCompileException.missingField("tracks");
        }
        if (spec.outputSpec() == null) {
            throw TimelineCompileException.missingField("outputSpec");
        }

        String timelineId = spec.id() != null ? spec.id() : "unknown";

        // Normalize tracks: sort by layer (asc), then type priority
        List<NormalizedTrack> tracks = normalizeTracks(spec.tracks());

        // Normalize caption layers from text overlays
        List<NormalizedCaptionLayer> captionLayers = normalizeCaptionLayers(spec.textOverlays());

        // Normalize output profile
        NormalizedOutputProfile outputProfile = normalizeOutputProfile(spec.outputSpec());

        // Compute total duration
        double totalDuration = spec.totalDuration() > 0
                ? spec.totalDuration()
                : tracks.stream().mapToDouble(NormalizedTrack::totalDuration).max().orElse(0);

        // Snapshot metadata
        Map<String, String> metadata = spec.metadata() != null
                ? Map.copyOf(spec.metadata())
                : Map.of();

        log.info("Timeline normalized: id={} tracks={} captions={} duration={}s",
                timelineId, tracks.size(), captionLayers.size(), totalDuration);

        return new NormalizedTimeline(
                timelineId, projectId, tracks, captionLayers,
                outputProfile, totalDuration, metadata);
    }

    /**
     * Normalize tracks: sort by layer (ascending), then by type priority (VIDEO > AUDIO > SUBTITLE).
     * Clips within each track are sorted by timelineStart (ascending).
     */
    private List<NormalizedTrack> normalizeTracks(List<TimelineTrack> tracks) {
        List<NormalizedTrack> normalized = new ArrayList<>();
        for (TimelineTrack track : tracks) {
            normalized.add(normalizeTrack(track));
        }
        // Sort by layer, then type priority
        normalized.sort(Comparator
                .comparingInt(NormalizedTrack::layer)
                .thenComparing(t -> typePriority(t.type())));
        return List.copyOf(normalized);
    }

    /**
     * Normalize a single track: validate and sort clips.
     */
    private NormalizedTrack normalizeTrack(TimelineTrack track) {
        if (track.clips() == null) {
            return new NormalizedTrack(
                    track.id(), track.name(), mapTrackType(track.type()),
                    track.layer(), track.muted(), List.of());
        }

        List<NormalizedClip> clips = new ArrayList<>();
        for (TimelineClip clip : track.clips()) {
            clips.add(normalizeClip(clip));
        }
        // Sort clips by timelineStart, then by original order
        clips.sort(Comparator.comparingDouble(NormalizedClip::timelineStart));

        return new NormalizedTrack(
                track.id(), track.name(), mapTrackType(track.type()),
                track.layer(), track.muted(), List.copyOf(clips));
    }

    /**
     * Normalize a single clip: validate fields, fail-closed on unsupported effects.
     */
    private NormalizedClip normalizeClip(TimelineClip clip) {
        if (clip.assetRef() == null) {
            throw TimelineCompileException.missingField("clip.assetRef (clip=" + clip.id() + ")");
        }
        if (!clip.hasValidTiming()) {
            throw TimelineCompileException.invalidData("clip.timing",
                    "clip=" + clip.id() + " in=" + clip.assetInPoint() + " out=" + clip.assetOutPoint());
        }

        // v0: fail-closed on unsupported effects
        if (clip.effects() != null && !clip.effects().isEmpty()) {
            for (TimelineClipEffect effect : clip.effects()) {
                if (effect.effectKey() != null && !effect.effectKey().isBlank()) {
                    throw TimelineCompileException.unsupported("clipEffect",
                            "clip=" + clip.id() + " effectKey=" + effect.effectKey()
                                    + " — v0 does not compile clip effects");
                }
            }
        }

        NormalizedAssetRef assetRef = normalizeAssetRef(clip.assetRef());

        return new NormalizedClip(
                clip.id(), assetRef, clip.timelineStart(),
                clip.assetInPoint(), clip.assetOutPoint(), clip.clipDuration());
    }

    /**
     * Normalize an asset reference.
     */
    private NormalizedAssetRef normalizeAssetRef(TimelineAssetRef ref) {
        if (ref.assetId() == null || ref.assetId().isBlank()) {
            throw TimelineCompileException.missingField("assetRef.assetId");
        }
        Map<String, String> metadata = ref.metadata() != null
                ? Map.copyOf(ref.metadata())
                : Map.of();
        return new NormalizedAssetRef(
                ref.assetId(), ref.storageUri(), ref.format(),
                ref.duration(), ref.width(), ref.height(), metadata);
    }

    /**
     * Normalize caption layers from text overlays.
     */
    private List<NormalizedCaptionLayer> normalizeCaptionLayers(List<TimelineTextOverlay> overlays) {
        if (overlays == null || overlays.isEmpty()) {
            return List.of();
        }
        List<NormalizedCaptionLayer> layers = new ArrayList<>();
        for (TimelineTextOverlay overlay : overlays) {
            if (overlay.text() == null || overlay.text().isBlank()) {
                throw TimelineCompileException.invalidData("textOverlay.text",
                        "overlay=" + overlay.id() + " — text must not be blank");
            }
            if (overlay.duration() <= 0) {
                throw TimelineCompileException.invalidData("textOverlay.duration",
                        "overlay=" + overlay.id() + " — duration must be positive");
            }
            layers.add(new NormalizedCaptionLayer(
                    overlay.id(), overlay.text(), overlay.fontFamily(),
                    overlay.fontSize(), overlay.color(), overlay.positionX(),
                    overlay.positionY(), overlay.startTime(), overlay.duration(),
                    overlay.backgroundColor()));
        }
        return List.copyOf(layers);
    }

    /**
     * Normalize output profile with deterministic defaults.
     */
    private NormalizedOutputProfile normalizeOutputProfile(TimelineOutputSpec spec) {
        String format = spec.format() != null && !spec.format().isBlank()
                ? spec.format() : "mp4";
        String resolution = spec.resolution() != null && !spec.resolution().isBlank()
                ? spec.resolution() : "1920x1080";
        double frameRate = spec.frameRate() > 0 ? spec.frameRate() : 30.0;
        String videoCodec = spec.videoCodec() != null && !spec.videoCodec().isBlank()
                ? spec.videoCodec() : "h264";
        int videoBitrate = spec.videoBitrate() > 0 ? spec.videoBitrate() : 8000;
        String pixelFormat = spec.pixelFormat() != null && !spec.pixelFormat().isBlank()
                ? spec.pixelFormat() : "yuv420p";

        // Audio spec defaults
        String audioCodec = "aac";
        int sampleRate = 48000;
        int channels = 2;
        int audioBitrate = 128;
        if (spec.audioSpec() != null) {
            audioCodec = spec.audioSpec().codec() != null ? spec.audioSpec().codec() : "aac";
            sampleRate = spec.audioSpec().sampleRate() > 0 ? spec.audioSpec().sampleRate() : 48000;
            channels = spec.audioSpec().channels() > 0 ? spec.audioSpec().channels() : 2;
            audioBitrate = spec.audioSpec().bitrateKbps() > 0 ? spec.audioSpec().bitrateKbps() : 128;
        }

        return new NormalizedOutputProfile(
                format, resolution, frameRate, videoCodec, videoBitrate,
                audioCodec, sampleRate, channels, audioBitrate, pixelFormat);
    }

    /**
     * Map TimelineTrack.TrackType to NormalizedTrack.TrackType.
     */
    private NormalizedTrack.TrackType mapTrackType(TimelineTrack.TrackType type) {
        if (type == null) return NormalizedTrack.TrackType.VIDEO;
        return switch (type) {
            case VIDEO -> NormalizedTrack.TrackType.VIDEO;
            case AUDIO -> NormalizedTrack.TrackType.AUDIO;
            case SUBTITLE -> NormalizedTrack.TrackType.SUBTITLE;
        };
    }

    /**
     * Track type priority for sorting (lower = higher priority).
     */
    private int typePriority(NormalizedTrack.TrackType type) {
        return switch (type) {
            case VIDEO -> 0;
            case AUDIO -> 1;
            case SUBTITLE -> 2;
        };
    }
}
