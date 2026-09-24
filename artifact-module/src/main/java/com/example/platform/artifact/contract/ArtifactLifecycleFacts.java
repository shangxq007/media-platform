package com.example.platform.artifact.contract;

import com.example.platform.artifact.domain.ArtifactState;
import java.time.Instant;
import java.util.Objects;

public record ArtifactLifecycleFacts(ArtifactState state, Instant observedAt) {
    public ArtifactLifecycleFacts {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(observedAt, "observedAt");
    }
}
