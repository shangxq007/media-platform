package com.example.platform.render.domain.timeline.compile;

import java.util.List;
import java.util.Map;

/**
 * Normalized internal timeline representation for deterministic compile.
 *
 * <p>Provider-neutral, deterministic, and stable for the same input TimelineRevision.
 * This is the canonical compile input for ArtifactDependencyGraph generation.</p>
 *
 * @param timelineId      source timeline identifier
 * @param projectId       project identifier
 * @param tracks          ordered list of normalized tracks (sorted by layer, then type)
 * @param captionLayers   caption/subtitle layers derived from text overlays
 * @param outputProfile   normalized output profile
 * @param totalDuration   total timeline duration in seconds
 * @param metadata        immutable metadata snapshot
 */
public record NormalizedTimeline(
        String timelineId,
        String projectId,
        List<NormalizedTrack> tracks,
        List<NormalizedCaptionLayer> captionLayers,
        NormalizedOutputProfile outputProfile,
        double totalDuration,
        Map<String, String> metadata) {

    /**
     * Returns all clips across all tracks in timeline order.
     */
    public List<NormalizedClip> allClips() {
        return tracks.stream()
                .flatMap(t -> t.clips().stream())
                .toList();
    }

    /**
     * Returns all unique asset refs across all clips.
     */
    public List<NormalizedAssetRef> allAssetRefs() {
        return allClips().stream()
                .map(NormalizedClip::assetRef)
                .distinct()
                .toList();
    }

    /**
     * Returns true if this timeline has caption/subtitle content.
     */
    public boolean hasCaptions() {
        return captionLayers != null && !captionLayers.isEmpty();
    }
}
