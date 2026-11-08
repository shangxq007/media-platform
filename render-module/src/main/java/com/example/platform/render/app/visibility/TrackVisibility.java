package com.example.platform.render.app.visibility;

import com.example.platform.render.domain.renderplan.RenderExecutionCoverage;
import com.example.platform.timeline.canonical.TrackType;
import java.util.List;
import java.util.Objects;

/** Per-track temporal projection; stacking is never inferred from list order. */
public record TrackVisibility(
        String trackId,
        TrackType trackType,
        int canonicalIndex,
        boolean stackingProven,
        List<RenderExecutionCoverage> gaps,
        List<ClipVisibility> clips) {

    public TrackVisibility {
        Objects.requireNonNull(trackId, "trackId");
        Objects.requireNonNull(trackType, "trackType");
        gaps = gaps == null ? List.of() : List.copyOf(gaps);
        clips = clips == null ? List.of() : List.copyOf(clips);
    }
}
