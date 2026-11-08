package com.example.platform.render.app.visibility;

import com.example.platform.render.domain.renderplan.RenderExecutionCoverage;
import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import com.example.platform.timeline.canonical.TrackType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One canonical clip's typed timeline/source visibility projection. */
public record ClipVisibility(
        String trackId,
        String clipId,
        String artifactId,
        String contentDigest,
        TrackType trackType,
        RenderExecutionCoverage timelineRange,
        RenderSampleWindow sourceRange,
        Optional<RenderExecutionCoverage> timelineCoverage,
        Optional<RenderSampleWindow> sourceWindow,
        List<FrameSample> frameSamples,
        VisibilityContribution contribution,
        VisibilityDecision decision,
        DecodeDependency decodeDependency,
        List<String> reasons) {

    public ClipVisibility {
        Objects.requireNonNull(trackId, "trackId");
        Objects.requireNonNull(clipId, "clipId");
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(contentDigest, "contentDigest");
        Objects.requireNonNull(trackType, "trackType");
        Objects.requireNonNull(timelineRange, "timelineRange");
        Objects.requireNonNull(sourceRange, "sourceRange");
        Objects.requireNonNull(timelineCoverage, "timelineCoverage");
        Objects.requireNonNull(sourceWindow, "sourceWindow");
        frameSamples = frameSamples == null ? List.of() : List.copyOf(frameSamples);
        Objects.requireNonNull(contribution, "contribution");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(decodeDependency, "decodeDependency");
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
