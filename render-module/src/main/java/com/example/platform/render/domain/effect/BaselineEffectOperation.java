package com.example.platform.render.domain.effect;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A single baseline effect operation in a plan.
 * Immutable. Internal domain model.
 */
public record BaselineEffectOperation(
        BaselineEffectOperationId id,
        BaselineEffectOperationType type,
        BaselineEffectOperationTarget target,
        List<BaselineEffectOperationParameter> parameters,
        BaselineEffectOperationSource source,
        Map<String, String> safeMetadata
) {
    public BaselineEffectOperation {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(source, "source must not be null");
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}
