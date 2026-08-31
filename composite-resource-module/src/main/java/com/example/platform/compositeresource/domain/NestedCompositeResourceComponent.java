package com.example.platform.compositeresource.domain;

public record NestedCompositeResourceComponent(
        CompositeComponentId componentId,
        CompositeResourceVersionPin pin) implements SemanticComponent {
    public NestedCompositeResourceComponent {
        if (componentId == null || pin == null) {
            throw new IllegalArgumentException("NestedCompositeResourceComponent requires component identity and exact pin");
        }
    }
}
