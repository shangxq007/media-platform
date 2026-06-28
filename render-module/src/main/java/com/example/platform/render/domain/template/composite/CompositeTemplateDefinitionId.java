package com.example.platform.render.domain.template.composite;

public record CompositeTemplateDefinitionId(String value) {
    public CompositeTemplateDefinitionId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("CompositeTemplateDefinitionId must not be blank");
    }
}
