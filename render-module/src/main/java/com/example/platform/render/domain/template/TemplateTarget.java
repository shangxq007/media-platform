package com.example.platform.render.domain.template;

import java.util.Map;

/**
 * Maps a semantic role to a concrete asset or content reference.
 * Internal domain model.
 *
 * <p>targetId is a Product ID when targetType=PRODUCT.
 * No raw URLs, local paths, or storage internals.</p>
 */
public record TemplateTarget(
        TemplateTargetRole role,
        TemplateTargetType targetType,
        String targetId,
        Map<String, String> safeMetadata) {

    public TemplateTarget {
        if (role == null) throw new IllegalArgumentException("Target role must not be null");
        if (targetType == null) throw new IllegalArgumentException("Target type must not be null");
        if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException("Target ID must not be blank");
    }

    public static TemplateTarget product(TemplateTargetRole role, String productId) {
        return new TemplateTarget(role, TemplateTargetType.PRODUCT, productId, Map.of());
    }
}
