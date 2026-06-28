package com.example.platform.render.domain.template.composite;

public record CompositeTemplateChildId(String value) {
    public CompositeTemplateChildId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("CompositeTemplateChildId must not be blank");
    }
}
