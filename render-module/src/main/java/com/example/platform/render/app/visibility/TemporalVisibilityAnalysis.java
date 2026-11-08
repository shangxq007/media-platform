package com.example.platform.render.app.visibility;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.shared.time.FrameRate;
import java.util.List;
import java.util.Objects;

/** Deterministic, read-only temporal visibility analysis result. */
public record TemporalVisibilityAnalysis(
        String tenantId,
        String projectId,
        String revisionId,
        String requestId,
        MediaTime requestStart,
        MediaTime requestEnd,
        FrameRate frameRate,
        boolean crossTrackPruningEnabled,
        List<TrackVisibility> tracks,
        List<OptimizationCandidate> candidates,
        List<String> globalReasons,
        String analysisDigest) {

    public TemporalVisibilityAnalysis {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(revisionId, "revisionId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(requestStart, "requestStart");
        Objects.requireNonNull(requestEnd, "requestEnd");
        Objects.requireNonNull(frameRate, "frameRate");
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        globalReasons = globalReasons == null ? List.of() : List.copyOf(globalReasons);
        Objects.requireNonNull(analysisDigest, "analysisDigest");
    }
}
