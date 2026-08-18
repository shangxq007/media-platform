package com.example.platform.timeline.canonicalmodel;

import java.util.Map;
import java.util.Objects;

/**
 * Canonical Effect semantic value object carried through the canonical gate and
 * semantic merge (FOURTH CORRECTION — single local semantic authority).
 *
 * <p>Effects are authored Timeline semantics, NOT opaque payloads: local
 * semantic identity is owned here ({@link #semanticFingerprint()}), and the
 * production diff/patch/merge path consumes this authority. Effect payloads
 * attached to a clip survive load -&gt; semantic merge -&gt; merged revision -&gt;
 * reload UNLESS the merge operation explicitly changes/deletes them.
 */
public record TimelineClipEffect(String id, String effectKey, Map<String, Object> parameters) {

    public TimelineClipEffect {
        Objects.requireNonNull(effectKey, "effectKey");
        if (effectKey.isBlank()) {
            throw new IllegalArgumentException("effectKey must not be blank");
        }
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    /**
     * FOURTH CORRECTION — deterministic complete local semantic fingerprint:
     * id + effectKey + parameters with canonical sorted key ordering.
     * Map insertion order is fingerprint-neutral. Provider/runtime fields are
     * excluded by construction (no provider fields exist in this value object).
     */
    public String semanticFingerprint() {
        StringBuilder sb = new StringBuilder();
        sb.append("id=").append(id == null ? "" : id).append(';')
          .append("key=").append(effectKey).append(';')
          .append("params=");
        if (!parameters.isEmpty()) {
            new java.util.TreeMap<>(parameters).forEach((k, v) ->
                    sb.append(k).append('=').append(v == null ? "" : v).append(','));
        }
        return sb.toString();
    }
}
