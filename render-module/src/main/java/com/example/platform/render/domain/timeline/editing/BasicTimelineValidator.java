package com.example.platform.render.domain.timeline.editing;

import com.example.platform.render.domain.timeline.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Validator for timeline editing operations.
 * Pure, side-effect free. Internal domain model.
 *
 * <p>Validates structural rules for tracks, clips, captions, watermarks,
 * effects, transitions, and output profile. Does not mutate input.</p>
 *
 * <p>Complexity target: O(n log n) for normal timeline validation.</p>
 */
public final class BasicTimelineValidator {

    private static final Set<String> FORBIDDEN_METADATA_KEYWORDS = Set.of(
            "bucket", "objectKey", "signedUrl", "providerName", "backendName",
            "rawCommand", "filtergraph", "filter_complex");

    private static final Set<String> ALLOWED_CONTAINERS = Set.of("mp4", "mov", "webm");
    private static final Set<String> ALLOWED_VIDEO_CODECS = Set.of("h264", "h265", "hevc", "vp8", "vp9");

    private BasicTimelineValidator() {}

    /**
     * Validates a timeline specification.
     * Returns typed validation issues. Does not mutate input.
     */
    public static List<TimelineValidationIssue> validate(TimelineSpec timeline) {
        if (timeline == null) {
            return List.of(TimelineValidationIssue.blocking(
                    TimelineValidationIssueCode.INVALID_TIMELINE_ID,
                    "timeline", "Timeline must not be null"));
        }

        List<TimelineValidationIssue> issues = new ArrayList<>();

        // Timeline id
        if (timeline.id() == null || timeline.id().isBlank()) {
            issues.add(TimelineValidationIssue.blocking(
                    TimelineValidationIssueCode.INVALID_TIMELINE_ID,
                    "timeline.id", "Timeline id must not be blank"));
        }

        // Output profile
        validateOutputProfile(timeline.outputSpec(), issues);

        // Tracks
        validateTracks(timeline.tracks(), issues);

        // Text overlays (captions)
        validateTextOverlays(timeline.textOverlays(), issues);

        // Metadata safety
        validateMetadata(timeline.metadata(), "timeline.metadata", issues);

        return issues;
    }

    /**
     * Validates that an edit request is safe to apply.
     */
    public static List<TimelineValidationIssue> validateRequest(TimelineEditRequest request) {
        if (request == null) {
            return List.of(TimelineValidationIssue.blocking(
                    TimelineValidationIssueCode.INVALID_TIMELINE_ID,
                    "request", "Request must not be null"));
        }

        List<TimelineValidationIssue> issues = new ArrayList<>();

        if (request.requestId() == null || request.requestId().isBlank()) {
            issues.add(TimelineValidationIssue.blocking(
                    TimelineValidationIssueCode.INVALID_TIMELINE_ID,
                    "request.requestId", "Request id must not be blank"));
        }

        if (request.timelineId() == null || request.timelineId().isBlank()) {
            issues.add(TimelineValidationIssue.blocking(
                    TimelineValidationIssueCode.INVALID_TIMELINE_ID,
                    "request.timelineId", "Timeline id must not be blank"));
        }

        // Validate metadata safety
        validateMetadata(request.safeMetadata(), "request.safeMetadata", issues);

        return issues;
    }

    // --- Track validation ---

    private static void validateTracks(List<TimelineTrack> tracks, List<TimelineValidationIssue> issues) {
        if (tracks == null || tracks.isEmpty()) {
            issues.add(TimelineValidationIssue.error(
                    TimelineValidationIssueCode.TRACK_NOT_FOUND,
                    "timeline.tracks", "Timeline must have at least one track"));
            return;
        }

        Set<String> trackIds = new HashSet<>();
        for (int i = 0; i < tracks.size(); i++) {
            TimelineTrack track = tracks.get(i);
            String fieldPrefix = "timeline.tracks[" + i + "]";

            // Track id
            if (track.id() == null || track.id().isBlank()) {
                issues.add(TimelineValidationIssue.error(
                        TimelineValidationIssueCode.INVALID_TIMELINE_ID,
                        fieldPrefix + ".id", "Track id must not be blank"));
            } else if (!trackIds.add(track.id())) {
                issues.add(TimelineValidationIssue.error(
                        TimelineValidationIssueCode.DUPLICATE_TRACK_ID,
                        fieldPrefix + ".id", "Duplicate track id: " + track.id()));
            }

            // Track type
            if (track.type() == null) {
                issues.add(TimelineValidationIssue.error(
                        TimelineValidationIssueCode.INVALID_TIMELINE_ID,
                        fieldPrefix + ".type", "Track type must not be null"));
            }

            // Clips
            validateClips(track.clips(), track.id(), issues, fieldPrefix);
        }
    }

    // --- Clip validation ---

