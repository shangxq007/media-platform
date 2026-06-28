package com.example.platform.render.domain.template.composite;

public record CompositeTemplateChildOrder(int value) {
    public CompositeTemplateChildOrder {
        if (value < 0) throw new IllegalArgumentException("Child order must be non-negative");
    }
}
