package com.example.platform.render.domain.template.composite;

public record CompositeTemplateExpansionPlanId(String value) {
    public CompositeTemplateExpansionPlanId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("CompositeTemplateExpansionPlanId must not be blank");
    }
}
