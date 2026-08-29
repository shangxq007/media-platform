package com.example.platform.render.domain.effect;

import java.util.Map;
import java.util.Objects;

/**
 * Semantic target for a baseline effect operation.
 * References timeline entities only — no provider/storage internals.
 * Immutable. Internal domain model.
 */
public record BaselineEffectOperationTarget(
        BaselineEffectOperationTargetType targetType,
        String targetId,
        Map<String, String> safeMetadata
) {
    public BaselineEffectOperationTarget {
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(targetId, "targetId must not be null");
        if (targetId.isBlank()) throw new IllegalArgumentException("targetId must not be blank");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
