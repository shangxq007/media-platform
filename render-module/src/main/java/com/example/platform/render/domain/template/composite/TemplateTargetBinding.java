package com.example.platform.render.domain.template.composite;

import com.example.platform.render.domain.template.TemplateTargetRole;
import java.util.Map;

/**
 * Maps a parent target role to a child target role.
 * Internal domain model.
 */
public record TemplateTargetBinding(
        TemplateTargetRole parentRole,
        CompositeTemplateChildId childId,
        TemplateTargetRole childRole,
        boolean required,
        Map<String, String> safeMetadata) {

    public TemplateTargetBinding {
        if (parentRole == null) throw new IllegalArgumentException("Parent role must not be null");
        if (childId == null) throw new IllegalArgumentException("Child ID must not be null");
        if (childRole == null) throw new IllegalArgumentException("Child role must not be null");
    }
}
