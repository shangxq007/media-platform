package com.example.platform.timeline.semantics.effect;

import java.util.Objects;

/**
 * ROADMAP20 correction R6-A: explicit typed authored Effect target for a
 * single clip on a single track — WHERE the effect applies (authored
 * membership), fully separated from {@code applicationRange} (WHEN).
 *
 * <p>Frozen principle
 * {@code EFFECT_MEMBERSHIP_IS_EXPLICIT_TYPED_AUTHORED_RELATION_NOT_TEMPORAL_HEURISTIC_V1}.
 */
public record ClipEffectTarget(String trackId, String clipId) implements EffectTarget {

    public ClipEffectTarget {
        Objects.requireNonNull(trackId, "trackId");
        Objects.requireNonNull(clipId, "clipId");
        if (trackId.isBlank()) {
            throw new IllegalArgumentException("trackId must not be blank");
        }
        if (clipId.isBlank()) {
            throw new IllegalArgumentException("clipId must not be blank");
        }
    }
}
