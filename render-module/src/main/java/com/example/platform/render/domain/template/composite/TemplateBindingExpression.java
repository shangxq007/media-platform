package com.example.platform.render.domain.template.composite;

/**
 * Inert binding expression — safe string only.
 * Internal domain model. Not executed, no SpEL/JS/Python.
 */
public record TemplateBindingExpression(String expression) {
    public TemplateBindingExpression {
        if (expression == null || expression.isBlank())
            throw new IllegalArgumentException("Binding expression must not be blank");
    }
}
