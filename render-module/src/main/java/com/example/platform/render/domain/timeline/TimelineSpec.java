package com.example.platform.render.domain.timeline;

import java.util.List;
import java.util.Map;

/**
 * Internal timeline specification model.
 *
 * <p>This is the platform's canonical timeline representation. It is a JSON-serializable
 * model that can be converted to/from other formats (e.g., OpenTimelineIO).</p>
 *
 * <h3>OTIO Note</h3>
 * <p>OpenTimelineIO is an interchange format, not a renderer. The
 * {@link OpenTimelineioAdapter} provides conversion between this model and OTIO JSON.</p>
 *
 * @param id              unique timeline identifier
 * @param name            human-readable timeline name
 * @param description     optional description
 * @param tracks          ordered list of tracks
 * @param textOverlays    text overlays to render on top of video
 * @param outputSpec      output specification
 * @param totalDuration   total timeline duration in seconds
 * @param metadata        arbitrary metadata key-value pairs
 */
public record TimelineSpec(
        String id,
        String name,
        String description,
        List<TimelineTrack> tracks,
        List<TimelineTextOverlay> textOverlays,
        TimelineOutputSpec outputSpec,
        double totalDuration,
        Map<String, String> metadata) {

    /**
     * Creates a minimal timeline spec with a single video track.
     */
    public static TimelineSpec create(String id, String name, TimelineOutputSpec outputSpec) {
        return new TimelineSpec(id, name, null,
                List.of(TimelineTrack.of(id + "-v1", "Video 1", TimelineTrack.TrackType.VIDEO)),
                List.of(), outputSpec, 0, Map.of());
    }

    /**
     * Validates this timeline specification.
     *
     * @return the validation result
     */
    public TimelineValidationResult validate() {
        java.util.List<String> errors = new java.util.ArrayList<>();
        java.util.List<String> warnings = new java.util.ArrayList<>();

        // Check for at least one track
        if (tracks == null || tracks.isEmpty()) {
            errors.add("Timeline must have at least one track");
        }

        // Validate each track
        if (tracks != null) {
            for (TimelineTrack track : tracks) {
                if (track.clips() == null || track.clips().isEmpty()) {
                    warnings.add("Track '" + track.name() + "' has no clips");
                }
                // Validate clips
                if (track.clips() != null) {
                    for (TimelineClip clip : track.clips()) {
                        if (!clip.hasValidTiming()) {
                            errors.add("Clip '" + clip.id() + "' has invalid timing: "
                                    + "in=" + clip.assetInPoint() + " out=" + clip.assetOutPoint());
                        }
                        if (clip.assetRef() == null) {
                            errors.add("Clip '" + clip.id() + "' has no asset reference");
                        }
                    }
                }
            }
        }

        // Validate output spec
        if (outputSpec == null) {
            errors.add("Output specification is required");
        } else {
            if (outputSpec.resolution() == null || outputSpec.resolution().isBlank()) {
                errors.add("Output resolution is required");
            }
            if (outputSpec.format() == null || outputSpec.format().isBlank()) {
                errors.add("Output format is required");
            }
        }

        // Validate text overlays
        if (textOverlays != null) {
            for (TimelineTextOverlay overlay : textOverlays) {
                if (overlay.text() == null || overlay.text().isBlank()) {
                    errors.add("Text overlay '" + overlay.id() + "' has empty text");
                }
                if (overlay.duration() <= 0) {
                    errors.add("Text overlay '" + overlay.id() + "' has invalid duration: " + overlay.duration());
                }
            }
        }

        if (errors.isEmpty()) {
            return warnings.isEmpty()
                    ? TimelineValidationResult.ok()
                    : TimelineValidationResult.okWithWarnings(warnings);
        }
        return TimelineValidationResult.invalid(errors);
    }

    /**
     * Returns the total duration of the timeline (end of the last clip across all tracks).
     */
    public double computeDuration() {
        if (tracks == null || tracks.isEmpty()) {
            return 0;
        }
        return tracks.stream()
                .mapToDouble(TimelineTrack::totalDuration)
                .max()
                .orElse(0);
    }
}
