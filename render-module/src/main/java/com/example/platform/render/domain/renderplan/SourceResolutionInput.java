package com.example.platform.render.domain.renderplan;

import com.example.platform.shared.identity.ArtifactId;
import java.util.Map;
import java.util.Objects;

/**
 * Per-artifact source resolution state map consumed by planning (C4). Typed keys
 * (ArtifactId), never a fingerprint input — resolution state affects plan status
 * and diagnostics but never the deterministic plan fingerprint (C7).
 *
 * @param states artifact -> resolution state (absent artifact == unresolved at plan time)
 */
public record SourceResolutionInput(Map<ArtifactId, RenderSourceResolutionState> states) {

    public SourceResolutionInput {
        Objects.requireNonNull(states, "states");
        states = Map.copyOf(states);
    }

    public static SourceResolutionInput empty() {
        return new SourceResolutionInput(Map.of());
    }
}
