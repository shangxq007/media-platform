package com.example.platform.render.app.operation;

import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding;
import com.example.platform.timeline.semantics.temporal.TemporalMapping;
import java.util.List;

/**
 * Small typed backend projection for H4 consumers. It exposes canonical WHAT,
 * validation and semantic change expectations; no provider HOW or raw syntax.
 */
public record AddMediaClipPreview(
        String operation,
        String planDigest,
        String targetTimelineId,
        String baseRevisionId,
        String baseContentHash,
        MediaStreamSourceBinding sourceBinding,
        MediaClip.TimeRange sourceRange,
        MediaClip.TimeRange placement,
        TemporalMapping temporalMapping,
        List<String> expectedChangedCanonicalObjects,
        List<String> validation,
        List<String> capabilityRequirements,
        List<String> warnings,
        List<String> failures,
        String candidateContentHash) {

    public AddMediaClipPreview {
        expectedChangedCanonicalObjects = List.copyOf(expectedChangedCanonicalObjects);
        validation = List.copyOf(validation);
        capabilityRequirements = List.copyOf(capabilityRequirements);
        warnings = List.copyOf(warnings);
        failures = List.copyOf(failures);
    }
}
