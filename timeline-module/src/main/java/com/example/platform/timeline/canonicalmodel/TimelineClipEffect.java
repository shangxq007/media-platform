package com.example.platform.timeline.canonicalmodel;

import java.util.Map;
import java.util.Objects;

/**
 * Opaque effect payload carried through the canonical gate and semantic merge.
 *
 * <p>CNM1 effect-preservation contract: effect payloads attached to a clip
 * survive load -&gt; semantic merge -&gt; merged revision -&gt; reload UNLESS the
 * merge operation explicitly changes/deletes them. Effects are NEVER
 * semantically merged (no effect merge framework); they are preserved
 * target/source-side verbatim. Unknown effect internals stay opaque JSON.
 */
public record TimelineClipEffect(String id, String effectKey, Map<String, Object> parameters) {

    public TimelineClipEffect {
        Objects.requireNonNull(effectKey, "effectKey");
        if (effectKey.isBlank()) {
            throw new IllegalArgumentException("effectKey must not be blank");
        }
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
