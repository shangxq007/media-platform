package com.example.platform.compositeresource.domain;

public record ExternalSemanticComponent(
        CompositeComponentId componentId,
        ExactSemanticObjectPin pin) implements SemanticComponent {
    public ExternalSemanticComponent {
        if (componentId == null || pin == null) {
            throw new IllegalArgumentException("ExternalSemanticComponent requires component identity and exact pin");
        }
    }
}
