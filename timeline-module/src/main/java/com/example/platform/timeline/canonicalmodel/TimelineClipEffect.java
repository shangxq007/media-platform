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
     * FIFTH CORRECTION — deep deterministic complete local semantic
     * fingerprint: id + effectKey + parameters with deep canonical ordering
     * (nested Maps key-sorted recursively), typed JSON encoding (number vs
     * string vs boolean vs null distinct), collision-resistant (JSON escaping
     * — no delimiter ambiguity). Delegates to the single local Effect semantic
     * codec authority ({@link EffectCanonicalSemantics}).
     */
    public String semanticFingerprint() {
        return EffectCanonicalSemantics.semanticFingerprint(this);
    }
}
