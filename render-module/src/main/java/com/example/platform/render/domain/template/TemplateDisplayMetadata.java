package com.example.platform.render.domain.template;

/**
 * Display metadata for a template — name, description, icon reference.
 * Internal domain model. No provider/storage internals.
 */
public record TemplateDisplayMetadata(
        String name,
        String description,
        String iconRef) {}
