package com.example.platform.render.domain.template;

/**
 * Constraint on template application — bounds and rules.
 * Internal domain model.
 */
public record TemplateConstraint(
        String constraintType,
        String value,
        String description) {}