    private static void validateClips(List<TimelineClip> clips, String trackId,
                                       List<TimelineValidationIssue> issues, String fieldPrefix) {
        if (clips == null) return;

        Set<String> clipIds = new HashSet<>();
        for (int i = 0; i < clips.size(); i++) {
            TimelineClip clip = clips.get(i);
            String clipPrefix = fieldPrefix + ".clips[" + i + "]";

            // Clip id
            if (clip.id() == null || clip.id().isBlank()) {
                issues.add(TimelineValidationIssue.error(
                        TimelineValidationIssueCode.DUPLICATE_CLIP_ID,
                        clipPrefix + ".id", "Clip id must not be blank"));
            } else if (!clipIds.add(clip.id())) {
                issues.add(TimelineValidationIssue.error(
                        TimelineValidationIssueCode.DUPLICATE_CLIP_ID,
                        clipPrefix + ".id", "Duplicate clip id: " + clip.id()));
            }

            // Clip timing
            if (!clip.hasValidTiming()) {
                issues.add(TimelineValidationIssue.error(
                        TimelineValidationIssueCode.INVALID_CLIP_TIME_RANGE,
                        clipPrefix, "Invalid clip timing: in=" + clip.assetInPoint()
                                + " out=" + clip.assetOutPoint() + " dur=" + clip.clipDuration()));
            }

            // Clip asset ref
            if (clip.assetRef() == null) {
                issues.add(TimelineValidationIssue.error(
                        TimelineValidationIssueCode.CLIP_NOT_FOUND,
                        clipPrefix + ".assetRef", "Clip must have an asset reference"));
            }

            // Clip effects — check for forbidden capabilities
            if (clip.effects() != null) {
                for (int j = 0; j < clip.effects().size(); j++) {
                    TimelineClipEffect effect = clip.effects().get(j);
                    validateEffect(effect, clip.id(), clipPrefix + ".effects[" + j + "]", issues);
                }
            }
        }
    }

    // --- Effect validation ---

    private static void validateEffect(TimelineClipEffect effect, String clipId,
                                        String fieldPrefix, List<TimelineValidationIssue> issues) {
        if (effect.effectKey() == null || effect.effectKey().isBlank()) {
            issues.add(TimelineValidationIssue.error(
                    TimelineValidationIssueCode.EFFECT_CAPABILITY_NOT_FOUND,
                    fieldPrefix + ".effectKey", "Effect key must not be blank"));
            return;
        }

        // Check for forbidden patterns
        String key = effect.effectKey().toLowerCase();
        if (key.contains("filtergraph") || key.contains("filter_complex")) {
            issues.add(TimelineValidationIssue.blocking(
                    TimelineValidationIssueCode.ARBITRARY_FILTERGRAPH_FORBIDDEN,
                    fieldPrefix + ".effectKey", "Arbitrary FFmpeg filtergraph forbidden"));
        }
        if (key.contains("rawcommand") || key.contains("shell command")) {
            issues.add(TimelineValidationIssue.blocking(
                    TimelineValidationIssueCode.RAW_PROVIDER_COMMAND_FORBIDDEN,
                    fieldPrefix + ".effectKey", "Raw provider command forbidden"));
        }

        // Check parameters for forbidden content (convert to string map for validation)
        if (effect.parameters() != null) {
            Map<String, String> stringParams = new java.util.HashMap<>();
            for (var entry : effect.parameters().entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    stringParams.put(entry.getKey(), entry.getValue().toString());
                }
            }
            validateMetadata(stringParams, fieldPrefix + ".parameters", issues);
        }
    }

    // --- Text overlay (caption) validation ---

    private static void validateTextOverlays(List<TimelineTextOverlay> overlays,
                                              List<TimelineValidationIssue> issues) {
        if (overlays == null) return;

        Set<String> overlayIds = new HashSet<>();
        for (int i = 0; i < overlays.size(); i++) {
            TimelineTextOverlay overlay = overlays.get(i);
            String fieldPrefix = "timeline.textOverlays[" + i + "]";

            // Id
            if (overlay.id() == null || overlay.id().isBlank()) {
                issues.add(TimelineValidationIssue.error(
                        TimelineValidationIssueCode.DUPLICATE_CAPTION_ID,
                        fieldPrefix + ".id", "Text overlay id must not be blank"));
            } else if (!overlayIds.add(overlay.id())) {
                issues.add(TimelineValidationIssue.error(
                        TimelineValidationIssueCode.DUPLICATE_CAPTION_ID,
                        fieldPrefix + ".id", "Duplicate text overlay id: " + overlay.id()));
            }

            // Text
            if (overlay.text() == null || overlay.text().isBlank()) {
                issues.add(TimelineValidationIssue.error(
                        TimelineValidationIssueCode.INVALID_CAPTION_TIME_RANGE,
                        fieldPrefix + ".text", "Text overlay text must not be blank"));
            }

            // Timing
            if (overlay.startTime() < 0) {
                issues.add(TimelineValidationIssue.error(
                        TimelineValidationIssueCode.INVALID_CAPTION_TIME_RANGE,
                        fieldPrefix + ".startTime", "Start time must be non-negative"));
            }
            if (overlay.duration() <= 0) {
                issues.add(TimelineValidationIssue.error(
                        TimelineValidationIssueCode.INVALID_CAPTION_TIME_RANGE,
                        fieldPrefix + ".duration", "Duration must be positive"));
            }
        }
    }

    // --- Output profile validation ---

    private static void validateOutputProfile(TimelineOutputSpec outputSpec,
                                               List<TimelineValidationIssue> issues) {
        if (outputSpec == null) {
            issues.add(TimelineValidationIssue.error(
                    TimelineValidationIssueCode.INVALID_OUTPUT_PROFILE,
                    "timeline.outputSpec", "Output specification is required"));
            return;
        }

        if (outputSpec.resolution() == null || outputSpec.resolution().isBlank()) {
            issues.add(TimelineValidationIssue.error(
                    TimelineValidationIssueCode.INVALID_OUTPUT_PROFILE,
                    "timeline.outputSpec.resolution", "Output resolution is required"));
        }

        if (outputSpec.format() == null || outputSpec.format().isBlank()) {
            issues.add(TimelineValidationIssue.error(
                    TimelineValidationIssueCode.INVALID_OUTPUT_PROFILE,
                    "timeline.outputSpec.format", "Output format is required"));
        } else if (!ALLOWED_CONTAINERS.contains(outputSpec.format().toLowerCase())) {
            issues.add(TimelineValidationIssue.warning(
                    TimelineValidationIssueCode.INVALID_OUTPUT_PROFILE,
                    "timeline.outputSpec.format",
                    "Container not in allowlist: " + outputSpec.format()));
        }

        if (outputSpec.videoCodec() != null && !ALLOWED_VIDEO_CODECS.contains(outputSpec.videoCodec().toLowerCase())) {
            issues.add(TimelineValidationIssue.warning(
                    TimelineValidationIssueCode.INVALID_OUTPUT_PROFILE,
                    "timeline.outputSpec.videoCodec",
                    "Video codec not in allowlist: " + outputSpec.videoCodec()));
        }

        if (outputSpec.frameRate() <= 0) {
            issues.add(TimelineValidationIssue.error(
                    TimelineValidationIssueCode.INVALID_OUTPUT_PROFILE,
                    "timeline.outputSpec.frameRate", "Frame rate must be positive"));
        }
    }

    // --- Metadata safety ---

    private static void validateMetadata(Map<String, String> metadata, String fieldPrefix,
                                          List<TimelineValidationIssue> issues) {
        if (metadata == null || metadata.isEmpty()) return;

        for (var entry : metadata.entrySet()) {
            String key = entry.getKey() != null ? entry.getKey().toLowerCase() : "";
            String value = entry.getValue() != null ? entry.getValue().toLowerCase() : "";
            for (String keyword : FORBIDDEN_METADATA_KEYWORDS) {
                if (key.contains(keyword) || value.contains(keyword)) {
                    issues.add(TimelineValidationIssue.blocking(
                            TimelineValidationIssueCode.STORAGE_INTERNALS_FORBIDDEN,
                            fieldPrefix + "." + entry.getKey(),
                            "Metadata contains forbidden keyword: " + keyword));
                    break;
                }
            }
        }
    }

    /**
     * Converts typed issues to the simple TimelineValidationResult format.
     */
    public static TimelineValidationResult toSimpleResult(List<TimelineValidationIssue> issues) {
        boolean hasBlocking = issues.stream().anyMatch(i ->
                i.severity() == TimelineValidationIssueSeverity.BLOCKING
                        || i.severity() == TimelineValidationIssueSeverity.ERROR);
        boolean hasWarnings = issues.stream().anyMatch(i ->
                i.severity() == TimelineValidationIssueSeverity.WARNING);

        if (hasBlocking) {
            List<String> errors = issues.stream()
                    .filter(i -> i.severity() == TimelineValidationIssueSeverity.BLOCKING
                            || i.severity() == TimelineValidationIssueSeverity.ERROR)
                    .map(i -> "[" + i.code() + "] " + i.message())
                    .toList();
            return TimelineValidationResult.invalid(errors);
        }

        if (hasWarnings) {
            List<String> warnings = issues.stream()
                    .filter(i -> i.severity() == TimelineValidationIssueSeverity.WARNING)
                    .map(i -> "[" + i.code() + "] " + i.message())
                    .toList();
            return TimelineValidationResult.okWithWarnings(warnings);
        }

        return TimelineValidationResult.ok();
    }
}
